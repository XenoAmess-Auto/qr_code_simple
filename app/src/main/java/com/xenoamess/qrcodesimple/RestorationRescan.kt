package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片扫描失败后的修复重试编排。
 * 利用 [QRCodeRestorationManager] 生成多种图像处理变体（灰度、对比度、锐化、二值化、缩放等），
 * 逐个重扫，直到某一变体识别成功或全部失败。
 */
object RestorationRescan {

    /** 最多尝试的变体数量（不含原图）。 */
    const val MAX_VARIANTS = 8

    /**
     * 对原始位图执行修复重扫。
     * @return 首个识别到结果的变体对应的扫描结果；全部失败时返回空列表。
     */
    suspend fun rescan(context: Context, original: Bitmap): List<QRCodeScanner.ScanResult> =
        withContext(Dispatchers.IO) {
            val variants = try {
                QRCodeRestorationManager.restoreQRCode(original)
            } catch (e: Throwable) {
                emptyList()
            }
            // 第一个变体是原图本身，已被常规流程扫过，跳过
            for (variant in variants.drop(1).take(MAX_VARIANTS)) {
                val results = QRCodeScanner.scanSync(context, variant)
                if (results.isNotEmpty()) {
                    return@withContext results
                }
            }
            emptyList()
        }
}

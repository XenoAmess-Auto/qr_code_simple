package com.xenoamess.qrcodesimple.scanner

import android.graphics.Bitmap
import android.util.Log
import com.xenoamess.qrcodesimple.QRCodeScanner

/**
 * F-Droid 构建的 ML Kit 引擎 stub：proprietary ML Kit 依赖在 F-Droid 产物中
 * 完全缺席，引擎直接返回空结果，扫描管线回退到 ZXing / WeChatQR / 自研解码器。
 * 构建脚本（-Pfdroid）用此文件替换 main 源集中的同名实现。
 */
internal object MlKitEngine {

    private const val TAG = "QRCodeScanner"

    suspend fun scan(bitmap: Bitmap): List<QRCodeScanner.ScanResult> {
        Log.d(TAG, "ML Kit engine unavailable in F-Droid build, skipped")
        return emptyList()
    }
}

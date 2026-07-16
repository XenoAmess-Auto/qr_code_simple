package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * RestorationRescan 编排测试：
 * 常规扫描失败的低质量图片，经图像修复变体重扫后应能识别。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RestorationRescanTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 单测环境不加载 OpenCV native 库，确保 WeChatQRCode 保持未初始化状态
    }

    /**
     * 生成一张极低动态范围的 QR 图：黑模块压缩到 120、白模块压缩到 135（整体动态范围 15）。
     * ZXing HybridBinarizer 对动态范围过小的块按纯色处理，因此直接扫描必然失败；
     * 修复变体中的全局二值化（阈值 128）可完美恢复黑白模块。
     */
    private fun createLowDynamicRangeQr(content: String): Bitmap {
        val source = BarcodeGenerator.generate(
            content,
            BarcodeGenerator.BarcodeConfig(format = com.xenoamess.qrcodesimple.data.BarcodeFormat.QR_CODE)
        )!!
        val width = source.width
        val height = source.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val gray = if (Color.red(pixels[i]) < 128) 120 else 135
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    @Test
    fun `rescan recovers low dynamic range qr content`() {
        val content = "RESTORATION-TEST-12345"
        val degraded = createLowDynamicRangeQr(content)

        // 前置条件：低动态范围原图直接扫描应失败，否则测试没有意义
        val directResults = QRCodeScanner.scanSync(context, degraded)
        assertTrue(directResults.isEmpty(), "Precondition failed: low dynamic range QR should not scan directly")

        val results = runBlocking { RestorationRescan.rescan(context, degraded) }
        assertTrue(results.isNotEmpty(), "Restoration rescan should find results")
        assertEquals(content, results.first().text)
    }

    @Test
    fun `rescan on blank bitmap returns empty`() {
        val blank = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val results = runBlocking { RestorationRescan.rescan(context, blank) }
        assertTrue(results.isEmpty(), "Blank bitmap should produce no results")
    }
}

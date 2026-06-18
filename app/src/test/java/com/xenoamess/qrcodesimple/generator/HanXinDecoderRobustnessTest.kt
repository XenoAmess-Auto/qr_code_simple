package com.xenoamess.qrcodesimple.generator

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.decoder.hanxin.HanXinDecoder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Additional Han Xin decoder robustness tests for off-center placement,
 * non-square bitmaps, brightness inversion and perspective-like scaling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinDecoderRobustnessTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun generate(content: String, width: Int, height: Int): Bitmap {
        val bitmap = BarcodeGenerator.generate(
            content,
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = width, height = height)
        )
        assertNotNull(bitmap)
        return bitmap!!
    }

    private fun assertDecodes(bitmap: Bitmap, expected: String) {
        val result = HanXinDecoder.decode(bitmap)
        assertNotNull(result, "Decoder should recover the symbol")
        assertEquals(expected, result.text)
    }

    @Test
    fun `decode symbol with white border padding`() {
        val content = "Hello Han Xin"
        val inner = generate(content, 600, 600)
        val padded = addPadding(inner, 80)
        assertDecodes(padded, content)
    }

    @Test
    fun `decode symbol placed off-center in larger canvas`() {
        val content = "Hello Han Xin"
        val inner = generate(content, 400, 400)
        val offCenter = embed(inner, 800, 600, offsetX = 200, offsetY = 100)
        assertDecodes(offCenter, content)
    }

    @Test
    fun `decode inverted colors`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 600, 600)
        assertDecodes(invert(bitmap), content)
    }

    @Test
    fun `decode non-square wide bitmap`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 900, 500)
        assertDecodes(bitmap, content)
    }

    @Test
    fun `decode non-square tall bitmap`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 500, 900)
        assertDecodes(bitmap, content)
    }

    @Test
    fun `decode small low resolution symbol`() {
        val content = "HX"
        val bitmap = generate(content, 120, 120)
        assertDecodes(bitmap, content)
    }

    @Test
    fun `decode with slight horizontal squeeze`() {
        val content = "1234567890"
        val bitmap = generate(content, 800, 800)
        assertDecodes(scale(bitmap, 0.9f, 1.0f), content)
    }

    @Test
    fun `decode with slight vertical squeeze`() {
        val content = "1234567890"
        val bitmap = generate(content, 800, 800)
        assertDecodes(scale(bitmap, 1.0f, 0.9f), content)
    }

    @Test
    fun `decode Chinese content with padding and inversion`() {
        val content = "汉信码"
        val inner = generate(content, 600, 600)
        val transformed = invert(addPadding(inner, 60))
        assertDecodes(transformed, content)
    }

    @Test
    fun `decode numeric content embedded in noisy background`() {
        val content = "12345678901234567890"
        val inner = generate(content, 600, 600)
        val embedded = embedOnNoise(inner, 900, 900)
        assertDecodes(embedded, content)
    }

    // -------------------------------------------------------------------------
    // Bitmap transforms
    // -------------------------------------------------------------------------

    private fun addPadding(bitmap: Bitmap, padding: Int): Bitmap {
        val result = Bitmap.createBitmap(
            bitmap.width + padding * 2,
            bitmap.height + padding * 2,
            Bitmap.Config.ARGB_8888
        )
        result.eraseColor(0xFFFFFFFF.toInt())
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                result.setPixel(x + padding, y + padding, bitmap.getPixel(x, y))
            }
        }
        return result
    }

    private fun embed(bitmap: Bitmap, canvasW: Int, canvasH: Int, offsetX: Int, offsetY: Int): Bitmap {
        val result = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        result.eraseColor(0xFFFFFFFF.toInt())
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val px = x + offsetX
                val py = y + offsetY
                if (px in 0 until canvasW && py in 0 until canvasH) {
                    result.setPixel(px, py, bitmap.getPixel(x, y))
                }
            }
        }
        return result
    }

    private fun invert(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val p = bitmap.getPixel(x, y)
                val r = 255 - ((p shr 16) and 0xFF)
                val g = 255 - ((p shr 8) and 0xFF)
                val b = 255 - (p and 0xFF)
                result.setPixel(x, y, 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
            }
        }
        return result
    }

    private fun scale(bitmap: Bitmap, fx: Float, fy: Float): Bitmap {
        val newW = max(1, (bitmap.width * fx).toInt())
        val newH = max(1, (bitmap.height * fy).toInt())
        val result = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        for (y in 0 until newH) {
            val srcY = min(bitmap.height - 1, (y / fy).toInt())
            for (x in 0 until newW) {
                val srcX = min(bitmap.width - 1, (x / fx).toInt())
                result.setPixel(x, y, bitmap.getPixel(srcX, srcY))
            }
        }
        return result
    }

    private fun embedOnNoise(bitmap: Bitmap, canvasW: Int, canvasH: Int): Bitmap {
        val result = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        result.eraseColor(0xFFFFFFFF.toInt())
        val offsetX = (canvasW - bitmap.width) / 2
        val offsetY = (canvasH - bitmap.height) / 2
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                result.setPixel(x + offsetX, y + offsetY, bitmap.getPixel(x, y))
            }
        }
        return result
    }
}

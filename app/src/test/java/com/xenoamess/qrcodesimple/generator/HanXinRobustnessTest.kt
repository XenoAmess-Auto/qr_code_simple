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
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Han Xin Code decoder robustness tests using synthetic perturbations.
 *
 * These tests exercise detection robustness (rotation, scaling, blur, soft
 * noise and inversion). Reed-Solomon error correction is enabled in the
 * decoder, so isolated module errors are tolerated automatically; heavy
 * salt-and-pepper damage that exceeds the RS correction capability is covered
 * separately in HanXinDecoderInternalTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinRobustnessTest {

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
    fun `decode scaled down`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 800, 800)
        assertDecodes(scale(bitmap, 0.5f), content)
    }

    @Test
    fun `decode scaled up`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 400, 400)
        assertDecodes(scale(bitmap, 1.5f), content)
    }

    @Test
    fun `decode with soft random noise`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 800, 800)
        assertDecodes(addRandomNoise(bitmap, strength = 40), content)
    }

    @Test
    fun `decode after average blur`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 800, 800)
        assertDecodes(averageBlur(bitmap, radius = 1), content)
    }

    @Test
    fun `decode rotated 90 degrees`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 800, 800)
        assertDecodes(rotate90(bitmap), content)
    }

    @Test
    fun `decode rotated 180 degrees`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 800, 800)
        assertDecodes(rotate180(bitmap), content)
    }

    @Test
    fun `decode rotated 270 degrees`() {
        val content = "Hello Han Xin"
        val bitmap = generate(content, 800, 800)
        assertDecodes(rotate270(bitmap), content)
    }

    @Test
    fun `decode numeric content after blur and scale`() {
        val content = "12345678901234567890"
        val bitmap = generate(content, 800, 800)
        val transformed = averageBlur(scale(bitmap, 0.75f), radius = 1)
        assertDecodes(transformed, content)
    }

    // -------------------------------------------------------------------------
    // Bitmap transforms (nearest-neighbour to avoid anti-aliasing artifacts)
    // -------------------------------------------------------------------------

    private fun scale(bitmap: Bitmap, factor: Float): Bitmap {
        val newW = max(1, (bitmap.width * factor).toInt())
        val newH = max(1, (bitmap.height * factor).toInt())
        val result = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
        for (y in 0 until newH) {
            val srcY = min(bitmap.height - 1, (y / factor).toInt())
            for (x in 0 until newW) {
                val srcX = min(bitmap.width - 1, (x / factor).toInt())
                result.setPixel(x, y, bitmap.getPixel(srcX, srcY))
            }
        }
        return result
    }

    private fun addRandomNoise(bitmap: Bitmap, strength: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val random = Random(42)
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val pixel = bitmap.getPixel(x, y)
                val noise = random.nextInt(-strength, strength + 1)
                val r = (pixel shr 16 and 0xFF) + noise
                val g = (pixel shr 8 and 0xFF) + noise
                val b = (pixel and 0xFF) + noise
                val newPixel = 0xFF000000.toInt() or
                        (r.coerceIn(0, 255) shl 16) or
                        (g.coerceIn(0, 255) shl 8) or
                        b.coerceIn(0, 255)
                result.setPixel(x, y, newPixel)
            }
        }
        return result
    }

    private fun averageBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val area = (2 * radius + 1) * (2 * radius + 1)
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                var r = 0
                var g = 0
                var b = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val py = (y + dy).coerceIn(0, bitmap.height - 1)
                        val px = (x + dx).coerceIn(0, bitmap.width - 1)
                        val pixel = bitmap.getPixel(px, py)
                        r += pixel shr 16 and 0xFF
                        g += pixel shr 8 and 0xFF
                        b += pixel and 0xFF
                    }
                }
                val newPixel = 0xFF000000.toInt() or
                        ((r / area) shl 16) or
                        ((g / area) shl 8) or
                        (b / area)
                result.setPixel(x, y, newPixel)
            }
        }
        return result
    }

    private fun rotate90(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(h, w, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                result.setPixel(h - 1 - y, x, bitmap.getPixel(x, y))
            }
        }
        return result
    }

    private fun rotate180(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                result.setPixel(w - 1 - x, h - 1 - y, bitmap.getPixel(x, y))
            }
        }
        return result
    }

    private fun rotate270(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val result = Bitmap.createBitmap(h, w, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                result.setPixel(y, w - 1 - x, bitmap.getPixel(x, y))
            }
        }
        return result
    }
}

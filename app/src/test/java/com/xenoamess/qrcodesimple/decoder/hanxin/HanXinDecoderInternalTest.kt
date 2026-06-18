package com.xenoamess.qrcodesimple.decoder.hanxin

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinDecoderInternalTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `decode clean generated bitmap`() {
        val bitmap = BarcodeGenerator.generate(
            "Hello Han Xin",
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 800, height = 800)
        )!!
        val result = HanXinDecoder.decode(bitmap)
        assertNotNull(result)
        assertEquals("Hello Han Xin", result.text)
    }

    @Test
    fun `decode clean generated bitmap with Chinese content`() {
        val bitmap = BarcodeGenerator.generate(
            "汉信码",
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 800, height = 800)
        )!!
        val result = HanXinDecoder.decode(bitmap)
        assertNotNull(result)
        assertEquals("汉信码", result.text)
    }

    @Test
    fun `decode after corrupting one data module`() {
        val content = "HX"
        val encoded = HanXinEncoder.encode(
            content,
            width = 230,
            height = 230,
            requestedEccLevel = 1,
            requestedVersion = 1
        )!!
        val size = encoded.version * 2 + 21
        val bitmap = encoded.bitmap.copy(encoded.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        // Corrupt a single data module well away from function patterns.
        flipModule(bitmap, 11, 11, size)

        val result = HanXinDecoder.decode(bitmap)
        assertNotNull(result, "RS correction should recover one corrupted module")
        assertEquals(content, result.text)
    }

    @Test
    fun `decode after corrupting first data modules`() {
        val content = "HX"
        val encoded = HanXinEncoder.encode(
            content,
            width = 230,
            height = 230,
            requestedEccLevel = 1,
            requestedVersion = 1
        )!!
        val size = encoded.version * 2 + 21
        assertEquals(23, size)

        val bitmap = encoded.bitmap.copy(encoded.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        // Corrupt the first data bits (row 0, columns 9..13) by inverting those
        // modules. Columns 9..13 are data in a version-1 symbol; columns 14 and
        // beyond are function info or finder patterns and must not be touched.
        // This forces the decoder off the direct parse path and exercises
        // Reed-Solomon error correction.
        for (mx in 9..13) {
            flipModule(bitmap, mx, 0, size)
        }

        val result = HanXinDecoder.decode(bitmap)
        assertNotNull(result, "RS correction should recover the corrupted symbol")
        assertEquals(content, result.text)
    }

    @Test
    fun `decode fails when errors exceed correction capability`() {
        val content = "HX"
        val encoded = HanXinEncoder.encode(
            content,
            width = 230,
            height = 230,
            requestedEccLevel = 1,
            requestedVersion = 1
        )!!
        val size = encoded.version * 2 + 21
        val bitmap = encoded.bitmap.copy(encoded.bitmap.config ?: Bitmap.Config.ARGB_8888, true)

        // Corrupt a large central data region. For version-1 L1 the total
        // correction budget is 88 byte errors, so 100+ flipped modules should
        // exceed the capability.
        for (mx in 2 until size - 2) {
            for (my in 9 until 15) {
                flipModule(bitmap, mx, my, size)
            }
        }

        val result = HanXinDecoder.decode(bitmap)
        assertNull(result, "Decoding should fail when errors exceed RS capability")
    }

    private fun flipModule(bitmap: Bitmap, mx: Int, my: Int, size: Int) {
        val moduleWidth = bitmap.width / size
        val moduleHeight = bitmap.height / size
        val startX = mx * moduleWidth
        val startY = my * moduleHeight
        val centerColor = bitmap.getPixel(startX + moduleWidth / 2, startY + moduleHeight / 2)
        val invertedColor = if (centerColor == Color.BLACK) Color.WHITE else Color.BLACK
        for (x in startX until startX + moduleWidth) {
            for (y in startY until startY + moduleHeight) {
                bitmap.setPixel(x, y, invertedColor)
            }
        }
    }
}

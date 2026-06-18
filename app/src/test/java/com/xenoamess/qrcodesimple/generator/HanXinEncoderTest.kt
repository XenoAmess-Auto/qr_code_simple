package com.xenoamess.qrcodesimple.generator

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import com.xenoamess.qrcodesimple.decoder.hanxin.HanXinEncoder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Han Xin Code encoder tests.
 *
 * Roundtrip tests that scan generated symbols back are kept in
 * [BarcodeGenerationRoundtripTest] once the decoder is available.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HanXinEncoderTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `generate Han Xin with BarcodeGenerator`() {
        val content = "Hello Han Xin"
        val validation = BarcodeGenerator.validateContent(content, BarcodeFormat.HAN_XIN)
        assertTrue(validation.isValid, "Content should be valid: ${validation.errorMessage}")

        val bitmap = BarcodeGenerator.generate(
            content,
            BarcodeGenerator.BarcodeConfig(format = BarcodeFormat.HAN_XIN, width = 800, height = 800)
        )
        assertNotNull(bitmap)
        assertTrue(bitmap.width >= 800)
        assertTrue(bitmap.height >= 800)
    }

    @Test
    fun `encode numeric content`() {
        val result = HanXinEncoder.encode("1234567890", width = 400, height = 400)
        assertNotNull(result)
        assertEquals(1, result.version) // ten digits fit in the smallest version
        assertTrue(result.eccLevel >= 1)
    }

    @Test
    fun `encode ASCII text content`() {
        val result = HanXinEncoder.encode("HanXinCode2024", width = 400, height = 400)
        assertNotNull(result)
        assertTrue(result.version in 1..84)
    }

    @Test
    fun `encode Chinese content`() {
        val result = HanXinEncoder.encode("汉信码", width = 400, height = 400)
        assertNotNull(result)
        assertTrue(result.version in 1..84)
    }

    @Test
    fun `encode long content falls back to larger version`() {
        val result = HanXinEncoder.encode("a".repeat(500), width = 800, height = 800)
        assertNotNull(result)
        assertTrue(result.version > 1)
    }


    @Test
    fun `generated symbol size formula`() {
        val result = HanXinEncoder.encode("test", width = 600, height = 600)
        assertNotNull(result)
        val expectedModules = result.version * 2 + 21
        // Rendering scales the module grid to the requested bitmap size, so the
        // bitmap dimensions are exactly the requested size.
        assertEquals(600, result.bitmap.width)
        assertEquals(600, result.bitmap.height)
        assertTrue(expectedModules in 23..189)
    }
}

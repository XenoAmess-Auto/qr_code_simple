package com.xenoamess.qrcodesimple.generator

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.ResultMetadataType
import com.xenoamess.qrcodesimple.AdvancedBarcodeGenerator
import com.xenoamess.qrcodesimple.QRCodeScanner
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FallbackStyleShapeFillTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun bitmapsAreDifferent(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return true
        for (x in 0 until a.width) {
            for (y in 0 until b.height) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) return true
            }
        }
        return false
    }

    @Test
    fun `UPC-EAN Extension shape and fill changes appearance and default scans back`() {
        val content = "12"
        val default = AdvancedBarcodeGenerator.StyleConfig(
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.DEFAULT,
            moduleFillRatio = 1.0f
        )
        val circle = AdvancedBarcodeGenerator.StyleConfig(
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.CIRCLE,
            moduleFillRatio = 0.8f
        )
        val rounded = AdvancedBarcodeGenerator.StyleConfig(
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.ROUNDED,
            moduleFillRatio = 0.8f
        )

        val defaultBitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.UPC_EAN_EXTENSION, 800, default)
        val circleBitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.UPC_EAN_EXTENSION, 800, circle)
        val roundedBitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.UPC_EAN_EXTENSION, 800, rounded)

        assertNotNull(defaultBitmap, "Default style should generate UPC-EAN Extension")
        assertNotNull(circleBitmap, "Circle style should generate UPC-EAN Extension")
        assertNotNull(roundedBitmap, "Rounded style should generate UPC-EAN Extension")

        assertTrue(
            bitmapsAreDifferent(defaultBitmap!!, circleBitmap!!),
            "Circle shape should change UPC-EAN Extension appearance"
        )
        assertTrue(
            bitmapsAreDifferent(defaultBitmap, roundedBitmap!!),
            "Rounded shape should change UPC-EAN Extension appearance"
        )

        val results = QRCodeScanner.scanSync(context, defaultBitmap)
        assertTrue(results.isNotEmpty(), "Default UPC-EAN Extension should scan back")
        val actual = results.firstOrNull { it.format == com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION }?.text
            ?: results.first().resultMetadata?.get(ResultMetadataType.UPC_EAN_EXTENSION) as? String
        assertEquals(content, actual, "UPC-EAN Extension content should roundtrip")
    }

    @Test
    fun `generate-only fallback formats apply module shape`() {
        val formats = listOf(
            BarcodeFormat.AZTEC_RUNE,
            BarcodeFormat.CODE_ONE
        )
        for (format in formats) {
            val content = BarcodeFormatTestFixtures.validContent(format)
            val default = AdvancedBarcodeGenerator.StyleConfig(
                moduleShape = AdvancedBarcodeGenerator.ModuleShape.DEFAULT,
                moduleFillRatio = 1.0f
            )
            val circle = AdvancedBarcodeGenerator.StyleConfig(
                moduleShape = AdvancedBarcodeGenerator.ModuleShape.CIRCLE,
                moduleFillRatio = 0.7f
            )

            val defaultBitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, default)
            val circleBitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, circle)

            assertNotNull(defaultBitmap, "Default style should generate $format")
            assertNotNull(circleBitmap, "Circle style should generate $format")
            assertTrue(
                bitmapsAreDifferent(defaultBitmap!!, circleBitmap!!),
                "Circle shape should change $format appearance"
            )
        }
    }
}

package com.xenoamess.qrcodesimple.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.zxing.ResultMetadataType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
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
class FormatStyleCapabilitiesTest {

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
    fun `sanitize keeps all fields for QR Code`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            foregroundColor = Color.RED,
            backgroundColor = Color.BLUE,
            cornerRadius = 0.3f,
            logoScale = 0.15f,
            ecLevel = ErrorCorrectionLevel.Q,
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.CIRCLE,
            moduleFillRatio = 0.5f,
            positionPatternShape = AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE,
            gradientAngle = 45f,
            gradientStops = listOf(
                AdvancedBarcodeGenerator.ColorStop(0f, Color.RED),
                AdvancedBarcodeGenerator.ColorStop(1f, Color.BLUE)
            )
        )
        val sanitized = AdvancedBarcodeGenerator.sanitize(style, BarcodeFormat.QR_CODE)
        assertEquals(style, sanitized)
    }

    @Test
    fun `sanitize resets QR-only fields for non-QR formats`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            foregroundColor = Color.RED,
            cornerRadius = 0.3f,
            ecLevel = ErrorCorrectionLevel.Q,
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.CIRCLE,
            moduleFillRatio = 0.5f,
            positionPatternShape = AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE
        )
        val sanitized = AdvancedBarcodeGenerator.sanitize(style, BarcodeFormat.CODE_128)
        assertEquals(Color.RED, sanitized.foregroundColor)
        assertEquals(0.3f, sanitized.cornerRadius)
        assertEquals(ErrorCorrectionLevel.H, sanitized.ecLevel)
        assertEquals(AdvancedBarcodeGenerator.ModuleShape.SQUARE, sanitized.moduleShape)
        assertEquals(0.8f, sanitized.moduleFillRatio)
        assertEquals(AdvancedBarcodeGenerator.PositionPatternShape.SQUARE, sanitized.positionPatternShape)
    }

    @Test
    fun `sanitize preserves ecLevel for supported non-QR formats`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.Q)
        for (format in listOf(
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF417,
            BarcodeFormat.HAN_XIN,
            BarcodeFormat.MICRO_QR,
            BarcodeFormat.GRID_MATRIX
        )) {
            val sanitized = AdvancedBarcodeGenerator.sanitize(style, format)
            assertEquals(ErrorCorrectionLevel.Q, sanitized.ecLevel, "EC level should be preserved for $format")
        }
    }

    @Test
    fun `generate ignores module shape for non-QR format`() {
        val content = "CODE128"
        val circle = AdvancedBarcodeGenerator.StyleConfig(
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.CIRCLE,
            moduleFillRatio = 0.5f
        )
        val square = AdvancedBarcodeGenerator.StyleConfig(
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.SQUARE,
            moduleFillRatio = 1.0f
        )
        val circleBitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.CODE_128, 800, circle)
        val squareBitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.CODE_128, 800, square)
        assertNotNull(circleBitmap)
        assertNotNull(squareBitmap)
        assertTrue(
            !bitmapsAreDifferent(circleBitmap!!, squareBitmap!!),
            "Non-QR format should ignore module shape and fill ratio"
        )
    }

    @Test
    fun `Aztec different EC levels produce different bitmaps`() {
        val content = "https://example.com/aztec/level/test"
        val low = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.AZTEC, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.L)
        )
        val high = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.AZTEC, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.H)
        )
        assertNotNull(low)
        assertNotNull(high)
        assertTrue(bitmapsAreDifferent(low!!, high!!), "Aztec bitmaps should differ by EC level")
    }

    @Test
    fun `PDF417 different EC levels produce different bitmaps`() {
        val content = "PDF417 test content for EC level comparison"
        val low = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.PDF417, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.L)
        )
        val high = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.PDF417, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.H)
        )
        assertNotNull(low)
        assertNotNull(high)
        assertTrue(bitmapsAreDifferent(low!!, high!!), "PDF417 bitmaps should differ by EC level")
    }

    @Test
    fun `Han Xin different EC levels produce different bitmaps`() {
        val content = "汉信码测试内容"
        val low = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.HAN_XIN, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.L)
        )
        val high = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.HAN_XIN, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.H)
        )
        assertNotNull(low)
        assertNotNull(high)
        assertTrue(bitmapsAreDifferent(low!!, high!!), "Han Xin bitmaps should differ by EC level")
    }

    @Test
    fun `Micro QR supports EC levels without crashing`() {
        val content = "ABC123"
        val low = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.MICRO_QR, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.L)
        )
        val high = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.MICRO_QR, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.H)
        )
        assertNotNull(low, "Micro QR should generate with L EC level")
        assertNotNull(high, "Micro QR should generate with H EC level")
    }

    @Test
    fun `Grid Matrix different EC levels produce different bitmaps`() {
        val content = "中文网格矩阵码测试"
        val low = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.GRID_MATRIX, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.L)
        )
        val high = AdvancedBarcodeGenerator.generateStyled(
            content, BarcodeFormat.GRID_MATRIX, 800,
            AdvancedBarcodeGenerator.StyleConfig(ecLevel = ErrorCorrectionLevel.H)
        )
        assertNotNull(low)
        assertNotNull(high)
        assertTrue(bitmapsAreDifferent(low!!, high!!), "Grid Matrix bitmaps should differ by EC level")
    }

    private fun actualRoundtripText(format: BarcodeFormat, results: List<QRCodeScanner.ScanResult>): String? {
        val first = results.firstOrNull() ?: return null
        return when (format) {
            BarcodeFormat.UPC_EAN_EXTENSION -> {
                results.firstOrNull { it.format == com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION }?.text
                    ?: first.resultMetadata?.get(ResultMetadataType.UPC_EAN_EXTENSION) as? String
            }
            else -> first.text
        }
    }

    @Test
    fun `EC level roundtrip for supported formats`() {
        val formats = listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF417,
            BarcodeFormat.HAN_XIN,
            BarcodeFormat.MICRO_QR
        )
        val contents = mapOf(
            BarcodeFormat.QR_CODE to "https://example.com",
            BarcodeFormat.AZTEC to "Aztec Test",
            BarcodeFormat.PDF417 to "PDF417 roundtrip test content",
            BarcodeFormat.HAN_XIN to "汉信码",
            BarcodeFormat.MICRO_QR to "MicroQR"
        )
        for (format in formats) {
            val content = contents[format] ?: continue
            for (ecLevel in listOf(
                ErrorCorrectionLevel.L,
                ErrorCorrectionLevel.M,
                ErrorCorrectionLevel.Q,
                ErrorCorrectionLevel.H
            )) {
                val style = AdvancedBarcodeGenerator.StyleConfig(ecLevel = ecLevel)
                val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, style)
                assertNotNull(bitmap, "Should generate $format with EC $ecLevel")
                val results = QRCodeScanner.scanSync(context, bitmap!!)
                assertTrue(results.isNotEmpty(), "Should scan back $format with EC $ecLevel")
                assertEquals(
                    content,
                    actualRoundtripText(format, results),
                    "Roundtrip content should match for $format with EC $ecLevel"
                )
            }
        }
    }

    @Test
    fun `QR-only styles roundtrip for QR Code`() {
        val content = "https://example.com"
        val styles = listOf(
            AdvancedBarcodeGenerator.StyleConfig(
                moduleShape = AdvancedBarcodeGenerator.ModuleShape.CIRCLE,
                moduleFillRatio = 0.85f,
                positionPatternShape = AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE
            ),
            AdvancedBarcodeGenerator.StyleConfig(
                moduleShape = AdvancedBarcodeGenerator.ModuleShape.SQUARE,
                moduleFillRatio = 1.0f,
                positionPatternShape = AdvancedBarcodeGenerator.PositionPatternShape.SQUARE
            )
        )
        for (style in styles) {
            val bitmap = AdvancedBarcodeGenerator.generateStyled(
                content, BarcodeFormat.QR_CODE, 800, style
            )
            assertNotNull(bitmap, "Should generate QR with $style")
            val results = QRCodeScanner.scanSync(context, bitmap!!)
            assertTrue(results.isNotEmpty(), "Should scan back QR with $style")
            assertEquals(
                content,
                actualRoundtripText(BarcodeFormat.QR_CODE, results),
                "Roundtrip content should match for QR with $style"
            )
        }
    }

    @Test
    fun `QR-only styles are sanitized for non-QR formats and roundtrip`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.CIRCLE,
            moduleFillRatio = 0.5f,
            positionPatternShape = AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE,
            ecLevel = ErrorCorrectionLevel.H
        )
        for (format in BarcodeFormat.entries.filter {
            it.isScannable && it != BarcodeFormat.QR_CODE
        }) {
            val content = BarcodeFormatTestFixtures.validContent(format)
            val sanitized = AdvancedBarcodeGenerator.sanitize(style, format)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, sanitized)
            assertNotNull(bitmap, "Should generate $format with sanitized QR-only style")
            val results = QRCodeScanner.scanSync(context, bitmap!!)
            assertTrue(results.isNotEmpty(), "Should scan back $format with sanitized QR-only style")
            assertEquals(
                BarcodeFormatTestFixtures.expectedRoundtripText(format, content),
                actualRoundtripText(format, results),
                "Roundtrip content should match for $format with sanitized QR-only style"
            )
        }
    }
}

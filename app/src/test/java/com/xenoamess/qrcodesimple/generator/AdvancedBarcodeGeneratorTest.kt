package com.xenoamess.qrcodesimple.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
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
class AdvancedBarcodeGeneratorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun generateContent(format: BarcodeFormat): String {
        return when (format) {
            BarcodeFormat.QR_CODE -> "https://example.com"
            BarcodeFormat.DATA_MATRIX -> "Hello DM"
            BarcodeFormat.AZTEC -> "Aztec Test"
            BarcodeFormat.PDF417 -> "PDF417 Test"
            BarcodeFormat.CODE_128 -> "CODE128-TEST"
            BarcodeFormat.CODE_39 -> "ABC-123"
            BarcodeFormat.CODE_93 -> "CODE93-TEST"
            BarcodeFormat.EAN_13 -> "1234567890128"
            BarcodeFormat.EAN_8 -> "12345670"
            BarcodeFormat.UPC_A -> "123456789012"
            BarcodeFormat.UPC_E -> "01234565"
            BarcodeFormat.CODABAR -> "12345"
            BarcodeFormat.ITF -> "1234567890"
            BarcodeFormat.UPC_EAN_EXTENSION -> "12"
            BarcodeFormat.RSS_14 -> "1234567890123"
            BarcodeFormat.RSS_EXPANDED -> "(01)12345678901231"
            BarcodeFormat.MICRO_QR -> "MicroQR"
            BarcodeFormat.PHARMACODE -> "1234"
            BarcodeFormat.PLESSEY -> "1A2B"
            BarcodeFormat.MSI_PLESSEY -> "12345"
            BarcodeFormat.TELEPEN -> "TELEPEN"
            BarcodeFormat.MAXICODE -> "[)>\u001E01\u001D961Z00004981\u001DUPSN\u001D06X610\u001D\u001D0011/1000\u001D\u001DN\u001D\u001D\u001E\u0004"
            BarcodeFormat.HAN_XIN -> "汉信码"
            BarcodeFormat.UNKNOWN -> "UNKNOWN"
        }
    }

    private fun expectedRoundtripText(format: BarcodeFormat, content: String): String {
        return when (format) {
            BarcodeFormat.RSS_14 -> content + "1"
            BarcodeFormat.RSS_EXPANDED -> content.replace("(", "[").replace(")", "]")
            else -> content
        }
    }

    private fun actualRoundtripText(format: BarcodeFormat, results: List<QRCodeScanner.ScanResult>): String? {
        val first = results.firstOrNull() ?: return null
        return when (format) {
            BarcodeFormat.UPC_EAN_EXTENSION -> {
                results.firstOrNull { it.format == com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION }?.text
                    ?: first.resultMetadata?.get(com.google.zxing.ResultMetadataType.UPC_EAN_EXTENSION) as? String
            }
            BarcodeFormat.RSS_EXPANDED -> first.text.replace("(", "[").replace(")", "]")
            else -> first.text
        }
    }

    @Test
    fun `roundtrip all formats with default style`() {
        for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
            val content = generateContent(format)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, AdvancedBarcodeGenerator.StyleConfig())
            assertNotNull(bitmap, "Should generate $format")

            val results = QRCodeScanner.scanSync(context, bitmap!!)
            assertTrue(results.isNotEmpty(), "Should scan back $format")

            val expected = expectedRoundtripText(format, content)
            val actual = actualRoundtripText(format, results)
            assertEquals(expected, actual, "Roundtrip content should match for $format")
        }
    }

    @Test
    fun `roundtrip all formats with custom colors`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            foregroundColor = Color.parseColor("#1976D2"),
            backgroundColor = Color.WHITE
        )
        for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
            val content = generateContent(format)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, style)
            assertNotNull(bitmap, "Should generate $format with custom colors")

            val results = QRCodeScanner.scanSync(context, bitmap!!)
            assertTrue(results.isNotEmpty(), "Should scan back $format with custom colors")
        }
    }

    @Test
    fun `roundtrip QR with logo`() {
        val logo = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(logo).drawColor(Color.RED)
        val style = AdvancedBarcodeGenerator.StyleConfig(logoBitmap = logo, logoScale = 0.15f)
        val content = "https://example.com"
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap, "Should generate QR with logo")

        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "Should scan back QR with logo")
        assertEquals(content, results.first().text, "Roundtrip content should match for QR with logo")
    }

    @Test
    fun `generateStyled works for all formats with default style`() {
        for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
            val content = generateContent(format)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, AdvancedBarcodeGenerator.StyleConfig())
            assertNotNull(bitmap, "Failed to generate $format with default style for content: $content")
        }
    }

    @Test
    fun `generateStyled works for all formats with custom colors`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            foregroundColor = Color.parseColor("#1976D2"),
            backgroundColor = Color.WHITE
        )
        for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
            val content = generateContent(format)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, style)
            assertNotNull(bitmap, "Failed to generate $format with custom colors for content: $content")
        }
    }

    @Test
    fun `generateStyled works for all formats with outer corner radius`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(cornerRadius = 0.2f)
        for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
            val content = generateContent(format)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, style)
            assertNotNull(bitmap, "Failed to generate $format with corner radius for content: $content")
        }
    }

    @Test
    fun `generateStyled works for all formats with all color schemes`() {
        val schemes = listOf(
            AdvancedBarcodeGenerator.ColorSchemes.CLASSIC,
            AdvancedBarcodeGenerator.ColorSchemes.BLUE,
            AdvancedBarcodeGenerator.ColorSchemes.GREEN,
            AdvancedBarcodeGenerator.ColorSchemes.RED,
            AdvancedBarcodeGenerator.ColorSchemes.PURPLE,
            AdvancedBarcodeGenerator.ColorSchemes.ORANGE,
            AdvancedBarcodeGenerator.ColorSchemes.DARK,
            AdvancedBarcodeGenerator.ColorSchemes.CYAN
        )

        for (scheme in schemes) {
            for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
                val content = generateContent(format)
                val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, scheme)
                assertNotNull(bitmap, "Failed to generate $format with color scheme for content: $content")
            }
        }
    }

    @Test
    fun `generateStyled QR supports all gradient directions`() {
        for (direction in AdvancedBarcodeGenerator.GradientDirection.entries) {
            val style = AdvancedBarcodeGenerator.StyleConfig(
                gradientStartColor = Color.parseColor("#1976D2"),
                gradientEndColor = Color.parseColor("#D32F2F"),
                gradientDirection = direction
            )
            val bitmap = AdvancedBarcodeGenerator.generateStyled("https://example.com", BarcodeFormat.QR_CODE, 800, style)
            assertNotNull(bitmap, "Failed to generate QR with gradient direction $direction")
        }
    }

    @Test
    fun `generateStyled QR supports various corner radii`() {
        for (radius in listOf(0f, 0.1f, 0.2f, 0.5f, 0.8f, 1f)) {
            val style = AdvancedBarcodeGenerator.StyleConfig(cornerRadius = radius)
            val bitmap = AdvancedBarcodeGenerator.generateStyled("https://example.com", BarcodeFormat.QR_CODE, 800, style)
            assertNotNull(bitmap, "Failed to generate QR with corner radius $radius")
        }
    }

    @Test
    fun `generateStyled QR supports various logo scales`() {
        for (scale in listOf(0.05f, 0.1f, 0.2f, 0.3f)) {
            val logo = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(logo).drawColor(Color.RED)
            val style = AdvancedBarcodeGenerator.StyleConfig(
                logoBitmap = logo,
                logoScale = scale
            )
            val bitmap = AdvancedBarcodeGenerator.generateStyled("https://example.com", BarcodeFormat.QR_CODE, 800, style)
            assertNotNull(bitmap, "Failed to generate QR with logo scale $scale")
        }
    }

    @Test
    fun `generateStyled QR supports various sizes`() {
        for (size in listOf(256, 512, 800, 1024)) {
            val bitmap = AdvancedBarcodeGenerator.generateStyled("https://example.com", BarcodeFormat.QR_CODE, size, AdvancedBarcodeGenerator.StyleConfig())
            assertNotNull(bitmap, "Failed to generate QR with size $size")
            assertTrue(bitmap!!.width == size && bitmap.height == size, "QR bitmap dimensions should match requested size")
        }
    }

    @Test
    fun `QR Code with custom foreground and background scans back`() {
        val content = "https://example.com"
        val style = AdvancedBarcodeGenerator.StyleConfig(
            foregroundColor = Color.parseColor("#1976D2"),
            backgroundColor = Color.WHITE
        )
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR should scan back with custom colors")
    }

    @Test
    fun `QR Code with each color scheme generates successfully`() {
        val content = "https://example.com"
        val schemes = listOf(
            AdvancedBarcodeGenerator.ColorSchemes.CLASSIC,
            AdvancedBarcodeGenerator.ColorSchemes.BLUE,
            AdvancedBarcodeGenerator.ColorSchemes.GREEN,
            AdvancedBarcodeGenerator.ColorSchemes.RED,
            AdvancedBarcodeGenerator.ColorSchemes.PURPLE,
            AdvancedBarcodeGenerator.ColorSchemes.ORANGE,
            AdvancedBarcodeGenerator.ColorSchemes.DARK,
            AdvancedBarcodeGenerator.ColorSchemes.CYAN
        )
        for (scheme in schemes) {
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, scheme)
            assertNotNull(bitmap, "Should generate QR with color scheme")
        }
    }

    @Test
    fun `QR Code with gradient scans back`() {
        val content = "https://example.com"
        val style = AdvancedBarcodeGenerator.StyleConfig(
            gradientStartColor = Color.parseColor("#1976D2"),
            gradientEndColor = Color.parseColor("#D32F2F"),
            gradientDirection = AdvancedBarcodeGenerator.GradientDirection.HORIZONTAL
        )
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR with gradient should scan back")
    }

    @Test
    fun `QR Code with logo scans back`() {
        val content = "https://example.com"
        val logo = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(logo)
        canvas.drawColor(Color.RED)
        val style = AdvancedBarcodeGenerator.StyleConfig(
            logoBitmap = logo,
            logoScale = 0.2f
        )
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR with small logo should scan back")
    }

    @Test
    fun `QR Code with combined style options scans back`() {
        val content = "https://example.com"
        val logo = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(logo).drawColor(Color.RED)
        val style = AdvancedBarcodeGenerator.StyleConfig(
            foregroundColor = Color.parseColor("#1976D2"),
            backgroundColor = Color.WHITE,
            logoBitmap = logo,
            logoScale = 0.15f
        )
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR with combined style should scan back")
    }
}

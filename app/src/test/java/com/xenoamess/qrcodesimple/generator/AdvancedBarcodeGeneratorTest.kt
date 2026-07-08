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

    private fun actualRoundtripText(format: BarcodeFormat, results: List<QRCodeScanner.ScanResult>): String? {
        val first = results.firstOrNull() ?: return null
        return when (format) {
            BarcodeFormat.UPC_EAN_EXTENSION -> {
                results.firstOrNull { it.format == com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION }?.text
                    ?: first.resultMetadata?.get(com.google.zxing.ResultMetadataType.UPC_EAN_EXTENSION) as? String
            }
            else -> first.text
        }
    }

    @Test
    fun `roundtrip all scannable formats with default style`() {
        for (format in BarcodeFormat.entries.filter { it.isScannable }) {
            val content = BarcodeFormatTestFixtures.validContent(format)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, AdvancedBarcodeGenerator.StyleConfig())
            assertNotNull(bitmap, "Should generate $format")

            val results = QRCodeScanner.scanSync(context, bitmap!!)
            assertTrue(results.isNotEmpty(), "Should scan back $format")

            val expected = BarcodeFormatTestFixtures.expectedRoundtripText(format, content)
            val actual = actualRoundtripText(format, results)
            assertEquals(expected, actual, "Roundtrip content should match for $format")
        }
    }

    @Test
    fun `roundtrip all scannable formats with custom colors`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(
            foregroundColor = Color.parseColor("#1976D2"),
            backgroundColor = Color.WHITE
        )
        for (format in BarcodeFormat.entries.filter { it.isScannable }) {
            val content = BarcodeFormatTestFixtures.validContent(format)
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
            val content = BarcodeFormatTestFixtures.validContent(format)
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
            val content = BarcodeFormatTestFixtures.validContent(format)
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, style)
            assertNotNull(bitmap, "Failed to generate $format with custom colors for content: $content")
        }
    }

    @Test
    fun `generateStyled works for all formats with outer corner radius`() {
        val style = AdvancedBarcodeGenerator.StyleConfig(cornerRadius = 0.2f)
        for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
            val content = BarcodeFormatTestFixtures.validContent(format)
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
            AdvancedBarcodeGenerator.ColorSchemes.CYAN,
            AdvancedBarcodeGenerator.ColorSchemes.QQ
        )

        for (scheme in schemes) {
            for (format in BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }) {
                val content = BarcodeFormatTestFixtures.validContent(format)
                val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, scheme)
                assertNotNull(bitmap, "Failed to generate $format with color scheme for content: $content")
            }
        }
    }

    @Test
    fun `generateStyled QR supports all gradient angles`() {
        for (angle in listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)) {
            val style = AdvancedBarcodeGenerator.StyleConfig(
                gradientStops = listOf(
                    AdvancedBarcodeGenerator.ColorStop(0f, Color.parseColor("#1976D2")),
                    AdvancedBarcodeGenerator.ColorStop(1f, Color.parseColor("#D32F2F"))
                ),
                gradientAngle = angle
            )
            val bitmap = AdvancedBarcodeGenerator.generateStyled("https://example.com", BarcodeFormat.QR_CODE, 800, style)
            assertNotNull(bitmap, "Failed to generate QR with gradient angle $angle")
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
            AdvancedBarcodeGenerator.ColorSchemes.CYAN,
            AdvancedBarcodeGenerator.ColorSchemes.QQ
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
            gradientStops = listOf(
                AdvancedBarcodeGenerator.ColorStop(0f, Color.parseColor("#1976D2")),
                AdvancedBarcodeGenerator.ColorStop(1f, Color.parseColor("#D32F2F"))
            ),
            gradientAngle = 0f
        )
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR with gradient should scan back")
    }

    @Test
    fun `QR Code with dot modules and concentric circle position patterns scans back`() {
        val content = "https://example.com"
        val style = AdvancedBarcodeGenerator.StyleConfig(
            moduleShape = AdvancedBarcodeGenerator.ModuleShape.CIRCLE,
            moduleFillRatio = 0.85f,
            positionPatternShape = AdvancedBarcodeGenerator.PositionPatternShape.CIRCLE,
            gradientStops = listOf(
                AdvancedBarcodeGenerator.ColorStop(0f, Color.parseColor("#00E5FF")),
                AdvancedBarcodeGenerator.ColorStop(0.5f, Color.parseColor("#2196F3")),
                AdvancedBarcodeGenerator.ColorStop(1f, Color.parseColor("#9C27B0"))
            ),
            gradientAngle = 0f
        )
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR with dot modules and circle position patterns should scan back")
        assertEquals(content, results.first().text, "Roundtrip content should match")
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
    fun `QR Code with foreground image scans back`() {
        val content = "https://example.com"
        val fg = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                fg.setPixel(x, y, Color.rgb(x * 255 / 100, y * 255 / 100, 128))
            }
        }
        val style = AdvancedBarcodeGenerator.StyleConfig(foregroundBitmap = fg)
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR with foreground image should scan back")
    }

    @Test
    fun `QR Code with background image scans back`() {
        val content = "https://example.com"
        val bg = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                bg.setPixel(x, y, Color.rgb(255, x * 255 / 100, y * 255 / 100))
            }
        }
        val style = AdvancedBarcodeGenerator.StyleConfig(backgroundBitmap = bg)
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR with background image should scan back")
    }

    @Test
    fun `QR Code with foreground and background images scans back`() {
        val content = "https://example.com"
        val fg = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bg = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (x in 0 until 100) {
            for (y in 0 until 100) {
                fg.setPixel(x, y, Color.rgb(0, x * 255 / 100, y * 255 / 100))
                bg.setPixel(x, y, Color.rgb(255, 255, 255))
            }
        }
        val style = AdvancedBarcodeGenerator.StyleConfig(
            foregroundBitmap = fg,
            backgroundBitmap = bg
        )
        val bitmap = AdvancedBarcodeGenerator.generateStyled(content, BarcodeFormat.QR_CODE, 800, style)
        assertNotNull(bitmap)
        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "QR with foreground and background images should scan back")
    }

    @Test
    fun `Chinese sentence 可是你不觉得这很有趣吗 roundtrips for 2D formats`() {
        val content = "可是你不觉得这很有趣吗？"
        val formats = listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF417,
            BarcodeFormat.HAN_XIN
        )
        for (format in formats) {
            val bitmap = AdvancedBarcodeGenerator.generateStyled(content, format, 800, AdvancedBarcodeGenerator.StyleConfig())
            assertNotNull(bitmap, "Should generate $format with Chinese sentence")

            val results = QRCodeScanner.scanSync(context, bitmap!!)
            assertTrue(results.isNotEmpty(), "Should scan back $format with Chinese sentence")
            assertEquals(content, results.first().text, "Roundtrip content should match for $format")
        }
    }
}

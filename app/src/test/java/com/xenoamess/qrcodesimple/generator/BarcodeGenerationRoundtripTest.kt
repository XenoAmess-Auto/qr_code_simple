package com.xenoamess.qrcodesimple.generator

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.BarcodeGenerator
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

/**
 * 全部 21 种条码格式的生成 → 自识别 roundtrip 测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BarcodeGenerationRoundtripTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun roundtrip(format: BarcodeFormat, content: String, expectedText: String = content) {
        val validation = BarcodeGenerator.validateContent(content, format)
        assertTrue(validation.isValid, "Content should be valid for $format: ${validation.errorMessage}")

        val config = BarcodeGenerator.BarcodeConfig(
            format = format,
            width = 800,
            height = 800
        )
        val bitmap = BarcodeGenerator.generate(content, config)
        assertNotNull(bitmap, "Failed to generate $format for content: $content")

        val results = QRCodeScanner.scanSync(context, bitmap!!)
        assertTrue(results.isNotEmpty(), "Scanner should detect generated $format")

        val actualText = if (format == BarcodeFormat.UPC_EAN_EXTENSION) {
            // UPC/EAN extension is returned as metadata alongside a main barcode
            results.firstOrNull { it.format == com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION }?.text
                ?: results.first().resultMetadata?.get(com.google.zxing.ResultMetadataType.UPC_EAN_EXTENSION) as? String
        } else {
            results.first().text
        }
        assertEquals(expectedText, actualText, "Scanned content should match for $format")
    }

    @Test
    fun `roundtrip QR Code`() = roundtrip(BarcodeFormat.QR_CODE, "https://example.com")

    @Test
    fun `roundtrip Data Matrix`() = roundtrip(BarcodeFormat.DATA_MATRIX, "Hello DM")

    @Test
    fun `roundtrip Aztec`() = roundtrip(BarcodeFormat.AZTEC, "Aztec Test")

    @Test
    fun `roundtrip PDF417`() = roundtrip(BarcodeFormat.PDF417, "PDF417 Test")

    @Test
    fun `roundtrip Code 128`() = roundtrip(BarcodeFormat.CODE_128, "CODE128-TEST")

    @Test
    fun `roundtrip Code 39`() = roundtrip(BarcodeFormat.CODE_39, "ABC-123")

    @Test
    fun `roundtrip Code 93`() = roundtrip(BarcodeFormat.CODE_93, "CODE93-TEST")

    @Test
    fun `roundtrip EAN-13`() = roundtrip(BarcodeFormat.EAN_13, "1234567890128")

    @Test
    fun `roundtrip EAN-8`() = roundtrip(BarcodeFormat.EAN_8, "12345670")

    @Test
    fun `roundtrip UPC-A`() = roundtrip(BarcodeFormat.UPC_A, "123456789012")

    @Test
    fun `roundtrip UPC-E`() = roundtrip(BarcodeFormat.UPC_E, "01234565")

    @Test
    fun `roundtrip Codabar`() = roundtrip(BarcodeFormat.CODABAR, "12345")

    @Test
    fun `roundtrip ITF`() = roundtrip(BarcodeFormat.ITF, "1234567890")

    @Test
    fun `roundtrip UPC-EAN Extension 2`() = roundtrip(BarcodeFormat.UPC_EAN_EXTENSION, "12")

    @Test
    fun `roundtrip UPC-EAN Extension 5`() = roundtrip(BarcodeFormat.UPC_EAN_EXTENSION, "12345")

    @Test
    fun `roundtrip RSS-14`() = roundtrip(BarcodeFormat.RSS_14, "1234567890123", "12345678901231")

    @Test
    fun `roundtrip RSS Expanded`() = roundtrip(BarcodeFormat.RSS_EXPANDED, "(01)12345678901231")

    @Test
    fun `roundtrip Micro QR`() = roundtrip(BarcodeFormat.MICRO_QR, "MicroQR")

    @Test
    fun `roundtrip Pharmacode`() = roundtrip(BarcodeFormat.PHARMACODE, "1234")

    @Test
    fun `roundtrip Plessey`() = roundtrip(BarcodeFormat.PLESSEY, "1A2B")

    @Test
    fun `roundtrip MSI Plessey`() = roundtrip(BarcodeFormat.MSI_PLESSEY, "12345")

    @Test
    fun `roundtrip Telepen`() = roundtrip(BarcodeFormat.TELEPEN, "HELLO")

    @Test
    fun `roundtrip MaxiCode mode 4`() = roundtrip(BarcodeFormat.MAXICODE, "[)>>\u001E01\u001D961Z00004952\u001DUPSN\u001D410 E MAIN ST\u001DSTE\u001DROCHESTER\u001DNY\u001D")

    @Test
    fun `roundtrip Han Xin Code ASCII`() = roundtrip(BarcodeFormat.HAN_XIN, "Hello Han Xin")

    @Test
    fun `roundtrip Han Xin Code numeric`() = roundtrip(BarcodeFormat.HAN_XIN, "12345678901234567890")

    @Test
    fun `roundtrip Han Xin Code Chinese`() = roundtrip(BarcodeFormat.HAN_XIN, "汉信码")
}

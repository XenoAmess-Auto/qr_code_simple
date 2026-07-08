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
 * 所有可扫描条码格式的生成 → 自识别 roundtrip 测试。
 * 仅生成格式只校验生成成功，不校验回扫。
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
            results.firstOrNull { it.format == com.google.zxing.BarcodeFormat.UPC_EAN_EXTENSION }?.text
                ?: results.first().resultMetadata?.get(com.google.zxing.ResultMetadataType.UPC_EAN_EXTENSION) as? String
        } else {
            results.first().text
        }
        assertEquals(expectedText, actualText, "Scanned content should match for $format")
    }

    private fun generateOnly(format: BarcodeFormat, content: String) {
        val validation = BarcodeGenerator.validateContent(content, format)
        assertTrue(validation.isValid, "Content should be valid for $format: ${validation.errorMessage}")

        val config = BarcodeGenerator.BarcodeConfig(
            format = format,
            width = 800,
            height = 800
        )
        val bitmap = BarcodeGenerator.generate(content, config)
        assertNotNull(bitmap, "Failed to generate $format for content: $content")
    }

    @Test
    fun `roundtrip all scannable formats`() {
        for (format in BarcodeFormat.entries.filter { it.isScannable }) {
            val content = BarcodeFormatTestFixtures.validContent(format)
            val expected = BarcodeFormatTestFixtures.expectedRoundtripText(format, content)
            roundtrip(format, content, expected)
        }
    }

    @Test
    fun `generate all generate-only formats`() {
        for (format in BarcodeFormat.entries.filter { !it.isScannable && it != BarcodeFormat.UNKNOWN }) {
            val content = BarcodeFormatTestFixtures.validContent(format)
            generateOnly(format, content)
        }
    }

    @Test
    fun `roundtrip Data Matrix Chinese`() = roundtrip(
        BarcodeFormat.DATA_MATRIX,
        "可是你不觉得这很有趣吗？",
        "可是你不觉得这很有趣吗？"
    )

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
    fun `roundtrip MaxiCode mode 4`() = roundtrip(
        BarcodeFormat.MAXICODE,
        "[)>>\u001E01\u001D961Z00004952\u001DUPSN\u001D410 E MAIN ST\u001DSTE\u001DROCHESTER\u001DNY\u001D"
    )

    @Test
    fun `roundtrip Han Xin Code ASCII`() = roundtrip(BarcodeFormat.HAN_XIN, "Hello Han Xin")

    @Test
    fun `roundtrip Han Xin Code numeric`() = roundtrip(BarcodeFormat.HAN_XIN, "12345678901234567890")

    @Test
    fun `roundtrip Han Xin Code Chinese`() = roundtrip(BarcodeFormat.HAN_XIN, "汉信码")

    @Test
    fun `generate Code 39 Extended`() = generateOnly(BarcodeFormat.CODE_39_EXTENDED, "ABC-123")

    @Test
    fun `generate ITF-14`() = generateOnly(BarcodeFormat.ITF_14, "1234567890123")

    @Test
    fun `generate Code 2 of 5 Standard`() = generateOnly(BarcodeFormat.CODE_2_OF_5_STANDARD, "12345")

    @Test
    fun `generate Code 2 of 5 Industrial`() = generateOnly(BarcodeFormat.CODE_2_OF_5_INDUSTRIAL, "12345")

    @Test
    fun `generate Code 2 of 5 IATA`() = generateOnly(BarcodeFormat.CODE_2_OF_5_IATA, "12345")

    @Test
    fun `generate Code 2 of 5 Datalogic`() = generateOnly(BarcodeFormat.CODE_2_OF_5_DATALOGIC, "12345")

    @Test
    fun `generate Code 2 of 5 Deutsche Post Leitcode`() = generateOnly(BarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_LEITCODE, "12345")

    @Test
    fun `generate Code 2 of 5 Deutsche Post Identcode`() = generateOnly(BarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE, "12345")

    @Test
    fun `generate Code 11`() = generateOnly(BarcodeFormat.CODE_11, "123-45")

    @Test
    fun `generate Code 16K`() = generateOnly(BarcodeFormat.CODE_16K, "CODE16K")

    @Test
    fun `generate Code 32`() = generateOnly(BarcodeFormat.CODE_32, "12345678")

    @Test
    fun `generate Code 49`() = generateOnly(BarcodeFormat.CODE_49, "CODE49")

    @Test
    fun `generate Codablock F`() = generateOnly(BarcodeFormat.CODABLOCK_F, "CODABLOCK")

    @Test
    fun `generate Channel Code`() = generateOnly(BarcodeFormat.CHANNEL_CODE, "123")

    @Test
    fun `generate LOGMARS`() = generateOnly(BarcodeFormat.LOGMARS, "LOGMARS")

    @Test
    fun `generate NVE-18`() = generateOnly(BarcodeFormat.NVE_18, "12345678901234567")

    @Test
    fun `generate DPD Code`() = generateOnly(BarcodeFormat.DPD_CODE, "ABCDEFGHIJK1234567890123456")

    @Test
    fun `generate Pharmacode Two-Track`() = generateOnly(BarcodeFormat.PHARMACODE_2_TRACK, "1234")

    @Test
    fun `generate Pharmazentralnummer`() = generateOnly(BarcodeFormat.PHARMAZENTRALNUMMER, "1234567")

    @Test
    fun `generate Telepen Numeric`() = generateOnly(BarcodeFormat.TELEPEN_NUMERIC, "12345")

    @Test
    fun `generate Postnet`() = generateOnly(BarcodeFormat.POSTNET, "12345")

    @Test
    fun `generate Royal Mail 4-State`() = generateOnly(BarcodeFormat.ROYAL_MAIL_4_STATE, "AB123")

    @Test
    fun `generate USPS OneCode`() = generateOnly(BarcodeFormat.USPS_ONE_CODE, "12345678901234567890")

    @Test
    fun `generate USPS Package`() = generateOnly(BarcodeFormat.USPS_PACKAGE, "[420]90210")

    @Test
    fun `generate Japan Post`() = generateOnly(BarcodeFormat.JAPAN_POST, "123-4567")

    @Test
    fun `generate KIX Code`() = generateOnly(BarcodeFormat.KIX_CODE, "1234AB")

    @Test
    fun `generate Korea Post`() = generateOnly(BarcodeFormat.KOREA_POST, "12345")

    @Test
    fun `generate Australia Post`() = generateOnly(BarcodeFormat.AUSTRALIA_POST, "12345678")

    @Test
    fun `generate GS1 DataBar Limited`() = generateOnly(BarcodeFormat.DATA_BAR_LIMITED, "12345678901")

    @Test
    fun `generate Composite`() = generateOnly(BarcodeFormat.COMPOSITE, "[01]12345678901231")

    @Test
    fun `generate EAN UPC Add-On`() = generateOnly(BarcodeFormat.EAN_UPC_ADD_ON, "12345")

    @Test
    fun `generate Swiss QR Code`() = generateOnly(BarcodeFormat.SWISS_QR_CODE, "TEST123")

    @Test
    fun `generate UPN QR Code`() = generateOnly(BarcodeFormat.UPN_QR_CODE, "TEST123")

    @Test
    fun `generate Aztec Rune`() = generateOnly(BarcodeFormat.AZTEC_RUNE, "100")

    @Test
    fun `generate Code One`() = generateOnly(BarcodeFormat.CODE_ONE, "12345")

    @Test
    fun `generate Grid Matrix`() = generateOnly(BarcodeFormat.GRID_MATRIX, "格矩阵测试")
}

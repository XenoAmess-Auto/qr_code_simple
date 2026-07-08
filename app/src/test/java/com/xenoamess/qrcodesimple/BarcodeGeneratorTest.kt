package com.xenoamess.qrcodesimple

import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BarcodeGenerator 单元测试
 */
class BarcodeGeneratorTest {

    @Test
    fun `validate EAN-13 with correct length`() {
        val result = BarcodeGenerator.validateContent("1234567890123", BarcodeFormat.EAN_13)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate EAN-13 with incorrect length`() {
        val result = BarcodeGenerator.validateContent("12345678901", BarcodeFormat.EAN_13)
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `validate EAN-8 with correct length`() {
        val result = BarcodeGenerator.validateContent("12345678", BarcodeFormat.EAN_8)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate EAN-8 with incorrect length`() {
        val result = BarcodeGenerator.validateContent("1234567", BarcodeFormat.EAN_8)
        assertFalse(result.isValid)
    }

    @Test
    fun `validate UPC-A with correct length`() {
        val result = BarcodeGenerator.validateContent("123456789012", BarcodeFormat.UPC_A)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate UPC-A with incorrect length`() {
        val result = BarcodeGenerator.validateContent("12345678901", BarcodeFormat.UPC_A)
        assertFalse(result.isValid)
    }

    @Test
    fun `validate UPC-E with correct length 6`() {
        val result = BarcodeGenerator.validateContent("123456", BarcodeFormat.UPC_E)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate UPC-E with correct length 8`() {
        val result = BarcodeGenerator.validateContent("12345678", BarcodeFormat.UPC_E)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate UPC-E with incorrect length`() {
        val result = BarcodeGenerator.validateContent("1234567", BarcodeFormat.UPC_E)
        assertFalse(result.isValid)
    }

    @Test
    fun `validate Code 39 with valid characters`() {
        val result = BarcodeGenerator.validateContent("ABC-123", BarcodeFormat.CODE_39)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate Code 39 with lowercase is valid after uppercase conversion`() {
        val result = BarcodeGenerator.validateContent("abc", BarcodeFormat.CODE_39)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate Code 39 with special characters`() {
        val result = BarcodeGenerator.validateContent("TEST-/.$+%", BarcodeFormat.CODE_39)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate Code 39 with space`() {
        val result = BarcodeGenerator.validateContent("TEST 123", BarcodeFormat.CODE_39)
        assertTrue(result.isValid)
    }

    @Test
    fun `Code 39 rejects Chinese`() {
        val result = BarcodeGenerator.validateContent("中文", BarcodeFormat.CODE_39)
        assertFalse(result.isValid)
    }

    @Test
    fun `QR code accepts any text`() {
        val result = BarcodeGenerator.validateContent("Any text @#$% 123", BarcodeFormat.QR_CODE)
        assertTrue(result.isValid)
    }

    @Test
    fun `QR code accepts Chinese text`() {
        val result = BarcodeGenerator.validateContent("中文内容", BarcodeFormat.QR_CODE)
        assertTrue(result.isValid)
    }

    @Test
    fun `valid RSS Expanded GS1 content`() {
        val result = BarcodeGenerator.validateContent("(01)12345678901231", BarcodeFormat.RSS_EXPANDED)
        assertTrue(result.isValid)
    }

    @Test
    fun `valid RSS Expanded bracket syntax content`() {
        val result = BarcodeGenerator.validateContent("[01]12345678901231", BarcodeFormat.RSS_EXPANDED)
        assertTrue(result.isValid)
    }

    @Test
    fun `RSS Expanded rejects Chinese`() {
        val result = BarcodeGenerator.validateContent("可是你不觉得这很有趣吗？", BarcodeFormat.RSS_EXPANDED)
        assertFalse(result.isValid)
    }

    @Test
    fun `Chinese sentence 可是你不觉得这很有趣吗 is accepted by 2D formats and rejected by 1D formats`() {
        val chineseSentence = "可是你不觉得这很有趣吗？"
        val acceptingFormats = listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF417,
            BarcodeFormat.MICRO_QR,
            BarcodeFormat.HAN_XIN,
            BarcodeFormat.GRID_MATRIX
        )
        val rejectingFormats = BarcodeFormat.entries.filter {
            it != BarcodeFormat.UNKNOWN && it !in acceptingFormats
        }

        for (format in acceptingFormats) {
            val result = BarcodeGenerator.validateContent(chineseSentence, format)
            assertTrue(result.isValid, "Expected $format to accept Chinese sentence")
        }

        for (format in rejectingFormats) {
            val result = BarcodeGenerator.validateContent(chineseSentence, format)
            assertFalse(result.isValid, "Expected $format to reject Chinese sentence")
        }
    }

    @Test
    fun `valid Data Matrix content`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.DATA_MATRIX)
        assertTrue(result.isValid)
    }

    @Test
    fun `Data Matrix accepts Chinese text with Okapi`() {
        val result = BarcodeGenerator.validateContent("中文内容", BarcodeFormat.DATA_MATRIX)
        assertTrue(result.isValid)
    }

    @Test
    fun `Data Matrix rejects empty content`() {
        val result = BarcodeGenerator.validateContent("", BarcodeFormat.DATA_MATRIX)
        assertFalse(result.isValid)
    }

    @Test
    fun `Aztec accepts Chinese text`() {
        val result = BarcodeGenerator.validateContent("中文内容", BarcodeFormat.AZTEC)
        assertTrue(result.isValid)
    }

    @Test
    fun `PDF417 accepts Chinese text`() {
        val result = BarcodeGenerator.validateContent("中文内容", BarcodeFormat.PDF417)
        assertTrue(result.isValid)
    }

    @Test
    fun `Han Xin accepts Chinese text`() {
        val result = BarcodeGenerator.validateContent("汉信码", BarcodeFormat.HAN_XIN)
        assertTrue(result.isValid)
    }

    @Test
    fun `Code 128 accepts ASCII but rejects Chinese`() {
        val asciiResult = BarcodeGenerator.validateContent("ASCII 123", BarcodeFormat.CODE_128)
        assertTrue(asciiResult.isValid)
        val chineseResult = BarcodeGenerator.validateContent("中文", BarcodeFormat.CODE_128)
        assertFalse(chineseResult.isValid)
    }

    @Test
    fun `Codabar only accepts valid characters`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.CODABAR)
        assertFalse(result.isValid)
    }

    @Test
    fun `Codabar accepts digits and symbols`() {
        val result = BarcodeGenerator.validateContent("1234-+$:/.", BarcodeFormat.CODABAR)
        assertTrue(result.isValid)
    }

    @Test
    fun `Codabar rejects Chinese`() {
        val result = BarcodeGenerator.validateContent("中文", BarcodeFormat.CODABAR)
        assertFalse(result.isValid)
    }

    @Test
    fun `ITF only accepts even number of digits`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.ITF)
        assertFalse(result.isValid)
    }

    @Test
    fun `ITF accepts even digit string`() {
        val result = BarcodeGenerator.validateContent("123456", BarcodeFormat.ITF)
        assertTrue(result.isValid)
    }

    @Test
    fun `ITF rejects odd digit string`() {
        val result = BarcodeGenerator.validateContent("12345", BarcodeFormat.ITF)
        assertFalse(result.isValid)
    }

    @Test
    fun `ITF rejects Chinese`() {
        val result = BarcodeGenerator.validateContent("中文", BarcodeFormat.ITF)
        assertFalse(result.isValid)
    }

    @Test
    fun `Code 93 only accepts valid characters`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.CODE_93)
        assertFalse(result.isValid)
    }

    @Test
    fun `Code 93 accepts alphanumeric and symbols`() {
        val result = BarcodeGenerator.validateContent("ABC-123.$/+%", BarcodeFormat.CODE_93)
        assertTrue(result.isValid)
    }

    @Test
    fun `Code 93 rejects Chinese`() {
        val result = BarcodeGenerator.validateContent("中文", BarcodeFormat.CODE_93)
        assertFalse(result.isValid)
    }

    @Test
    fun `Telepen rejects Chinese`() {
        val result = BarcodeGenerator.validateContent("中文", BarcodeFormat.TELEPEN)
        assertFalse(result.isValid)
    }

    @Test
    fun `Plessey rejects Chinese`() {
        val result = BarcodeGenerator.validateContent("中文", BarcodeFormat.PLESSEY)
        assertFalse(result.isValid)
    }

    @Test
    fun `MSI Plessey rejects Chinese`() {
        val result = BarcodeGenerator.validateContent("中文", BarcodeFormat.MSI_PLESSEY)
        assertFalse(result.isValid)
    }
}

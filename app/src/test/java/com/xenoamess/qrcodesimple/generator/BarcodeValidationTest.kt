package com.xenoamess.qrcodesimple.generator

import com.xenoamess.qrcodesimple.BarcodeGenerator
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 条码内容验证专项测试
 */
class BarcodeValidationTest {

    @Test
    fun `valid QR code content`() {
        val result = BarcodeGenerator.validateContent("Hello QR", BarcodeFormat.QR_CODE)
        assertTrue(result.isValid)
    }

    @Test
    fun `valid Data Matrix content`() {
        val result = BarcodeGenerator.validateContent("Hello DM", BarcodeFormat.DATA_MATRIX)
        assertTrue(result.isValid)
    }

    @Test
    fun `valid Aztec content`() {
        val result = BarcodeGenerator.validateContent("Aztec", BarcodeFormat.AZTEC)
        assertTrue(result.isValid)
    }

    @Test
    fun `valid PDF417 content`() {
        val result = BarcodeGenerator.validateContent("PDF417", BarcodeFormat.PDF417)
        assertTrue(result.isValid)
    }

    @Test
    fun `invalid EAN-13 wrong length`() {
        val result = BarcodeGenerator.validateContent("1234567890", BarcodeFormat.EAN_13)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun `invalid EAN-8 wrong length`() {
        val result = BarcodeGenerator.validateContent("1234567", BarcodeFormat.EAN_8)
        assertFalse(result.isValid)
    }

    @Test
    fun `invalid UPC-A wrong length`() {
        val result = BarcodeGenerator.validateContent("1234567890", BarcodeFormat.UPC_A)
        assertFalse(result.isValid)
    }

    @Test
    fun `invalid UPC-E wrong length`() {
        val result = BarcodeGenerator.validateContent("123456", BarcodeFormat.UPC_E)
        assertFalse(result.isValid)
    }

    @Test
    fun `invalid Code 39 characters`() {
        val result = BarcodeGenerator.validateContent("abc_123", BarcodeFormat.CODE_39)
        assertFalse(result.isValid)
    }

    @Test
    fun `valid Codabar content`() {
        val result = BarcodeGenerator.validateContent("12345", BarcodeFormat.CODABAR)
        assertTrue(result.isValid)
    }

    @Test
    fun `valid ITF content`() {
        val result = BarcodeGenerator.validateContent("1234567890", BarcodeFormat.ITF)
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
    fun `valid Han Xin content`() {
        val result = BarcodeGenerator.validateContent("汉信码", BarcodeFormat.HAN_XIN)
        assertTrue(result.isValid)
    }

    @Test
    fun `valid Micro QR content`() {
        val result = BarcodeGenerator.validateContent("Micro", BarcodeFormat.MICRO_QR)
        assertTrue(result.isValid)
    }

    @Test
    fun `invalid Micro QR overly long content`() {
        val result = BarcodeGenerator.validateContent("A".repeat(1000), BarcodeFormat.MICRO_QR)
        assertFalse(result.isValid)
    }

    @Test
    fun `valid MaxiCode content`() {
        val result = BarcodeGenerator.validateContent(
            "[)>\u001E01\u001D961Z00004952\u001DUPSN\u001D",
            BarcodeFormat.MAXICODE
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `valid UPC-EAN Extension 2-digit`() {
        val result = BarcodeGenerator.validateContent("12", BarcodeFormat.UPC_EAN_EXTENSION)
        assertTrue(result.isValid)
    }

    @Test
    fun `valid UPC-EAN Extension 5-digit`() {
        val result = BarcodeGenerator.validateContent("12345", BarcodeFormat.UPC_EAN_EXTENSION)
        assertTrue(result.isValid)
    }

    @Test
    fun `invalid UPC-EAN Extension length`() {
        val result = BarcodeGenerator.validateContent("123", BarcodeFormat.UPC_EAN_EXTENSION)
        assertFalse(result.isValid)
    }
}

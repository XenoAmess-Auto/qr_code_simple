package com.xenoamess.qrcodesimple

import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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
        // Code 39 小写字母会被转大写，所以是有效的
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
    fun `QR code accepts any text`() {
        val result = BarcodeGenerator.validateContent("Any text @#$% 123", BarcodeFormat.QR_CODE)
        assertTrue(result.isValid)
    }

    @Test
    fun `Code 128 accepts any text`() {
        val result = BarcodeGenerator.validateContent("Any text 123", BarcodeFormat.CODE_128)
        assertTrue(result.isValid)
    }

    @Test
    fun `Data Matrix accepts any text`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.DATA_MATRIX)
        assertTrue(result.isValid)
    }

    @Test
    fun `Aztec accepts any text`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.AZTEC)
        assertTrue(result.isValid)
    }

    @Test
    fun `PDF417 accepts any text`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.PDF417)
        assertTrue(result.isValid)
    }

    @Test
    fun `Codabar accepts any text`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.CODABAR)
        assertTrue(result.isValid)
    }

    @Test
    fun `ITF accepts any text`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.ITF)
        assertTrue(result.isValid)
    }

    @Test
    fun `Code 93 accepts any text`() {
        val result = BarcodeGenerator.validateContent("Hello World", BarcodeFormat.CODE_93)
        assertTrue(result.isValid)
    }
}

package com.xenoamess.qrcodesimple

import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * SVG 条码生成器测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SvgBarcodeGenerationTest {

    private fun assertSvg(format: BarcodeFormat, content: String) {
        val svg = SvgQRCodeGenerator.generateSVG(
            content = content,
            format = format,
            config = SvgQRCodeGenerator.SvgConfig(width = 400, height = 200)
        )
        assertTrue(svg.startsWith("<?xml"), "$format SVG should start with XML declaration")
        assertTrue(svg.contains("<svg"), "$format SVG should contain svg tag")
        assertTrue(svg.contains("</svg>"), "$format SVG should close svg tag")
    }

    @Test
    fun `generate SVG for QR Code`() = assertSvg(BarcodeFormat.QR_CODE, "https://example.com")

    @Test
    fun `generate SVG for Data Matrix`() = assertSvg(BarcodeFormat.DATA_MATRIX, "Hello DM")

    @Test
    fun `generate SVG for Aztec`() = assertSvg(BarcodeFormat.AZTEC, "Aztec Test")

    @Test
    fun `generate SVG for PDF417`() = assertSvg(BarcodeFormat.PDF417, "PDF417 Test")

    @Test
    fun `generate SVG for Code 128`() = assertSvg(BarcodeFormat.CODE_128, "CODE128")

    @Test
    fun `generate SVG for Code 39`() = assertSvg(BarcodeFormat.CODE_39, "ABC123")

    @Test
    fun `generate SVG for EAN-13`() = assertSvg(BarcodeFormat.EAN_13, "1234567890128")

    @Test
    fun `generate SVG for RSS-14`() = assertSvg(BarcodeFormat.RSS_14, "1234567890123")

    @Test
    fun `generate SVG for RSS Expanded`() = assertSvg(BarcodeFormat.RSS_EXPANDED, "(01)12345678901231")

    @Test
    fun `generate SVG for Micro QR`() = assertSvg(BarcodeFormat.MICRO_QR, "Micro")

    @Test
    fun `generate SVG for Han Xin`() = assertSvg(BarcodeFormat.HAN_XIN, "汉信码")

    @Test
    fun `generate SVG for Pharmacode`() = assertSvg(BarcodeFormat.PHARMACODE, "1234")

    @Test
    fun `generate SVG for Telepen`() = assertSvg(BarcodeFormat.TELEPEN, "TEST")

    @Test
    fun `generate styled SVG for QR Code`() {
        val svg = SvgQRCodeGenerator.generateStyledSVG("https://example.com", cornerRadius = 4f)
        assertTrue(svg.contains("<svg"))
    }
}

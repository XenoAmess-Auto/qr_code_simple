package com.xenoamess.qrcodesimple

import com.google.zxing.BarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 条码格式与历史记录类型映射的自测
 */
class BarcodeFormatMappingTest {

    @Test
    fun `QR Code maps to QR_CODE history type`() {
        assertEquals(HistoryType.QR_CODE, BarcodeFormat.QR_CODE.toHistoryType())
    }

    @Test
    fun `2D matrix codes map to their own history types`() {
        assertEquals(HistoryType.DATA_MATRIX, BarcodeFormat.DATA_MATRIX.toHistoryType())
        assertEquals(HistoryType.AZTEC, BarcodeFormat.AZTEC.toHistoryType())
        assertEquals(HistoryType.PDF417, BarcodeFormat.PDF_417.toHistoryType())
    }

    @Test
    fun `RSS and MaxiCode map to their own history types`() {
        assertEquals(HistoryType.RSS_14, BarcodeFormat.RSS_14.toHistoryType())
        assertEquals(HistoryType.RSS_EXPANDED, BarcodeFormat.RSS_EXPANDED.toHistoryType())
        assertEquals(HistoryType.MAXICODE, BarcodeFormat.MAXICODE.toHistoryType())
    }

    @Test
    fun `linear barcodes map to BARCODE history type`() {
        assertEquals(HistoryType.BARCODE, BarcodeFormat.CODE_128.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.CODE_39.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.CODE_93.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.EAN_13.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.EAN_8.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.UPC_A.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.UPC_E.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.UPC_EAN_EXTENSION.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.CODABAR.toHistoryType())
        assertEquals(HistoryType.BARCODE, BarcodeFormat.ITF.toHistoryType())
    }

    @Test
    fun `new history types are defined`() {
        // 确保新增的历史类型确实存在于枚举中
        HistoryType.valueOf("RSS_14")
        HistoryType.valueOf("RSS_EXPANDED")
        HistoryType.valueOf("MAXICODE")
    }

    @Test
    fun `ZXing supports new formats`() {
        // 验证 ZXing 3.5.3 确实包含这些格式枚举
        BarcodeFormat.valueOf("RSS_14")
        BarcodeFormat.valueOf("RSS_EXPANDED")
        BarcodeFormat.valueOf("MAXICODE")
    }
}

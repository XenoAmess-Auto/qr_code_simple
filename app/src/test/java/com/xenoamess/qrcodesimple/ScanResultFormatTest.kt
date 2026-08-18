package com.xenoamess.qrcodesimple

import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanResultFormatTest {
    @Test
    fun `custom formats keep their app identity without a fake zxing format`() {
        val result = QRCodeScanner.ScanResult("12", QRCodeScanner.Library.CUSTOM_LINEAR, appFormat = BarcodeFormat.PLESSEY)
        assertNull(result.format)
        assertEquals(BarcodeFormat.PLESSEY, result.appFormat)
        assertEquals("12#PLESSEY", result.deduplicationKey)
    }
}

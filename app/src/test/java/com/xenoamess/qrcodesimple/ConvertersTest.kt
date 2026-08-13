package com.xenoamess.qrcodesimple

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.Converters
import com.xenoamess.qrcodesimple.data.HistoryType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `history type roundtrip covers all values`() {
        HistoryType.entries.forEach { type ->
            assertEquals(type, converters.toHistoryType(converters.fromHistoryType(type)))
        }
    }

    @Test
    fun `fromHistoryType uses enum name`() {
        assertEquals("QR_CODE", converters.fromHistoryType(HistoryType.QR_CODE))
        assertEquals("MSI_PLESSEY", converters.fromHistoryType(HistoryType.MSI_PLESSEY))
    }
}

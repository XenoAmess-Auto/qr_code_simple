package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
import android.widget.Filter
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BarcodeFormatAdapterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun formats() = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }

    private fun flushFilter(adapter: BarcodeFormatAdapter, constraint: String) {
        val latch = CountDownLatch(1)
        adapter.filter.filter(constraint, Filter.FilterListener { latch.countDown() })
        Thread.sleep(300)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Filter should complete", latch.await(0, TimeUnit.SECONDS))
    }

    @Test
    fun filterEmptyReturnsAllFormats() {
        val adapter = BarcodeFormatAdapter(context, formats())
        flushFilter(adapter, "")
        assertEquals(formats().size, adapter.count)
    }

    @Test
    fun filterByLocalizedNameMatchesExact() {
        val adapter = BarcodeFormatAdapter(context, formats())
        flushFilter(adapter, "EAN-13")
        assertEquals(listOf(BarcodeFormat.EAN_13), (0 until adapter.count).map { adapter.getItem(it) })
    }

    @Test
    fun filterByEnumNameContains() {
        val adapter = BarcodeFormatAdapter(context, formats())
        flushFilter(adapter, "QR")
        val results = (0 until adapter.count).map { adapter.getItem(it) }
        assertTrue(results.contains(BarcodeFormat.QR_CODE))
        assertTrue(results.contains(BarcodeFormat.SWISS_QR_CODE))
        assertTrue(results.contains(BarcodeFormat.UPN_QR_CODE))
        assertTrue(!results.contains(BarcodeFormat.EAN_13))
    }

    @Test
    fun filterStartsWithRankedBeforeContains() {
        val adapter = BarcodeFormatAdapter(context, formats())
        flushFilter(adapter, "EAN")
        assertEquals(BarcodeFormat.EAN_13, adapter.getItem(0))
    }
}

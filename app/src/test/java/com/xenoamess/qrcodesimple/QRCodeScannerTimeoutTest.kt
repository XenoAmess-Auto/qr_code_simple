package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class QRCodeScannerTimeoutTest {

    @Test
    fun scanAsFlow_respectsTotalTimeout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val config = ScanConfig(
            totalTimeoutMs = 1L,
            perEngineTimeoutMs = 1L,
            maxDimension = 100
        )

        val elapsed = runBlocking {
            val start = System.currentTimeMillis()
            val results = QRCodeScanner.scanAsFlow(context, bitmap, config).toList()
            val elapsed = System.currentTimeMillis() - start
            assertTrue("Expected no results but got $results", results.flatten().isEmpty())
            elapsed
        }

        assertTrue("scanAsFlow took too long: ${elapsed}ms", elapsed < 500)
        bitmap.recycle()
    }
}

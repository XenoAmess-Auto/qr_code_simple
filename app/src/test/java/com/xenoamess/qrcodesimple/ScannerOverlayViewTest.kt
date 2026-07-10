package com.xenoamess.qrcodesimple

import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ScannerOverlayViewTest {

    private fun createAndLayoutView(): ScannerOverlayView {
        val view = ScannerOverlayView(ApplicationProvider.getApplicationContext())
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 400)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return view
    }

    @Test
    fun defaultScannerRectIsCenteredAfterLayout() {
        val view = createAndLayoutView()
        val rect = view.scannerRect
        assertNotNull(rect)
        assertTrue(rect!!.width() > 0)
        assertTrue(rect.height() > 0)
        assertEquals(rect.centerX(), 200)
        assertEquals(rect.centerY(), 200)
    }

    @Test
    fun scannerRectSetterUpdatesValue() {
        val view = createAndLayoutView()
        val newRect = Rect(10, 20, 110, 120)
        view.scannerRect = newRect
        assertEquals(newRect, view.scannerRect)
    }

    @Test
    fun colorSettersUpdatePaintColor() {
        val view = createAndLayoutView()
        view.scanLineColor = Color.RED
        view.cornerColor = Color.GREEN
        assertEquals(Color.RED, view.scanLineColor)
        assertEquals(Color.GREEN, view.cornerColor)
    }

    @Test
    fun stopAnimationDoesNotCrash() {
        val view = createAndLayoutView()
        view.stopAnimation()
    }
}

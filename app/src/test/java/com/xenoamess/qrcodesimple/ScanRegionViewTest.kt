package com.xenoamess.qrcodesimple

import android.graphics.RectF
import android.os.Looper
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ScanRegionViewTest {

    private fun createView(): ScanRegionView {
        val view = ScanRegionView(ApplicationProvider.getApplicationContext())
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 400, 400)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return view
    }

    @Test
    fun selectionViaDragNotifiesListener() {
        val view = createView()
        var selectedRect: RectF? = null
        var cleared = false

        view.setOnRegionSelectedListener(object : ScanRegionView.OnRegionSelectedListener {
            override fun onRegionSelected(rect: RectF) {
                selectedRect = RectF(rect)
            }

            override fun onRegionCleared() {
                cleared = true
            }
        })

        dispatchTouch(view, MotionEvent.ACTION_DOWN, 50f, 50f)
        dispatchTouch(view, MotionEvent.ACTION_MOVE, 250f, 250f)
        dispatchTouch(view, MotionEvent.ACTION_UP, 250f, 250f)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(selectedRect)
        assertTrue(view.hasSelection())
        assertEquals(50f, selectedRect!!.left, 0f)
        assertEquals(50f, selectedRect!!.top, 0f)
        assertEquals(250f, selectedRect!!.right, 0f)
        assertEquals(250f, selectedRect!!.bottom, 0f)
        assertFalse(cleared)
    }

    @Test
    fun clearSelectionEmptiesRegionAndNotifies() {
        val view = createView()
        var cleared = false
        view.setOnRegionSelectedListener(object : ScanRegionView.OnRegionSelectedListener {
            override fun onRegionSelected(rect: RectF) {}
            override fun onRegionCleared() { cleared = true }
        })

        dispatchTouch(view, MotionEvent.ACTION_DOWN, 50f, 50f)
        dispatchTouch(view, MotionEvent.ACTION_MOVE, 250f, 250f)
        dispatchTouch(view, MotionEvent.ACTION_UP, 250f, 250f)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(view.hasSelection())
        view.clearSelection()
        assertFalse(view.hasSelection())
        assertNull(view.getSelectionRect())
        assertTrue(cleared)
    }

    @Test
    fun smallSelectionIsCleared() {
        val view = createView()
        var cleared = false
        view.setOnRegionSelectedListener(object : ScanRegionView.OnRegionSelectedListener {
            override fun onRegionSelected(rect: RectF) {}
            override fun onRegionCleared() { cleared = true }
        })

        dispatchTouch(view, MotionEvent.ACTION_DOWN, 50f, 50f)
        dispatchTouch(view, MotionEvent.ACTION_MOVE, 80f, 80f)
        dispatchTouch(view, MotionEvent.ACTION_UP, 80f, 80f)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertFalse(view.hasSelection())
        assertTrue(cleared)
    }

    private fun dispatchTouch(view: ScanRegionView, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}

package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class AngleDialViewTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun touchRight_setsAngleToZero() {
        val view = AngleDialView(context)
        measureAndLayout(view, 400, 400)

        val angles = mutableListOf<Float>()
        view.onAngleChanged = { angles.add(it) }

        dispatchTouch(view, 380f, 200f)
        assertEquals(0f, view.angle, 2f)
        assertTrue("Angle changed callback should fire", angles.isNotEmpty())
        assertEquals(0f, angles.last(), 2f)
    }

    @Test
    fun touchBottom_setsAngleTo90() {
        val view = AngleDialView(context)
        measureAndLayout(view, 400, 400)
        dispatchTouch(view, 200f, 380f)
        assertEquals(90f, view.angle, 2f)
    }

    @Test
    fun touchLeft_setsAngleTo180() {
        val view = AngleDialView(context)
        measureAndLayout(view, 400, 400)
        dispatchTouch(view, 20f, 200f)
        assertEquals(180f, view.angle, 2f)
    }

    @Test
    fun touchTop_setsAngleTo270() {
        val view = AngleDialView(context)
        measureAndLayout(view, 400, 400)
        dispatchTouch(view, 200f, 20f)
        assertEquals(270f, view.angle, 2f)
    }

    @Test
    fun angleSetter_invalidatesAndNotifies() {
        val view = AngleDialView(context)
        val angles = mutableListOf<Float>()
        view.onAngleChanged = { angles.add(it) }
        view.angle = 123f
        assertEquals(123f, view.angle, 0f)
        assertEquals(123f, angles.last(), 0f)
    }

    private fun measureAndLayout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
    }

    private fun dispatchTouch(view: View, x: Float, y: Float) {
        val downTime = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun flushMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

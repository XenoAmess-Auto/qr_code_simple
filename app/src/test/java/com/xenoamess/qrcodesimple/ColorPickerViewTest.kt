package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ColorPickerViewTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun setColor_updatesCurrentColor() {
        val view = ColorPickerView(context)
        view.setColor(Color.RED)
        assertEquals(Color.RED, view.currentColor)
    }

    @Test
    fun touchSv_changesSaturationAndValue() {
        val view = ColorPickerView(context)
        measureAndLayout(view, 400, 460)
        view.setColor(Color.RED)

        val changes = mutableListOf<Int>()
        view.onColorChanged = { changes.add(it) }

        dispatchTouch(view, 200f, 100f)
        flushMainLooper()

        assertTrue("Color should change after touching SV area", changes.isNotEmpty())
        assertNotEquals(Color.RED, view.currentColor)
    }

    @Test
    fun touchHueBar_changesHue() {
        val view = ColorPickerView(context)
        measureAndLayout(view, 400, 460)
        view.setColor(Color.RED)

        val changes = mutableListOf<Int>()
        view.onColorChanged = { changes.add(it) }

        // Hue bar is the second band: 332..380
        dispatchTouch(view, 300f, 360f)
        flushMainLooper()

        assertTrue("Hue should change after touching hue bar", changes.isNotEmpty())
        assertNotEquals(Color.RED, view.currentColor)
    }

    @Test
    fun touchAlphaBar_changesAlpha() {
        val view = ColorPickerView(context)
        measureAndLayout(view, 400, 460)
        view.setColor(Color.RED)

        val changes = mutableListOf<Int>()
        view.onColorChanged = { changes.add(it) }

        // Alpha bar is the third band: 412..460
        dispatchTouch(view, 100f, 430f)
        flushMainLooper()

        assertTrue("Alpha should change after touching alpha bar", changes.isNotEmpty())
        assertTrue("Alpha should be reduced", Color.alpha(view.currentColor) < 255)
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

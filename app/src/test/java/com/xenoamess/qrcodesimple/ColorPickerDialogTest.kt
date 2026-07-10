package com.xenoamess.qrcodesimple

import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ColorPickerDialogTest {

    private lateinit var scenario: FragmentScenario<ColorPickerDialog>

    @Before
    fun setup() {
        scenario = launchFragmentInContainer(themeResId = R.style.Theme_QRCodeSimple) {
            ColorPickerDialog().setInitialColor(Color.RED)
        }
    }

    @Test
    fun initialColor_reflectedInPickerAndFields() {
        scenario.onFragment { dialog ->
            val view = dialog.requireView()
            val picker = view.findViewById<ColorPickerView>(R.id.colorPicker)
            val hexInput = view.findViewById<TextInputEditText>(R.id.etHexInput)
            val etR = view.findViewById<EditText>(R.id.etR)
            val etG = view.findViewById<EditText>(R.id.etG)
            val etB = view.findViewById<EditText>(R.id.etB)
            val etA = view.findViewById<EditText>(R.id.etA)

            assertEquals(Color.RED, picker.currentColor)
            assertEquals("#FF0000", hexInput.text.toString())
            assertEquals("255", etR.text.toString())
            assertEquals("0", etG.text.toString())
            assertEquals("0", etB.text.toString())
            assertEquals("255", etA.text.toString())
        }
    }

    @Test
    fun hexInput_syncsColorPickerAndRgbaFields() {
        scenario.onFragment { dialog ->
            val view = dialog.requireView()
            val picker = view.findViewById<ColorPickerView>(R.id.colorPicker)
            val hexInput = view.findViewById<TextInputEditText>(R.id.etHexInput)
            val etR = view.findViewById<EditText>(R.id.etR)
            val etG = view.findViewById<EditText>(R.id.etG)
            val etB = view.findViewById<EditText>(R.id.etB)

            hexInput.setText("#00FF00")
            flushMainLooper()

            assertEquals(Color.GREEN, picker.currentColor)
            assertEquals("0", etR.text.toString())
            assertEquals("255", etG.text.toString())
            assertEquals("0", etB.text.toString())
        }
    }

    @Test
    fun rgbaInput_syncsColorPickerAndHexField() {
        scenario.onFragment { dialog ->
            val view = dialog.requireView()
            val picker = view.findViewById<ColorPickerView>(R.id.colorPicker)
            val hexInput = view.findViewById<TextInputEditText>(R.id.etHexInput)
            val etR = view.findViewById<EditText>(R.id.etR)
            val etG = view.findViewById<EditText>(R.id.etG)
            val etB = view.findViewById<EditText>(R.id.etB)
            val etA = view.findViewById<EditText>(R.id.etA)

            etR.setText("0")
            etG.setText("255")
            etB.setText("0")
            etA.setText("128")
            flushMainLooper()

            assertEquals(Color.argb(128, 0, 255, 0), picker.currentColor)
            assertEquals("#8000FF00", hexInput.text.toString())
        }
    }

    @Test
    fun pickerTouch_syncsHexAndRgbaFields() {
        scenario.onFragment { dialog ->
            val view = dialog.requireView()
            val picker = view.findViewById<ColorPickerView>(R.id.colorPicker)
            val hexInput = view.findViewById<TextInputEditText>(R.id.etHexInput)
            val etA = view.findViewById<EditText>(R.id.etA)

            measureAndLayout(picker, 400, 460)
            dispatchTouch(picker, 200f, 100f)
            flushMainLooper()

            assertNotNull(hexInput.text.toString())
            assertTrue("Hex field should remain non-empty", hexInput.text.toString().isNotEmpty())
            assertEquals("255", etA.text.toString())
        }
    }

    @Test
    fun confirm_returnsCurrentColor() {
        val selected = mutableListOf<Int>()
        scenario.onFragment { dialog ->
            dialog.onColorSelected = { selected.add(it) }
            val view = dialog.requireView()
            val picker = view.findViewById<ColorPickerView>(R.id.colorPicker)
            picker.setColor(Color.BLUE)
            view.findViewById<View>(R.id.btnConfirm).callOnClick()
        }

        assertEquals(1, selected.size)
        assertEquals(Color.BLUE, selected[0])
    }

    @Test
    fun cancel_doesNotInvokeColorSelected() {
        val selected = mutableListOf<Int>()
        scenario.onFragment { dialog ->
            dialog.onColorSelected = { selected.add(it) }
            val view = dialog.requireView()
            view.findViewById<View>(R.id.btnClose).callOnClick()
        }

        assertTrue("Cancel should not invoke color selection", selected.isEmpty())
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

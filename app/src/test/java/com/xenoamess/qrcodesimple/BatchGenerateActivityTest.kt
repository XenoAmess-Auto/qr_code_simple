package com.xenoamess.qrcodesimple

import android.text.InputType
import android.widget.AutoCompleteTextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BatchGenerateActivityTest {

    private lateinit var scenario: ActivityScenario<BatchGenerateActivity>

    @Before
    fun setup() {
        scenario = ActivityScenario.launch(BatchGenerateActivity::class.java)
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) {
            scenario.close()
        }
    }

    private fun findSpinner(): AutoCompleteTextView {
        var spinner: AutoCompleteTextView? = null
        scenario.onActivity { activity ->
            spinner = activity.findViewById(R.id.spinnerFormat)
        }
        return spinner!!
    }

    private fun flushFilter() {
        Thread.sleep(300)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun formatSelectorIsEditable() {
        val spinner = findSpinner()
        assertTrue("Format selector should be enabled", spinner.isEnabled)
        assertNotEquals(
            "Format selector should be editable (inputType != TYPE_NULL)",
            InputType.TYPE_NULL,
            spinner.inputType
        )
    }

    @Test
    fun formatDropdownOpensOnClick() {
        onView(withId(R.id.spinnerFormat)).perform(click())
        val spinner = findSpinner()
        val adapter = spinner.adapter as BarcodeFormatAdapter
        assertEquals(
            "Dropdown should show all formats after click",
            BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }.size,
            adapter.count
        )
    }

    @Test
    fun formatDropdownFiltersByTyping() {
        onView(withId(R.id.spinnerFormat)).perform(click(), replaceText("EAN-13"))
        flushFilter()
        val spinner = findSpinner()
        val adapter = spinner.adapter as BarcodeFormatAdapter
        val results = (0 until adapter.count).map { adapter.getItem(it) }
        assertTrue("EAN-13 should be in filtered results", results.contains(BarcodeFormat.EAN_13))
        assertTrue("QR Code should not be in filtered results", !results.contains(BarcodeFormat.QR_CODE))
    }

    @Test
    fun formatDropdownSelectsFormat() {
        onView(withId(R.id.spinnerFormat)).perform(click())
        closeSoftKeyboard()
        scenario.onActivity { activity ->
            val spinner = activity.findViewById<AutoCompleteTextView>(R.id.spinnerFormat)
            val adapter = spinner.adapter as BarcodeFormatAdapter
            val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
            val position = formats.indexOf(BarcodeFormat.CODE_128)
            assertTrue("CODE_128 should be in full adapter", position >= 0)
            spinner.onItemClickListener?.onItemClick(null, spinner, position, adapter.getItemId(position))
            assertEquals(BarcodeFormat.CODE_128, activity.selectedFormat)
            assertEquals(BarcodeFormat.CODE_128.localizedName(activity), spinner.text.toString())
        }
    }

    @Test
    fun formatDropdownRestoresInvalidInput() {
        var selectedBefore = BarcodeFormat.UNKNOWN
        scenario.onActivity { activity ->
            selectedBefore = activity.selectedFormat
        }

        onView(withId(R.id.spinnerFormat)).perform(click(), replaceText("not a format"))
        scenario.onActivity { activity ->
            val spinner = activity.findViewById<AutoCompleteTextView>(R.id.spinnerFormat)
            spinner.onFocusChangeListener?.onFocusChange(spinner, false)
            assertEquals(selectedBefore, activity.selectedFormat)
            assertEquals(selectedBefore.localizedName(activity), spinner.text.toString())
        }
    }
}

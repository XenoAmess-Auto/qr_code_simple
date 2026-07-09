package com.xenoamess.qrcodesimple

import android.text.InputType
import android.widget.AutoCompleteTextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class GenerateFragmentFormatSelectorUiTest : BaseGenerateFragmentUiTest() {

    private fun findSpinner(fragment: GenerateFragment): AutoCompleteTextView =
        fragment.requireView().findViewById(R.id.spinnerFormat)

    @Test
    fun formatSelectorIsEditable() {
        scenario.onFragment { fragment ->
            val spinner = findSpinner(fragment)
            assertTrue("Format selector should be enabled", spinner.isEnabled)
            assertNotEquals(
                "Format selector should be editable (inputType != TYPE_NULL)",
                InputType.TYPE_NULL,
                spinner.inputType
            )
        }
    }

    @Test
    fun formatDropdownOpensOnClick() {
        onView(withId(R.id.spinnerFormat)).perform(scrollTo(), click())
        scenario.onFragment { fragment ->
            val spinner = findSpinner(fragment)
            val adapter = spinner.adapter as BarcodeFormatAdapter
            assertEquals(
                "Dropdown should show all formats after click",
                BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }.size,
                adapter.count
            )
        }
    }

    @Test
    fun formatDropdownFiltersByTyping() {
        onView(withId(R.id.spinnerFormat)).perform(scrollTo(), click(), replaceText("QR"))
        scenario.onFragment { fragment ->
            // allow Filter thread to finish and results to be posted to the main thread
            Thread.sleep(300)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val spinner = findSpinner(fragment)
            val adapter = spinner.adapter as BarcodeFormatAdapter
            val results = (0 until adapter.count).map { adapter.getItem(it) }
            assertTrue("QR Code should be in filtered results", results.contains(BarcodeFormat.QR_CODE))
            assertTrue("Swiss QR Code should be in filtered results", results.contains(BarcodeFormat.SWISS_QR_CODE))
            assertTrue("UPN QR Code should be in filtered results", results.contains(BarcodeFormat.UPN_QR_CODE))
            assertTrue("EAN-13 should not be in filtered results", !results.contains(BarcodeFormat.EAN_13))
        }
    }

    @Test
    fun formatDropdownSelectsFormat() {
        onView(withId(R.id.spinnerFormat)).perform(scrollTo(), click())
        closeSoftKeyboard()
        scenario.onFragment { fragment ->
            val spinner = findSpinner(fragment)
            val adapter = spinner.adapter as BarcodeFormatAdapter
            val formats = BarcodeFormat.entries.filter { it != BarcodeFormat.UNKNOWN }
            val position = formats.indexOf(BarcodeFormat.EAN_13)
            assertTrue("EAN-13 should be in full adapter", position >= 0)
            spinner.onItemClickListener?.onItemClick(null, spinner, position, adapter.getItemId(position))
            assertEquals(BarcodeFormat.EAN_13, fragment.selectedFormat)
            assertEquals(BarcodeFormat.EAN_13.localizedName(spinner.context), spinner.text.toString())
        }
    }

    @Test
    fun formatDropdownRestoresInvalidInput() {
        var originalFormat = BarcodeFormat.UNKNOWN
        var originalText = ""
        scenario.onFragment { fragment ->
            originalFormat = fragment.selectedFormat
            originalText = findSpinner(fragment).text.toString()
        }

        // focus, type invalid text, then invoke the focus-loss listener directly
        onView(withId(R.id.spinnerFormat)).perform(scrollTo(), click(), replaceText("not a format"))
        scenario.onFragment { fragment ->
            val spinner = findSpinner(fragment)
            spinner.onFocusChangeListener?.onFocusChange(spinner, false)
            assertEquals(originalFormat, fragment.selectedFormat)
            assertEquals(originalText, spinner.text.toString())
        }
    }
}

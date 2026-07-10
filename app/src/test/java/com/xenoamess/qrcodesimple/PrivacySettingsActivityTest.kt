package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class PrivacySettingsActivityTest {

    private lateinit var scenario: ActivityScenario<PrivacySettingsActivity>

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QRCodeApp.setPrivacyMode(context, false)
        AppLockManager.clearPin()
        AppLockManager.init(context)
        scenario = ActivityScenario.launch(PrivacySettingsActivity::class.java)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
        QRCodeApp.setPrivacyMode(ApplicationProvider.getApplicationContext(), false)
        AppLockManager.clearPin()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun privacySwitchDisabledByDefault() {
        scenario.onActivity { activity ->
            val switch = activity.findViewById<Switch>(R.id.switchPrivacyMode)
            assertFalse(switch.isChecked)
            assertFalse(QRCodeApp.isPrivacyMode(activity))
        }
    }

    @Test
    fun enablingPrivacyModeShowsConfirmDialogAndTogglesOnConfirm() {
        onView(withId(R.id.switchPrivacyMode)).perform(click())
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        scenario.onActivity { activity ->
            assertTrue(QRCodeApp.isPrivacyMode(activity))
            val switch = activity.findViewById<Switch>(R.id.switchPrivacyMode)
            assertTrue(switch.isChecked)
        }
    }

    @Test
    fun cancellingPrivacyModeDialogKeepsSwitchOff() {
        onView(withId(R.id.switchPrivacyMode)).perform(click())
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idleMain()

        scenario.onActivity { activity ->
            assertFalse(QRCodeApp.isPrivacyMode(activity))
            val switch = activity.findViewById<Switch>(R.id.switchPrivacyMode)
            assertFalse(switch.isChecked)
        }
    }

    @Test
    fun enablingAppLockWithoutPinShowsSetPinDialog() {
        onView(withId(R.id.switchAppLock)).perform(click())
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        val editTexts = dialog.findAllEditTexts()
        assertEquals(2, editTexts.size)
    }

    @Test
    fun cancellingSetPinDialogKeepsLockDisabled() {
        onView(withId(R.id.switchAppLock)).perform(click())
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idleMain()

        assertFalse(AppLockManager.isLockEnabled())
        scenario.onActivity { activity ->
            val switch = activity.findViewById<Switch>(R.id.switchAppLock)
            assertFalse(switch.isChecked)
        }
    }

    @Test
    fun setPinAndEnableAppLock() {
        onView(withId(R.id.switchAppLock)).perform(click())
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val editTexts = dialog.findAllEditTexts()
        assertEquals(2, editTexts.size)

        editTexts[0].setText("123456")
        editTexts[1].setText("123456")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        assertTrue(AppLockManager.isLockEnabled())
        assertTrue(AppLockManager.hasPin())
        scenario.onActivity { activity ->
            assertTrue(activity.findViewById<Switch>(R.id.switchAppLock).isChecked)
            assertEquals(View.VISIBLE, activity.findViewById<MaterialButton>(R.id.btnChangePin).visibility)
        }
    }

    @Test
    fun mismatchingPinDoesNotEnableLock() {
        onView(withId(R.id.switchAppLock)).perform(click())
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val editTexts = dialog.findAllEditTexts()
        editTexts[0].setText("123456")
        editTexts[1].setText("654321")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        assertFalse(AppLockManager.isLockEnabled())
    }

    @Test
    fun clearHistoryButtonShowsConfirmDialog() {
        onView(withId(R.id.btnClearAllHistory)).perform(click())
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        assertFalse(ShadowDialog.getLatestDialog()?.isShowing ?: false)
    }

    private fun AlertDialog.findAllEditTexts(): List<EditText> {
        val result = mutableListOf<EditText>()
        collectEditTexts(window?.decorView as? ViewGroup ?: return result, result)
        return result
    }

    private fun collectEditTexts(root: ViewGroup, out: MutableList<EditText>) {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is EditText) out.add(child)
            if (child is ViewGroup) collectEditTexts(child, out)
        }
    }
}

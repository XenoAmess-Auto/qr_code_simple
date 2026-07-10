package com.xenoamess.qrcodesimple

import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
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
class DatabaseSecurityActivityTest {

    private lateinit var scenario: ActivityScenario<DatabaseSecurityActivity>

    @Before
    fun setup() {
        LocaleHelper.setLanguage(ApplicationProvider.getApplicationContext(), "system")
        scenario = ActivityScenario.launch(DatabaseSecurityActivity::class.java)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun encryptionStatusShowsEnabled() {
        scenario.onActivity { activity ->
            val tv = activity.findViewById<TextView>(R.id.tvEncryptionStatus)
            assertEquals(activity.getString(R.string.encryption_enabled), tv.text.toString())
        }
    }

    @Test
    fun resetButtonShowsConfirmDialog() {
        onView(withId(R.id.btnResetDatabase)).perform(click())
        idleMain()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
    }

    @Test
    fun exportBackupButtonLaunchesBackupActivity() {
        onView(withId(R.id.btnExportBackup)).perform(click())
        idleMain()

        var intent: Intent? = null
        scenario.onActivity { activity ->
            intent = Shadows.shadowOf(activity).nextStartedActivity
        }
        assertNotNull(intent)
        assertEquals("com.xenoamess.qrcodesimple.BackupActivity", intent?.component?.className)
    }
}

package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandaloneNavigationDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun standalonePagesExposeRealActionBarAndUpNavigation() {
        assertActionBar<GenerateActivity>(R.string.generate_qr)
        assertActionBar<BatchGenerateActivity>(R.string.batch_generate)
        assertActionBar<PrivacySettingsActivity>(R.string.privacy_settings)
        assertActionBar<DatabaseSecurityActivity>(R.string.database_security)
        assertActionBar<BackupActivity>(R.string.backup_restore)
    }

    private inline fun <reified T : AppCompatActivity> assertActionBar(titleRes: Int) {
        val scenario = ActivityScenario.launch<T>(Intent(context, T::class.java))
        scenario.onActivity { activity ->
            val actionBar = activity.supportActionBar
            assertNotNull("${T::class.java.simpleName} must expose an ActionBar", actionBar)
            assertEquals(activity.getString(titleRes), actionBar?.title)
            assertTrue(
                "${T::class.java.simpleName} must expose Up navigation",
                actionBar!!.displayOptions and ActionBar.DISPLAY_HOME_AS_UP != 0
            )
        }
        scenario.close()
    }
}

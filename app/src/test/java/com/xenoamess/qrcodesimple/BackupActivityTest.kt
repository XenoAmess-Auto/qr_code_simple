package com.xenoamess.qrcodesimple

import android.content.Intent
import android.os.Looper
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BackupActivityTest {

    private lateinit var scenario: ActivityScenario<BackupActivity>

    @Before
    fun setup() {
        LocaleHelper.setLanguage(ApplicationProvider.getApplicationContext(), "system")
        scenario = ActivityScenario.launch(BackupActivity::class.java)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun captureNextStartedActivity(): Intent? {
        var intent: Intent? = null
        scenario.onActivity { activity ->
            intent = Shadows.shadowOf(activity).nextStartedActivity
        }
        return intent
    }

    @Test
    fun exportJsonButtonLaunchesCreateDocumentIntent() {
        onView(withId(R.id.btnExportJson)).perform(click())
        idleMain()

        val intent = captureNextStartedActivity()
        assertNotNull(intent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent?.action)
        assertEquals("application/json", intent?.type)
        assertTrue(intent?.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        val title = intent?.getStringExtra(Intent.EXTRA_TITLE)
        assertNotNull(title)
        assertTrue(title!!.endsWith(".json"))
    }

    @Test
    fun exportCsvButtonLaunchesCreateDocumentIntent() {
        onView(withId(R.id.btnExportCsv)).perform(click())
        idleMain()

        val intent = captureNextStartedActivity()
        assertNotNull(intent)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent?.action)
        assertEquals("text/csv", intent?.type)
        assertTrue(intent?.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        val title = intent?.getStringExtra(Intent.EXTRA_TITLE)
        assertNotNull(title)
        assertTrue(title!!.endsWith(".csv"))
    }

    @Test
    fun importButtonLaunchesOpenDocumentIntent() {
        // 布局在 ScrollView 中，Robolectric 下 Espresso scrollTo 不可靠，直接触发点击
        scenario.onActivity { activity ->
            activity.findViewById<android.widget.Button>(R.id.btnImport).performClick()
        }
        idleMain()

        val intent = captureNextStartedActivity()
        assertNotNull(intent)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent?.action)
        assertEquals("*/*", intent?.type)
        assertTrue(intent?.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        val mimeTypes = intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        assertNotNull(mimeTypes)
        assertTrue(mimeTypes!!.contains("application/json"))
        assertTrue(mimeTypes.contains("text/csv"))
    }
}

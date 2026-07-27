package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.FragmentScenario
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
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class AboutFragmentUiTest {

    private lateinit var scenario: FragmentScenario<AboutFragment>

    @Before
    fun setup() {
        LocaleHelper.setLanguage(ApplicationProvider.getApplicationContext(), "system")
        scenario = FragmentScenario.launchInContainer(AboutFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun flushMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun versionTextContainsVersionAndGitHash() {
        scenario.onFragment { fragment ->
            val text = fragment.requireView().findViewById<TextView>(R.id.tvVersion).text.toString()
            assertTrue("Version text should contain version", text.contains(BuildConfig.VERSION_NAME))
            assertTrue("Version text should contain git hash", text.contains(BuildConfig.GIT_HASH))
        }
    }

    @Test
    fun languageButtonOpensDialogAndChangesLanguage() {
        onView(withId(R.id.btnLanguage)).perform(click())
        flushMainLooper()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        assertEquals(LocaleHelper.SUPPORTED_LANGUAGES.size, dialog.listView.adapter.count)

        val targetPosition = LocaleHelper.SUPPORTED_LANGUAGES.indexOfFirst { it.code == "en" }
        assertTrue(targetPosition >= 0)
        dialog.listView.performItemClick(
            dialog.listView.adapter.getView(targetPosition, null, dialog.listView),
            targetPosition,
            dialog.listView.adapter.getItemId(targetPosition)
        )
        flushMainLooper()

        assertEquals("en", LocaleHelper.getLanguage(ApplicationProvider.getApplicationContext()))
    }

    @Test
    fun privacyButtonOpensPrivacySettings() {
        var startedIntent: Intent? = null
        scenario.onFragment { fragment ->
            startedIntent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
        }

        onView(withId(R.id.btnPrivacy)).perform(click())
        flushMainLooper()

        scenario.onFragment { fragment ->
            startedIntent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
        }
        assertNotNull(startedIntent)
        assertEquals("com.xenoamess.qrcodesimple.PrivacySettingsActivity", startedIntent?.component?.className)
    }

    @Test
    fun githubProjectButtonOpensUrl() {
        assertUrlButtonOpens(R.id.btnGitHubProject, "https://github.com/XenoAmess-Auto/qr_code_simple")
    }

    @Test
    fun donateButtonOpensUrl() {
        assertUrlButtonOpens(R.id.btnDonate, "https://ko-fi.com/xenoamess")
    }

    @Test
    fun checkUpdateButtonShowsDialogWhenNewVersionAvailable() {
        AppUpdateManager.fetcherForTesting = {
            AppUpdateChecker.ReleaseInfo(
                version = "99.0.0",
                changelog = "big changes",
                htmlUrl = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/tag/v99.0.0",
                apkUrl = "https://example.com/app-release.apk",
                apkSizeBytes = 123L
            )
        }
        try {
            onView(withId(R.id.btnCheckUpdate)).perform(click())
            assertTrue(
                "Update dialog should appear",
                waitFor { ShadowDialog.getLatestDialog() != null }
            )
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val title = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text.toString()
            assertTrue(title.contains("99.0.0"))
        } finally {
            AppUpdateManager.fetcherForTesting = null
        }
    }

    @Test
    fun checkUpdateButtonShowsToastWhenUpToDate() {
        AppUpdateManager.fetcherForTesting = {
            AppUpdateChecker.ReleaseInfo(
                version = "0.0.1",
                changelog = "",
                htmlUrl = "https://example.com",
                apkUrl = null,
                apkSizeBytes = 0L
            )
        }
        try {
            onView(withId(R.id.btnCheckUpdate)).perform(click())
            assertTrue(
                "Up-to-date toast should appear",
                waitFor { ShadowToast.getTextOfLatestToast() != null }
            )
            assertEquals(
                ApplicationProvider.getApplicationContext<Context>().getString(R.string.update_already_latest),
                ShadowToast.getTextOfLatestToast()
            )
        } finally {
            AppUpdateManager.fetcherForTesting = null
        }
    }

    @Test
    fun checkUpdateButtonShowsToastWhenCheckFails() {
        AppUpdateManager.fetcherForTesting = { null }
        try {
            onView(withId(R.id.btnCheckUpdate)).perform(click())
            assertTrue(
                "Failure toast should appear",
                waitFor { ShadowToast.getTextOfLatestToast() != null }
            )
            assertEquals(
                ApplicationProvider.getApplicationContext<Context>().getString(R.string.update_check_failed),
                ShadowToast.getTextOfLatestToast()
            )
        } finally {
            AppUpdateManager.fetcherForTesting = null
        }
    }

    @Test
    fun autoUpdateSwitchPersistsPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        // 避免打开开关时触发真实网络请求
        AppUpdateManager.fetcherForTesting = { null }

        try {
            scenario.onFragment { fragment ->
                val switch = fragment.requireView().findViewById<android.widget.Switch>(R.id.switchAutoUpdate)
                assertEquals(false, switch.isChecked)
                switch.isChecked = true
            }
            flushMainLooper()
            assertTrue(QRCodeApp.isAppUpdateAutoCheckEnabled(context))

            scenario.onFragment { fragment ->
                fragment.requireView().findViewById<android.widget.Switch>(R.id.switchAutoUpdate).isChecked = false
            }
            flushMainLooper()
            assertEquals(false, QRCodeApp.isAppUpdateAutoCheckEnabled(context))
        } finally {
            AppUpdateManager.fetcherForTesting = null
            QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        }
    }

    private fun waitFor(maxMs: Long = 3000, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs) {
            flushMainLooper()
            if (condition()) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun assertUrlButtonOpens(buttonId: Int, expectedUrl: String) {
        var startedIntent: Intent? = null
        scenario.onFragment { fragment ->
            startedIntent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
        }

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<View>(buttonId).performClick()
        }
        flushMainLooper()

        scenario.onFragment { fragment ->
            startedIntent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
        }
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent?.action)
        assertEquals(expectedUrl, startedIntent?.data.toString())
    }
}

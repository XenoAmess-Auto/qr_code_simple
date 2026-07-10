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

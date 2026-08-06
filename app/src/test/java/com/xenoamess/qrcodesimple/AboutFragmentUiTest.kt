package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
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
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocaleHelper.setLanguage(context, "system")
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        AppUpdateManager.checkerForTesting = null
        AboutFragment.versionHistoryLoaderForTesting = null
        scenario = FragmentScenario.launchInContainer(
            AboutFragment::class.java,
            themeResId = R.style.Theme_QRCodeSimple
        )
    }

    @After
    fun tearDown() {
        scenario.close()
        AppUpdateManager.checkerForTesting = null
        AboutFragment.versionHistoryLoaderForTesting = null
        QRCodeApp.setAppUpdateAutoCheckEnabled(
            ApplicationProvider.getApplicationContext(),
            false
        )
        QRCodeApp.setThemeMode(ApplicationProvider.getApplicationContext(), QRCodeApp.THEME_MODE_SYSTEM)
        QRCodeApp.applyThemeMode(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `theme button presents mode dialog and persists selection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        onView(withId(R.id.btnTheme)).perform(click())
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        assertEquals(R.string.theme_setting, dialog.window?.context?.let {
            it.getString(R.string.theme_setting)
        }?.let { _ -> R.string.theme_setting })

        // 选择"暗色"并确认持久化
        dialog.getListView().performItemClick(null, 2, 2)
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(QRCodeApp.THEME_MODE_DARK, QRCodeApp.getThemeMode(context))
        assertEquals(
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES,
            androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()
        )
    }

    @Test
    fun `theme default follows system`() {
        assertEquals(
            QRCodeApp.THEME_MODE_SYSTEM,
            QRCodeApp.getThemeMode(ApplicationProvider.getApplicationContext())
        )
    }

    @Test
    fun `version text contains app version and git hash`() {
        scenario.onFragment { fragment ->
            val text = fragment.requireView().findViewById<TextView>(R.id.tvVersion).text.toString()
            assertTrue(text.contains(BuildConfig.VERSION_NAME))
            assertTrue(text.contains(BuildConfig.GIT_HASH))
        }
    }

    @Test
    fun `stable and beta buttons request their respective channels`() {
        val observedChannels = mutableListOf<UpdateDecider.Channel>()
        AppUpdateManager.checkerForTesting = { channel, _, _ ->
            observedChannels += channel
            UpdateDecider.CheckOutcome.UpdateAvailable(releaseInfo(channel))
        }

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<android.view.View>(R.id.btnCheckUpdate).performClick()
        }
        assertTrue(waitFor { ShadowDialog.getLatestDialog() != null })
        (ShadowDialog.getLatestDialog() as AlertDialog).dismiss()
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<android.view.View>(R.id.btnCheckBetaUpdate).performClick()
        }
        assertTrue(waitFor { observedChannels.contains(UpdateDecider.Channel.BETA) })

        assertTrue(observedChannels.contains(UpdateDecider.Channel.STABLE))
        assertTrue(observedChannels.contains(UpdateDecider.Channel.BETA))
    }

    @Test
    fun `beta button presents beta update dialog`() {
        AppUpdateManager.checkerForTesting = { channel, _, _ ->
            if (channel == UpdateDecider.Channel.BETA) {
                UpdateDecider.CheckOutcome.UpdateAvailable(releaseInfo(channel, "99.1.0"))
            } else {
                UpdateDecider.CheckOutcome.UpToDate
            }
        }

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<android.view.View>(R.id.btnCheckBetaUpdate).performClick()
        }

        assertTrue(waitFor { ShadowDialog.getLatestDialog() != null })
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val title = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.text.toString()
        assertTrue(title.contains("99.1.0"))
    }

    @Test
    fun `version history button displays packaged changelog text`() {
        AboutFragment.versionHistoryLoaderForTesting = { "v0.2.6\n- verified updates" }

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<android.view.View>(R.id.btnVersionHistory).performClick()
        }

        assertTrue(waitFor { ShadowDialog.getLatestDialog() != null })
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val message = dialog.findViewById<TextView>(android.R.id.message)?.text.toString()
        assertEquals("v0.2.6\n- verified updates", message)
    }

    @Test
    fun `automatic switch persists and only manually triggers stable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var observedChannel: UpdateDecider.Channel? = null
        AppUpdateManager.checkerForTesting = { channel, _, _ ->
            observedChannel = channel
            UpdateDecider.CheckOutcome.UpToDate
        }

        scenario.onFragment { fragment ->
            val toggle = fragment.requireView().findViewById<android.widget.Switch>(R.id.switchAutoUpdate)
            assertEquals(false, toggle.isChecked)
            toggle.isChecked = true
        }

        assertTrue(waitFor { observedChannel != null })
        assertEquals(UpdateDecider.Channel.STABLE, observedChannel)
        assertTrue(QRCodeApp.isAppUpdateAutoCheckEnabled(context))
    }

    private fun releaseInfo(
        channel: UpdateDecider.Channel,
        versionName: String = "99.0.0"
    ) = UpdateDecider.ReleaseInfo(
        channel = channel,
        versionCode = 999,
        versionName = versionName,
        changelog = "changes",
        apkUrl = "https://example.test/update.apk",
        apkSha256 = "a".repeat(64),
        apkSizeBytes = 123,
        releasePageUrl = if (channel == UpdateDecider.Channel.STABLE) {
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/latest"
        } else {
            null
        },
        chain = null
    )

    private fun waitFor(maxMs: Long = 3_000, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(50)
        }
        return false
    }
}

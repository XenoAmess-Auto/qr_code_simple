package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * AppUpdateManager 自动检查节流与弹窗流程测试（注入假 fetcher，不触网）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = QRCodeApp::class)
class AppUpdateManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        prefs().edit().remove(KEY_LAST_CHECK).apply()
    }

    @After
    fun tearDown() {
        AppUpdateManager.fetcherForTesting = null
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        prefs().edit().remove(KEY_LAST_CHECK).apply()
    }

    private fun prefs() = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private fun flushMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
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

    private fun newRelease(version: String = "99.0.0") = AppUpdateChecker.ReleaseInfo(
        version = version,
        changelog = "big changes",
        htmlUrl = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/tag/v$version",
        apkUrl = "https://example.com/app-release.apk",
        apkSizeBytes = 123L
    )

    @Test
    fun `auto check skipped when switch disabled`() {
        var fetchCount = 0
        AppUpdateManager.fetcherForTesting = { fetchCount++; newRelease() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            flushMainLooper()
            Thread.sleep(300)
            flushMainLooper()
        }
        assertEquals(0, fetchCount)
    }

    @Test
    fun `auto check skipped when throttled within 24h`() {
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, true)
        prefs().edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        var fetchCount = 0
        AppUpdateManager.fetcherForTesting = { fetchCount++; newRelease() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            flushMainLooper()
            Thread.sleep(300)
            flushMainLooper()
        }
        assertEquals(0, fetchCount)
    }

    @Test
    fun `auto check shows dialog when new version found`() {
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, true)
        var fetchCount = 0
        AppUpdateManager.fetcherForTesting = { fetchCount++; newRelease() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertTrue(
                "Update dialog should appear",
                waitFor { ShadowDialog.getLatestDialog() != null }
            )
        }
        assertEquals(1, fetchCount)
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        // 检查时间已被记录，24h 内不会再次检查
        assertFalse(QRCodeApp.tryMarkAppUpdateChecked(context))
    }

    @Test
    fun `auto check silent when up to date`() {
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, true)
        AppUpdateManager.fetcherForTesting = { newRelease("0.0.1") }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            flushMainLooper()
            Thread.sleep(300)
            flushMainLooper()
        }
        assertEquals(null, ShadowDialog.getLatestDialog())
    }

    @Test
    fun `tryMarkAppUpdateChecked throttles within 24h`() {
        assertTrue(QRCodeApp.tryMarkAppUpdateChecked(context))
        assertFalse(QRCodeApp.tryMarkAppUpdateChecked(context))
    }

    @Test
    fun `auto check preference defaults to false`() {
        assertFalse(QRCodeApp.isAppUpdateAutoCheckEnabled(context))
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, true)
        assertTrue(QRCodeApp.isAppUpdateAutoCheckEnabled(context))
    }

    companion object {
        private const val KEY_LAST_CHECK = "app_update_last_check"
    }
}

package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = QRCodeApp::class)
class AppUpdateManagerTest {

    private lateinit var context: Context

    private class FakeConnection(
        url: URL,
        private val code: Int,
        private val body: ByteArray
    ) : HttpURLConnection(url) {
        override fun getResponseCode(): Int = code
        override fun getContentLengthLong(): Long = body.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppUpdateManager.checkerForTesting = null
        AppUpdateManager.downloadConnectionFactoryForTesting = null
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        prefs().edit().remove(KEY_LAST_CHECK).apply()
    }

    @After
    fun tearDown() {
        AppUpdateManager.checkerForTesting = null
        AppUpdateManager.downloadConnectionFactoryForTesting = null
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        prefs().edit().remove(KEY_LAST_CHECK).remove(KEY_AUTO_CHECK).apply()
        File(context.filesDir, "manager-download-test.apk").delete()
        File(context.filesDir, "manager-download-test.apk.part").delete()
    }

    @Test
    fun `auto check is skipped when stable switch is disabled`() {
        var fetchCount = 0
        AppUpdateManager.checkerForTesting = { _, _, _ ->
            fetchCount++
            UpdateDecider.CheckOutcome.UpdateAvailable(newRelease())
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            Thread.sleep(250)
            flushMainLooper()
        }

        assertEquals(0, fetchCount)
    }

    @Test
    fun `automatic check queries stable only and remains throttled for 24 hours`() {
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, true)
        var observedChannel: UpdateDecider.Channel? = null
        AppUpdateManager.checkerForTesting = { channel, _, _ ->
            observedChannel = channel
            UpdateDecider.CheckOutcome.UpdateAvailable(newRelease())
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertTrue(waitFor { ShadowDialog.getLatestDialog() != null })
        }

        assertEquals(UpdateDecider.Channel.STABLE, observedChannel)
        assertFalse(QRCodeApp.tryMarkAppUpdateChecked(context))
    }

    @Test
    fun `manual beta check uses beta channel`() {
        var observedChannel: UpdateDecider.Channel? = null
        AppUpdateManager.checkerForTesting = { channel, _, _ ->
            observedChannel = channel
            UpdateDecider.CheckOutcome.UpdateAvailable(newRelease(channel = channel, versionName = "99.1.0"))
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { AppUpdateManager.checkBetaUpdate(it) }
            assertTrue(waitFor { ShadowDialog.getLatestDialog() != null })
        }

        assertEquals(UpdateDecider.Channel.BETA, observedChannel)
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
    }

    @Test
    fun `auto check defaults to disabled for absent preferences`() {
        prefs().edit().remove(KEY_AUTO_CHECK).apply()

        assertFalse(QRCodeApp.isAppUpdateAutoCheckEnabled(context))
    }

    @Test
    fun `verified download falls back through mirrors then verifies bytes`() {
        val payload = ByteArray(8 * 1024) { (it % 251).toByte() }
        val target = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/update.apk"
        val destination = File(context.filesDir, "manager-mirror-download-test.apk")
        val seen = mutableListOf<String>()
        AppUpdateManager.downloadConnectionFactoryForTesting = { url ->
            val text = url.toString()
            seen.add(text)
            when (text) {
                "https://ghfast.top/$target" -> FakeConnection(url, 404, ByteArray(0))
                "https://gh-proxy.com/$target" -> FakeConnection(url, 200, payload)
                else -> error("Unexpected URL: $url")
            }
        }

        val result = AppUpdateManager.downloadVerifiedArtifact(
            url = target,
            endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
            destination = destination,
            expectedSizeBytes = payload.size.toLong(),
            expectedSha256 = sha256(payload),
            onProgress = {}
        )

        assertNotNull(result)
        assertTrue(payload.contentEquals(destination.readBytes()))
        assertEquals(
            listOf("https://ghfast.top/$target", "https://gh-proxy.com/$target"),
            seen
        )
    }

    @Test
    fun `verified download publishes only exact sized and hashed artifact`() {
        val payload = ByteArray(16 * 1024) { (it % 251).toByte() }
        val destination = File(context.filesDir, "manager-download-test.apk")
        AppUpdateManager.downloadConnectionFactoryForTesting = {
            FakeConnection(URL("https://objects.githubusercontent.com/release.apk"), 200, payload)
        }
        val progress = mutableListOf<Int>()

        val result = AppUpdateManager.downloadVerifiedArtifact(
            url = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/update.apk",
            endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
            destination = destination,
            expectedSizeBytes = payload.size.toLong(),
            expectedSha256 = sha256(payload),
            onProgress = { progress += it }
        )

        assertNotNull(result)
        assertTrue(payload.contentEquals(destination.readBytes()))
        assertFalse(File(context.filesDir, "manager-download-test.apk.part").exists())
        assertTrue(progress.isNotEmpty() && progress.last() == 100)
    }

    @Test
    fun `failed verification removes part and leaves prior complete artifact untouched`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val destination = File(context.filesDir, "manager-download-test.apk")
        destination.writeBytes(byteArrayOf(9, 9))
        AppUpdateManager.downloadConnectionFactoryForTesting = {
            FakeConnection(URL("https://objects.githubusercontent.com/release.apk"), 200, payload)
        }

        val result = AppUpdateManager.downloadVerifiedArtifact(
            url = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/update.apk",
            endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
            destination = destination,
            expectedSizeBytes = payload.size.toLong(),
            expectedSha256 = "0".repeat(64),
            onProgress = {}
        )

        assertEquals(null, result)
        assertTrue(byteArrayOf(9, 9).contentEquals(destination.readBytes()))
        assertFalse(File(context.filesDir, "manager-download-test.apk.part").exists())
    }

    @Test
    fun `artifact download rejects an untrusted source before opening a connection`() {
        var opened = false
        AppUpdateManager.downloadConnectionFactoryForTesting = {
            opened = true
            FakeConnection(it, 200, byteArrayOf(1))
        }

        val result = AppUpdateManager.downloadVerifiedArtifact(
            url = "https://example.test/update.apk",
            endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
            destination = File(context.filesDir, "manager-download-test.apk"),
            expectedSizeBytes = 1,
            expectedSha256 = sha256(byteArrayOf(1)),
            onProgress = {}
        )

        assertEquals(null, result)
        assertFalse(opened)
    }

    private fun newRelease(
        channel: UpdateDecider.Channel = UpdateDecider.Channel.STABLE,
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

    private fun prefs() = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private fun flushMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitFor(maxMs: Long = 3_000, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs) {
            flushMainLooper()
            if (condition()) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }

    companion object {
        private const val KEY_LAST_CHECK = "app_update_last_check"
        private const val KEY_AUTO_CHECK = "app_update_auto_check"
    }
}

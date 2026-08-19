package com.xenoamess.qrcodesimple

import android.content.DialogInterface
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
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
import org.robolectric.shadows.ShadowToast

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

    private class BlockingConnection(
        url: URL,
        private val releaseOnDisconnect: Boolean = true
    ) : HttpURLConnection(url) {
        val readStarted = CountDownLatch(1)
        val readFinished = CountDownLatch(1)
        val disconnected = AtomicBoolean(false)
        private val released = CountDownLatch(1)

        override fun getResponseCode(): Int = HTTP_OK
        override fun getContentLengthLong(): Long = 1
        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int {
                readStarted.countDown()
                released.await(3, TimeUnit.SECONDS)
                readFinished.countDown()
                return -1
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = read()
        }

        override fun disconnect() {
            disconnected.set(true)
            if (releaseOnDisconnect) released.countDown()
        }

        fun releaseRead() = released.countDown()

        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppUpdateManager.resetForTesting()
        AppUpdateManager.checkerForTesting = null
        AppUpdateManager.downloadConnectionFactoryForTesting = null
        File(context.filesDir, "updates").deleteRecursively()
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        prefs().edit().remove(KEY_LAST_CHECK).apply()
    }

    @After
    fun tearDown() {
        AppUpdateManager.resetForTesting()
        AppUpdateManager.checkerForTesting = null
        AppUpdateManager.downloadConnectionFactoryForTesting = null
        QRCodeApp.setAppUpdateAutoCheckEnabled(context, false)
        prefs().edit().remove(KEY_LAST_CHECK).remove(KEY_AUTO_CHECK).apply()
        File(context.filesDir, "manager-download-test.apk").delete()
        File(context.filesDir, "manager-download-test.apk.part").delete()
        File(context.filesDir, "updates").deleteRecursively()
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
    fun `returning from unknown source settings without permission clears pending and allows retry`() {
        AppUpdateManager.canInstallPackagesForTesting = { false }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppUpdateManager.startInstallForTesting(activity, newRelease())
                assertTrue(AppUpdateManager.hasPendingInstallForTesting())
            }

            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(AppUpdateManager.hasPendingInstallForTesting())

                AppUpdateManager.onInstallPermissionResult(activity)
                assertFalse(AppUpdateManager.hasPendingInstallForTesting())
                assertEquals(
                    activity.getString(R.string.update_install_permission_not_granted),
                    ShadowToast.getTextOfLatestToast()
                )

                AppUpdateManager.startInstallForTesting(activity, newRelease())
                assertTrue(AppUpdateManager.hasPendingInstallForTesting())
            }
        }
    }

    @Test
    fun `host resume consumes granted unknown source permission without activity result`() {
        val permissionGranted = AtomicBoolean(false)
        val apk = File(context.filesDir, "resume-permission.apk")
        AppUpdateManager.canInstallPackagesForTesting = { permissionGranted.get() }
        AppUpdateManager.acquireUpdateApkForTesting = { _, _, _, _ ->
            apk.apply { writeBytes(byteArrayOf(1)) }
        }
        AppUpdateManager.archiveVerifierForTesting = { _, _, _ -> true }
        AppUpdateManager.installApkForTesting = { _, _ -> true }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppUpdateManager.startInstallForTesting(activity, newRelease())
                assertTrue(AppUpdateManager.hasPendingInstallForTesting())

                permissionGranted.set(true)
                AppUpdateManager.onHostResume(activity)
            }

            assertFalse(AppUpdateManager.hasPendingInstallForTesting())
            assertTrue(waitFor { AppUpdateManager.hasPendingInstallerForTesting() })
        }
    }

    @Test
    fun `pending permission state safely expires with process state`() {
        AppUpdateManager.canInstallPackagesForTesting = { false }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                AppUpdateManager.startInstallForTesting(activity, newRelease())
                assertTrue(AppUpdateManager.hasPendingInstallForTesting())

                AppUpdateManager.resetForTesting()
                AppUpdateManager.onInstallPermissionResult(activity)

                assertFalse(AppUpdateManager.hasPendingInstallForTesting())
                assertFalse(AppUpdateManager.hasActiveDownloadForTesting())
            }
        }
    }

    @Test
    fun `orphan cleanup removes update work while excluding active artifacts`() {
        val updates = File(context.filesDir, "updates").apply { mkdirs() }
        val orphanDownload = File(updates, "orphan.apk.1.download").apply { writeBytes(byteArrayOf(1)) }
        val orphanPart = File(updates, "orphan.apk.1.download.part").apply { writeBytes(byteArrayOf(1)) }
        val orphanLegacyApk = File(updates, "qr-code-simple-0.2.6.apk").apply {
            writeBytes(byteArrayOf(1))
        }
        val orphanIncremental = File(updates, "incremental-orphan").apply {
            mkdirs()
            File(this, "hop-0.patch").writeBytes(byteArrayOf(1))
        }
        val activeDownload = File(updates, "active.apk.2.download").apply { writeBytes(byteArrayOf(2)) }
        val activePart = File(updates, "active.apk.2.download.part").apply { writeBytes(byteArrayOf(2)) }
        val protectedLegacyApk = File(updates, "qr-code-simple-0.2.7.apk").apply {
            writeBytes(byteArrayOf(2))
        }
        val activeIncremental = File(updates, "incremental-active").apply {
            mkdirs()
            File(this, "hop-0.patch").writeBytes(byteArrayOf(2))
        }
        val legacyLookalike = File(updates, "qr-code-simple-latest.apk").apply {
            writeBytes(byteArrayOf(3))
        }
        val legacyNamedDirectory = File(updates, "qr-code-simple-0.2.8.apk").apply {
            mkdirs()
            File(this, "keep.txt").writeText("keep")
        }
        val unrelated = File(updates, "keep.txt").apply { writeText("keep") }

        AppUpdateManager.cleanupUpdateArtifacts(
            context,
            setOf(
                activeDownload.absolutePath,
                activePart.absolutePath,
                protectedLegacyApk.absolutePath,
                activeIncremental.absolutePath
            )
        )

        assertFalse(orphanDownload.exists())
        assertFalse(orphanPart.exists())
        assertFalse(orphanLegacyApk.exists())
        assertFalse(orphanIncremental.exists())
        assertTrue(activeDownload.exists())
        assertTrue(activePart.exists())
        assertTrue(protectedLegacyApk.exists())
        assertTrue(activeIncremental.exists())
        assertTrue(legacyLookalike.exists())
        assertTrue(legacyNamedDirectory.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `orphan cleanup preserves legacy apk owned by pending installer`() {
        val apk = File(context.filesDir, "updates/qr-code-simple-99.0.0.apk")
        AppUpdateManager.canInstallPackagesForTesting = { true }
        AppUpdateManager.acquireUpdateApkForTesting = { _, _, _, _ ->
            apk.apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1))
            }
        }
        AppUpdateManager.archiveVerifierForTesting = { _, _, _ -> true }
        AppUpdateManager.installApkForTesting = { _, _ -> true }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { AppUpdateManager.startInstallForTesting(it, newRelease()) }
            assertTrue(waitFor { AppUpdateManager.hasPendingInstallerForTesting() })

            AppUpdateManager.cleanupUpdateArtifacts(context)

            assertTrue(apk.exists())
        }
    }

    @Test
    fun `orphan cleanup preserves active download before cancel disconnects network`() {
        AppUpdateManager.canInstallPackagesForTesting = { true }
        var connection: BlockingConnection? = null
        AppUpdateManager.downloadConnectionFactoryForTesting = { url ->
            BlockingConnection(url).also { connection = it }
        }
        val release = downloadableRelease()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { AppUpdateManager.startInstallForTesting(it, release) }
            assertTrue(waitFor { connection?.readStarted?.count == 0L })
            assertTrue(waitFor { updatePartFiles().size == 1 })

            val activePart = updatePartFiles().single()
            AppUpdateManager.cleanupUpdateArtifacts(context)

            assertTrue(activePart.exists())

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()

            assertTrue(waitFor { connection?.disconnected?.get() == true })
            assertTrue(waitFor { !AppUpdateManager.hasActiveDownloadForTesting() })
            assertFalse(dialog.isShowing)
        }
    }

    @Test
    fun `cancel during connection creation disconnects the late connection`() {
        AppUpdateManager.canInstallPackagesForTesting = { true }
        val factoryEntered = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        val lateConnection = AtomicReference<BlockingConnection>()
        AppUpdateManager.downloadConnectionFactoryForTesting = { url ->
            factoryEntered.countDown()
            releaseFactory.await(3, TimeUnit.SECONDS)
            BlockingConnection(url).also { lateConnection.set(it) }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                AppUpdateManager.startInstallForTesting(it, downloadableRelease())
            }
            assertTrue(factoryEntered.await(3, TimeUnit.SECONDS))

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            releaseFactory.countDown()

            assertTrue(waitFor { lateConnection.get()?.disconnected?.get() == true })
            assertFalse(AppUpdateManager.hasActiveDownloadForTesting())
            assertFalse(dialog.isShowing)
        }
    }

    @Test
    fun `superseded session cannot clear new connection state or part file`() {
        AppUpdateManager.canInstallPackagesForTesting = { true }
        val connectionNumber = AtomicInteger()
        val first = AtomicReference<BlockingConnection>()
        val second = AtomicReference<BlockingConnection>()
        AppUpdateManager.downloadConnectionFactoryForTesting = { url ->
            BlockingConnection(url, releaseOnDisconnect = false).also { connection ->
                if (connectionNumber.incrementAndGet() == 1) first.set(connection) else second.set(connection)
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                AppUpdateManager.startInstallForTesting(it, downloadableRelease())
            }
            assertTrue(waitFor { first.get()?.readStarted?.count == 0L })

            scenario.onActivity {
                AppUpdateManager.startInstallForTesting(it, downloadableRelease())
            }
            assertTrue(waitFor { second.get()?.readStarted?.count == 0L })
            assertTrue(first.get()?.disconnected?.get() == true)
            assertTrue(waitFor { updatePartFiles().size == 2 })

            first.get()?.releaseRead()

            assertTrue(waitFor { updatePartFiles().size == 1 })
            assertTrue(AppUpdateManager.hasActiveDownloadForTesting())
            assertFalse(second.get()?.disconnected?.get() == true)

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
            second.get()?.releaseRead()
            assertTrue(second.get()?.readFinished?.await(3, TimeUnit.SECONDS) == true)
            assertTrue(waitFor(maxMs = 10_000) {
                second.get()?.disconnected?.get() == true && updatePartFiles().isEmpty()
            })
        }
    }

    @Test
    fun `destroying host cancels download and dismisses progress dialog`() {
        AppUpdateManager.canInstallPackagesForTesting = { true }
        var connection: BlockingConnection? = null
        AppUpdateManager.downloadConnectionFactoryForTesting = { url ->
            BlockingConnection(url).also { connection = it }
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { AppUpdateManager.startInstallForTesting(it, downloadableRelease()) }
        assertTrue(waitFor { connection?.readStarted?.count == 0L })
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog

        scenario.close()

        assertTrue(waitFor { connection?.disconnected?.get() == true })
        assertFalse(dialog.isShowing)
        assertFalse(AppUpdateManager.hasActiveDownloadForTesting())
    }

    @Test
    fun `resume after package installer cancellation checks version and clears attempt`() {
        AppUpdateManager.canInstallPackagesForTesting = { true }
        val apk = File(context.filesDir, "installer-attempt.apk")
        AppUpdateManager.acquireUpdateApkForTesting = { _, _, _, _ ->
            apk.apply { writeBytes(byteArrayOf(1)) }
        }
        AppUpdateManager.archiveVerifierForTesting = { _, _, _ -> true }
        AppUpdateManager.installApkForTesting = { _, _ -> true }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { AppUpdateManager.startInstallForTesting(it, newRelease()) }
            assertTrue(waitFor { AppUpdateManager.hasPendingInstallerForTesting() })
            assertTrue(apk.exists())

            scenario.onActivity { AppUpdateManager.onHostResume(it) }

            assertFalse(AppUpdateManager.hasPendingInstallerForTesting())
            assertFalse(apk.exists())
            assertTrue(ShadowDialog.getLatestDialog()?.isShowing == true)
        }
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

    @Test
    fun `artifact cancellation is propagated rather than converted to failure`() {
        val error = org.junit.Assert.assertThrows(CancellationException::class.java) {
            AppUpdateManager.downloadVerifiedArtifact(
                url = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/update.apk",
                endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
                destination = File(context.filesDir, "manager-download-test.apk"),
                expectedSizeBytes = 1,
                expectedSha256 = sha256(byteArrayOf(1)),
                onProgress = {},
                isCancelled = { true }
            )
        }

        assertNotNull(error)
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

    private fun downloadableRelease(): UpdateDecider.ReleaseInfo {
        return newRelease().copy(
            apkUrl = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v99.0.0/update.apk",
            apkSha256 = sha256(byteArrayOf(1)),
            apkSizeBytes = 1
        )
    }

    private fun prefs() = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private fun updatePartFiles(): List<File> = File(context.filesDir, "updates")
        .listFiles()
        .orEmpty()
        .filter { it.name.endsWith(".part") }

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

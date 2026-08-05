package com.xenoamess.qrcodesimple

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IncrementalUpdaterTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var baseApk: File
    private lateinit var updater: IncrementalUpdater
    private val patchByUrl = mutableMapOf<String, ByteArray>()

    @Before
    fun setUp() {
        baseApk = File(context.filesDir, "incremental-updater-base.apk")
        baseApk.delete()
        File(context.filesDir, "updates").deleteRecursively()
        patchByUrl.clear()
        updater = IncrementalUpdater(context).apply {
            installedApkProvider = { baseApk.takeIf { it.isFile } }
            // Fake patcher: the patch bytes ARE the target bytes (mirrors the ApkDiffPatch
            // contract where a valid patch reconstructs the target exactly).
            patcher = { _, patch, output ->
                output.parentFile?.mkdirs()
                output.writeBytes(patch.readBytes())
            }
        }
    }

    @After
    fun tearDown() {
        baseApk.delete()
        File(context.filesDir, "updates").deleteRecursively()
    }

    @Test
    fun `two hop chain downloads verifies patches and produces verified target`() = runBlocking {
        val base = ByteArray(8 * 1024) { (it % 31).toByte() }
        val middle = base.copyOf().also { bytes ->
            for (index in 500 until 900) bytes[index] = (bytes[index] + 7).toByte()
        }
        val target = middle.copyOf().also { bytes ->
            for (index in 2_000 until 2_400) bytes[index] = (bytes[index] - 3).toByte()
        }
        baseApk.writeBytes(base)
        val first = createHop(18, 19, middle)
        val second = createHop(19, 20, target)
        val chain = UpdateDecider.UpdateChain(
            fromApkSha256 = sha256(base),
            totalSizeBytes = first.sizeBytes + second.sizeBytes,
            hops = listOf(first, second)
        )
        updater.downloader = fakeDownloader()
        val output = File(context.filesDir, "updates/qr-code-simple-0.2.7.apk")
        val progress = mutableListOf<Int>()

        val result = updater.executeChain(
            chain,
            output,
            sha256(target),
            target.size.toLong()
        ) { progress += it }

        assertNotNull(result)
        assertTrue(target.contentEquals(result!!.readBytes()))
        assertTrue(progress.isNotEmpty() && progress.last() == 100)
        assertTrue(progress.zipWithNext().all { (previous, next) -> next >= previous })
        assertFalse(File(context.filesDir, "updates/incremental").exists())
    }

    @Test
    fun `patch hash failure cleans all temporary artifacts`() = runBlocking {
        val base = ByteArray(4 * 1024) { 1 }
        val target = base.copyOf().also { it[100] = 9 }
        baseApk.writeBytes(base)
        val hop = createHop(18, 19, target).copy(patchSha256 = "0".repeat(64))
        val chain = UpdateDecider.UpdateChain(
            fromApkSha256 = sha256(base),
            totalSizeBytes = hop.sizeBytes,
            hops = listOf(hop)
        )
        updater.downloader = fakeDownloader()
        val output = File(context.filesDir, "updates/qr-code-simple-0.2.6.apk")

        assertNull(updater.executeChain(chain, output, sha256(target), target.size.toLong()) {})
        assertFalse(output.exists())
        assertFalse(File(output.parentFile, ".${output.name}.incremental.part").exists())
        assertFalse(File(context.filesDir, "updates/incremental").exists())
    }

    @Test
    fun `result hash failure cleans output and temp files`() = runBlocking {
        val base = ByteArray(4 * 1024) { 1 }
        val target = base.copyOf().also { it[100] = 9 }
        baseApk.writeBytes(base)
        val hop = createHop(18, 19, target).copy(resultSha256 = "0".repeat(64))
        val chain = UpdateDecider.UpdateChain(
            fromApkSha256 = sha256(base),
            totalSizeBytes = hop.sizeBytes,
            hops = listOf(hop)
        )
        updater.downloader = fakeDownloader()
        val output = File(context.filesDir, "updates/qr-code-simple-0.2.6.apk")

        assertNull(updater.executeChain(chain, output, sha256(target), target.size.toLong()) {})
        assertFalse(output.exists())
        assertFalse(File(output.parentFile, ".${output.name}.incremental.part").exists())
        assertFalse(File(context.filesDir, "updates/incremental").exists())
    }

    @Test
    fun `base hash mismatch rejects chain without patching`() = runBlocking {
        baseApk.writeBytes(ByteArray(4 * 1024) { 1 })
        val hop = createHop(18, 19, ByteArray(4 * 1024) { 2 })
        val chain = UpdateDecider.UpdateChain(
            fromApkSha256 = "f".repeat(64),
            totalSizeBytes = hop.sizeBytes,
            hops = listOf(hop)
        )
        val output = File(context.filesDir, "updates/qr-code-simple-0.2.6.apk")

        assertNull(updater.executeChain(chain, output, "0".repeat(64), 1) {})
        assertFalse(output.exists())
        assertFalse(File(context.filesDir, "updates/incremental").exists())
    }

    private fun createHop(
        fromVersionCode: Long,
        toVersionCode: Long,
        target: ByteArray
    ): UpdateDecider.PatchHop {
        val url = "https://example.test/$fromVersionCode-$toVersionCode.patch"
        patchByUrl[url] = target
        return UpdateDecider.PatchHop(
            toVersionCode = toVersionCode,
            url = url,
            sizeBytes = target.size.toLong(),
            patchSha256 = sha256(target),
            resultSha256 = sha256(target)
        )
    }

    private fun fakeDownloader(): suspend (
        String,
        File,
        UpdateDecider.PatchHop,
        (Int) -> Unit
    ) -> Boolean = { url, destination, _, onProgress ->
        val bytes = patchByUrl[url]
        if (bytes == null) {
            false
        } else {
            destination.parentFile?.mkdirs()
            destination.writeBytes(bytes)
            onProgress(50)
            onProgress(100)
            true
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }
}

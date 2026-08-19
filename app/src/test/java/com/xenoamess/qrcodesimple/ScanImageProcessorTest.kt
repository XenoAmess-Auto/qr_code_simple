package com.xenoamess.qrcodesimple

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ScanImageProcessorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // 1x1 透明 PNG 的真实字节，避免依赖 Robolectric 的 Bitmap.compress 伪编码
    private val pngBytes = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
        android.util.Base64.DEFAULT
    )

    private fun writeFile(name: String, bytes: ByteArray): Uri {
        val file = File(context.cacheDir, name)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitFor(maxMs: Long = 5000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun `isDecodable returns true for real png`() {
        val uri = writeFile("real.png", pngBytes)
        assertTrue(ScanImageProcessor.isDecodable(context, uri))
    }

    @Test
    fun `isDecodable returns false for missing file`() {
        val uri = Uri.fromFile(File(context.cacheDir, "does_not_exist.png"))
        assertFalse(ScanImageProcessor.isDecodable(context, uri))
    }

    @Test
    fun `processImage routes decodable image to ResultActivity`() {
        val uri = writeFile("route.png", pngBytes)
        ScanImageProcessor.processImage(context, uri)

        assertTrue(
            "ResultActivity should be started",
            waitFor {
                Shadows.shadowOf(context as Application).peekNextStartedActivity() != null
            }
        )
        val intent = Shadows.shadowOf(context as Application).nextStartedActivity
        assertNotNull(intent)
        assertEquals(ResultActivity::class.java.name, intent.component?.className)
        assertEquals(uri.toString(), intent.getStringExtra(ResultActivity.EXTRA_BITMAP_URI))
    }

    @Test
    fun `processImage shows toast for undecodable image`() {
        val uri = Uri.fromFile(File(context.cacheDir, "missing_image.png"))
        ScanImageProcessor.processImage(context, uri)

        assertTrue(
            "toast should appear",
            waitFor { ShadowToast.getTextOfLatestToast() != null }
        )
        assertEquals(
            context.getString(R.string.failed_to_load_image),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `processMedia routes video mime to VideoScanActivity`() {
        val uri = writeFile("video.mp4", "fake".toByteArray())
        ScanImageProcessor.processMedia(context, uri, "video/mp4")

        val intent = Shadows.shadowOf(context as Application).nextStartedActivity
        assertNotNull(intent)
        assertEquals(VideoScanActivity::class.java.name, intent.component?.className)
        assertEquals(uri.toString(), intent.getStringExtra(VideoScanActivity.EXTRA_VIDEO_URI))
    }

    @Test
    fun `saturated executor reports busy instead of rejecting image requests`() {
        val executor = sharedMediaExecutor()
        val blockers = saturate(executor)
        try {
            val missing = Uri.fromFile(File(context.cacheDir, "rejected.png"))

            ScanImageProcessor.processImage(context, missing)
            ScanImageProcessor.processMedia(context, missing, "image/png")
            idleMain()

            assertEquals(
                context.getString(R.string.scan_queue_busy),
                ShadowToast.getTextOfLatestToast()
            )
        } finally {
            release(blockers, executor)
        }
    }

    @Test
    fun `cancelling queued shared import removes its future immediately`() {
        val executor = sharedMediaExecutor()
        val blockers = saturate(executor)
        try {
            val queuedBlockerRange = executor.maximumPoolSize until blockers.futures.size
            queuedBlockerRange.forEach { blockers.futures[it].cancel(true) }
            executor.purge()
            assertTrue(waitFor { executor.queue.isEmpty() })

            val operation = ScanImageProcessor.prepareSharedMedia(
                context,
                Uri.parse("content://malicious.provider/queued.png"),
                "image/png"
            ) {}
            assertTrue(waitFor { executor.queue.size == 1 })

            operation.cancel()

            assertTrue(waitFor { executor.queue.isEmpty() })
        } finally {
            release(blockers, executor)
        }
    }

    @Test
    fun `declared oversized image is rejected before opening stream`() {
        var opened = false
        var createdFile: File? = null

        val result = ScanImageProcessor.copySharedContentToCache(
            context = context,
            mimeType = "image/png",
            declaredSize = ScanImageProcessor.MAX_SHARED_IMAGE_BYTES + 1,
            onFileCreated = { createdFile = it },
            openInput = {
                opened = true
                pngBytes.inputStream()
            }
        )

        assertSame(ScanImageProcessor.CopyResult.TooLarge, result)
        assertFalse(opened)
        assertEquals(null, createdFile)
    }

    @Test
    fun `video uses a larger hard limit than image`() {
        val result = ScanImageProcessor.copySharedContentToCache(
            context,
            "video/mp4",
            ScanImageProcessor.MAX_SHARED_IMAGE_BYTES + 1
        ) {
            "small video".byteInputStream()
        }

        assertTrue(result is ScanImageProcessor.CopyResult.Success)
        val copiedUri = (result as ScanImageProcessor.CopyResult.Success).uri
        assertTrue(File(copiedUri.path!!).delete())
    }

    @Test
    fun `lying declared size is stopped by streaming image limit and partial file is deleted`() {
        var createdFile: File? = null
        val result = ScanImageProcessor.copySharedContentToCache(
            context = context,
            mimeType = "image/png",
            declaredSize = 1L,
            onFileCreated = { createdFile = it },
            openInput = { SizedInputStream(ScanImageProcessor.MAX_SHARED_IMAGE_BYTES + 1) }
        )

        assertSame(ScanImageProcessor.CopyResult.TooLarge, result)
        assertNotNull(createdFile)
        assertFalse(createdFile!!.exists())
    }

    @Test
    fun `stream exception deletes partial shared file`() {
        var createdFile: File? = null
        val result = ScanImageProcessor.copySharedContentToCache(
            context = context,
            mimeType = "image/png",
            declaredSize = null,
            onFileCreated = { createdFile = it },
            openInput = {
                object : InputStream() {
                    private var firstRead = true

                    override fun read(): Int = throw IOException("broken provider")

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (!firstRead) throw IOException("broken provider")
                        firstRead = false
                        buffer.fill(1, offset, offset + minOf(length, 1024))
                        return minOf(length, 1024)
                    }
                }
            }
        )

        assertSame(ScanImageProcessor.CopyResult.Failed, result)
        assertNotNull(createdFile)
        assertFalse(createdFile!!.exists())
    }

    @Test
    fun `expired orphan cleanup preserves recent shared file`() {
        val directory = ScanImageProcessor.sharedMediaDirectory(context).apply { mkdirs() }
        val suffix = System.nanoTime()
        val expired = File(directory, "shared_expired_$suffix.png").apply {
            writeBytes(pngBytes)
            setLastModified(1L)
        }
        val recent = File(directory, "shared_recent_$suffix.png").apply { writeBytes(pngBytes) }

        ScanImageProcessor.cleanupExpiredSharedMedia(
            context,
            ScanImageProcessor.SHARED_MEDIA_MAX_AGE_MS + 2L
        )

        assertFalse(expired.exists())
        assertTrue(recent.exists())
        recent.delete()
    }

    @Test
    fun `expired owned file with active lease survives cleanup until released`() {
        val copied = ScanImageProcessor.copySharedContentToCache(
            context,
            "image/png",
            pngBytes.size.toLong()
        ) { pngBytes.inputStream() } as ScanImageProcessor.CopyResult.Success
        val file = File(copied.uri.path!!).apply { setLastModified(1L) }

        ScanImageProcessor.retainOwnedSharedMedia(context, copied.uri, copied.leaseToken)
        ScanImageProcessor.cleanupExpiredSharedMedia(
            context,
            ScanImageProcessor.SHARED_MEDIA_MAX_AGE_MS + 2L
        )

        assertTrue(file.exists())
        ScanImageProcessor.deleteOwnedSharedMedia(
            context,
            copied.uri,
            ownsTempFile = true,
            leaseToken = copied.leaseToken
        )
        assertFalse(file.exists())
    }

    private class SizedInputStream(private var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining == 0L) return -1
            remaining--
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0L) return -1
            val count = minOf(remaining, length.toLong()).toInt()
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
    }

    private data class ExecutorBlockers(
        val latch: CountDownLatch,
        val futures: List<Future<*>>,
        val originalCorePoolSize: Int
    )

    private fun sharedMediaExecutor(): ThreadPoolExecutor {
        val field = ScanImageProcessor::class.java.getDeclaredField("executor")
        field.isAccessible = true
        return field.get(ScanImageProcessor) as ThreadPoolExecutor
    }

    private fun saturate(executor: ThreadPoolExecutor): ExecutorBlockers {
        assertTrue(waitFor { executor.activeCount == 0 && executor.queue.isEmpty() })
        val release = CountDownLatch(1)
        val started = CountDownLatch(executor.maximumPoolSize)
        val originalCorePoolSize = executor.corePoolSize
        executor.corePoolSize = executor.maximumPoolSize
        executor.prestartAllCoreThreads()
        val futures = MutableList(executor.maximumPoolSize) {
            executor.submit {
                started.countDown()
                release.await()
            }
        }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        repeat(executor.queue.remainingCapacity()) {
            futures += executor.submit { release.await() }
        }
        assertEquals(executor.maximumPoolSize, executor.activeCount)
        assertEquals(0, executor.queue.remainingCapacity())
        return ExecutorBlockers(release, futures, originalCorePoolSize)
    }

    private fun release(blockers: ExecutorBlockers, executor: ThreadPoolExecutor) {
        blockers.latch.countDown()
        blockers.futures.forEach { it.cancel(true) }
        executor.purge()
        assertTrue(waitFor { executor.activeCount == 0 && executor.queue.isEmpty() })
        executor.corePoolSize = blockers.originalCorePoolSize
    }
}

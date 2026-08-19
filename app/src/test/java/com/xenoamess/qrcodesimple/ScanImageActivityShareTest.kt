package com.xenoamess.qrcodesimple

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * ScanImageActivity 系统分享入口（ACTION_SEND / ACTION_SEND_MULTIPLE）路由测试。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ScanImageActivityShareTest {

    private val pngBytes = android.util.Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
        android.util.Base64.DEFAULT
    )

    @Before
    fun clearFileProviderCache() {
        val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    /** processImage 路由经后台线程探测后回主线程跳转，轮询等待其完成。 */
    private fun waitForStartedActivity(activity: android.app.Activity, maxMs: Long = 5000): Intent? {
        val deadline = System.currentTimeMillis() + maxMs
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            val next = Shadows.shadowOf(activity).peekNextStartedActivity()
            if (next != null) return Shadows.shadowOf(activity).nextStartedActivity
            Thread.sleep(20)
        }
        return null
    }

    /** 生成一个真实可解码的 PNG 临时文件，返回其 file Uri。 */
    private fun createTempImageUri(): Uri {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "share_test_${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    private fun createContentUri(name: String, bytes: ByteArray): Uri {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(directory, "${System.nanoTime()}_$name").apply { writeBytes(bytes) }
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    @Test
    fun `send image routes to ResultActivity and finishes`() {
        val imageUri = createContentUri("shared.png", pngBytes)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()
        idleMain()
        val activity = controller.get()

        val next = waitForStartedActivity(activity)
        assertNotNull(next)
        assertEquals(ResultActivity::class.java.name, next!!.component?.className)
        val routedUri = Uri.parse(next.getStringExtra(ResultActivity.EXTRA_BITMAP_URI))
        assertTrue(routedUri != imageUri)
        assertEquals("file", routedUri.scheme)
        val ownedFile = File(routedUri.path!!)
        assertNotNull(next.getStringExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE_LEASE))
        assertTrue(activity.isFinishing)
        controller.destroy()
        ownedFile.delete()
    }

    @Test
    fun `finishing result consumer deletes owned file in its app storage`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val copied = ScanImageProcessor.copySharedContentToCache(
            context,
            "image/png",
            pngBytes.size.toLong()
        ) { pngBytes.inputStream() } as ScanImageProcessor.CopyResult.Success
        val ownedFile = File(copied.uri.path!!)
        val intent = Intent(context, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_BITMAP_URI, copied.uri.toString())
            putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE, true)
            putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE_LEASE, copied.leaseToken)
        }
        val controller = Robolectric.buildActivity(ResultActivity::class.java, intent).setup()

        controller.get().onSupportNavigateUp()
        idleMain()
        controller.destroy()

        assertTrue(waitFor { !ownedFile.exists() })
    }

    @Test
    fun `send video routes to VideoScanActivity and finishes`() {
        val videoUri = createContentUri("shared.mp4", "fake video".toByteArray())
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, videoUri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()
        idleMain()
        val activity = controller.get()

        val next = waitForStartedActivity(activity)
        assertNotNull(next)
        assertEquals(VideoScanActivity::class.java.name, next!!.component?.className)
        val routedUri = Uri.parse(next.getStringExtra(VideoScanActivity.EXTRA_VIDEO_URI))
        assertTrue(routedUri != videoUri)
        assertEquals("file", routedUri.scheme)
        val ownedFile = File(routedUri.path!!)
        val leaseToken = next.getStringExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE_LEASE)
        assertNotNull(leaseToken)
        assertTrue(ownedFile.exists())
        assertTrue(activity.isFinishing)
        controller.destroy()
        ownedFile.delete()
    }

    @Test
    fun `finishing video consumer deletes owned file in its app storage`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bytes = "fake video".toByteArray()
        val copied = ScanImageProcessor.copySharedContentToCache(
            context,
            "video/mp4",
            bytes.size.toLong()
        ) { bytes.inputStream() } as ScanImageProcessor.CopyResult.Success
        val ownedFile = File(copied.uri.path!!)
        val intent = Intent(context, VideoScanActivity::class.java).apply {
            putExtra(VideoScanActivity.EXTRA_VIDEO_URI, copied.uri.toString())
            putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE, true)
            putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE_LEASE, copied.leaseToken)
        }
        val controller = Robolectric.buildActivity(VideoScanActivity::class.java, intent).setup()
        val processingJobField = VideoScanActivity::class.java.getDeclaredField("processingJob").apply {
            isAccessible = true
        }

        assertTrue(waitFor {
            (processingJobField.get(controller.get()) as? kotlinx.coroutines.Job)?.isCompleted != false
        })
        controller.get().onSupportNavigateUp()
        idleMain()
        controller.destroy()

        assertTrue(waitFor { !ownedFile.exists() })
    }

    @Test
    fun `destroying result consumer releases lease without deleting owned file`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val copied = ScanImageProcessor.copySharedContentToCache(
            context,
            "image/png",
            pngBytes.size.toLong()
        ) { pngBytes.inputStream() } as ScanImageProcessor.CopyResult.Success
        val ownedFile = File(copied.uri.path!!).apply { setLastModified(1L) }
        val intent = Intent(context, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_BITMAP_URI, copied.uri.toString())
            putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE, true)
            putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE_LEASE, copied.leaseToken)
        }

        Robolectric.buildActivity(ResultActivity::class.java, intent).setup().destroy()

        assertTrue(ownedFile.exists())
        ScanImageProcessor.cleanupExpiredSharedMedia(
            context,
            ScanImageProcessor.SHARED_MEDIA_MAX_AGE_MS + 2L
        )
        assertFalse(ownedFile.exists())
    }

    @Test
    fun `destroying video consumer releases lease without deleting owned file`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bytes = "fake video".toByteArray()
        val copied = ScanImageProcessor.copySharedContentToCache(
            context,
            "video/mp4",
            bytes.size.toLong()
        ) { bytes.inputStream() } as ScanImageProcessor.CopyResult.Success
        val ownedFile = File(copied.uri.path!!).apply { setLastModified(1L) }
        val intent = Intent(context, VideoScanActivity::class.java).apply {
            putExtra(VideoScanActivity.EXTRA_VIDEO_URI, copied.uri.toString())
            putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE, true)
            putExtra(ScanImageProcessor.EXTRA_OWNED_TEMP_FILE_LEASE, copied.leaseToken)
        }

        Robolectric.buildActivity(VideoScanActivity::class.java, intent).setup().destroy()

        assertTrue(ownedFile.exists())
        ScanImageProcessor.cleanupExpiredSharedMedia(
            context,
            ScanImageProcessor.SHARED_MEDIA_MAX_AGE_MS + 2L
        )
        assertFalse(ownedFile.exists())
    }

    @Test
    fun `send multiple images routes first image to ResultActivity`() {
        val first = createContentUri("first.png", pngBytes)
        val second = createContentUri("second.png", pngBytes)
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()
        idleMain()
        val activity = controller.get()

        val next = waitForStartedActivity(activity)
        assertNotNull(next)
        assertEquals(ResultActivity::class.java.name, next!!.component?.className)
        assertTrue(first.toString() != next.getStringExtra(ResultActivity.EXTRA_BITMAP_URI))
        assertTrue(activity.isFinishing)
        controller.destroy()
    }

    @Test
    fun `declared oversized share reports error without leaving partial file`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "images/oversized.png").apply {
            parentFile?.mkdirs()
            RandomAccessFile(this, "rw").use {
                it.setLength(ScanImageProcessor.MAX_SHARED_IMAGE_BYTES + 1)
            }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            source
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()

        assertTrue(waitFor { controller.get().isFinishing })
        assertEquals(context.getString(R.string.shared_media_too_large), ShadowToast.getTextOfLatestToast())
        controller.destroy()
        source.delete()
    }

    @Test
    fun `unreadable shared stream reports error and deletes partial file`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "images/disappeared.png").apply {
            parentFile?.mkdirs()
            writeBytes(pngBytes)
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            source
        )
        source.delete()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()

        assertTrue(waitFor { controller.get().isFinishing })
        assertEquals(context.getString(R.string.failed_to_load_image), ShadowToast.getTextOfLatestToast())
        controller.destroy()
    }

    @Test
    fun `plain launch shows normal ui without finishing`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), ScanImageActivity::class.java)
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()
        idleMain()
        val activity = controller.get()

        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertTrue(!activity.isFinishing)
        controller.destroy()
    }

    @Test
    @Config(sdk = [33], application = QRCodeApp::class)
    fun `api 33 send rejects file stream extra`() {
        val imageUri = createTempImageUri()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()
        val activity = controller.get()

        assertTrue(waitFor { activity.isFinishing })
        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertEquals(activity.getString(R.string.failed_to_load_image), ShadowToast.getTextOfLatestToast())
        controller.destroy()
    }

    @Test
    @Config(sdk = [33], application = QRCodeApp::class)
    fun `api 33 send multiple rejects file stream list`() {
        val first = createTempImageUri()
        val second = createTempImageUri()
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()
        val activity = controller.get()

        assertTrue(waitFor { activity.isFinishing })
        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertEquals(activity.getString(R.string.failed_to_load_image), ShadowToast.getTextOfLatestToast())
        controller.destroy()
    }

    @Test
    fun `android resource share is rejected instead of bypassing copy limit`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val resourceUri = Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_qr_code}")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, resourceUri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).setup()

        assertTrue(waitFor { controller.get().isFinishing })
        assertNull(Shadows.shadowOf(controller.get()).nextStartedActivity)
        controller.destroy()
    }

    @Test
    fun `finishing host before observing ready result does not orphan owned file`() {
        val imageUri = createContentUri("finishing.png", pngBytes)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).create()
        val activity = controller.get()
        val viewModel = ViewModelProvider(activity)[ScanImageShareViewModel::class.java]

        assertTrue(waitFor { viewModel.result.value is ScanImageProcessor.SharedMediaResult.Ready })
        val ready = viewModel.result.value as ScanImageProcessor.SharedMediaResult.Ready
        val ownedFile = File(ready.uri.path!!)
        assertTrue(ownedFile.exists())
        activity.finish()
        controller.destroy()

        assertFalse(ownedFile.exists())
        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `configuration changes reuse request and route only once from latest host`() {
        val imageUri = createContentUri("recreated.png", pngBytes)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).create()
        val oldHosts = mutableListOf(controller.get())
        val retainedViewModel = ViewModelProvider(controller.get())[ScanImageShareViewModel::class.java]

        assertTrue(waitFor { retainedViewModel.result.value != null })
        controller.configurationChange()
        assertSame(retainedViewModel, ViewModelProvider(controller.get())[ScanImageShareViewModel::class.java])
        oldHosts += controller.get()
        controller.configurationChange()
        assertSame(retainedViewModel, ViewModelProvider(controller.get())[ScanImageShareViewModel::class.java])
        val latestHost = controller.get()
        controller.start().resume().visible()

        val routed = waitForStartedActivity(latestHost)
        assertNotNull(routed)
        assertEquals(ResultActivity::class.java.name, routed!!.component?.className)
        oldHosts.forEach { old -> assertNull(Shadows.shadowOf(old).peekNextStartedActivity()) }
        assertNull(Shadows.shadowOf(latestHost).nextStartedActivity)
        controller.destroy()
        Uri.parse(routed.getStringExtra(ResultActivity.EXTRA_BITMAP_URI)).path?.let(::File)?.delete()
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
}

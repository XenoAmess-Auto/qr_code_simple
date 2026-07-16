package com.xenoamess.qrcodesimple

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * ScanImageActivity 系统分享入口（ACTION_SEND / ACTION_SEND_MULTIPLE）路由测试。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class ScanImageActivityShareTest {

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
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

    @Test
    fun `send image routes to ResultActivity and finishes`() {
        val imageUri = createTempImageUri()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).create()
        idleMain()
        val activity = controller.get()

        val next = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(next)
        assertEquals(ResultActivity::class.java.name, next!!.component?.className)
        assertEquals(imageUri.toString(), next.getStringExtra(ResultActivity.EXTRA_BITMAP_URI))
        assertTrue(activity.isFinishing)
        controller.destroy()
    }

    @Test
    fun `send video routes to VideoScanActivity and finishes`() {
        val videoUri = Uri.parse("content://media/external/video/media/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, videoUri)
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).create()
        idleMain()
        val activity = controller.get()

        val next = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(next)
        assertEquals(VideoScanActivity::class.java.name, next!!.component?.className)
        assertEquals(videoUri.toString(), next.getStringExtra(VideoScanActivity.EXTRA_VIDEO_URI))
        assertTrue(activity.isFinishing)
        controller.destroy()
    }

    @Test
    fun `send multiple images routes first image to ResultActivity`() {
        val first = createTempImageUri()
        val second = createTempImageUri()
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).create()
        idleMain()
        val activity = controller.get()

        val next = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(next)
        assertEquals(ResultActivity::class.java.name, next!!.component?.className)
        assertEquals(first.toString(), next.getStringExtra(ResultActivity.EXTRA_BITMAP_URI))
        assertTrue(activity.isFinishing)
        controller.destroy()
    }

    @Test
    fun `plain launch shows normal ui without finishing`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), ScanImageActivity::class.java)
        val controller = Robolectric.buildActivity(ScanImageActivity::class.java, intent).create()
        idleMain()
        val activity = controller.get()

        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertTrue(!activity.isFinishing)
        controller.destroy()
    }
}

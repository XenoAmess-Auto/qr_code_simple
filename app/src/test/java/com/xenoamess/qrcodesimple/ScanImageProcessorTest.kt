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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File

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
}

package com.xenoamess.qrcodesimple

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * VideoScanActivity 控制流场景：无视频、无效视频 URI 的错误处理。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class VideoScanControlTest {

    private fun idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun `launch without video uri finishes with toast`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VideoScanActivity::class.java)
        val scenario = ActivityScenario.launch<VideoScanActivity>(intent)
        idleMain()

        // Activity 很快 finish 并销毁，直接验证状态与提示，不再 onActivity
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        assertEquals(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.no_video_provided),
            ShadowToast.getTextOfLatestToast()
        )
        scenario.close()
    }

    @Test
    fun `launch with unreadable video uri shows error without crash`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VideoScanActivity::class.java).apply {
            putExtra(VideoScanActivity.EXTRA_VIDEO_URI, "content://nonexistent/video/1")
        }
        val scenario = ActivityScenario.launch<VideoScanActivity>(intent)

        // 等后台线程处理失败
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline && ShadowToast.getTextOfLatestToast() == null) {
            idleMain()
            Thread.sleep(50)
        }
        idleMain()

        // 不应崩溃；可能弹出错误提示
        scenario.onActivity { assertTrue(!it.isDestroyed) }
        scenario.close()
    }
}

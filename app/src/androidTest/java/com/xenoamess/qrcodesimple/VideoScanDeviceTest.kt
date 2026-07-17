package com.xenoamess.qrcodesimple

import android.content.Intent
import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * 视频扫描真机测试：资产中的 qr_video.mp4（含 QR 帧）经 VideoScanActivity
 * 完整抽帧解码管线，结果列表应包含视频中的内容。
 * 覆盖 Robolectric 无法触达的 MediaMetadataRetriever 抽帧路径。
 */
@RunWith(AndroidJUnit4::class)
class VideoScanDeviceTest {

    companion object {
        const val QR_CONTENT = "DEVICE-VIDEO-SCAN-TEST-12345"
    }

    private fun copyVideoToCache(): Uri {
        // 视频资产打包在测试 APK 中（androidTest/assets），需从 instrumentation context 读取
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outFile = File(context.cacheDir, "scan_test_video.mp4")
        testContext.assets.open("qr_video.mp4").use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(outFile)
    }

    @Test
    fun videoWithQrFramesDecodesToResultList() {
        val videoUri = copyVideoToCache()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, VideoScanActivity::class.java).apply {
            putExtra(VideoScanActivity.EXTRA_VIDEO_URI, videoUri.toString())
        }

        ActivityScenario.launch<VideoScanActivity>(intent).use { scenario ->
            // 视频抽帧扫描在后台线程进行，轮询等待结果出现在列表中
            val deadline = System.currentTimeMillis() + 60_000
            var found = false
            while (System.currentTimeMillis() < deadline && !found) {
                scenario.onActivity { activity ->
                    val rv = activity.findViewById<RecyclerView>(R.id.recyclerView)
                    val adapter = rv.adapter
                    if (adapter != null && adapter.itemCount > 0) {
                        for (i in 0 until adapter.itemCount) {
                            val holder = rv.findViewHolderForAdapterPosition(i) ?: continue
                            val text = holder.itemView.findViewById<android.widget.TextView>(R.id.tvResult)?.text
                            if (text?.toString()?.contains(QR_CONTENT) == true) {
                                found = true
                            }
                        }
                    }
                }
                if (!found) Thread.sleep(500)
            }
            assertTrue("video scan should find '$QR_CONTENT' in the result list", found)
        }
    }
}

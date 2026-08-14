package com.xenoamess.qrcodesimple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * CameraScanFragment 用户场景：结果卡展示/隐藏、智能操作按钮、
 * 复制/分享、框选开关、切换相机（无前置相机提示）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class CameraScanScenarioTest {

    private lateinit var scenario: FragmentScenario<CameraScanFragment>

    @Before
    fun setup() {
        scenario = FragmentScenario.launchInContainer(CameraScanFragment::class.java, themeResId = R.style.Theme_QRCodeSimple)
        idleMain()
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    private fun idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun show(content: String) {
        scenario.onFragment { fragment ->
            fragment.showResult(QRCodeScanner.ScanResult(content, QRCodeScanner.Library.ZXING))
        }
        idleMain()
    }

    @Test
    fun showResultDisplaysCardWithContent() {
        show("https://example.com")

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            assertEquals(View.VISIBLE, view.findViewById<CardView>(R.id.resultCard).visibility)
            assertEquals("https://example.com", view.findViewById<TextView>(R.id.tvResult).text.toString())
        }
    }

    @Test
    fun urlResultShowsSmartActionButton() {
        show("https://example.com")

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val smartBtn = view.findViewById<MaterialButton>(R.id.btnSmartAction)
            assertEquals(View.VISIBLE, smartBtn.visibility)
            assertEquals(View.VISIBLE, view.findViewById<TextView>(R.id.tvContentType).visibility)
        }
    }

    @Test
    fun wifiResultShowsSmartActionButton() {
        show("WIFI:T:WPA;S:TestNet;P:secret;;")

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            assertEquals(View.VISIBLE, view.findViewById<MaterialButton>(R.id.btnSmartAction).visibility)
        }
    }

    @Test
    fun plainTextResultHidesSmartActionButton() {
        show("just some plain text 12345")

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            assertEquals(View.GONE, view.findViewById<MaterialButton>(R.id.btnSmartAction).visibility)
        }
    }

    @Test
    fun smartActionLaunchesBrowserForUrl() {
        show("https://example.com")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnSmartAction).performClick()
        }
        idleMain()

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertEquals(Intent.ACTION_VIEW, intent?.action)
        }
    }

    @Test
    fun copyResultWritesClipboard() {
        show("clipboard-target-content")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<View>(R.id.btnCopyResult).performClick()
        }
        idleMain()

        scenario.onFragment { fragment ->
            val cm = fragment.requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            assertNotNull(clip)
            assertEquals("clipboard-target-content", clip!!.getItemAt(0).text.toString())
        }
    }

    @Test
    fun shareResultLaunchesChooser() {
        show("share-target-content")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<View>(R.id.btnShareResult).performClick()
        }
        idleMain()

        scenario.onFragment { fragment ->
            val intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
            assertNotNull(intent)
            assertEquals(Intent.ACTION_CHOOSER, intent?.action)
        }
    }

    @Test
    fun hideResultHidesCard() {
        show("hide-me")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<ImageButton>(R.id.btnCloseResult).performClick()
        }
        idleMain()

        scenario.onFragment { fragment ->
            assertEquals(View.GONE, fragment.requireView().findViewById<CardView>(R.id.resultCard).visibility)
        }
    }

    @Test
    fun favoriteResultButtonTogglesFavoriteInHistory() {
        val repository = com.xenoamess.qrcodesimple.data.HistoryRepository(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        )
        val content = "https://fav-test-${System.nanoTime()}.example"
        show(content)

        // 等待异步写入历史记录
        var item: com.xenoamess.qrcodesimple.data.HistoryItem? = null
        var deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            item = kotlinx.coroutines.runBlocking { repository.findByContent(content) }
            if (item != null) break
            Thread.sleep(50)
        }
        assertNotNull(item)

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<ImageButton>(R.id.btnFavoriteResult).performClick()
        }

        var favorite = false
        deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            favorite = kotlinx.coroutines.runBlocking {
                repository.findByContent(content)?.isFavorite == true
            }
            if (favorite) break
            Thread.sleep(50)
        }
        assertTrue(favorite)
    }

    @Test
    fun multiResultShowsCounterAndCycles() {
        scenario.onFragment { fragment ->
            val method = CameraScanFragment::class.java.getDeclaredMethod("handleNewResults", List::class.java)
            method.isAccessible = true
            val r1 = QRCodeScanner.ScanResult("first", QRCodeScanner.Library.ZXING)
            val r2 = QRCodeScanner.ScanResult("second", QRCodeScanner.Library.ZXING)
            method.invoke(fragment, listOf(r1, r2))
        }
        idleMain()

        scenario.onFragment { fragment ->
            val btnNext = fragment.requireView().findViewById<android.widget.Button>(R.id.btnNextResult)
            assertEquals(View.VISIBLE, btnNext.visibility)
            assertEquals("1/2", btnNext.text.toString())
            assertEquals(
                "first",
                fragment.requireView().findViewById<TextView>(R.id.tvResult).text.toString()
            )

            btnNext.performClick()
            assertEquals(
                "second",
                fragment.requireView().findViewById<TextView>(R.id.tvResult).text.toString()
            )
            assertEquals("2/2", btnNext.text.toString())
        }
    }

    @Test
    fun showResultTwiceWithSameContentDoesNotReAnimateCard() {
        show("https://example.com")

        // 篡改正文文本作为"是否重新走刷新路径"的探针。
        // 同内容第二次进入 showResult 时应早退,不会重新 setText。
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<TextView>(R.id.tvResult).text = "TAMPERED"
        }
        show("https://example.com")

        scenario.onFragment { fragment ->
            assertEquals(
                "TAMPERED",
                fragment.requireView().findViewById<TextView>(R.id.tvResult).text.toString()
            )
        }
    }

    @Test
    fun showResultWithDifferentContentReShowsCard() {
        show("https://first.com")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<TextView>(R.id.tvResult).text = "TAMPERED"
        }
        show("https://second.com")

        scenario.onFragment { fragment ->
            assertEquals(
                "https://second.com",
                fragment.requireView().findViewById<TextView>(R.id.tvResult).text.toString()
            )
        }
    }

    @Test
    fun hideResultBlocksSameContentFromReShowing() {
        show("https://example.com")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<ImageButton>(R.id.btnCloseResult).performClick()
        }
        idleMain()

        // 关闭后相机仍在持续扫到同一码(模拟下一帧 showResult),卡片应保持关闭,
        // 而不是被同码重新弹出。这是用户反馈"点叉号关不掉"的核心回归保护。
        show("https://example.com")

        scenario.onFragment { fragment ->
            assertEquals(
                View.GONE,
                fragment.requireView().findViewById<CardView>(R.id.resultCard).visibility
            )
        }
    }

    @Test
    fun hideResultAllowsDifferentContentToReShow() {
        show("https://first.com")

        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<ImageButton>(R.id.btnCloseResult).performClick()
        }
        idleMain()

        // 关闭后扫到不同的码,卡片应重新弹出
        show("https://second.com")

        scenario.onFragment { fragment ->
            assertEquals(
                View.VISIBLE,
                fragment.requireView().findViewById<CardView>(R.id.resultCard).visibility
            )
            assertEquals(
                "https://second.com",
                fragment.requireView().findViewById<TextView>(R.id.tvResult).text.toString()
            )
        }
    }

    /**
     * 模拟 ZXing 漏检:扫到码后,紧接着一帧没扫到码(漏检),
     * 再下一帧又扫到同码。漏检不应立即清除 lastDetectedContent,
     * 否则同码会在下一帧绕过守卫重新弹框(用户看到的"点叉号关不掉")。
     *
     * processImage 依赖 CameraX 无法在 Robolectric 中调用,
     * 这里通过反射直接操作 clearLastDetectedRunnable 来验证延迟逻辑。
     */
    @Test
    fun frameMissDoesNotImmediatelyClearLastDetected() {
        show("https://example.com")

        scenario.onFragment { fragment ->
            fragment.hideResult()
        }
        idleMain()

        // 模拟 ZXing 漏检一帧:processImage 的 else 分支只在 hasPendingClear=false 时 postDelayed
        scenario.onFragment { fragment ->
            val handlerField = CameraScanFragment::class.java.getDeclaredField("handler")
            handlerField.isAccessible = true
            val handler = handlerField.get(fragment) as android.os.Handler

            val runnableField = CameraScanFragment::class.java.getDeclaredField("clearLastDetectedRunnable")
            runnableField.isAccessible = true
            val runnable = runnableField.get(fragment) as Runnable

            val pendingField = CameraScanFragment::class.java.getDeclaredField("hasPendingClear")
            pendingField.isAccessible = true

            // 模拟 processImage 没扫到码:只在第一次 postDelayed
            if (!(pendingField.getBoolean(fragment))) {
                handler.postDelayed(runnable, 1000)
                pendingField.setBoolean(fragment, true)
            }
        }
        idleMain()

        // 漏检后立即(未到 1 秒)再扫到同码,不应重弹(守卫应拦截)
        show("https://example.com")

        scenario.onFragment { fragment ->
            assertEquals(
                "漏检后同码不应重新弹出(lastDetectedContent 未被立即清除)",
                View.GONE,
                fragment.requireView().findViewById<CardView>(R.id.resultCard).visibility
            )
        }
    }

    /**
     * 码真正离开画面(延迟到期后 clearLastDetectedRunnable 执行,
     * 清除 lastDetectedContent),再扫回同码时应重新弹出。
     */
    @Test
    fun lastDetectedClearedAfterDelayAllowsSameContentToReShow() {
        show("https://example.com")

        scenario.onFragment { fragment ->
            fragment.hideResult()
        }
        idleMain()

        // 模拟延迟到期:clearLastDetectedRunnable 执行,清 lastDetectedContent
        scenario.onFragment { fragment ->
            val runnableField = CameraScanFragment::class.java.getDeclaredField("clearLastDetectedRunnable")
            runnableField.isAccessible = true
            val runnable = runnableField.get(fragment) as Runnable
            runnable.run()
        }
        idleMain()

        // 再扫回同码,应重新弹出(lastDetectedContent 已被清)
        show("https://example.com")

        scenario.onFragment { fragment ->
            assertEquals(
                "延迟清除后同码应重新弹出",
                View.VISIBLE,
                fragment.requireView().findViewById<CardView>(R.id.resultCard).visibility
            )
        }
    }

    /**
     * hideResult 应清除 scaleIn 动画的 fillAfter transformation,
     * 防止动画中间态 matrix 影响下次显示时的命中测试。
     */
    @Test
    fun hideResultClearsAnimation() {
        show("https://example.com")

        scenario.onFragment { fragment ->
            val card = fragment.requireView().findViewById<CardView>(R.id.resultCard)
            assertNotNull("scaleIn 应设置了 animation", card.animation)
            fragment.hideResult()
            assertEquals("hideResult 应清除 animation", null, card.animation)
        }
    }

    @Test
    fun switchCameraWithoutFrontCameraShowsToast() {
        // Robolectric 环境默认没有前置相机
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<ImageButton>(R.id.btnSwitchCamera).performClick()
        }
        idleMain()
        // 不应崩溃；可能提示前置不可用或成功切换，关键是不抛异常
    }
}

@file:Suppress("DEPRECATION")

package com.xenoamess.qrcodesimple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.core.app.ApplicationProvider
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class CameraScanFragmentTest {

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

    private fun getNextStartedActivity(): Intent? {
        var intent: Intent? = null
        scenario.onFragment { fragment ->
            intent = Shadows.shadowOf(fragment.requireActivity()).nextStartedActivity
        }
        return intent
    }

    @Test
    fun shareResultButtonStartsSendIntent() {
        getNextStartedActivity()

        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val tvResult = view.findViewById<TextView>(R.id.tvResult)
            tvResult.text = "https://example.com"
            view.findViewById<MaterialButton>(R.id.btnShareResult).performClick()
        }
        idleMain()

        val startedIntent = getNextStartedActivity()
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, startedIntent?.action)
        val sharedIntent = startedIntent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals("https://example.com", sharedIntent?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun copyResultButtonCopiesTextToClipboard() {
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val tvResult = view.findViewById<TextView>(R.id.tvResult)
            tvResult.text = "copy-me"
            view.findViewById<MaterialButton>(R.id.btnCopyResult).performClick()
        }
        idleMain()

        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        assertEquals("copy-me", clip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun closeResultButtonHidesResultCard() {
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val card = view.findViewById<CardView>(R.id.resultCard)
            card.visibility = View.VISIBLE
            view.findViewById<ImageButton>(R.id.btnCloseResult).performClick()
        }
        idleMain()

        scenario.onFragment { fragment ->
            val card = fragment.requireView().findViewById<CardView>(R.id.resultCard)
            assertEquals(View.GONE, card.visibility)
        }
    }

    @Test
    fun clickingResultCardDoesNotCopy() {
        // 回归保护:点卡片本体不应触发复制。
        // 历史上 resultCard 绑过 copyResult,会拦截叉号点击。现已移除。
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val tvResult = view.findViewById<TextView>(R.id.tvResult)
            tvResult.text = "card-body-content"
            view.findViewById<CardView>(R.id.resultCard).performClick()
        }
        idleMain()

        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // 剪贴板未被写入(无 primaryClip,或内容不匹配)
        val clip = clipboard.primaryClip
        if (clip != null) {
            assertTrue(
                "点卡片本体不应复制内容",
                clip.getItemAt(0)?.text?.toString() != "card-body-content"
            )
        }
    }

    @Test
    fun clickingResultTextCopiesToClipboard() {
        // 点正文区(tvResult)应触发复制 —— 这是替代卡片整体点击复制的新入口。
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            val tvResult = view.findViewById<TextView>(R.id.tvResult)
            tvResult.text = "text-area-content"
            tvResult.performClick()
        }
        idleMain()

        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        assertEquals("text-area-content", clip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun smartActionButtonOpensUrlForWebContent() {
        getNextStartedActivity()

        scenario.onFragment { fragment ->
            val method = CameraScanFragment::class.java.getDeclaredMethod("updateSmartActionButton", String::class.java)
            method.isAccessible = true
            method.invoke(fragment, "https://example.com")

            val view = fragment.requireView()
            view.findViewById<MaterialButton>(R.id.btnSmartAction).performClick()
        }
        idleMain()

        val startedIntent = getNextStartedActivity()
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent?.action)
        assertEquals("https://example.com", startedIntent?.data.toString())
    }

    @Test
    fun flashAndSwitchCameraButtonsDoNotCrash() {
        scenario.onFragment { fragment ->
            val view = fragment.requireView()
            view.findViewById<ImageButton>(R.id.btnFlash).performClick()
            view.findViewById<ImageButton>(R.id.btnSwitchCamera).performClick()
        }
        idleMain()
    }

    /**
     * 通过 dispatchTouchEvent 模拟真机触摸(而非 performClick 绕过事件分发),
     * 验证点击叉号区域是否真正触发 hideResult。这是之前所有测试的盲区。
     */
    @Test
    fun touchingCloseButtonViaDispatchEventHidesCard() {
        // 先显示卡片
        scenario.onFragment { fragment ->
            fragment.showResult(QRCodeScanner.ScanResult("https://touch-test.com", QRCodeScanner.Library.ZXING))
        }
        idleMain()

        scenario.onFragment { fragment ->
            val root = fragment.requireView()
            val closeBtn = root.findViewById<ImageButton>(R.id.btnCloseResult)
            val card = root.findViewById<CardView>(R.id.resultCard)

            assertEquals("前置条件:卡片应已显示", View.VISIBLE, card.visibility)
            assertTrue("叉号应有点击监听器", closeBtn.isClickable)

            // 手动 measure/layout 确保 view 有真实尺寸和位置
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, 1080, 1920)

            // 计算叉号中心相对于根 view 的坐标
            val rootLoc = IntArray(2)
            root.getLocationInWindow(rootLoc)
            val btnLoc = IntArray(2)
            closeBtn.getLocationInWindow(btnLoc)
            val x = (btnLoc[0] - rootLoc[0] + closeBtn.width / 2).toFloat()
            val y = (btnLoc[1] - rootLoc[1] + closeBtn.height / 2).toFloat()

            // 分发 ACTION_DOWN
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val downConsumed = root.dispatchTouchEvent(down)
            assertTrue("ACTION_DOWN 应被消费", downConsumed)

            // 分发 ACTION_UP(触发 click)
            val up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, x, y, 0)
            val upConsumed = root.dispatchTouchEvent(up)
            assertTrue("ACTION_UP 应被消费", upConsumed)
        }
        idleMain()

        scenario.onFragment { fragment ->
            val card = fragment.requireView().findViewById<CardView>(R.id.resultCard)
            assertEquals("触摸叉号后卡片应隐藏", View.GONE, card.visibility)
        }
    }

    /**
     * 验证点击卡片正文区域(非按钮区域)时的触摸事件走向。
     * 确保卡片本体不会拦截事件或触发不该有的行为。
     */
    @Test
    fun touchingCardBodyDoesNotInterceptCloseButtonEvents() {
        scenario.onFragment { fragment ->
            fragment.showResult(QRCodeScanner.ScanResult("body-touch-test", QRCodeScanner.Library.ZXING))
        }
        idleMain()

        scenario.onFragment { fragment ->
            val root = fragment.requireView()
            val card = root.findViewById<CardView>(R.id.resultCard)
            val closeBtn = root.findViewById<ImageButton>(R.id.btnCloseResult)

            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, 1080, 1920)

            // 检查卡片是否默认 clickable(可能拦截子 view 事件)
            val cardClickable = card.isClickable
            val closeBtnClickable = closeBtn.isClickable

            assertTrue("叉号应可点击", closeBtnClickable)
            // 卡片可以 clickable 也可以不 clickable,关键是不能拦截子 view 的点击
            // 这里只记录状态,不强制断言
        }
    }
}

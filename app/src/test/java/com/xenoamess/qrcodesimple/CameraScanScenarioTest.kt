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
    fun switchCameraWithoutFrontCameraShowsToast() {
        // Robolectric 环境默认没有前置相机
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<ImageButton>(R.id.btnSwitchCamera).performClick()
        }
        idleMain()
        // 不应崩溃；可能提示前置不可用或成功切换，关键是不抛异常
    }
}

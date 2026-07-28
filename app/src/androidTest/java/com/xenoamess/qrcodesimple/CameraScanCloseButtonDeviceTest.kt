package com.xenoamess.qrcodesimple

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.cardview.widget.CardView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟器真机测试:验证 CameraScanFragment 结果卡片叉号在 ViewPager2 环境下
 * 通过真实触摸事件(Espresso click 走 dispatchTouchEvent 链路)能否正常关闭卡片。
 *
 * 关键场景:模拟 processImage 后台线程持续扫描的竞态 ——
 * 用户点叉号后,后台线程仍在每 300ms 调用 showResult(同码),
 * 卡片不应被重新弹出。
 */
@RunWith(AndroidJUnit4::class)
class CameraScanCloseButtonDeviceTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO
    )

    private fun getFragment(scenario: ActivityScenario<MainActivity>): CameraScanFragment? {
        var result: CameraScanFragment? = null
        scenario.onActivity { activity ->
            result = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
        }
        return result
    }

    private fun showResultOnMain(fragment: CameraScanFragment?, content: String) {
        Handler(Looper.getMainLooper()).post {
            fragment?.showResult(QRCodeScanner.ScanResult(content, QRCodeScanner.Library.ZXING))
        }
    }

    private fun assertCardVisibility(scenario: ActivityScenario<MainActivity>, expected: Int, message: String) {
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
            val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
            assertEquals(message, expected, card?.visibility)
        }
    }

    @Test
    fun closeResultButtonHidesCardInViewPager2() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(2000)
            val fragment = getFragment(scenario)
            assertNotNull("CameraScanFragment 应存在于 ViewPager2 position 0", fragment)

            showResultOnMain(fragment, "https://close-button-device-test.com")
            Thread.sleep(1000)
            assertCardVisibility(scenario, View.VISIBLE, "前置条件:卡片应已显示")

            onView(withId(R.id.btnCloseResult)).perform(click())
            Thread.sleep(500)
            assertCardVisibility(scenario, View.GONE, "点击叉号后卡片应隐藏")
        }
    }

    /**
     * 用户点叉号后,后台线程扫到不同码 → 卡片应重弹。
     */
    @Test
    fun cardReShowsWhenBackgroundThreadScansDifferentCode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(2000)
            val fragment = getFragment(scenario)
            assertNotNull(fragment)

            // 显示卡片 A
            showResultOnMain(fragment, "https://code-a.com")
            Thread.sleep(500)
            assertCardVisibility(scenario, View.VISIBLE, "前置条件:卡片A应已显示")

            // 点叉号
            onView(withId(R.id.btnCloseResult)).perform(click())
            Thread.sleep(500)
            assertCardVisibility(scenario, View.GONE, "点击叉号后卡片应隐藏")

            // 后台扫到不同码 B
            showResultOnMain(fragment, "https://code-b.com")
            Thread.sleep(500)
            assertCardVisibility(scenario, View.VISIBLE, "不同码应重弹卡片")
        }
    }
}

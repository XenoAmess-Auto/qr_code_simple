package com.xenoamess.qrcodesimple

import android.view.View
import androidx.cardview.widget.CardView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
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
 * 这是 Robolectric 单元测试无法覆盖的场景:真实 View hierarchy、真实事件分发、
 * 真实 ViewPager2 嵌套。
 */
@RunWith(AndroidJUnit4::class)
class CameraScanCloseButtonDeviceTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO
    )

    @Test
    fun closeResultButtonHidesCardInViewPager2() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // 等待 ViewPager2 初始化,默认在 tab 0 (CameraScanFragment)
            Thread.sleep(2000)

            // 通过反射调用 showResult 显示结果卡片
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                assertNotNull("CameraScanFragment 应存在于 ViewPager2 position 0", fragment)
                fragment?.showResult(
                    QRCodeScanner.ScanResult("https://close-button-device-test.com", QRCodeScanner.Library.ZXING)
                )
            }
            Thread.sleep(1000)

            // 验证卡片已显示
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("前置条件:卡片应已显示", View.VISIBLE, card?.visibility)
            }

            // 用 Espresso click() 走真实事件分发链路点击叉号
            onView(withId(R.id.btnCloseResult)).perform(click())
            Thread.sleep(500)

            // 验证卡片已隐藏
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("点击叉号后卡片应隐藏", View.GONE, card?.visibility)
            }
        }
    }

    @Test
    fun closeResultButtonStaysHiddenAfterClick() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // 显示结果卡片
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                fragment?.showResult(
                    QRCodeScanner.ScanResult("https://stays-hidden-test.com", QRCodeScanner.Library.ZXING)
                )
            }
            Thread.sleep(1000)

            // 点击叉号
            onView(withId(R.id.btnCloseResult)).perform(click())
            Thread.sleep(500)

            // 验证卡片隐藏
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("点击叉号后卡片应隐藏", View.GONE, card?.visibility)
            }

            // 再等 2 秒,确认卡片没有被重新弹出
            Thread.sleep(2000)
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("2 秒后卡片仍应隐藏(未被重新弹出)", View.GONE, card?.visibility)
            }
        }
    }
}

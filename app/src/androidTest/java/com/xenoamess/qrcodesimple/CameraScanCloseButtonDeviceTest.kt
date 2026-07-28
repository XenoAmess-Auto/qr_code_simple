package com.xenoamess.qrcodesimple

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

    @Test
    fun closeResultButtonHidesCardInViewPager2() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                assertNotNull("CameraScanFragment 应存在于 ViewPager2 position 0", fragment)
                fragment?.showResult(
                    QRCodeScanner.ScanResult("https://close-button-device-test.com", QRCodeScanner.Library.ZXING)
                )
            }
            Thread.sleep(1000)

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("前置条件:卡片应已显示", View.VISIBLE, card?.visibility)
            }

            onView(withId(R.id.btnCloseResult)).perform(click())
            Thread.sleep(500)

            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("点击叉号后卡片应隐藏", View.GONE, card?.visibility)
            }
        }
    }

    /**
     * 模拟真机竞态:用户点叉号后,后台线程持续每 300ms 调用 showResult(同码)。
     * 之前的修复在这个场景下会失败(漏检清 last → 同码重弹)。
     * userDismissed 修复后应保持隐藏。
     */
    @Test
    fun cardStaysHiddenWhenBackgroundThreadContinuouslyScansSameCode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            val content = "https://race-test-same-code.com"
            val scanResult = QRCodeScanner.ScanResult(content, QRCodeScanner.Library.ZXING)

            // 显示卡片
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                fragment?.showResult(scanResult)
            }
            Thread.sleep(500)

            // 启动后台线程模拟 processImage 持续扫描同码
            val stopFlag = java.util.concurrent.atomic.AtomicBoolean(false)
            val bgThread = Thread {
                while (!stopFlag.get()) {
                    scenario.onActivity { activity ->
                        val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                        fragment?.showResult(scanResult)
                    }
                    Thread.sleep(300)
                }
            }
            bgThread.start()

            // 等卡片显示
            Thread.sleep(500)

            // 点叉号
            onView(withId(R.id.btnCloseResult)).perform(click())
            Thread.sleep(500)

            // 验证卡片隐藏
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("点击叉号后卡片应隐藏", View.GONE, card?.visibility)
            }

            // 等 3 秒(覆盖 1 秒延迟 + 余量),后台线程持续扫同码
            Thread.sleep(3000)

            // 验证卡片仍隐藏(这是之前修复失败的竞态场景)
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("后台持续扫同码时,卡片应保持隐藏(userDismissed 守卫)", View.GONE, card?.visibility)
            }

            stopFlag.set(true)
            bgThread.join(5000)
        }
    }

    /**
     * 用户点叉号后,后台线程扫到不同码 → 卡片应重弹。
     */
    @Test
    fun cardReShowsWhenBackgroundThreadScansDifferentCode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(2000)

            // 显示卡片 A
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                fragment?.showResult(QRCodeScanner.ScanResult("https://code-a.com", QRCodeScanner.Library.ZXING))
            }
            Thread.sleep(500)

            // 点叉号
            onView(withId(R.id.btnCloseResult)).perform(click())
            Thread.sleep(500)

            // 验证卡片隐藏
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("点击叉号后卡片应隐藏", View.GONE, card?.visibility)
            }

            // 后台扫到不同码 B
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                fragment?.showResult(QRCodeScanner.ScanResult("https://code-b.com", QRCodeScanner.Library.ZXING))
            }
            Thread.sleep(500)

            // 验证卡片重弹
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = fragment?.requireView()?.findViewById<CardView>(R.id.resultCard)
                assertEquals("不同码应重弹卡片", View.VISIBLE, card?.visibility)
            }
        }
    }
}

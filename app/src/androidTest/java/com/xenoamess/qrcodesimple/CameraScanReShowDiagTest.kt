package com.xenoamess.qrcodesimple

import android.os.Handler
import android.os.Looper
import android.util.Log
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
 * 诊断测试:验证"关闭后码离开再回来重弹"的完整流程。
 * 模拟 processImage 的 if/else 分支行为。
 */
@RunWith(AndroidJUnit4::class)
class CameraScanReShowDiagTest {

    companion object {
        private const val TAG = "ReShowDiag"
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO
    )

    @Test
    fun closeThenCodeLeavesThenCodeReturnsShouldReShow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var fragment: CameraScanFragment? = null
            val start = System.currentTimeMillis()
            while (fragment == null && System.currentTimeMillis() - start < 15_000) {
                scenario.onActivity { activity ->
                    fragment = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                }
                if (fragment == null) android.os.SystemClock.sleep(200)
            }
            assertNotNull(fragment)
            val frag = fragment!!
            val mainHandler = Handler(Looper.getMainLooper())

            // 步骤 1:扫到码 "abc" → 卡片显示
            Log.d(TAG, "STEP 1: showResult(abc)")
            mainHandler.post {
                frag.showResult(QRCodeScanner.ScanResult("https://abc.com", QRCodeScanner.Library.ZXING))
            }
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                val f = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = f?.requireView()?.findViewById<CardView>(R.id.resultCard)
                Log.d(TAG, "STEP 1 result: card visibility=${card?.visibility} (0=VISIBLE expected)")
                assertEquals("步骤1:卡片应显示", View.VISIBLE, card?.visibility)
            }

            // 步骤 2:用户点叉号 → 卡片隐藏
            Log.d(TAG, "STEP 2: click close button")
            onView(withId(R.id.btnCloseResult)).perform(click())
            Thread.sleep(500)
            scenario.onActivity { activity ->
                val f = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = f?.requireView()?.findViewById<CardView>(R.id.resultCard)
                Log.d(TAG, "STEP 2 result: card visibility=${card?.visibility} (8=GONE expected)")
                assertEquals("步骤2:卡片应隐藏", View.GONE, card?.visibility)
            }

            // 步骤 3:模拟码离开画面 → processImage else 分支
            // 模拟连续 else 分支(postDelayed 1秒),然后等待延迟到期
            Log.d(TAG, "STEP 3: simulate code leaving (else branch x3 + wait 1.2s)")
            repeat(3) {
                mainHandler.post {
                    // 模拟 processImage else 分支
                    val handlerField = CameraScanFragment::class.java.getDeclaredField("handler")
                    handlerField.isAccessible = true
                    val handler = handlerField.get(frag) as Handler
                    val runnableField = CameraScanFragment::class.java.getDeclaredField("clearLastDetectedRunnable")
                    runnableField.isAccessible = true
                    val runnable = runnableField.get(frag) as Runnable
                    handler.removeCallbacks(runnable)
                    handler.postDelayed(runnable, 1000)
                }
                Thread.sleep(300)
            }
            // 等待 1.2 秒让 clearLastDetectedRunnable 执行
            Thread.sleep(1200)

            // 检查 lastDetectedContent 是否被清
            scenario.onActivity { activity ->
                val f = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val field = CameraScanFragment::class.java.getDeclaredField("lastDetectedContent")
                field.isAccessible = true
                val last = field.get(f)
                Log.d(TAG, "STEP 3 result: lastDetectedContent=$last (null expected after clear)")
            }

            // 步骤 4:再次扫到同码 "abc" → 卡片应重弹
            Log.d(TAG, "STEP 4: showResult(abc) again - should re-show")
            mainHandler.post {
                frag.showResult(QRCodeScanner.ScanResult("https://abc.com", QRCodeScanner.Library.ZXING))
            }
            Thread.sleep(1000)
            scenario.onActivity { activity ->
                val f = activity.supportFragmentManager.findFragmentByTag("f0") as? CameraScanFragment
                val card = f?.requireView()?.findViewById<CardView>(R.id.resultCard)
                Log.d(TAG, "STEP 4 result: card visibility=${card?.visibility} (0=VISIBLE expected)")
                assertEquals("步骤4:码离开再回来应重弹", View.VISIBLE, card?.visibility)
            }
        }
    }
}

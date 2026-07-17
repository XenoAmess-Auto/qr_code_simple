package com.xenoamess.qrcodesimple

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 应用级冒烟：MainActivity 启动、主界面可见、无崩溃。
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    // 预授相机/媒体权限，避免首启权限弹窗挡住 MainActivity
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO
    )

    @Test
    fun mainActivityLaunchesWithTabs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertNotNull(activity.supportFragmentManager)
            }
        }
    }

    @Test
    fun appContextHasExpectedPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.xenoamess.qrcodesimple", context.packageName)
    }

    @Test
    fun weChatEngineInitStatusIsResolvable() {
        // WeChatQRCode 在 ARM 翻译层上可能初始化成功或失败；两种状态都必须
        // 被应用妥善处理（失败时回退 ZXing/ML Kit），这里验证应用未崩溃且状态可读。
        val initialized = QRCodeApp.isWeChatQRCodeInitialized
        if (!initialized) {
            assertNotNull(QRCodeApp.initErrorMessage)
        }
    }
}

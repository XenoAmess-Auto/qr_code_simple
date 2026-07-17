package com.xenoamess.qrcodesimple.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器。
 * 覆盖两条关键旅程：冷启动到主界面、相机扫描页首帧。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCameraScan() = baselineProfileRule.collect(
        packageName = "com.xenoamess.qrcodesimple"
    ) {
        // 冷启动
        startActivityAndWait()

        // 等待主界面 ViewPager 就绪
        device.wait(Until.hasObject(By.res("com.xenoamess.qrcodesimple:id/viewPager")), 10_000)

        // 默认首个 Tab 即相机扫描页，等待预览帧流出
        device.wait(Until.hasObject(By.res("com.xenoamess.qrcodesimple:id/previewView")), 10_000)
        Thread.sleep(1_500)
    }

    @Test
    fun startupOnly() = baselineProfileRule.collect(
        packageName = "com.xenoamess.qrcodesimple"
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.res("com.xenoamess.qrcodesimple:id/viewPager")), 10_000)
    }
}

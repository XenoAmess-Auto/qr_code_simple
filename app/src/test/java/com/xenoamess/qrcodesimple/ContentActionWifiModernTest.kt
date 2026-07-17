package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Android 10+（API 29+）WiFi 连接路径场景测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ContentActionWifiModernTest {

    private lateinit var activity: Activity
    private lateinit var handler: ContentActionHandler

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        handler = ContentActionHandler(activity)
    }

    private fun idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun confirmWifiDialog(content: String) {
        val buttons = handler.getActionButtons(content)
        buttons[0].onClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()
    }

    @Test
    fun `wep on android 10 plus shows not supported toast and opens wifi settings`() {
        confirmWifiDialog("WIFI:T:WEP;S:OldNet;P:secret;;")

        // WEP 提示不支持
        assertEquals(
            activity.getString(R.string.wifi_wep_not_supported),
            ShadowToast.getTextOfLatestToast()
        )
        // 跳转到系统 WiFi 设置
        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        assertEquals(android.provider.Settings.ACTION_WIFI_SETTINGS, intent?.action)
    }

    @Test
    fun `wpa2 on android 10 plus sends request toast`() {
        confirmWifiDialog("WIFI:T:WPA;S:ModernNet;P:secret;;")

        assertEquals(
            activity.getString(R.string.wifi_request_sent, "ModernNet"),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `open network on android 10 plus sends request toast without passphrase`() {
        confirmWifiDialog("WIFI:T:nopass;S:OpenNet;;")

        assertEquals(
            activity.getString(R.string.wifi_request_sent, "OpenNet"),
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `wpa3 uses suggestion path without crash`() {
        // WPA3 走 setWpa3Passphrase 分支；Robolectric 的 ShadowConnectivityManager 记录请求
        confirmWifiDialog("WIFI:T:WPA3;S:NewNet;P:secret;;")

        assertEquals(
            activity.getString(R.string.wifi_request_sent, "NewNet"),
            ShadowToast.getTextOfLatestToast()
        )
    }
}

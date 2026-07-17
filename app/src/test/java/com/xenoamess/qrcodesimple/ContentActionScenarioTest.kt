package com.xenoamess.qrcodesimple

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ContentActionHandler 场景测试：每类解析内容的动作按钮点击后的真实行为
 * （启动的 Intent / 弹出的确认框 / 无应用时的错误提示）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContentActionScenarioTest {

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

    private fun clickFirst(content: String): Intent? {
        val buttons = handler.getActionButtons(content)
        assertEquals(1, buttons.size)
        buttons[0].onClick()
        return Shadows.shadowOf(activity).nextStartedActivity
    }

    // ==================== Contact ====================

    @Test
    fun `contact action launches contacts insert with all fields`() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nTEL:+123456\nEMAIL:john@example.com\nORG:Acme\nEND:VCARD"
        val intent = clickFirst(vcard)

        assertNotNull(intent)
        assertEquals(Intent.ACTION_INSERT, intent?.action)
        assertEquals(ContactsContract.Contacts.CONTENT_TYPE, intent?.type)
        assertEquals("John Doe", intent?.getStringExtra(ContactsContract.Intents.Insert.NAME))
        assertEquals("+123456", intent?.getStringExtra(ContactsContract.Intents.Insert.PHONE))
        assertEquals("john@example.com", intent?.getStringExtra(ContactsContract.Intents.Insert.EMAIL))
        assertEquals("Acme", intent?.getStringExtra(ContactsContract.Intents.Insert.COMPANY))
    }

    // ==================== Calendar ====================

    @Test
    fun `calendar action launches calendar insert with event fields`() {
        val vevent = "BEGIN:VEVENT\nSUMMARY:Team Meeting\nDESCRIPTION:Weekly sync\nLOCATION:Room 42\nDTSTART:20260720T100000Z\nDTEND:20260720T110000Z\nEND:VEVENT"
        val intent = clickFirst(vevent)

        assertNotNull(intent)
        assertEquals(Intent.ACTION_INSERT, intent?.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent?.data)
        assertEquals("Team Meeting", intent?.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("Weekly sync", intent?.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertEquals("Room 42", intent?.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertTrue(intent?.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0) ?: 0 > 0)
        assertTrue(intent?.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0) ?: 0 > 0)
    }

    // ==================== Email ====================

    @Test
    fun `email action launches sendto with subject and body`() {
        val intent = clickFirst("mailto:a@b.com?subject=Hi&body=Hello there")

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SENDTO, intent?.action)
        assertEquals(Uri.parse("mailto:a@b.com"), intent?.data)
        assertEquals("Hi", intent?.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Hello there", intent?.getStringExtra(Intent.EXTRA_TEXT))
    }

    // ==================== Geo ====================

    @Test
    fun `geo action with coordinates launches geo intent`() {
        val intent = clickFirst("geo:37.7749,-122.4194")

        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertTrue(intent?.data.toString().startsWith("geo:37.7749,-122.4194"))
    }

    // ==================== SMS ====================

    @Test
    fun `sms action launches sendto with body`() {
        val intent = clickFirst("SMSTO:+1234567890:Hello there")

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SENDTO, intent?.action)
        assertTrue(intent?.data.toString().startsWith("smsto:+1234567890"))
        assertEquals("Hello there", intent?.getStringExtra("sms_body"))
    }

    // ==================== Phone ====================

    @Test
    fun `phone action shows confirmation dialog then dials`() {
        val buttons = handler.getActionButtons("tel:+1234567890")
        buttons[0].onClick()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()
        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        assertEquals(Intent.ACTION_DIAL, intent?.action)
        assertEquals(Uri.parse("tel:+1234567890"), intent?.data)
    }

    @Test
    fun `phone dialog cancel does not dial`() {
        val buttons = handler.getActionButtons("tel:+1234567890")
        buttons[0].onClick()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idleMain()

        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
    }

    // ==================== WiFi ====================

    @Test
    fun `wifi action shows confirm dialog with ssid`() {
        val buttons = handler.getActionButtons("WIFI:T:WPA;S:MyNetwork;P:secret;;")
        buttons[0].onClick()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        val message = dialog.findViewById<android.widget.TextView>(android.R.id.message)?.text
        assertTrue(message.toString().contains("MyNetwork"))
    }

    @Test
    fun `wifi legacy path on api 28 adds network configuration`() {
        val buttons = handler.getActionButtons("WIFI:T:WPA;S:LegacyNet;P:secret;;")
        buttons[0].onClick()

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMain()

        // API 28 走 connectWifiLegacy：应向 WifiManager 添加配置（shadow 从 id 0 开始记录）
        val wifiManager = activity.applicationContext
            .getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val shadowWifi = Shadows.shadowOf(wifiManager)
        val added = shadowWifi.getWifiConfiguration(0)
        assertNotNull(added)
        assertTrue(added!!.SSID?.contains("LegacyNet") == true)
    }

    @Test
    fun `wifi with empty ssid shows invalid toast without dialog`() {
        val buttons = handler.getActionButtons("WIFI:T:WPA;S:;P:secret;;")
        buttons[0].onClick()

        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.wifi_invalid),
            ShadowToast.getTextOfLatestToast()
        )
    }

    // ==================== 无结果内容 ====================

    @Test
    fun `unknown content has no action buttons`() {
        assertTrue(handler.getActionButtons("random plain text 12345").isEmpty())
    }
}

package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ContentActionHandler 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContentActionHandlerTest {

    private lateinit var activity: Activity
    private lateinit var handler: ContentActionHandler

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        handler = ContentActionHandler(activity)
    }

    @Test
    fun `getActionButtons for URL returns open url button`() {
        val buttons = handler.getActionButtons("https://example.com")
        assertEquals(1, buttons.size)
        assertEquals(R.drawable.ic_open_in_browser, buttons[0].iconResId)
    }

    @Test
    fun `getActionButtons for WiFi returns connect button`() {
        val wifi = "WIFI:T:WPA;S:MyNetwork;P:password;;"
        val buttons = handler.getActionButtons(wifi)
        assertEquals(1, buttons.size)
        assertEquals(R.drawable.ic_wifi, buttons[0].iconResId)
    }

    @Test
    fun `getActionButtons for contact returns add contact button`() {
        val vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nEND:VCARD"
        val buttons = handler.getActionButtons(vcard)
        assertEquals(1, buttons.size)
        assertEquals(R.drawable.ic_contact, buttons[0].iconResId)
    }

    @Test
    fun `getActionButtons for email returns send email button`() {
        val buttons = handler.getActionButtons("mailto:test@example.com?subject=Hello")
        assertEquals(1, buttons.size)
        assertEquals(R.drawable.ic_email, buttons[0].iconResId)
    }

    @Test
    fun `getActionButtons for phone returns call button`() {
        val buttons = handler.getActionButtons("tel:+1234567890")
        assertEquals(1, buttons.size)
        assertEquals(R.drawable.ic_phone, buttons[0].iconResId)
    }

    @Test
    fun `getActionButtons for SMS returns sms button`() {
        val buttons = handler.getActionButtons("SMSTO:+1234567890:Hello")
        assertEquals(1, buttons.size)
        assertEquals(R.drawable.ic_sms, buttons[0].iconResId)
    }

    @Test
    fun `getActionButtons for geo returns map button`() {
        val buttons = handler.getActionButtons("geo:37.7749,-122.4194")
        assertEquals(1, buttons.size)
        assertEquals(R.drawable.ic_location, buttons[0].iconResId)
    }

    @Test
    fun `getActionButtons for text returns empty list`() {
        val buttons = handler.getActionButtons("plain text")
        assertTrue(buttons.isEmpty())
    }

    @Test
    fun `getContentTypeLabel maps types correctly`() {
        assertTrue(handler.getContentTypeLabel("https://example.com").isNotEmpty())
        assertTrue(handler.getContentTypeLabel("WIFI:T:WPA;S:test;;").isNotEmpty())
        assertTrue(handler.getContentTypeLabel("plain").isNotEmpty())
    }

    @Test
    fun `getContentTypeIcon returns drawable resource for all types`() {
        assertTrue(handler.getContentTypeIcon("https://example.com") > 0)
        assertTrue(handler.getContentTypeIcon("WIFI:T:WPA;S:test;;") > 0)
        assertTrue(handler.getContentTypeIcon("plain") > 0)
    }

    @Test
    fun `openUrl falls back to toast when no browser installed`() {
        // Click the button; since no browser is installed, it should not crash
        val buttons = handler.getActionButtons("https://example.com")
        assertEquals(1, buttons.size)
        buttons[0].onClick()
    }

    @Test
    fun `action button click launches intent for url`() {
        val shadowApp = Shadows.shadowOf(activity.application)
        shadowApp.grantPermissions("android.permission.INTERNET")

        val buttons = handler.getActionButtons("https://example.com")
        buttons[0].onClick()

        val intent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals(Uri.parse("https://example.com"), intent?.data)
    }
}

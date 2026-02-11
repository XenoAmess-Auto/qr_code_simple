package com.xenoamess.qrcodesimple

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ContentParser 单元测试
 */
class ContentParserTest {

    @Test
    fun `parse WiFi QR code`() {
        val wifiContent = "WIFI:T:WPA;S:TestNetwork;P:TestPassword123;;"
        val result = ContentParser.parse(wifiContent)

        assertTrue(result is ContentParser.ParsedContent.Wifi)
        val wifi = result as ContentParser.ParsedContent.Wifi
        assertEquals("TestNetwork", wifi.ssid)
        assertEquals("TestPassword123", wifi.password)
        assertEquals("WPA", wifi.encryption)
    }

    @Test
    fun `parse WiFi QR code without encryption type`() {
        val wifiContent = "WIFI:S:OpenNetwork;P:;;"
        val result = ContentParser.parse(wifiContent)

        assertTrue(result is ContentParser.ParsedContent.Wifi)
        val wifi = result as ContentParser.ParsedContent.Wifi
        assertEquals("OpenNetwork", wifi.ssid)
        assertEquals("", wifi.password)
    }

    @Test
    fun `parse URL`() {
        val url = "https://www.example.com/path?query=value"
        val result = ContentParser.parse(url)

        assertTrue(result is ContentParser.ParsedContent.Url)
        assertEquals(url, (result as ContentParser.ParsedContent.Url).url)
    }

    @Test
    fun `parse URL with www prefix`() {
        val url = "www.example.com"
        val result = ContentParser.parse(url)

        assertTrue(result is ContentParser.ParsedContent.Url)
        assertEquals("https://www.example.com", (result as ContentParser.ParsedContent.Url).url)
    }

    @Test
    fun `parse phone number`() {
        val phone = "+8613800138000"
        val result = ContentParser.parse(phone)

        assertTrue(result is ContentParser.ParsedContent.Phone)
        assertEquals("+8613800138000", (result as ContentParser.ParsedContent.Phone).number)
    }

    @Test
    fun `parse tel protocol`() {
        val phone = "tel:+8613800138000"
        val result = ContentParser.parse(phone)

        assertTrue(result is ContentParser.ParsedContent.Phone)
        assertEquals("+8613800138000", (result as ContentParser.ParsedContent.Phone).number)
    }

    @Test
    fun `parse email with mailto`() {
        val email = "mailto:test@example.com?subject=Hello&body=World"
        val result = ContentParser.parse(email)

        assertTrue(result is ContentParser.ParsedContent.Email)
        val emailContent = result as ContentParser.ParsedContent.Email
        assertEquals("test@example.com", emailContent.address)
        assertEquals("Hello", emailContent.subject)
        assertEquals("World", emailContent.body)
    }

    @Test
    fun `parse MATMSG email`() {
        val email = "MATMSG:TO:test@example.com;SUB:Test Subject;BODY:Test Body;;"
        val result = ContentParser.parse(email)

        assertTrue(result is ContentParser.ParsedContent.Email)
        val emailContent = result as ContentParser.ParsedContent.Email
        assertEquals("test@example.com", emailContent.address)
        assertEquals("Test Subject", emailContent.subject)
        assertEquals("Test Body", emailContent.body)
    }

    @Test
    fun `parse geo location`() {
        val geo = "geo:37.7749,-122.4194"
        val result = ContentParser.parse(geo)

        assertTrue(result is ContentParser.ParsedContent.GeoLocation)
        val location = result as ContentParser.ParsedContent.GeoLocation
        assertEquals(37.7749, location.latitude, 0.0001)
        assertEquals(-122.4194, location.longitude, 0.0001)
    }

    @Test
    fun `parse geo location with query`() {
        val geo = "geo:0,0?q=San+Francisco"
        val result = ContentParser.parse(geo)

        assertTrue(result is ContentParser.ParsedContent.GeoLocation)
        val location = result as ContentParser.ParsedContent.GeoLocation
        assertEquals("San+Francisco", location.query)
    }

    @Test
    fun `parse SMS`() {
        val sms = "sms:+8613800138000?body=Hello%20World"
        val result = ContentParser.parse(sms)

        assertTrue(result is ContentParser.ParsedContent.SMS)
        val smsContent = result as ContentParser.ParsedContent.SMS
        assertEquals("+8613800138000", smsContent.number)
        assertEquals("Hello%20World", smsContent.message)
    }

    @Test
    fun `parse SMSTO`() {
        val sms = "SMSTO:+8613800138000:Hello World"
        val result = ContentParser.parse(sms)

        assertTrue(result is ContentParser.ParsedContent.SMS)
        assertEquals("+8613800138000", (result as ContentParser.ParsedContent.SMS).number)
    }

    @Test
    fun `parse vCard contact`() {
        val vcard = """BEGIN:VCARD
VERSION:3.0
FN:John Doe
TEL:+8613800138000
EMAIL:john@example.com
END:VCARD""".trimIndent()

        val result = ContentParser.parse(vcard)

        assertTrue(result is ContentParser.ParsedContent.Contact)
        val contact = result as ContentParser.ParsedContent.Contact
        assertEquals("John Doe", contact.name)
        assertEquals("+8613800138000", contact.phone)
        assertEquals("john@example.com", contact.email)
    }

    @Test
    fun `parse MECARD contact`() {
        val mecard = "MECARD:N:John Doe;TEL:+8613800138000;EMAIL:john@example.com;;"
        val result = ContentParser.parse(mecard)

        assertTrue(result is ContentParser.ParsedContent.Contact)
        val contact = result as ContentParser.ParsedContent.Contact
        assertEquals("John Doe", contact.name)
        assertEquals("+8613800138000", contact.phone)
    }

    @Test
    fun `parse calendar event`() {
        val event = """BEGIN:VEVENT
SUMMARY:Meeting
DTSTART:20240211T100000Z
DTEND:20240211T110000Z
LOCATION:Conference Room
END:VEVENT""".trimIndent()

        val result = ContentParser.parse(event)

        assertTrue(result is ContentParser.ParsedContent.CalendarEvent)
        val calendarEvent = result as ContentParser.ParsedContent.CalendarEvent
        assertEquals("Meeting", calendarEvent.title)
        assertEquals("Conference Room", calendarEvent.location)
    }

    @Test
    fun `detect WiFi type`() {
        assertEquals(
            ContentParser.ContentType.WIFI,
            ContentParser.detectType("WIFI:S:test;;")
        )
    }

    @Test
    fun `detect URL type`() {
        assertEquals(
            ContentParser.ContentType.URL,
            ContentParser.detectType("https://example.com")
        )
    }

    @Test
    fun `detect text type for plain text`() {
        assertEquals(
            ContentParser.ContentType.TEXT,
            ContentParser.detectType("Hello World")
        )
    }

    @Test
    fun `parse plain text`() {
        val text = "Just some plain text"
        val result = ContentParser.parse(text)

        assertTrue(result is ContentParser.ParsedContent.Text)
        assertEquals(text, (result as ContentParser.ParsedContent.Text).text)
    }

    @Test
    fun `parse empty WiFi returns empty values`() {
        val wifiContent = "WIFI:;;"
        val result = ContentParser.parse(wifiContent)

        assertTrue(result is ContentParser.ParsedContent.Wifi)
        val wifi = result as ContentParser.ParsedContent.Wifi
        assertEquals("", wifi.ssid)
    }
}

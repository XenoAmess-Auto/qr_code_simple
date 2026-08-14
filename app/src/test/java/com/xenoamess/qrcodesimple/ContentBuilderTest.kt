package com.xenoamess.qrcodesimple

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Calendar

/**
 * ContentBuilder ↔ ContentParser 回环：构造的结构化载荷必须被本应用扫描端
 * 正确解析。注意解析侧对部分转义/编码保持原样（既有行为锁定），回环用例
 * 使用解析不变的值，转义正确性由 builder 侧断言单独覆盖。
 */
class ContentBuilderTest {

    @Test
    fun `wifi payload roundtrips`() {
        val payload = ContentBuilder.wifi("MyHomeWifi", "secret123", "WPA")
        val parsed = ContentParser.parse(payload)
        assertTrue(parsed is ContentParser.ParsedContent.Wifi)
        parsed as ContentParser.ParsedContent.Wifi
        assertEquals("MyHomeWifi", parsed.ssid)
        assertEquals("secret123", parsed.password)
        assertEquals("WPA", parsed.encryption)
    }

    @Test
    fun `wifi without password still parses`() {
        val parsed = ContentParser.parse(ContentBuilder.wifi("OpenNet", "", "nopass"))
        assertTrue(parsed is ContentParser.ParsedContent.Wifi)
        assertEquals("OpenNet", (parsed as ContentParser.ParsedContent.Wifi).ssid)
    }

    @Test
    fun `wifi escapes separators per spec`() {
        val payload = ContentBuilder.wifi("My;Network", "p:w,1", "WPA")
        assertTrue(payload.contains("S:My\\;Network;"))
        assertTrue(payload.contains("P:p\\:w\\,1;"))
    }

    @Test
    fun `vcard roundtrips contact fields`() {
        val payload = ContentBuilder.contactVCard(
            name = "Ada Lovelace",
            phone = "+1234567890",
            email = "ada@example.com",
            organization = "Example Inc",
            address = "1 Infinite Loop"
        )
        val parsed = ContentParser.parse(payload)
        assertTrue(parsed is ContentParser.ParsedContent.Contact)
        parsed as ContentParser.ParsedContent.Contact
        assertEquals("Ada Lovelace", parsed.name)
        assertEquals("+1234567890", parsed.phone)
        assertEquals("ada@example.com", parsed.email)
        assertEquals("Example Inc", parsed.organization)
        assertEquals("1 Infinite Loop", parsed.address)
    }

    @Test
    fun `calendar event roundtrips with local datetime`() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.AUGUST, 20, 14, 30, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.HOUR_OF_DAY, 2)
        val end = cal.timeInMillis

        val payload = ContentBuilder.calendarEvent("Team Sync", "Room 3", "Weekly sync", start, end, false)
        val parsed = ContentParser.parse(payload)
        assertTrue(parsed is ContentParser.ParsedContent.CalendarEvent)
        parsed as ContentParser.ParsedContent.CalendarEvent
        assertEquals("Team Sync", parsed.title)
        assertEquals("Room 3", parsed.location)
        assertEquals("Weekly sync", parsed.description)
        assertEquals(start, parsed.startTime)
        assertEquals(end, parsed.endTime)
    }

    @Test
    fun `all day event uses date-only dtstart`() {
        val now = System.currentTimeMillis()
        val payload = ContentBuilder.calendarEvent("Holiday", "", "", now, now, true)
        val parsed = ContentParser.parse(payload)
        assertTrue(parsed is ContentParser.ParsedContent.CalendarEvent)
        assertTrue((parsed as ContentParser.ParsedContent.CalendarEvent).isAllDay)
    }

    @Test
    fun `email roundtrips with plain values`() {
        val payload = ContentBuilder.email("hi@example.com", "Hello", "World")
        val parsed = ContentParser.parse(payload)
        assertTrue(parsed is ContentParser.ParsedContent.Email)
        parsed as ContentParser.ParsedContent.Email
        assertEquals("hi@example.com", parsed.address)
        assertEquals("Hello", parsed.subject)
        assertEquals("World", parsed.body)
    }

    @Test
    fun `email url-encodes spaces in subject`() {
        val payload = ContentBuilder.email("hi@example.com", "Hello World", "")
        assertTrue(payload.contains("subject=Hello+World") || payload.contains("subject=Hello%20World"))
    }

    @Test
    fun `sms roundtrips`() {
        val payload = ContentBuilder.sms("+8613800138000", "OK")
        val parsed = ContentParser.parse(payload)
        assertTrue(parsed is ContentParser.ParsedContent.SMS)
        parsed as ContentParser.ParsedContent.SMS
        assertEquals("+8613800138000", parsed.number)
        assertEquals("OK", parsed.message)
    }

    @Test
    fun `phone payload parses as phone`() {
        val parsed = ContentParser.parse(ContentBuilder.phone("+8613800138000"))
        assertTrue(parsed is ContentParser.ParsedContent.Phone)
        assertEquals("+8613800138000", (parsed as ContentParser.ParsedContent.Phone).number)
    }

    @Test
    fun `geo roundtrips coordinates and query`() {
        val payload = ContentBuilder.geo(31.2304, 121.4737, "Shanghai")
        val parsed = ContentParser.parse(payload)
        assertTrue(parsed is ContentParser.ParsedContent.GeoLocation)
        parsed as ContentParser.ParsedContent.GeoLocation
        assertEquals(31.2304, parsed.latitude, 0.0001)
        assertEquals(121.4737, parsed.longitude, 0.0001)
        assertEquals("Shanghai", parsed.query)
    }

    @Test
    fun `url adds https scheme when missing`() {
        assertEquals("https://example.com", ContentBuilder.url("example.com"))
        assertEquals("http://example.com", ContentBuilder.url("http://example.com"))
        val parsed = ContentParser.parse(ContentBuilder.url("example.com"))
        assertTrue(parsed is ContentParser.ParsedContent.Url)
    }
}

package com.xenoamess.qrcodesimple

import java.util.regex.Pattern

/**
 * 智能内容解析器 - 识别 WiFi/联系人/日历/邮件/URL/地理位置等格式
 */
object ContentParser {

    sealed class ParsedContent {
        data class Wifi(
            val ssid: String,
            val password: String = "",
            val encryption: String = "WPA" // WPA/WEP/nopass
        ) : ParsedContent()

        data class Contact(
            val name: String = "",
            val phone: String = "",
            val email: String = "",
            val address: String = "",
            val organization: String = "",
            val title: String = "",
            val url: String = ""
        ) : ParsedContent()

        data class CalendarEvent(
            val title: String = "",
            val description: String = "",
            val location: String = "",
            val startTime: Long = 0,
            val endTime: Long = 0,
            val isAllDay: Boolean = false
        ) : ParsedContent()

        data class Email(
            val address: String,
            val subject: String = "",
            val body: String = ""
        ) : ParsedContent()

        data class Url(val url: String) : ParsedContent()

        data class GeoLocation(
            val latitude: Double,
            val longitude: Double,
            val query: String = ""
        ) : ParsedContent()

        data class Phone(val number: String) : ParsedContent()

        data class SMS(
            val number: String,
            val message: String = ""
        ) : ParsedContent()

        data class Text(val text: String) : ParsedContent()
    }

    /**
     * 解析内容并返回类型
     */
    fun parse(content: String): ParsedContent {
        val trimmed = content.trim()
        
        return when {
            isWifi(trimmed) -> parseWifi(trimmed)
            isContact(trimmed) -> parseContact(trimmed)
            isCalendarEvent(trimmed) -> parseCalendarEvent(trimmed)
            isEmail(trimmed) -> parseEmail(trimmed)
            isGeoLocation(trimmed) -> parseGeoLocation(trimmed)
            isSMS(trimmed) -> parseSMS(trimmed)
            isPhoneNumber(trimmed) -> ParsedContent.Phone(extractPhoneNumber(trimmed))
            isUrl(trimmed) -> ParsedContent.Url(normalizeUrl(trimmed))
            else -> ParsedContent.Text(trimmed)
        }
    }

    /**
     * 检测内容类型（用于显示图标或标签）
     */
    fun detectType(content: String): ContentType {
        val trimmed = content.trim()
        return when {
            isWifi(trimmed) -> ContentType.WIFI
            isContact(trimmed) -> ContentType.CONTACT
            isCalendarEvent(trimmed) -> ContentType.CALENDAR
            isEmail(trimmed) -> ContentType.EMAIL
            isGeoLocation(trimmed) -> ContentType.LOCATION
            isSMS(trimmed) -> ContentType.SMS
            isPhoneNumber(trimmed) -> ContentType.PHONE
            isUrl(trimmed) -> ContentType.URL
            else -> ContentType.TEXT
        }
    }

    enum class ContentType {
        WIFI, CONTACT, CALENDAR, EMAIL, LOCATION, SMS, PHONE, URL, TEXT
    }

    // ==================== WiFi ====================
    
    private fun isWifi(content: String): Boolean {
        return content.startsWith("WIFI:", ignoreCase = true)
    }

    private fun parseWifi(content: String): ParsedContent.Wifi {
        val ssid = extractParam(content, "S")
        val password = extractParam(content, "P")
        val encryption = extractParam(content, "T").ifEmpty { "WPA" }
        return ParsedContent.Wifi(ssid, password, encryption)
    }

    // ==================== Contact (vCard) ====================
    
    private fun isContact(content: String): Boolean {
        return content.contains("BEGIN:VCARD", ignoreCase = true) ||
               content.contains("MECARD:", ignoreCase = true)
    }

    private fun parseContact(content: String): ParsedContent.Contact {
        return if (content.contains("BEGIN:VCARD")) {
            parseVCard(content)
        } else {
            parseMeCard(content)
        }
    }

    private fun parseVCard(content: String): ParsedContent.Contact {
        val name = extractVCardField(content, "FN")
        val phone = extractVCardField(content, "TEL")
        val email = extractVCardField(content, "EMAIL")
        val address = extractVCardField(content, "ADR")
        val org = extractVCardField(content, "ORG")
        val title = extractVCardField(content, "TITLE")
        val url = extractVCardField(content, "URL")
        return ParsedContent.Contact(name, phone, email, address, org, title, url)
    }

    private fun parseMeCard(content: String): ParsedContent.Contact {
        val name = extractMeCardField(content, "N")
        val phone = extractMeCardField(content, "TEL")
        val email = extractMeCardField(content, "EMAIL")
        val address = extractMeCardField(content, "ADR")
        return ParsedContent.Contact(name, phone, email, address)
    }

    // ==================== Calendar Event ====================
    
    private fun isCalendarEvent(content: String): Boolean {
        return content.startsWith("BEGIN:VEVENT", ignoreCase = true) ||
               content.startsWith("BEGIN:VCALENDAR", ignoreCase = true)
    }

    private fun parseCalendarEvent(content: String): ParsedContent.CalendarEvent {
        val summary = extractVCardField(content, "SUMMARY")
        val description = extractVCardField(content, "DESCRIPTION")
        val location = extractVCardField(content, "LOCATION")
        val dtStart = extractVCardField(content, "DTSTART")
        val dtEnd = extractVCardField(content, "DTEND")
        
        return ParsedContent.CalendarEvent(
            title = summary,
            description = description,
            location = location,
            startTime = parseDateTime(dtStart),
            endTime = parseDateTime(dtEnd),
            isAllDay = dtStart.length == 8 // YYYYMMDD format
        )
    }

    // ==================== Email ====================
    
    private fun isEmail(content: String): Boolean {
        return content.startsWith("mailto:", ignoreCase = true) ||
               content.startsWith("MATMSG:", ignoreCase = true)
    }

    private fun parseEmail(content: String): ParsedContent.Email {
        return if (content.startsWith("mailto:")) {
            parseMailto(content)
        } else {
            parseMatmsg(content)
        }
    }

    private fun parseMailto(content: String): ParsedContent.Email {
        // mailto:address@example.com?subject=XXX&body=YYY
        val withoutScheme = content.substring(7) // Remove "mailto:"
        val parts = withoutScheme.split("?", limit = 2)
        val address = parts[0]
        
        var subject = ""
        var body = ""
        
        if (parts.size > 1) {
            val query = parts[1]
            val params = parseQueryString(query)
            subject = params["subject"] ?: ""
            body = params["body"] ?: ""
        }
        
        return ParsedContent.Email(address, subject, body)
    }

    private fun parseMatmsg(content: String): ParsedContent.Email {
        val address = extractParam(content, "TO")
        val subject = extractParam(content, "SUB")
        val body = extractParam(content, "BODY")
        return ParsedContent.Email(address, subject, body)
    }

    // ==================== Geo Location ====================
    
    private fun isGeoLocation(content: String): Boolean {
        return content.startsWith("geo:", ignoreCase = true) ||
               content.startsWith("GEO:", ignoreCase = true)
    }

    private fun parseGeoLocation(content: String): ParsedContent.GeoLocation {
        // geo:lat,lng?q=query or geo:lat,lng
        val withoutScheme = content.substring(4) // Remove "geo:"
        val parts = withoutScheme.split("?", limit = 2)
        val coords = parts[0]
        
        val query = if (parts.size > 1) {
            parseQueryString(parts[1])["q"] ?: ""
        } else {
            ""
        }
        
        val coordParts = coords.split(",")
        val lat = coordParts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
        val lon = coordParts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        
        return ParsedContent.GeoLocation(lat, lon, query)
    }

    // ==================== SMS ====================
    
    private fun isSMS(content: String): Boolean {
        return content.startsWith("sms:", ignoreCase = true) ||
               content.startsWith("SMSTO:", ignoreCase = true)
    }

    private fun parseSMS(content: String): ParsedContent.SMS {
        return if (content.startsWith("SMSTO:", ignoreCase = true)) {
            // SMSTO:number:message
            val withoutScheme = content.substring(6)
            val parts = withoutScheme.split(":", limit = 2)
            val number = parts[0]
            val message = parts.getOrNull(1) ?: ""
            ParsedContent.SMS(number, message)
        } else {
            // sms:number?body=message
            val withoutScheme = content.substring(4)
            val parts = withoutScheme.split("?", limit = 2)
            val number = parts[0]
            
            val message = if (parts.size > 1) {
                parseQueryString(parts[1])["body"] ?: ""
            } else {
                ""
            }
            
            ParsedContent.SMS(number, message)
        }
    }

    // ==================== Phone ====================
    
    private fun isPhoneNumber(content: String): Boolean {
        val phonePattern = Pattern.compile("^[+]?[0-9]{7,15}$")
        val cleanNumber = content.replace(Regex("[\\s\\-\\(\\)]"), "")
        return phonePattern.matcher(cleanNumber).matches() ||
               content.startsWith("tel:", ignoreCase = true)
    }

    private fun extractPhoneNumber(content: String): String {
        return if (content.startsWith("tel:")) {
            content.substring(4)
        } else {
            content.replace(Regex("[\\s\\-\\(\\)]"), "")
        }
    }

    // ==================== URL ====================
    
    private fun isUrl(content: String): Boolean {
        val urlPattern = Pattern.compile(
            "^(https?://|www\\.)[a-zA-Z0-9\\-\\.]+\\.[a-zA-Z]{2,}(/.*)?$",
            Pattern.CASE_INSENSITIVE
        )
        return urlPattern.matcher(content).matches()
    }

    private fun normalizeUrl(content: String): String {
        return if (content.startsWith("www.", ignoreCase = true)) {
            "https://$content"
        } else {
            content
        }
    }

    // ==================== Helper Functions ====================

    private fun extractParam(content: String, key: String): String {
        val pattern = Pattern.compile("$key:([^;]*);", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(content)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }

    private fun extractVCardField(content: String, field: String): String {
        val lines = content.lines()
        for (line in lines) {
            if (line.startsWith("$field:", ignoreCase = true)) {
                return line.substringAfter(":").trim()
            }
        }
        return ""
    }

    private fun extractMeCardField(content: String, field: String): String {
        val pattern = Pattern.compile("$field:([^;]*);", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(content)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }

    private fun parseQueryString(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val keyValue = pair.split("=", limit = 2)
            if (keyValue.size == 2) {
                result[keyValue[0]] = keyValue[1]
            }
        }
        return result
    }

    private fun parseDateTime(dt: String): Long {
        // 简单解析 ISO 格式日期
        return try {
            val clean = dt.replace(Regex("[^0-9]"), "")
            if (clean.length >= 14) {
                // YYYYMMDDHHMMSS format
                val year = clean.substring(0, 4).toInt()
                val month = clean.substring(4, 6).toInt() - 1
                val day = clean.substring(6, 8).toInt()
                val hour = clean.substring(8, 10).toInt()
                val minute = clean.substring(10, 12).toInt()
                val calendar = java.util.Calendar.getInstance()
                calendar.set(year, month, day, hour, minute)
                calendar.timeInMillis
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }
}

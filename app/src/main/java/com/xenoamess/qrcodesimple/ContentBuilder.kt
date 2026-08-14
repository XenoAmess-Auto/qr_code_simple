package com.xenoamess.qrcodesimple

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 结构化内容生成器：把表单字段构建为扫描端 [ContentParser] 可解析的载荷串。
 * 与解析侧严格互为逆操作（roundtrip 测试保护）。
 */
object ContentBuilder {

    // ==================== WiFi ====================

    fun wifi(ssid: String, password: String, encryption: String): String {
        // ZXing WiFi 格式：WIFI:T:WPA;S:ssid;P:pass;; —— \ ; , : " 需反斜杠转义
        val enc = encryption.ifEmpty { "WPA" }
        return buildString {
            append("WIFI:T:").append(escapeWifi(enc)).append(";")
            append("S:").append(escapeWifi(ssid)).append(";")
            if (password.isNotEmpty()) {
                append("P:").append(escapeWifi(password)).append(";")
            }
            append(";")
        }
    }

    private fun escapeWifi(value: String): String = buildString {
        for (c in value) {
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
            append(c)
        }
    }

    // ==================== Contact (vCard 3.0) ====================

    fun contactVCard(
        name: String,
        phone: String,
        email: String,
        organization: String,
        address: String
    ): String {
        return buildString {
            append("BEGIN:VCARD\nVERSION:3.0\n")
            if (name.isNotEmpty()) append("FN:").append(escapeVCard(name)).append('\n')
            if (phone.isNotEmpty()) append("TEL:").append(escapeVCard(phone)).append('\n')
            if (email.isNotEmpty()) append("EMAIL:").append(escapeVCard(email)).append('\n')
            if (organization.isNotEmpty()) append("ORG:").append(escapeVCard(organization)).append('\n')
            if (address.isNotEmpty()) append("ADR:").append(escapeVCard(address)).append('\n')
            append("END:VCARD")
        }
    }

    private fun escapeVCard(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    // ==================== Calendar Event (vCalendar) ====================

    fun calendarEvent(
        title: String,
        location: String,
        description: String,
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean
    ): String {
        val dtFormat = if (isAllDay) {
            SimpleDateFormat("yyyyMMdd", Locale.US)
        } else {
            SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
        }
        return buildString {
            append("BEGIN:VEVENT\n")
            if (title.isNotEmpty()) append("SUMMARY:").append(escapeVCard(title)).append('\n')
            if (location.isNotEmpty()) append("LOCATION:").append(escapeVCard(location)).append('\n')
            if (description.isNotEmpty()) append("DESCRIPTION:").append(escapeVCard(description)).append('\n')
            append("DTSTART:").append(dtFormat.format(Date(startMillis))).append('\n')
            append("DTEND:").append(dtFormat.format(Date(endMillis))).append('\n')
            append("END:VEVENT")
        }
    }

    // ==================== Email (mailto) ====================

    fun email(address: String, subject: String, body: String): String {
        val params = buildString {
            if (subject.isNotEmpty()) append("subject=").append(urlEncode(subject))
            if (body.isNotEmpty()) {
                if (isNotEmpty()) append('&')
                append("body=").append(urlEncode(body))
            }
        }
        return "mailto:$address" + if (params.isNotEmpty()) "?$params" else ""
    }

    // ==================== SMS ====================

    fun sms(number: String, message: String): String {
        return "sms:$number" + if (message.isNotEmpty()) "?body=${urlEncode(message)}" else ""
    }

    // ==================== Phone ====================

    fun phone(number: String): String = "tel:$number"

    // ==================== Geo ====================

    fun geo(latitude: Double, longitude: Double, query: String): String {
        val base = "geo:$latitude,$longitude"
        return if (query.isNotEmpty()) "$base?q=${urlEncode(query)}" else base
    }

    // ==================== URL ====================

    fun url(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}

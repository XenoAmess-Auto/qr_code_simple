package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.xenoamess.qrcodesimple.ContentParser.ParsedContent
import java.util.Calendar

/**
 * 智能内容操作处理器 - 提供一键操作功能
 */
class ContentActionHandler(private val activity: Activity) {

    /**
     * 获取内容类型对应的操作按钮列表
     */
    fun getActionButtons(content: String): List<ActionButton> {
        val parsed = ContentParser.parse(content)
        return when (parsed) {
            is ParsedContent.Wifi -> listOf(
                ActionButton(
                    R.drawable.ic_wifi,
                    activity.getString(R.string.action_connect_wifi),
                ) { connectToWifi(parsed) }
            )
            is ParsedContent.Contact -> listOf(
                ActionButton(
                    R.drawable.ic_contact,
                    activity.getString(R.string.action_add_contact),
                ) { addContact(parsed) }
            )
            is ParsedContent.CalendarEvent -> listOf(
                ActionButton(
                    R.drawable.ic_calendar,
                    activity.getString(R.string.action_add_event),
                ) { addCalendarEvent(parsed) }
            )
            is ParsedContent.Email -> listOf(
                ActionButton(
                    R.drawable.ic_email,
                    activity.getString(R.string.action_send_email),
                ) { sendEmail(parsed) }
            )
            is ParsedContent.Url -> listOf(
                ActionButton(
                    R.drawable.ic_open_in_browser,
                    activity.getString(R.string.action_open_url),
                ) { openUrl(parsed.url) }
            )
            is ParsedContent.GeoLocation -> listOf(
                ActionButton(
                    R.drawable.ic_location,
                    activity.getString(R.string.action_open_map),
                ) { openMap(parsed) }
            )
            is ParsedContent.Phone -> listOf(
                ActionButton(
                    R.drawable.ic_phone,
                    activity.getString(R.string.action_call),
                ) { makePhoneCall(parsed.number) }
            )
            is ParsedContent.SMS -> listOf(
                ActionButton(
                    R.drawable.ic_sms,
                    activity.getString(R.string.action_send_sms),
                ) { sendSMS(parsed) }
            )
            is ParsedContent.Text -> emptyList()
        }
    }

    /**
     * 获取内容类型标签
     */
    fun getContentTypeLabel(content: String): String {
        val type = ContentParser.detectType(content)
        return when (type) {
            ContentParser.ContentType.WIFI -> activity.getString(R.string.content_type_wifi)
            ContentParser.ContentType.CONTACT -> activity.getString(R.string.content_type_contact)
            ContentParser.ContentType.CALENDAR -> activity.getString(R.string.content_type_calendar)
            ContentParser.ContentType.EMAIL -> activity.getString(R.string.content_type_email)
            ContentParser.ContentType.LOCATION -> activity.getString(R.string.content_type_location)
            ContentParser.ContentType.SMS -> activity.getString(R.string.content_type_sms)
            ContentParser.ContentType.PHONE -> activity.getString(R.string.content_type_phone)
            ContentParser.ContentType.URL -> activity.getString(R.string.content_type_url)
            ContentParser.ContentType.TEXT -> activity.getString(R.string.content_type_text)
        }
    }

    /**
     * 获取内容图标
     */
    fun getContentTypeIcon(content: String): Int {
        val type = ContentParser.detectType(content)
        return when (type) {
            ContentParser.ContentType.WIFI -> R.drawable.ic_wifi
            ContentParser.ContentType.CONTACT -> R.drawable.ic_contact
            ContentParser.ContentType.CALENDAR -> R.drawable.ic_calendar
            ContentParser.ContentType.EMAIL -> R.drawable.ic_email
            ContentParser.ContentType.LOCATION -> R.drawable.ic_location
            ContentParser.ContentType.SMS -> R.drawable.ic_sms
            ContentParser.ContentType.PHONE -> R.drawable.ic_phone
            ContentParser.ContentType.URL -> R.drawable.ic_open_in_browser
            ContentParser.ContentType.TEXT -> R.drawable.ic_text
        }
    }

    // ==================== WiFi ====================

    private fun connectToWifi(wifi: ParsedContent.Wifi) {
        if (wifi.ssid.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.wifi_invalid), Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 Suggestion API
            connectWifiModern(wifi)
        } else {
            // 旧版本直接配置
            connectWifiLegacy(wifi)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectWifiModern(wifi: ParsedContent.Wifi) {
        // Android 10+ 不能直接连接WiFi，引导用户到系统设置
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
        activity.startActivity(intent)
        Toast.makeText(
            activity,
            activity.getString(R.string.wifi_manual_connect, wifi.ssid),
            Toast.LENGTH_LONG
        ).show()
    }

    @Suppress("DEPRECATION")
    private fun connectWifiLegacy(wifi: ParsedContent.Wifi) {
        try {
            val wifiManager = activity.applicationContext.getSystemService(Activity.WIFI_SERVICE) as WifiManager
            
            val config = WifiConfiguration().apply {
                SSID = "\"${wifi.ssid}\""
                if (wifi.password.isNotEmpty()) {
                    when (wifi.encryption.uppercase()) {
                        "WEP" -> wepKeys[0] = "\"${wifi.password}\""
                        "WPA", "WPA2" -> preSharedKey = "\"${wifi.password}\""
                    }
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }

            val networkId = wifiManager.addNetwork(config)
            if (networkId != -1) {
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()
                Toast.makeText(
                    activity,
                    activity.getString(R.string.wifi_connecting, wifi.ssid),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.wifi_add_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(
                activity,
                activity.getString(R.string.wifi_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ==================== Contact ====================

    private fun addContact(contact: ParsedContent.Contact) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            if (contact.name.isNotEmpty()) {
                putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
            }
            if (contact.phone.isNotEmpty()) {
                putExtra(ContactsContract.Intents.Insert.PHONE, contact.phone)
            }
            if (contact.email.isNotEmpty()) {
                putExtra(ContactsContract.Intents.Insert.EMAIL, contact.email)
            }
            if (contact.organization.isNotEmpty()) {
                putExtra(ContactsContract.Intents.Insert.COMPANY, contact.organization)
            }
        }
        
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, activity.getString(R.string.no_app_found), Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== Calendar ====================

    private fun addCalendarEvent(event: ParsedContent.CalendarEvent) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            if (event.title.isNotEmpty()) {
                putExtra(CalendarContract.Events.TITLE, event.title)
            }
            if (event.description.isNotEmpty()) {
                putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            }
            if (event.location.isNotEmpty()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            }
            if (event.startTime > 0) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startTime)
            }
            if (event.endTime > 0) {
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endTime)
            }
            if (event.isAllDay) {
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            }
        }
        
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, activity.getString(R.string.no_calendar_app), Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== Email ====================

    private fun sendEmail(email: ParsedContent.Email) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${email.address}")
            if (email.subject.isNotEmpty()) {
                putExtra(Intent.EXTRA_SUBJECT, email.subject)
            }
            if (email.body.isNotEmpty()) {
                putExtra(Intent.EXTRA_TEXT, email.body)
            }
        }
        
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, activity.getString(R.string.no_email_app), Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== URL ====================

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, activity.getString(R.string.no_browser_found), Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== Map ====================

    private fun openMap(location: ParsedContent.GeoLocation) {
        val uri = if (location.query.isNotEmpty()) {
            Uri.parse("geo:0,0?q=${Uri.encode(location.query)}")
        } else {
            Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
        }
        
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 回退到网页版地图
            val webUri = Uri.parse(
                "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
            )
            val webIntent = Intent(Intent.ACTION_VIEW, webUri)
            try {
                activity.startActivity(webIntent)
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(activity, activity.getString(R.string.no_map_app), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== Phone ====================

    private fun makePhoneCall(number: String) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.confirm_call))
            .setMessage(number)
            .setPositiveButton(activity.getString(R.string.call)) { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                try {
                    activity.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(activity, activity.getString(R.string.no_phone_app), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    // ==================== SMS ====================

    private fun sendSMS(sms: ParsedContent.SMS) {
        val uri = Uri.parse("smsto:${sms.number}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (sms.message.isNotEmpty()) {
                putExtra("sms_body", sms.message)
            }
        }
        
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, activity.getString(R.string.no_sms_app), Toast.LENGTH_SHORT).show()
        }
    }

    data class ActionButton(
        val iconResId: Int,
        val text: String,
        val onClick: () -> Unit
    )
}

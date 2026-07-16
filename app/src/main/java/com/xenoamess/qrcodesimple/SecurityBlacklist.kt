package com.xenoamess.qrcodesimple

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 恶意链接黑名单数据模型。
 *
 * 加载优先级：filesDir 覆盖文件（在线更新产物，version 更高才生效）
 * > assets 内置 blacklist.json > 代码内置兜底列表。
 */
data class SecurityBlacklist(
    val version: Int,
    val domains: Set<String>,
    val suspiciousKeywords: List<String>,
    val shortUrlServices: List<String>
) {
    companion object {
        private const val TAG = "SecurityBlacklist"
        const val ASSET_PATH = "security/blacklist.json"
        const val OVERRIDE_RELATIVE_PATH = "security/blacklist.json"

        /** 代码内置兜底列表（与 assets/blacklist.json 的 version 1 保持一致）。 */
        private val FALLBACK = SecurityBlacklist(
            version = 0,
            domains = setOf(
                "phishing.com", "malware.net", "virus.org",
                "suspicious.site", "dangerous.link"
            ),
            suspiciousKeywords = listOf(
                "login", "verify", "account", "password", "credential",
                "security", "update", "confirm", "authenticate"
            ),
            shortUrlServices = listOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly")
        )

        /** 无 Context 场景下的兜底列表。 */
        fun fallback(): SecurityBlacklist = FALLBACK

        /**
         * 解析并校验黑名单 JSON；非法输入返回 null。
         */
        fun parse(json: String): SecurityBlacklist? {
            return try {
                val obj = JSONObject(json)
                val version = obj.getInt("version")
                require(version >= 1) { "invalid version" }
                val domains = obj.getJSONArray("domains").let { arr ->
                    (0 until arr.length()).map { arr.getString(it).lowercase() }.toSet()
                }
                val keywords = obj.getJSONArray("suspiciousKeywords").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
                val shortUrls = obj.getJSONArray("shortUrlServices").let { arr ->
                    (0 until arr.length()).map { arr.getString(it).lowercase() }
                }
                SecurityBlacklist(version, domains, keywords, shortUrls)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid blacklist JSON", e)
                null
            }
        }

        /** 从 assets 加载内置黑名单；失败时回退到代码内置列表。 */
        fun loadBundled(context: Context): SecurityBlacklist {
            return try {
                context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
                    .let { parse(it) } ?: FALLBACK
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load bundled blacklist, using fallback", e)
                FALLBACK
            }
        }

        /** 从 filesDir 加载在线更新产物；不存在或损坏时返回 null。 */
        fun loadOverride(context: Context): SecurityBlacklist? {
            val file = File(context.filesDir, OVERRIDE_RELATIVE_PATH)
            if (!file.exists()) return null
            return try {
                parse(file.readText())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load override blacklist", e)
                null
            }
        }

        /** 保存在线更新产物（覆盖写）。 */
        fun saveOverride(context: Context, blacklist: SecurityBlacklist, rawJson: String): Boolean {
            return try {
                val file = File(context.filesDir, OVERRIDE_RELATIVE_PATH)
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(rawJson)
                if (!tmp.renameTo(file)) {
                    file.writeText(rawJson)
                    tmp.delete()
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save override blacklist", e)
                false
            }
        }
    }
}

package com.xenoamess.qrcodesimple

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 应用更新检查器：查询 GitHub Releases 最新版本。
 *
 * 设计原则（与 BlacklistUpdater 一致）：
 * - 静默失败：任何网络/解析问题只记日志并返回 null，不抛异常；
 * - 响应有大小上限，防止异常响应占用内存；
 * - 版本比较为纯函数，独立可测。
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/XenoAmess-Auto/qr_code_simple/releases/latest"

    private const val APK_ASSET_NAME = "app-release.apk"

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_BYTES = 1024 * 1024

    /** 测试注入点：替换网络连接工厂。生产环境为 null，走真实 URL.openConnection。 */
    internal var connectionFactoryForTesting: ((URL) -> HttpURLConnection)? = null

    /** 一次成功拉取的 release 信息。 */
    data class ReleaseInfo(
        val version: String,
        val changelog: String,
        val htmlUrl: String,
        val apkUrl: String?,
        val apkSizeBytes: Long
    )

    /**
     * 拉取最新 release 信息；任何失败返回 null。
     */
    fun fetchLatestRelease(): ReleaseInfo? {
        return try {
            val json = download(LATEST_RELEASE_URL) ?: return null
            parse(json)
        } catch (e: Throwable) {
            Log.w(TAG, "Fetch latest release failed", e)
            null
        }
    }

    /**
     * 解析 GitHub releases/latest 响应 JSON。独立成函数便于单测。
     */
    fun parse(json: String): ReleaseInfo? {
        return try {
            val root = JSONObject(json)
            val tagName = root.optString("tag_name")
            val version = tagName.removePrefix("v")
            if (version.isEmpty()) return null
            val changelog = root.optString("body")
            val htmlUrl = root.optString("html_url")
            if (htmlUrl.isEmpty()) return null

            var apkUrl: String? = null
            var apkSize = 0L
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name") == APK_ASSET_NAME) {
                        apkUrl = asset.optString("browser_download_url").ifEmpty { null }
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }
            ReleaseInfo(version, changelog, htmlUrl, apkUrl, apkSize)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse release json", e)
            null
        }
    }

    /**
     * 比较版本号：remote 是否比 local 更新。
     * 去 v 前缀后按 . 拆为数字段逐段比较，缺段按 0；任一段非纯数字返回 false。
     */
    fun isNewer(remote: String, local: String): Boolean {
        fun partsOf(v: String): List<Long>? {
            val parts = v.removePrefix("v").split(".")
            if (parts.any { it.isEmpty() }) return null
            return parts.map { it.toLongOrNull() ?: return null }
        }
        val remoteParts = partsOf(remote) ?: return false
        val localParts = partsOf(local) ?: return false
        val count = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until count) {
            val r = remoteParts.getOrElse(i) { 0L }
            val l = localParts.getOrElse(i) { 0L }
            if (r != l) return r > l
        }
        return false
    }

    /** 最多读取 maxBytes 字节；超出返回 null。 */
    private fun readCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream(maxBytes.coerceAtMost(8192))
        val chunk = ByteArray(4096)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun download(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (connectionFactoryForTesting?.invoke(URL(url))
                ?: URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "qr_code_simple/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Unexpected response code: ${connection.responseCode}")
                return null
            }
            connection.inputStream.use { input ->
                val bytes = readCapped(input, MAX_BYTES) ?: run {
                    Log.w(TAG, "Release response too large")
                    return null
                }
                bytes.toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}

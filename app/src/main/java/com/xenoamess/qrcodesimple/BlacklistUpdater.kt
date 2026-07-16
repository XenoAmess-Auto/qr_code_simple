package com.xenoamess.qrcodesimple

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * 恶意链接黑名单在线更新器。
 *
 * 设计原则（与产品决策一致）：
 * - 完全静默：任何失败（无网络、超时、内容非法、非更新版本）只记日志，不抛异常、不提示用户；
 * - 只接受 schema 合法且 version 高于当前生效版本的列表；
 * - 下载内容有大小上限，防止异常响应占用内存。
 */
object BlacklistUpdater {

    private const val TAG = "BlacklistUpdater"

    /** 黑名单源：本仓库 master 分支内置列表的 raw 地址。 */
    private const val BLACKLIST_URL =
        "https://raw.githubusercontent.com/XenoAmess-Auto/qr_code_simple/master/app/src/main/assets/security/blacklist.json"

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_BYTES = 64 * 1024

    /**
     * 静默尝试在线更新。
     * @return true 表示拉取并应用了更新版本；其余任何情况返回 false。
     */
    fun updateSilently(context: Context): Boolean {
        return try {
            val json = download(BLACKLIST_URL) ?: return false
            val currentVersion = currentEffectiveVersion(context)
            val parsed = validateUpdate(json, currentVersion) ?: return false
            if (!SecurityBlacklist.saveOverride(context, parsed, json)) return false
            // 热加载，使新列表立即生效
            SecurityManager.init(context)
            Log.i(TAG, "Blacklist updated to version ${parsed.version}")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Silent blacklist update failed", e)
            false
        }
    }

    /**
     * 校验候选内容：schema 合法且 version 高于当前版本才接受。
     * 独立成函数便于单测。
     */
    fun validateUpdate(json: String, currentVersion: Int): SecurityBlacklist? {
        val parsed = SecurityBlacklist.parse(json) ?: return null
        return if (parsed.version > currentVersion) parsed else null
    }

    private fun currentEffectiveVersion(context: Context): Int {
        val bundled = SecurityBlacklist.loadBundled(context)
        val override = SecurityBlacklist.loadOverride(context)
        return maxOf(bundled.version, override?.version ?: 0)
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
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Unexpected response code: ${connection.responseCode}")
                return null
            }
            connection.inputStream.use { input ->
                val bytes = readCapped(input, MAX_BYTES) ?: run {
                    Log.w(TAG, "Blacklist response too large")
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

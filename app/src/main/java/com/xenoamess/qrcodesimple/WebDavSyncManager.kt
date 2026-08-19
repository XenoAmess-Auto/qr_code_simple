@file:Suppress("DEPRECATION")

package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WebDAV 云同步：把加密历史备份上传/恢复到用户自管的 WebDAV 服务器。
 *
 * - 手动触发，无后台自动同步（避免隐式网络行为）。
 * - 备份文件复用 [HistoryBackupManager] 的加密导出，**以 WebDAV 密码加密**：
 *   服务器即使被攻破也只能拿到密文。
 * - 服务器地址/用户名存普通 prefs，密码经 EncryptedSharedPreferences 加密落盘。
 */
object WebDavSyncManager {

    private const val TAG = "WebDavSyncManager"
    private const val PREFS_NAME = "app_settings"
    private const val ENC_PREFS_NAME = "webdav_secure"
    private const val KEY_URL = "webdav_url"
    private const val KEY_USERNAME = "webdav_username"
    private const val KEY_PASSWORD = "webdav_password"
    private const val KEY_AUTO_UPLOAD = "webdav_auto_upload"
    private const val KEY_LAST_SYNC = "webdav_last_sync"

    const val REMOTE_FILE_NAME = "qr-code-simple-backup.qrbk"

    enum class Outcome {
        SUCCESS, NOT_CONFIGURED, AUTH_FAILED, NOT_FOUND, NETWORK_ERROR, TOO_LARGE, DECRYPT_FAILED
    }

    data class Config(
        val url: String,
        val username: String,
        val password: CharArray
    ) {
        fun remoteFileUrl(): String = url.trimEnd('/') + "/" + REMOTE_FILE_NAME
    }

    fun saveConfig(context: Context, url: String, username: String, password: CharArray) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_USERNAME, username.trim())
            .apply()
        SecurePrefs.putString(context, PREFS_NAME, KEY_PASSWORD, password.concatToString())
    }

    fun loadConfig(context: Context): Config? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, null)?.trim().orEmpty()
        if (url.isEmpty()) return null
        val username = prefs.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val password = readPassword(context) ?: return Config(url, username, CharArray(0))
        return Config(url, username, password)
    }

    fun hasConfig(context: Context): Boolean =
        !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URL, null)
            ?.trim()
            .isNullOrEmpty()

    /** 自动上传开关（默认关）：开启后主 Activity 退到后台时节流上传。 */
    fun isAutoUploadEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPLOAD, false)

    fun setAutoUploadEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_UPLOAD, enabled).apply()
    }

    fun getLastSync(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)

    private fun markSynced(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    fun clearConfig(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_URL).remove(KEY_USERNAME).remove(KEY_PASSWORD).apply()
        try {
            encryptedPrefs(context).edit().remove(KEY_PASSWORD).apply()
        } catch (e: Exception) {
            Log.w(TAG, "failed to clear legacy encrypted password: ${e.message}")
        }
    }

    private fun readPassword(context: Context): CharArray? {
        // 新格式：SecurePrefs（Keystore AES/GCM）
        SecurePrefs.getString(context, PREFS_NAME, KEY_PASSWORD)?.let { return it.toCharArray() }

        // 旧 EncryptedSharedPreferences（webdav_secure）：命中后迁移并删除
        try {
            val legacy = encryptedPrefs(context).getString(KEY_PASSWORD, null)
            if (legacy != null) {
                Log.i(TAG, "Migrating WebDAV password to SecurePrefs")
                SecurePrefs.putString(context, PREFS_NAME, KEY_PASSWORD, legacy)
                try {
                    encryptedPrefs(context).edit().remove(KEY_PASSWORD).apply()
                } catch (e: Exception) {
                    Log.w(TAG, "failed to remove legacy encrypted password: ${e.message}")
                }
                return legacy.toCharArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "legacy encrypted prefs unavailable: ${e.message}")
        }
        return null
    }

    /** 上传：以 WebDAV 密码加密导出全部历史，PUT 到远端固定文件名。 */
    suspend fun upload(context: Context): Outcome = upload(context, ::loadConfig)

    internal suspend fun upload(
        context: Context,
        configLoader: (Context) -> Config?
    ): Outcome {
        val config = configLoader(context) ?: return Outcome.NOT_CONFIGURED
        return try {
            upload(context, config)
        } finally {
            config.password.fill('\u0000')
        }
    }

    internal suspend fun upload(context: Context, config: Config): Outcome = withContext(Dispatchers.IO) {
        if (config.password.isEmpty()) return@withContext Outcome.NOT_CONFIGURED
        val data = try {
            HistoryBackupManager.exportEncryptedJson(context, config.password)
        } catch (e: Exception) {
            Log.w(TAG, "export for webdav upload failed: ${e.message}")
            return@withContext Outcome.NETWORK_ERROR
        }
        when (WebDavClient.upload(config.remoteFileUrl(), config.username, config.password, data)) {
            WebDavClient.Result.SUCCESS -> {
                markSynced(context)
                Outcome.SUCCESS
            }
            WebDavClient.Result.AUTH_FAILED -> Outcome.AUTH_FAILED
            WebDavClient.Result.NOT_FOUND -> Outcome.NOT_FOUND
            WebDavClient.Result.TOO_LARGE -> Outcome.TOO_LARGE
            WebDavClient.Result.NETWORK_ERROR -> Outcome.NETWORK_ERROR
        }
    }

    /** 恢复：GET 远端备份并用 WebDAV 密码解密导入（按现有去重规则合并）。 */
    suspend fun download(context: Context): Outcome = download(context, ::loadConfig)

    internal suspend fun download(
        context: Context,
        configLoader: (Context) -> Config?
    ): Outcome {
        val config = configLoader(context) ?: return Outcome.NOT_CONFIGURED
        return try {
            download(context, config)
        } finally {
            config.password.fill('\u0000')
        }
    }

    internal suspend fun download(context: Context, config: Config): Outcome = withContext(Dispatchers.IO) {
        if (config.password.isEmpty()) return@withContext Outcome.NOT_CONFIGURED
        val (result, data) = WebDavClient.download(config.remoteFileUrl(), config.username, config.password)
        when (result) {
            WebDavClient.Result.SUCCESS -> {
                if (data == null) return@withContext Outcome.NETWORK_ERROR
                val importResult = try {
                    HistoryBackupManager.importEncrypted(context, data, config.password)
                } catch (e: Exception) {
                    Log.w(TAG, "decrypt/import webdav backup failed: ${e.message}")
                    return@withContext Outcome.DECRYPT_FAILED
                }
                if (importResult.success) Outcome.SUCCESS else Outcome.DECRYPT_FAILED
            }
            WebDavClient.Result.AUTH_FAILED -> Outcome.AUTH_FAILED
            WebDavClient.Result.NOT_FOUND -> Outcome.NOT_FOUND
            WebDavClient.Result.TOO_LARGE -> Outcome.TOO_LARGE
            WebDavClient.Result.NETWORK_ERROR -> Outcome.NETWORK_ERROR
        }
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            ENC_PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}

package com.xenoamess.qrcodesimple

import android.app.Application
import android.content.Context
import android.util.Log
import com.king.wechat.qrcode.WeChatQRCodeDetector
import com.xenoamess.qrcodesimple.data.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QRCodeApp : Application() {

    companion object {
        private const val TAG = "QRCodeApp"
        private const val PREFS_NAME = "app_settings"
        private const val KEY_PRIVACY_MODE = "privacy_mode"
        
        @Volatile
        var isWeChatQRCodeInitialized = false
            private set
        
        @Volatile
        var initErrorMessage: String? = null
            private set

        /**
         * 检查是否处于隐私模式（无痕扫描）
         */
        fun isPrivacyMode(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PRIVACY_MODE, false)
        }

        /**
         * 设置隐私模式
         */
        fun setPrivacyMode(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PRIVACY_MODE, enabled).apply()
        }

        private const val KEY_HISTORY_RETENTION_DAYS = "history_retention_days"
        private const val KEY_BLACKLIST_AUTO_UPDATE = "blacklist_auto_update"
        private const val KEY_BLACKLIST_LAST_CHECK = "blacklist_last_check"
        private const val BLACKLIST_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** 历史记录自动清理天数；0 表示永久保留。 */
        fun getHistoryRetentionDays(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_HISTORY_RETENTION_DAYS, 0)
        }

        fun setHistoryRetentionDays(context: Context, days: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_HISTORY_RETENTION_DAYS, days).apply()
        }

        /** 恶意链接黑名单自动更新开关（默认关闭）。 */
        fun isBlacklistAutoUpdateEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_BLACKLIST_AUTO_UPDATE, false)
        }

        fun setBlacklistAutoUpdateEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_BLACKLIST_AUTO_UPDATE, enabled).apply()
        }

        /**
         * 检查库是否已初始化，未初始化则尝试初始化
         * 用于 Activity 在需要时检查状态
         */
        fun ensureInitialized(app: Application): Boolean {
            return if (isWeChatQRCodeInitialized) {
                true
            } else {
                // 如果启动时初始化失败，尝试再次初始化
                initWeChatQRCodeDetector(app)
            }
        }

        fun initWeChatQRCodeDetector(app: Application): Boolean {
            if (isWeChatQRCodeInitialized) return true

            return try {
                Log.d(TAG, "Starting WeChatQRCodeDetector initialization...")

                // 先加载 OpenCV native 库
                System.loadLibrary("opencv_java4")
                Log.d(TAG, "OpenCV native library loaded")

                WeChatQRCodeDetector.init(app)
                isWeChatQRCodeInitialized = true
                initErrorMessage = null
                Log.i(TAG, "WeChatQRCodeDetector initialized successfully")
                true
            } catch (e: UnsatisfiedLinkError) {
                initErrorMessage = "Native library load failed: ${e.message}"
                Log.e(TAG, "Native library load failed", e)
                false
            } catch (e: Exception) {
                initErrorMessage = "Initialization failed: ${e.message}"
                Log.e(TAG, "Failed to initialize WeChatQRCodeDetector", e)
                false
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(base))
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化标签管理器
        TagManager.init(this)


        // 初始化应用锁
        AppLockManager.init(this)
        
        // 应用启动时预加载 WeChatQRCode 库（可选，失败不影响其他库）
        val success = initWeChatQRCodeDetector(this)
        if (!success) {
            Log.w(TAG, "WeChatQRCode pre-initialization failed, will use ZXing/ML Kit as fallback. Error: $initErrorMessage")
        }

        cleanupExpiredHistory()

        // 初始化恶意链接黑名单（assets 内置 + filesDir 在线更新产物）
        SecurityManager.init(this)
        maybeUpdateBlacklist()
    }

    /**
     * 开启自动更新时，后台静默检查黑名单更新（24h 节流；任何失败静默忽略）。
     */
    private fun maybeUpdateBlacklist() {
        if (!isBlacklistAutoUpdateEnabled(this)) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_BLACKLIST_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - lastCheck < BLACKLIST_CHECK_INTERVAL_MS) return
        // 先记录检查时间，避免失败时每次启动都请求网络
        prefs.edit().putLong(KEY_BLACKLIST_LAST_CHECK, System.currentTimeMillis()).apply()
        CoroutineScope(Dispatchers.IO).launch {
            BlacklistUpdater.updateSilently(this@QRCodeApp)
        }
    }

    /**
     * 按设置的历史保留天数自动清理过期记录（收藏豁免）；0 表示永久保留，直接跳过。
     */
    private fun cleanupExpiredHistory() {
        val retentionDays = getHistoryRetentionDays(this)
        if (retentionDays <= 0) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
                val deleted = HistoryRepository(this@QRCodeApp)
                    .deleteOlderThan(cutoff)
                if (deleted > 0) {
                    Log.i(TAG, "Auto-deleted $deleted history items older than $retentionDays days")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean up expired history", e)
            }
        }
    }
}

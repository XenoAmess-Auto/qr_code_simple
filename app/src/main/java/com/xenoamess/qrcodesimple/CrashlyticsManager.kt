package com.xenoamess.qrcodesimple

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * 崩溃监控管理器
 * 集成 Firebase Crashlytics
 */
object CrashlyticsManager {

    private const val TAG = "Crashlytics"

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null
    private var isInitialized = false

    /**
     * 初始化 Firebase
     */
    fun initialize(context: Context) {
        try {
            // 初始化 Firebase
            FirebaseApp.initializeApp(context)

            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
            crashlytics = FirebaseCrashlytics.getInstance()

            // 启用崩溃收集
            crashlytics?.setCrashlyticsCollectionEnabled(true)

            // 设置自定义键值
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            crashlytics?.setCustomKey("app_version", packageInfo.versionName ?: "unknown")
            crashlytics?.setCustomKey("app_version_code", packageInfo.longVersionCode)

            isInitialized = true
            Log.i(TAG, "Crashlytics initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Crashlytics", e)
            isInitialized = false
        }
    }

    /**
     * 记录非致命异常
     */
    fun recordException(throwable: Throwable, message: String? = null) {
        if (!isInitialized) {
            Log.w(TAG, "Crashlytics not initialized")
            return
        }

        message?.let {
            crashlytics?.log(it)
        }
        crashlytics?.recordException(throwable)
        Log.e(TAG, "Exception recorded to Crashlytics", throwable)
    }

    /**
     * 记录自定义日志
     */
    fun log(message: String) {
        if (!isInitialized) return
        crashlytics?.log(message)
    }

    /**
     * 设置用户标识
     */
    fun setUserId(userId: String) {
        if (!isInitialized) return
        crashlytics?.setUserId(userId)
        firebaseAnalytics?.setUserId(userId)
    }

    /**
     * 设置自定义键值
     */
    fun setCustomKey(key: String, value: String) {
        if (!isInitialized) return
        crashlytics?.setCustomKey(key, value)
    }

    fun setCustomKey(key: String, value: Int) {
        if (!isInitialized) return
        crashlytics?.setCustomKey(key, value)
    }

    fun setCustomKey(key: String, value: Boolean) {
        if (!isInitialized) return
        crashlytics?.setCustomKey(key, value)
    }

    /**
     * 记录事件
     */
    fun logEvent(eventName: String, params: Map<String, String>? = null) {
        if (!isInitialized) return

        try {
            val bundle = android.os.Bundle()
            params?.forEach { (key, value) ->
                bundle.putString(key, value)
            }
            firebaseAnalytics?.logEvent(eventName, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event", e)
        }
    }

    /**
     * 测试崩溃
     */
    fun testCrash() {
        throw RuntimeException("Test Crash") // 仅用于测试
    }

    /**
     * 是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
}

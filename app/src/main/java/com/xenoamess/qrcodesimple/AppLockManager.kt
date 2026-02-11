package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest

/**
 * 应用锁管理器
 */
object AppLockManager {

    private const val PREFS_NAME = "app_lock"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_LAST_UNLOCKED = "last_unlocked"
    private const val LOCK_TIMEOUT = 5 * 60 * 1000 // 5分钟超时

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 检查是否启用了应用锁
     */
    fun isLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCK_ENABLED, false)
    }

    /**
     * 启用/禁用应用锁
     */
    fun setLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
    }

    /**
     * 设置 PIN 密码
     */
    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PASSWORD_HASH, hash).apply()
        setLockEnabled(true)
    }

    /**
     * 验证 PIN
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        return storedHash == hashPin(pin)
    }

    /**
     * 检查是否有设置密码
     */
    fun hasPin(): Boolean {
        return prefs.getString(KEY_PASSWORD_HASH, null) != null
    }

    /**
     * 清除密码
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PASSWORD_HASH)
            .putBoolean(KEY_LOCK_ENABLED, false)
            .apply()
    }

    /**
     * 检查生物识别是否可用
     */
    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * 检查生物识别是否启用
     */
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /**
     * 启用/禁用生物识别
     */
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /**
     * 显示生物识别认证
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    recordUnlock()
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock QR Code Simple")
            .setSubtitle("Use your biometric credential")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * 记录解锁时间
     */
    fun recordUnlock() {
        prefs.edit().putLong(KEY_LAST_UNLOCKED, System.currentTimeMillis()).apply()
    }

    /**
     * 检查是否需要重新锁定
     */
    fun shouldLock(): Boolean {
        if (!isLockEnabled()) return false
        
        val lastUnlocked = prefs.getLong(KEY_LAST_UNLOCKED, 0)
        val elapsed = System.currentTimeMillis() - lastUnlocked
        return elapsed > LOCK_TIMEOUT
    }

    /**
     * 检查应用是否已解锁
     */
    fun isUnlocked(): Boolean {
        if (!isLockEnabled()) return true
        return !shouldLock()
    }

    /**
     * 重置锁定状态
     */
    fun lock() {
        prefs.edit().remove(KEY_LAST_UNLOCKED).apply()
    }

    /**
     * 哈希 PIN
     */
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}

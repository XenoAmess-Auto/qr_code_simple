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
        val hash = hashPinPbkdf2(pin)
        prefs.edit().putString(KEY_PASSWORD_HASH, hash).apply()
        setLockEnabled(true)
    }

    /**
     * 验证 PIN。旧版裸 SHA-256 存储验证通过后透明迁移到 PBKDF2。
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        return if (storedHash.startsWith("$PBKDF2_PREFIX$")) {
            verifyPbkdf2(pin, storedHash)
        } else {
            val legacyMatch = storedHash == legacySha256Hex(pin)
            if (legacyMatch) {
                prefs.edit().putString(KEY_PASSWORD_HASH, hashPinPbkdf2(pin)).apply()
            }
            legacyMatch
        }
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
     * 旧版哈希：裸 SHA-256 十六进制（仅用于迁移验证）。
     */
    private fun legacySha256Hex(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).toHex()
    }

    private const val PBKDF2_PREFIX = "pbkdf2"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val PBKDF2_SALT_BYTES = 16
    private const val PBKDF2_KEY_BYTES = 32

    /** PBKDF2WithHmacSHA256 + 随机盐，存储格式 pbkdf2$iterations$saltHex$hashHex。 */
    private fun hashPinPbkdf2(pin: String): String {
        val salt = ByteArray(PBKDF2_SALT_BYTES)
        java.security.SecureRandom().nextBytes(salt)
        val hash = pbkdf2(pin, salt, PBKDF2_ITERATIONS)
        return "$PBKDF2_PREFIX$$PBKDF2_ITERATIONS$${salt.toHex()}$${hash.toHex()}"
    }

    private fun verifyPbkdf2(pin: String, stored: String): Boolean {
        val parts = stored.split("$")
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations <= 0) return false
        val salt = parts[2].hexToBytesOrNull() ?: return false
        val expected = parts[3].hexToBytesOrNull() ?: return false
        val actual = pbkdf2(pin, salt, iterations)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, iterations, PBKDF2_KEY_BYTES * 8)
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0 || isEmpty()) return null
        return try {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: NumberFormatException) {
            null
        }
    }
}

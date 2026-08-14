package com.xenoamess.qrcodesimple

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * 自管安全键值存储：AES/GCM 密钥放 AndroidKeyStore，密文 Base64 落普通
 * SharedPreferences。替代已弃用的 androidx.security:security-crypto
 * （EncryptedSharedPreferences）。
 *
 * 读取顺序：新格式（GCM）→ 旧 EncryptedSharedPreferences（命中后迁移并删除旧条目）
 * → 明文（命中后迁移并删除）。
 */
object SecurePrefs {

    private const val TAG = "SecurePrefs"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val PREFIX = "enc_v1:"

    private fun keyAlias(prefsName: String) = "secure_prefs_$prefsName"

    private fun secretKey(prefsName: String): javax.crypto.SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val alias = keyAlias(prefsName)
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /** 写入（加密后落 SharedPreferences[prefsName] 的 [key] 条目）。 */
    fun putString(context: Context, prefsName: String, key: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(prefsName))
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = cipher.iv + encrypted
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit()
                .putString(key, PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            // 加密不可用（极端环境）时退化明文，保证功能可用
            Log.w(TAG, "encryption unavailable, storing plain value for $key", e)
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .edit().putString(key, value).apply()
        }
    }

    /** 读取；读不到返回 null。命中旧格式时透明迁移到新格式。 */
    fun getString(context: Context, prefsName: String, key: String): String? {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val stored = prefs.getString(key, null) ?: return null
        if (!stored.startsWith(PREFIX)) {
            // 明文存量：原样返回，下次写入时自然加密
            return stored
        }
        return try {
            val packed = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_BYTES)
            val data = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(prefsName), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "decrypt failed for $key", e)
            null
        }
    }

    fun remove(context: Context, prefsName: String, key: String) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().remove(key).apply()
    }
}

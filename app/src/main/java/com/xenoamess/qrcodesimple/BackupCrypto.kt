package com.xenoamess.qrcodesimple

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份文件加密原语。
 *
 * 文件布局：MAGIC(5B, "QRBK1") + VERSION(1B) + SALT(16B) + IV(12B) + AES-256/GCM 密文。
 * 密钥由 PBKDF2-HmacSHA256（100,000 次迭代，256 位）从用户密码派生。
 */
object BackupCrypto {

    private val MAGIC = byteArrayOf('Q'.code.toByte(), 'R'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_BITS = 128
    private val HEADER_LENGTH = MAGIC.size + 1 + SALT_LENGTH + IV_LENGTH

    /** 判断数据是否为加密备份。 */
    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plain)

        return ByteArray(HEADER_LENGTH + ciphertext.size).also { out ->
            MAGIC.copyInto(out, 0)
            out[MAGIC.size] = VERSION
            salt.copyInto(out, MAGIC.size + 1)
            iv.copyInto(out, MAGIC.size + 1 + SALT_LENGTH)
            ciphertext.copyInto(out, HEADER_LENGTH)
        }
    }

    /**
     * 解密加密备份。
     * @throws javax.crypto.AEADBadTagException 密码错误或数据被篡改
     * @throws IllegalArgumentException 数据不是合法的加密备份格式
     */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        require(isEncrypted(data) && data.size > HEADER_LENGTH) {
            "Not an encrypted backup file"
        }
        val salt = data.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_LENGTH)
        val iv = data.copyOfRange(MAGIC.size + 1 + SALT_LENGTH, HEADER_LENGTH)
        val ciphertext = data.copyOfRange(HEADER_LENGTH, data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}

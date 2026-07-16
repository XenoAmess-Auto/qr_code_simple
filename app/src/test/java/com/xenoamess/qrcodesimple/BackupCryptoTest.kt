package com.xenoamess.qrcodesimple

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.AEADBadTagException

/**
 * BackupCrypto 加密原语测试。
 */
@RunWith(AndroidJUnit4::class)
class BackupCryptoTest {

    private val plain = """{"version":1,"items":[{"content":"hello"}]}""".toByteArray(Charsets.UTF_8)
    private val password = "p@ssw0rd-测试".toCharArray()

    @Test
    fun `encrypt then decrypt roundtrips`() {
        val encrypted = BackupCrypto.encrypt(plain, password)
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertFalse(plain.contentEquals(encrypted))

        val decrypted = BackupCrypto.decrypt(encrypted, password)
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun `same input produces different ciphertext due to random salt and iv`() {
        val a = BackupCrypto.encrypt(plain, password)
        val b = BackupCrypto.encrypt(plain, password)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(BackupCrypto.decrypt(a, password), BackupCrypto.decrypt(b, password))
    }

    @Test(expected = AEADBadTagException::class)
    fun `wrong password fails with AEADBadTag`() {
        val encrypted = BackupCrypto.encrypt(plain, password)
        BackupCrypto.decrypt(encrypted, "wrong-password".toCharArray())
    }

    @Test(expected = AEADBadTagException::class)
    fun `tampered ciphertext fails with AEADBadTag`() {
        val encrypted = BackupCrypto.encrypt(plain, password)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()
        BackupCrypto.decrypt(encrypted, password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non backup data rejected`() {
        BackupCrypto.decrypt("not a backup".toByteArray(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated backup rejected`() {
        val encrypted = BackupCrypto.encrypt(plain, password)
        BackupCrypto.decrypt(encrypted.copyOf(10), password)
    }

    @Test
    fun `isEncrypted detects magic header`() {
        assertTrue(BackupCrypto.isEncrypted(BackupCrypto.encrypt(plain, password)))
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertFalse(BackupCrypto.isEncrypted(ByteArray(0)))
    }
}

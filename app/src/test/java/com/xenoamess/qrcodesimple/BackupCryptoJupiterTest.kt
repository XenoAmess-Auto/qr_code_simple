package com.xenoamess.qrcodesimple

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException

/**
 * JUnit 5 (Jupiter) 引擎冒烟测试：验证 useJUnitPlatform 下 Jupiter 用例能被发现并执行。
 */
class BackupCryptoJupiterTest {

    private val plain = "jupiter-smoke".toByteArray(Charsets.UTF_8)
    private val password = "pw".toCharArray()

    @Test
    fun `jupiter engine runs encrypt decrypt roundtrip`() {
        val encrypted = BackupCrypto.encrypt(plain, password)
        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertArrayEquals(plain, BackupCrypto.decrypt(encrypted, password))
    }

    @Test
    fun `jupiter engine runs wrong password rejection`() {
        val encrypted = BackupCrypto.encrypt(plain, password)
        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.decrypt(encrypted, "nope".toCharArray())
        }
        assertFalse(BackupCrypto.isEncrypted(plain))
    }
}

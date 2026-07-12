package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * AppLockManager 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppLockManagerTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppLockManager.init(context)
        AppLockManager.clearPin()
    }

    @Test
    fun `lock is disabled by default`() {
        assertFalse(AppLockManager.isLockEnabled())
    }

    @Test
    fun `set lock enabled`() {
        AppLockManager.setLockEnabled(true)
        assertTrue(AppLockManager.isLockEnabled())
    }

    @Test
    fun `set and verify pin`() {
        AppLockManager.setPin("1234")
        assertTrue(AppLockManager.hasPin())
        assertTrue(AppLockManager.isLockEnabled())
        assertTrue(AppLockManager.verifyPin("1234"))
        assertFalse(AppLockManager.verifyPin("0000"))
    }

    @Test
    fun `clear pin disables lock`() {
        AppLockManager.setPin("1234")
        AppLockManager.clearPin()
        assertFalse(AppLockManager.hasPin())
        assertFalse(AppLockManager.isLockEnabled())
    }

    @Test
    fun `biometric enabled persists`() {
        AppLockManager.setBiometricEnabled(true)
        assertTrue(AppLockManager.isBiometricEnabled())
        AppLockManager.setBiometricEnabled(false)
        assertFalse(AppLockManager.isBiometricEnabled())
    }

    @Test
    fun `unlocked when lock disabled`() {
        assertTrue(AppLockManager.isUnlocked())
    }

    @Test
    fun `should lock after timeout`() {
        AppLockManager.setPin("1234")
        AppLockManager.lock()
        assertTrue(AppLockManager.shouldLock())
    }

    @Test
    fun `recordUnlock prevents immediate lock`() {
        AppLockManager.setPin("1234")
        AppLockManager.recordUnlock()
        assertFalse(AppLockManager.shouldLock())
        assertTrue(AppLockManager.isUnlocked())
    }

    @Test
    fun `isUnlocked returns true when lock disabled`() {
        AppLockManager.clearPin()
        assertTrue(AppLockManager.isUnlocked())
    }

    @Test
    fun `lock clears last unlocked time`() {
        AppLockManager.setPin("1234")
        AppLockManager.recordUnlock()
        AppLockManager.lock()
        assertTrue(AppLockManager.shouldLock())
    }

    @Test
    fun `setPin enables lock and hashPin produces different hashes for different pins`() {
        AppLockManager.setPin("1234")
        AppLockManager.setPin("5678")
        assertTrue(AppLockManager.isLockEnabled())
        assertTrue(AppLockManager.verifyPin("5678"))
        assertFalse(AppLockManager.verifyPin("1234"))
    }

    @Test
    fun `biometric availability can be checked without crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Result depends on Robolectric shadow; just ensure no crash
        AppLockManager.isBiometricAvailable(context)
    }
}

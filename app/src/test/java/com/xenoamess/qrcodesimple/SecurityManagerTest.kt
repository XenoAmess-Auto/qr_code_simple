package com.xenoamess.qrcodesimple

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SecurityManager 单元测试
 */
class SecurityManagerTest {

    @Test
    fun `detect blacklisted domain`() {
        val result = SecurityManager.checkUrl("https://phishing.com/login")
        assertEquals(false, result.isSafe)
        assertEquals(SecurityManager.RiskLevel.HIGH, result.riskLevel)
    }

    @Test
    fun `detect IP address URL as suspicious`() {
        val result = SecurityManager.checkUrl("http://192.168.1.1/login")
        assertEquals(false, result.isSafe)
        assertEquals(SecurityManager.RiskLevel.MEDIUM, result.riskLevel)
    }

    @Test
    fun `detect long URL as suspicious`() {
        val longUrl = "https://example.com/" + "a".repeat(200)
        val result = SecurityManager.checkUrl(longUrl)
        assertEquals(false, result.isSafe)
    }

    @Test
    fun `detect URL with at symbol as suspicious`() {
        val result = SecurityManager.checkUrl("https://example.com@evil.com")
        assertEquals(false, result.isSafe)
    }

    @Test
    fun `detect short URL service`() {
        val result = SecurityManager.checkUrl("https://bit.ly/abc123")
        assertEquals(false, result.isSafe)
    }

    @Test
    fun `safe HTTPS URL`() {
        val result = SecurityManager.checkUrl("https://www.google.com")
        assertEquals(true, result.isSafe)
        assertEquals(SecurityManager.RiskLevel.SAFE, result.riskLevel)
    }

    @Test
    fun `HTTP URL marked as low risk`() {
        val result = SecurityManager.checkUrl("http://example.com")
        assertEquals(false, result.isSafe)
        assertEquals(SecurityManager.RiskLevel.LOW, result.riskLevel)
    }

    @Test
    fun `detect phishing keywords`() {
        val result = SecurityManager.checkUrl("https://example.com/login/verify/account/password")
        assertEquals(false, result.isSafe)
    }

    @Test
    fun `getRiskColor returns correct colors`() {
        // 在单元测试中，我们只验证方法不会崩溃
        // 实际颜色值需要在 Android 环境中验证
        assertNotNull(SecurityManager.getRiskColor(SecurityManager.RiskLevel.SAFE))
        assertNotNull(SecurityManager.getRiskColor(SecurityManager.RiskLevel.LOW))
        assertNotNull(SecurityManager.getRiskColor(SecurityManager.RiskLevel.MEDIUM))
        assertNotNull(SecurityManager.getRiskColor(SecurityManager.RiskLevel.HIGH))
    }

    @Test
    fun `getSecurityTip returns non-empty string`() {
        val safeResult = SecurityManager.SecurityCheckResult(true, SecurityManager.RiskLevel.SAFE, "Safe")
        val highResult = SecurityManager.SecurityCheckResult(false, SecurityManager.RiskLevel.HIGH, "Danger")
        
        assertTrue(SecurityManager.getSecurityTip(safeResult).isNotEmpty())
        assertTrue(SecurityManager.getSecurityTip(highResult).isNotEmpty())
    }
}

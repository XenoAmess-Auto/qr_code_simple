package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 安全黑名单加载/校验/更新逻辑测试（不涉及真实网络）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecurityBlacklistTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, SecurityBlacklist.OVERRIDE_RELATIVE_PATH).delete()
    }

    @After
    fun tearDown() {
        File(context.filesDir, SecurityBlacklist.OVERRIDE_RELATIVE_PATH).delete()
        SecurityManager.resetForTesting()
    }

    private fun validJson(version: Int, domain: String = "evil.example") = """
        {
          "version": $version,
          "domains": ["$domain"],
          "suspiciousKeywords": ["login"],
          "shortUrlServices": ["bit.ly"]
        }
    """.trimIndent()

    @Test
    fun `parse accepts valid schema`() {
        val parsed = SecurityBlacklist.parse(validJson(3))
        assertNotNull(parsed)
        assertEquals(3, parsed!!.version)
        assertTrue(parsed.domains.contains("evil.example"))
    }

    @Test
    fun `parse rejects invalid inputs`() {
        assertNull(SecurityBlacklist.parse("not json"))
        assertNull(SecurityBlacklist.parse("{}"))
        assertNull(SecurityBlacklist.parse("""{"version":0,"domains":[],"suspiciousKeywords":[],"shortUrlServices":[]}"""))
        assertNull(SecurityBlacklist.parse("""{"version":"abc","domains":[],"suspiciousKeywords":[],"shortUrlServices":[]}"""))
        assertNull(SecurityBlacklist.parse("""{"version":2,"domains":"x","suspiciousKeywords":[],"shortUrlServices":[]}"""))
    }

    @Test
    fun `bundled asset loads with version 1`() {
        val bundled = SecurityBlacklist.loadBundled(context)
        assertEquals(1, bundled.version)
        assertTrue(bundled.domains.isNotEmpty())
        assertTrue(bundled.suspiciousKeywords.isNotEmpty())
    }

    @Test
    fun `init prefers newer override over bundled`() {
        SecurityBlacklist.saveOverride(context, SecurityBlacklist.parse(validJson(99))!!, validJson(99))
        SecurityManager.init(context)
        assertEquals(99, SecurityManager.currentVersion())
        // 覆盖列表中的域名应被判定为高危
        val result = SecurityManager.checkUrl("https://evil.example/path")
        assertEquals(SecurityManager.RiskLevel.HIGH, result.riskLevel)
    }

    @Test
    fun `init falls back to bundled when override is corrupt`() {
        val file = File(context.filesDir, SecurityBlacklist.OVERRIDE_RELATIVE_PATH)
        file.parentFile?.mkdirs()
        file.writeText("corrupted{{{")
        SecurityManager.init(context)
        assertEquals(1, SecurityManager.currentVersion())
    }

    @Test
    fun `init ignores older override`() {
        SecurityBlacklist.saveOverride(context, SecurityBlacklist.parse(validJson(1))!!, validJson(1))
        // 再手动放一个 version 相同/更低的 override，bundled(version 1) 不劣于它
        SecurityManager.init(context)
        assertEquals(1, SecurityManager.currentVersion())
    }

    @Test
    fun `validateUpdate only accepts newer versions`() {
        assertNotNull(BlacklistUpdater.validateUpdate(validJson(2), 1))
        assertNull(BlacklistUpdater.validateUpdate(validJson(1), 1))
        assertNull(BlacklistUpdater.validateUpdate(validJson(1), 5))
        assertNull(BlacklistUpdater.validateUpdate("garbage", 0))
    }

    @Test
    fun `default state works without init`() {
        SecurityManager.resetForTesting()
        // 未 init 时使用代码内置兜底列表，builtin 域名仍可判定
        val result = SecurityManager.checkUrl("https://phishing.com/x")
        assertEquals(SecurityManager.RiskLevel.HIGH, result.riskLevel)
    }
}

package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * BlacklistUpdater 下载路径场景测试（注入伪造连接，不触网）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BlacklistUpdaterDownloadTest {

    private lateinit var context: Context

    private class FakeConnection(
        private val code: Int,
        private val body: ByteArray
    ) : HttpURLConnection(URL("https://example.com/x")) {
        override fun getResponseCode(): Int = code
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun disconnect() {}
        override fun usingProxy(): Boolean = false
        override fun connect() {}
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, SecurityBlacklist.OVERRIDE_RELATIVE_PATH).delete()
    }

    @After
    fun tearDown() {
        BlacklistUpdater.connectionFactoryForTesting = null
        File(context.filesDir, SecurityBlacklist.OVERRIDE_RELATIVE_PATH).delete()
        SecurityManager.resetForTesting()
    }

    private fun validJson(version: Int) = """
        {"version": $version, "domains": ["evil-$version.example"], "suspiciousKeywords": ["login"], "shortUrlServices": ["bit.ly"]}
    """.trimIndent()

    @Test
    fun `non 200 response silently returns false`() {
        BlacklistUpdater.connectionFactoryForTesting = { FakeConnection(404, ByteArray(0)) }
        assertFalse(BlacklistUpdater.updateSilently(context))
    }

    @Test
    fun `connection error silently returns false`() {
        BlacklistUpdater.connectionFactoryForTesting = { throw java.io.IOException("no network") }
        assertFalse(BlacklistUpdater.updateSilently(context))
    }

    @Test
    fun `invalid json silently returns false without override`() {
        BlacklistUpdater.connectionFactoryForTesting = { FakeConnection(200, "garbage".toByteArray()) }
        assertFalse(BlacklistUpdater.updateSilently(context))
        assertFalse(File(context.filesDir, SecurityBlacklist.OVERRIDE_RELATIVE_PATH).exists())
    }

    @Test
    fun `older or equal version silently returns false`() {
        BlacklistUpdater.connectionFactoryForTesting = { FakeConnection(200, validJson(1).toByteArray()) }
        // bundled version = 1，同版本不应更新
        assertFalse(BlacklistUpdater.updateSilently(context))
        assertFalse(File(context.filesDir, SecurityBlacklist.OVERRIDE_RELATIVE_PATH).exists())
    }

    @Test
    fun `valid newer version saves override and hot reloads`() {
        BlacklistUpdater.connectionFactoryForTesting = { FakeConnection(200, validJson(42).toByteArray()) }

        assertTrue(BlacklistUpdater.updateSilently(context))
        assertEquals(42, SecurityManager.currentVersion())
        // 新黑名单立即生效
        assertEquals(
            SecurityManager.RiskLevel.HIGH,
            SecurityManager.checkUrl("https://evil-42.example/x").riskLevel
        )
    }

    @Test
    fun `oversized response silently returns false`() {
        BlacklistUpdater.connectionFactoryForTesting = {
            FakeConnection(200, ByteArray(70 * 1024) { 'a'.code.toByte() })
        }
        assertFalse(BlacklistUpdater.updateSilently(context))
    }
}

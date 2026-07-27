package com.xenoamess.qrcodesimple

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * AppUpdateChecker 解析与版本比较测试（注入伪造连接，不触网）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateCheckerTest {

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

    @After
    fun tearDown() {
        AppUpdateChecker.connectionFactoryForTesting = null
    }

    private fun releaseJson(
        tag: String = "v9.9.9",
        body: String = "some changes",
        htmlUrl: String = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/tag/v9.9.9",
        withApk: Boolean = true
    ): String {
        val assets = if (withApk) {
            """[{"name": "app-release.apk", "browser_download_url": "https://example.com/app-release.apk", "size": 123456},
                {"name": "app-release.aab", "browser_download_url": "https://example.com/app-release.aab", "size": 999}]"""
        } else {
            """[{"name": "app-release.aab", "browser_download_url": "https://example.com/app-release.aab", "size": 999}]"""
        }
        return """{"tag_name": "$tag", "body": "$body", "html_url": "$htmlUrl", "assets": $assets}"""
    }

    @Test
    fun `fetch parses release with apk asset`() {
        AppUpdateChecker.connectionFactoryForTesting = { FakeConnection(200, releaseJson().toByteArray()) }
        val info = AppUpdateChecker.fetchLatestRelease()
        assertNotNull(info)
        assertEquals("9.9.9", info!!.version)
        assertEquals("some changes", info.changelog)
        assertEquals("https://example.com/app-release.apk", info.apkUrl)
        assertEquals(123456L, info.apkSizeBytes)
    }

    @Test
    fun `fetch returns null apkUrl when no apk asset`() {
        AppUpdateChecker.connectionFactoryForTesting = {
            FakeConnection(200, releaseJson(withApk = false).toByteArray())
        }
        val info = AppUpdateChecker.fetchLatestRelease()
        assertNotNull(info)
        assertNull(info!!.apkUrl)
    }

    @Test
    fun `non 200 response returns null`() {
        AppUpdateChecker.connectionFactoryForTesting = { FakeConnection(404, ByteArray(0)) }
        assertNull(AppUpdateChecker.fetchLatestRelease())
    }

    @Test
    fun `connection error returns null`() {
        AppUpdateChecker.connectionFactoryForTesting = { throw java.io.IOException("no network") }
        assertNull(AppUpdateChecker.fetchLatestRelease())
    }

    @Test
    fun `invalid json returns null`() {
        AppUpdateChecker.connectionFactoryForTesting = { FakeConnection(200, "garbage".toByteArray()) }
        assertNull(AppUpdateChecker.fetchLatestRelease())
    }

    @Test
    fun `missing tag returns null`() {
        AppUpdateChecker.connectionFactoryForTesting = {
            FakeConnection(200, """{"body": "x", "html_url": "https://example.com"}""".toByteArray())
        }
        assertNull(AppUpdateChecker.fetchLatestRelease())
    }

    @Test
    fun `oversized response returns null`() {
        AppUpdateChecker.connectionFactoryForTesting = {
            FakeConnection(200, ByteArray(2 * 1024 * 1024) { 'a'.code.toByte() })
        }
        assertNull(AppUpdateChecker.fetchLatestRelease())
    }

    @Test
    fun `isNewer compares numeric segments`() {
        assertTrue(AppUpdateChecker.isNewer("0.2.6", "0.2.5"))
        assertTrue(AppUpdateChecker.isNewer("v0.2.6", "0.2.5"))
        assertTrue(AppUpdateChecker.isNewer("1.0.0", "0.9.9"))
        assertTrue(AppUpdateChecker.isNewer("0.10.0", "0.9.9"))
        assertTrue(AppUpdateChecker.isNewer("0.2.5.1", "0.2.5"))
        assertFalse(AppUpdateChecker.isNewer("0.2.5", "0.2.5"))
        assertFalse(AppUpdateChecker.isNewer("v0.2.5", "0.2.5"))
        assertFalse(AppUpdateChecker.isNewer("0.2.4", "0.2.5"))
        assertFalse(AppUpdateChecker.isNewer("0.2", "0.2.5"))
        assertFalse(AppUpdateChecker.isNewer("", "0.2.5"))
        assertFalse(AppUpdateChecker.isNewer("garbage", "0.2.5"))
    }
}

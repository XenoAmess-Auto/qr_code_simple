package com.xenoamess.qrcodesimple

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Metadata requests are fully injected so these tests never leave the JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateCheckerTest {

    private class FakeConnection(
        url: URL,
        private val code: Int,
        private val body: ByteArray
    ) : HttpURLConnection(url) {
        override fun getResponseCode(): Int = code
        override fun getContentLengthLong(): Long = body.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

    @After
    fun tearDown() {
        AppUpdateChecker.connectionFactoryForTesting = null
    }

    @Test
    fun `stable check uses version json and canonical apk before legacy asset`() {
        val metadataUrl =
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/version.json"
        val canonicalUrl =
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/qr-code-simple-0.2.6.apk"
        val legacyUrl =
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/app-release.apk"
        AppUpdateChecker.connectionFactoryForTesting = { url ->
            when (url.toString()) {
                AppUpdateChecker.LATEST_RELEASE_URL -> FakeConnection(
                    url,
                    200,
                    releaseJson(
                        metadataUrl = metadataUrl,
                        canonicalUrl = canonicalUrl,
                        legacyUrl = legacyUrl
                    ).toByteArray()
                )
                metadataUrl -> FakeConnection(
                    URL("https://objects.githubusercontent.com/release-version.json"),
                    200,
                    versionJson().toByteArray()
                )
                else -> error("Unexpected URL: $url")
            }
        }

        val outcome = AppUpdateChecker.checkStable(localVersionCode = 18, localVersionName = "0.2.5")

        assertTrue(outcome is UpdateDecider.CheckOutcome.UpdateAvailable)
        val info = (outcome as UpdateDecider.CheckOutcome.UpdateAvailable).info
        assertEquals(19L, info.versionCode)
        assertEquals("0.2.6", info.versionName)
        assertEquals(canonicalUrl, info.apkUrl)
        assertEquals("version metadata changes", info.changelog)
        assertEquals(4L, info.apkSizeBytes)
    }

    @Test
    fun `missing version json is structured metadata error not up to date`() {
        AppUpdateChecker.connectionFactoryForTesting = { url ->
            FakeConnection(
                url,
                200,
                """{
                    "tag_name":"v0.2.6",
                    "html_url":"https://github.com/XenoAmess-Auto/qr_code_simple/releases/tag/v0.2.6",
                    "assets":[{"name":"qr-code-simple-0.2.6.apk","browser_download_url":"https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/a.apk","size":4}]
                }""".toByteArray()
            )
        }

        val outcome = AppUpdateChecker.checkStable(18, "0.2.5")

        assertEquals(
            UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.RELEASE_METADATA_INVALID),
            outcome
        )
    }

    @Test
    fun `invalid required version metadata is structured error`() {
        val metadataUrl =
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/version.json"
        AppUpdateChecker.connectionFactoryForTesting = { url ->
            when (url.toString()) {
                AppUpdateChecker.LATEST_RELEASE_URL ->
                    FakeConnection(url, 200, releaseJson(metadataUrl = metadataUrl).toByteArray())
                metadataUrl -> FakeConnection(
                    url,
                    200,
                    """{"versionCode":19,"versionName":"0.2.6","apkSize":4}""".toByteArray()
                )
                else -> error("Unexpected URL: $url")
            }
        }

        val outcome = AppUpdateChecker.checkStable(18, "0.2.5")

        assertEquals(
            UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.VERSION_METADATA_INVALID),
            outcome
        )
    }

    @Test
    fun `beta check uses fixed GitHub Pages metadata and apk urls`() {
        AppUpdateChecker.connectionFactoryForTesting = { url ->
            assertEquals(AppUpdateChecker.BETA_VERSION_JSON_URL, url.toString())
            FakeConnection(url, 200, versionJson(versionCode = 20, versionName = "0.2.7").toByteArray())
        }

        val outcome = AppUpdateChecker.checkBeta(18, "0.2.5")

        assertTrue(outcome is UpdateDecider.CheckOutcome.UpdateAvailable)
        val info = (outcome as UpdateDecider.CheckOutcome.UpdateAvailable).info
        assertEquals(UpdateDecider.Channel.BETA, info.channel)
        assertEquals(UpdateDecider.BETA_APK_URL, info.apkUrl)
        assertEquals(20L, info.versionCode)
    }

    @Test
    fun `oversized metadata response is rejected before parsing`() {
        AppUpdateChecker.connectionFactoryForTesting = { url ->
            FakeConnection(url, 200, ByteArray(2 * 1024 * 1024))
        }

        val outcome = AppUpdateChecker.checkBeta(18, "0.2.5")

        assertEquals(
            UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.RESPONSE_TOO_LARGE),
            outcome
        )
    }

    @Test
    fun `stable rejects an untrusted version metadata endpoint before requesting it`() {
        val untrustedMetadataUrl = "https://example.test/version.json"
        var versionMetadataRequested = false
        AppUpdateChecker.connectionFactoryForTesting = { url ->
            if (url.toString() == untrustedMetadataUrl) versionMetadataRequested = true
            FakeConnection(url, 200, releaseJson(metadataUrl = untrustedMetadataUrl).toByteArray())
        }

        val outcome = AppUpdateChecker.checkStable(18, "0.2.5")

        assertEquals(
            UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.HTTP_RESPONSE),
            outcome
        )
        assertTrue(!versionMetadataRequested)
    }

    private fun releaseJson(
        metadataUrl: String,
        canonicalUrl: String =
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/qr-code-simple-0.2.6.apk",
        legacyUrl: String? = null
    ): String {
        val legacy = legacyUrl?.let {
            ",{\"name\":\"app-release.apk\",\"browser_download_url\":\"$it\",\"size\":4}"
        }.orEmpty()
        return """{
            "tag_name":"v0.2.6",
            "body":"release body",
            "html_url":"https://github.com/XenoAmess-Auto/qr_code_simple/releases/tag/v0.2.6",
            "assets":[
                {"name":"qr-code-simple-0.2.6.apk","browser_download_url":"$canonicalUrl","size":4},
                {"name":"version.json","browser_download_url":"$metadataUrl"}$legacy
            ]
        }"""
    }

    private fun versionJson(
        versionCode: Long = 19,
        versionName: String = "0.2.6"
    ): String = """{
        "versionCode":$versionCode,
        "versionName":"$versionName",
        "changelog":"version metadata changes",
        "apkSha256":"${"a".repeat(64)}",
        "apkSize":4
    }"""
}

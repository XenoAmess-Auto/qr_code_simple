package com.xenoamess.qrcodesimple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateDeciderTest {

    @Test
    fun `version metadata requires typed version code semantic name hash and size`() {
        assertNotNull(UpdateDecider.parseVersionMetadata(validMetadata()))
        assertNull(
            UpdateDecider.parseVersionMetadata(
                validMetadata().replace("\"versionCode\":19", "\"versionCode\":\"19\"")
            )
        )
        assertNull(UpdateDecider.parseVersionMetadata(validMetadata().replace("0.2.6", "v0.2.6")))
        assertNull(UpdateDecider.parseVersionMetadata(validMetadata().replace("\"apkSize\":100", "\"apkSize\":0")))
        assertNull(UpdateDecider.parseVersionMetadata(validMetadata().replace("${"a".repeat(64)}", "short")))
    }

    @Test
    fun `stable release accepts exact canonical asset and does not infer version from its name`() {
        val release = UpdateDecider.parseStableRelease(
            """{
                "tag_name":"v0.2.6",
                "body":"release notes",
                "html_url":"https://github.com/XenoAmess-Auto/qr_code_simple/releases/tag/v0.2.6",
                "assets":[
                    {"name":"version.json","browser_download_url":"https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/version.json"},
                    {"name":"qr-code-simple-0.2.6.apk","browser_download_url":"https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/update.apk","size":100}
                ]
            }"""
        )!!
        val metadata = UpdateDecider.parseVersionMetadata(validMetadata())!!

        val info = UpdateDecider.createStableReleaseInfo(release, metadata, localVersionCode = 18)

        assertNotNull(info)
        assertEquals(
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/update.apk",
            info!!.apkUrl
        )
        assertEquals("metadata changelog", info.changelog)
        assertNull(
            UpdateDecider.createStableReleaseInfo(
                release.copy(tagName = "v0.2.5"),
                metadata,
                localVersionCode = 18
            )
        )
    }

    @Test
    fun `legacy apk is accepted only when no canonical candidate exists`() {
        val metadata = UpdateDecider.parseVersionMetadata(validMetadata())!!
        val legacyRelease = rawRelease(canonical = emptyList(), legacy = asset("app-release.apk"))
        assertEquals(
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/app-release.apk",
            UpdateDecider.createStableReleaseInfo(legacyRelease, metadata, 18)!!.apkUrl
        )

        val mismatchedCanonical = rawRelease(
            canonical = listOf(asset("qr-code-simple-0.2.5.apk")),
            legacy = asset("app-release.apk")
        )
        assertNull(UpdateDecider.createStableReleaseInfo(mismatchedCanonical, metadata, 18))
    }

    @Test
    fun `stable channel rejects prerelease and build suffixed metadata`() {
        val prerelease = UpdateDecider.parseVersionMetadata(
            validMetadata().replace("0.2.6", "0.2.7-beta")
        )!!
        val buildSuffix = UpdateDecider.parseVersionMetadata(
            validMetadata().replace("0.2.6", "0.2.7+1")
        )!!

        assertNull(
            UpdateDecider.createStableReleaseInfo(
                rawRelease(canonical = listOf(asset("qr-code-simple-0.2.7-beta.apk")), legacy = null)
                    .copy(tagName = "v0.2.7-beta"),
                prerelease,
                18
            )
        )
        assertNull(
            UpdateDecider.createStableReleaseInfo(
                rawRelease(canonical = listOf(asset("qr-code-simple-0.2.7+1.apk")), legacy = null)
                    .copy(tagName = "v0.2.7+1"),
                buildSuffix,
                18
            )
        )
    }

    @Test
    fun `valid chains are retained while malformed chains safely fall back to full apk`() {
        val metadata = UpdateDecider.parseVersionMetadata(
            """{
                "versionCode":20,
                "versionName":"0.2.7",
                "apkSha256":"${"b".repeat(64)}",
                "apkSize":1000,
                "chains":{
                    "18":{
                        "fromApkSha256":"${"a".repeat(64)}",
                        "totalSize":300,
                        "hops":[
                            {"toVersionCode":19,"url":"https://example.test/18-19.patch","size":100,"patchSha256":"${"c".repeat(64)}","resultSha256":"${"d".repeat(64)}"},
                            {"toVersionCode":20,"url":"https://example.test/19-20.patch","size":200,"patchSha256":"${"e".repeat(64)}","resultSha256":"${"b".repeat(64)}"}
                        ]
                    },
                    "17":{
                        "fromApkSha256":"${"a".repeat(64)}",
                        "totalSize":100,
                        "hops":[{"toVersionCode":20,"url":"https://example.test/bad.patch","size":100,"patchSha256":"bad","resultSha256":"${"b".repeat(64)}"}]
                    }
                }
            }"""
        )!!

        assertEquals(1, metadata.chains.size)
        assertEquals(2, metadata.chains.getValue(18).hops.size)
        assertEquals(20L, metadata.chains.getValue(18).hops.last().toVersionCode)
    }

    @Test
    fun `cross channel chains are surfaced and plannable`() {
        val metadataJson = { patchUrl: String ->
            """{
                "versionCode":20,
                "versionName":"0.2.7",
                "apkSha256":"${"b".repeat(64)}",
                "apkSize":1000,
                "chains":{
                    "18":{
                        "fromApkSha256":"${"a".repeat(64)}",
                        "totalSize":300,
                        "hops":[
                            {"toVersionCode":20,"url":"$patchUrl","size":300,"patchSha256":"${"c".repeat(64)}","resultSha256":"${"b".repeat(64)}"}
                        ]
                    }
                }
            }"""
        }

        // Beta install (code 18) updating to stable: patch hosted on the stable release.
        val stableMetadata = UpdateDecider.parseVersionMetadata(
            metadataJson(
                "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.7/patch-18-to-20.patch"
            )
        )!!
        val stableInfo = UpdateDecider.createStableReleaseInfo(
            rawRelease(
                canonical = listOf(
                    UpdateDecider.ReleaseAsset(
                        name = "qr-code-simple-0.2.7.apk",
                        url = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.7/qr-code-simple-0.2.7.apk",
                        sizeBytes = 1000
                    )
                ),
                legacy = null
            ).copy(tagName = "v0.2.7"),
            stableMetadata,
            localVersionCode = 18
        )!!
        assertNotNull(stableInfo.chain)

        // Stable install (code 18) updating to beta: patch hosted on the beta-archive release.
        val betaMetadata = UpdateDecider.parseVersionMetadata(
            metadataJson(
                "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/beta-archive/patch-beta-18-to-20.patch"
            )
        )!!
        val betaInfo = UpdateDecider.createBetaReleaseInfo(betaMetadata, localVersionCode = 18)
        assertNotNull(betaInfo.chain)

        assertTrue(
            ChainPlanner.choosePlan(stableInfo.chain, "a".repeat(64), stableInfo.apkSizeBytes)
                is ChainPlanner.UpdatePlan.Incremental
        )
        assertTrue(
            ChainPlanner.choosePlan(betaInfo.chain, "a".repeat(64), betaInfo.apkSizeBytes)
                is ChainPlanner.UpdatePlan.Incremental
        )
    }

    @Test
    fun `version code wins and equal codes use major minor patch fallback`() {
        val remote = releaseInfo(versionCode = 18, versionName = "0.3.0")
        assertTrue(
            UpdateDecider.decide(18, "0.2.9", remote) is UpdateDecider.CheckOutcome.UpdateAvailable
        )
        assertEquals(
            UpdateDecider.CheckOutcome.UpToDate,
            UpdateDecider.decide(18, "0.3.0+12", remote)
        )
        assertTrue(
            UpdateDecider.decide(19, "0.1.0", remote) is UpdateDecider.CheckOutcome.UpToDate
        )
        assertTrue(
            UpdateDecider.decide(17, "99.0.0", remote) is UpdateDecider.CheckOutcome.UpdateAvailable
        )
        assertFalse(UpdateDecider.isNewer("0.2.6", "0.2.6+8"))
    }

    @Test
    fun `endpoint trust accepts GitHub release redirects but not arbitrary hosts`() {
        val releaseUrl =
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/version.json"
        val objectStorageUrl = "https://objects.githubusercontent.com/release-version.json"

        assertTrue(
            UpdateDecider.isTrustedInitialEndpoint(
                releaseUrl,
                UpdateDecider.EndpointTrust.GITHUB_RELEASE
            )
        )
        assertFalse(
            UpdateDecider.isTrustedInitialEndpoint(
                objectStorageUrl,
                UpdateDecider.EndpointTrust.GITHUB_RELEASE
            )
        )
        assertTrue(
            UpdateDecider.isTrustedResolvedEndpoint(
                objectStorageUrl,
                UpdateDecider.EndpointTrust.GITHUB_RELEASE
            )
        )
        assertFalse(
            UpdateDecider.isTrustedResolvedEndpoint(
                "https://example.test/update.apk",
                UpdateDecider.EndpointTrust.GITHUB_RELEASE
            )
        )
        assertTrue(
            UpdateDecider.isTrustedInitialEndpoint(
                AppUpdateChecker.BETA_VERSION_JSON_URL,
                UpdateDecider.EndpointTrust.BETA_PAGES
            )
        )
    }

    private fun validMetadata(): String = """{
        "versionCode":19,
        "versionName":"0.2.6",
        "changelog":"metadata changelog",
        "apkSha256":"${"a".repeat(64)}",
        "apkSize":100
    }"""

    private fun rawRelease(
        canonical: List<UpdateDecider.ReleaseAsset>,
        legacy: UpdateDecider.ReleaseAsset?
    ) = UpdateDecider.RawRelease(
        tagName = "v0.2.6",
        changelog = "release notes",
        releasePageUrl = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/tag/v0.2.6",
        versionJsonUrl =
            "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/version.json",
        canonicalApkAssets = canonical,
        legacyApkAsset = legacy
    )

    private fun asset(name: String) = UpdateDecider.ReleaseAsset(
        name = name,
        url = "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/v0.2.6/$name",
        sizeBytes = 100
    )

    private fun releaseInfo(versionCode: Long, versionName: String) = UpdateDecider.ReleaseInfo(
        channel = UpdateDecider.Channel.STABLE,
        versionCode = versionCode,
        versionName = versionName,
        changelog = "",
        apkUrl = "https://example.test/update.apk",
        apkSha256 = "a".repeat(64),
        apkSizeBytes = 100,
        releasePageUrl = "https://example.test/release",
        chain = null
    )
}

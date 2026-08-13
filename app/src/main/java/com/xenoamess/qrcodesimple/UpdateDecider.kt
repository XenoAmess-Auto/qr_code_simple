package com.xenoamess.qrcodesimple

import android.util.Log
import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure parsing and update-decision rules. Network and file operations deliberately live elsewhere.
 */
object UpdateDecider {

    private const val TAG = "UpdateDecider"

    const val VERSION_JSON_ASSET_NAME = "version.json"
    const val LEGACY_APK_ASSET_NAME = "app-release.apk"
    const val CANONICAL_APK_PREFIX = "qr-code-simple-"
    const val BETA_ARCHIVE_TAG = "beta-archive"
    const val BETA_APK_URL_PREFIX =
        "https://github.com/XenoAmess-Auto/qr_code_simple/releases/download/$BETA_ARCHIVE_TAG/beta-"

    private const val GITHUB_API_HOST = "api.github.com"
    private const val GITHUB_RELEASES_HOST = "github.com"
    private const val BETA_PAGES_HOST = "xenoamess-auto.github.io"
    private const val GITHUB_REPOSITORY_PATH = "/XenoAmess-Auto/qr_code_simple"
    private val GITHUB_RELEASE_OBJECT_STORAGE_HOSTS = setOf(
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    // Metadata must never authorize an unbounded artifact download.
    const val MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L

    private const val MAX_CHANGELOG_CHARS = 32 * 1024
    private const val MAX_PATCH_HOPS = 16
    private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
    private val STABLE_VERSION_PATTERN = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$")
    private val VERSION_PATTERN = Regex(
        "^([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$"
    )

    enum class Channel {
        STABLE,
        BETA
    }

    /** Trust policy for the URL before connection and after HTTPS redirects resolve. */
    enum class EndpointTrust {
        GITHUB_API,
        GITHUB_RELEASE,
        BETA_PAGES
    }

    enum class UpdateCheckError {
        NETWORK,
        HTTP_RESPONSE,
        RESPONSE_TOO_LARGE,
        RESPONSE_ENCODING,
        RELEASE_METADATA_INVALID,
        VERSION_METADATA_INVALID,
        STABLE_ASSET_INVALID,
        LOCAL_VERSION_INVALID
    }

    data class ReleaseAsset(
        val name: String,
        val url: String,
        val sizeBytes: Long
    )

    data class RawRelease(
        val tagName: String,
        val changelog: String,
        val releasePageUrl: String,
        val versionJsonUrl: String,
        val canonicalApkAssets: List<ReleaseAsset>,
        val legacyApkAsset: ReleaseAsset?
    )

    data class SemanticVersion(
        val major: Long,
        val minor: Long,
        val patch: Long
    ) {
        companion object {
            fun parse(value: String): SemanticVersion? {
                val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
                return SemanticVersion(
                    major = match.groupValues[1].toLongOrNull() ?: return null,
                    minor = match.groupValues[2].toLongOrNull() ?: return null,
                    patch = match.groupValues[3].toLongOrNull() ?: return null
                )
            }
        }
    }

    data class PatchHop(
        val toVersionCode: Long,
        val url: String,
        val sizeBytes: Long,
        val patchSha256: String,
        val resultSha256: String
    )

    data class UpdateChain(
        val fromApkSha256: String,
        val totalSizeBytes: Long,
        val hops: List<PatchHop>
    )

    data class VersionMetadata(
        val versionCode: Long,
        val versionName: String,
        val changelog: String?,
        val apkSha256: String,
        val apkSizeBytes: Long,
        val chains: Map<Long, UpdateChain>
    )

    data class ReleaseInfo(
        val channel: Channel,
        val versionCode: Long,
        val versionName: String,
        val changelog: String,
        val apkUrl: String,
        val apkSha256: String,
        val apkSizeBytes: Long,
        val releasePageUrl: String?,
        val chain: UpdateChain?
    )

    sealed interface CheckOutcome {
        data class UpdateAvailable(val info: ReleaseInfo) : CheckOutcome
        data object UpToDate : CheckOutcome
        data class Error(val error: UpdateCheckError) : CheckOutcome
    }

    /**
     * Parses the GitHub Releases API response without deriving any version from an asset name.
     * The version is only accepted later from version.json.
     */
    fun parseStableRelease(json: String): RawRelease? {
        return try {
            val root = JSONObject(json)
            val tagName = requiredString(root, "tag_name") ?: return null
            val releasePageUrl = requiredHttpsUrl(root, "html_url") ?: return null
            val changelog = optionalText(root, "body") ?: ""
            val assets = root.opt("assets") as? JSONArray ?: return null

            var versionJsonUrl: String? = null
            var legacyApkAsset: ReleaseAsset? = null
            val canonicalApkAssets = mutableListOf<ReleaseAsset>()

            for (index in 0 until assets.length()) {
                val assetObject = assets.opt(index) as? JSONObject ?: continue
                val name = requiredString(assetObject, "name") ?: continue
                when {
                    name == VERSION_JSON_ASSET_NAME -> {
                        if (versionJsonUrl != null) return null
                        versionJsonUrl = requiredHttpsUrl(assetObject, "browser_download_url")
                            ?: return null
                    }
                    isCanonicalApkCandidate(name) -> {
                        parseReleaseAsset(assetObject)?.let(canonicalApkAssets::add) ?: return null
                    }
                    name == LEGACY_APK_ASSET_NAME -> {
                        if (legacyApkAsset != null) return null
                        legacyApkAsset = parseReleaseAsset(assetObject) ?: return null
                    }
                }
            }

            RawRelease(
                tagName = tagName,
                changelog = changelog,
                releasePageUrl = releasePageUrl,
                versionJsonUrl = versionJsonUrl ?: return null,
                canonicalApkAssets = canonicalApkAssets,
                legacyApkAsset = legacyApkAsset
            )
        } catch (e: Exception) {
            Log.w(TAG, "metadata/endpoint rejected: ${e.message}")
            null
        }
    }

    /**
     * Parses the release version metadata. The APK hash and byte size are mandatory so the client
     * never installs an artifact that it cannot verify.
     */
    fun parseVersionMetadata(json: String): VersionMetadata? {
        return try {
            val root = JSONObject(json)
            val versionCode = requiredPositiveLong(root, "versionCode") ?: return null
            val versionName = requiredString(root, "versionName") ?: return null
            if (SemanticVersion.parse(versionName) == null) return null
            val apkSha256 = requiredSha256(root, "apkSha256") ?: return null
            val apkSizeBytes = requiredArtifactSize(root, "apkSize") ?: return null
            val changelog = optionalText(root, "changelog")?.takeIf { it.isNotBlank() }

            val chains = mutableMapOf<Long, UpdateChain>()
            if (root.has("chains") && !root.isNull("chains")) {
                val chainsObject = root.opt("chains") as? JSONObject ?: return null
                val keys = chainsObject.keys()
                while (keys.hasNext()) {
                    val fromVersionCode = keys.next().toLongOrNull()?.takeIf { it > 0 } ?: continue
                    val chainObject = chainsObject.opt(fromVersionCode.toString()) as? JSONObject ?: continue
                    parseChain(
                        fromVersionCode = fromVersionCode,
                        targetVersionCode = versionCode,
                        targetApkSha256 = apkSha256,
                        chainObject = chainObject
                    )?.let { chains[fromVersionCode] = it }
                }
            }

            VersionMetadata(
                versionCode = versionCode,
                versionName = versionName,
                changelog = changelog,
                apkSha256 = apkSha256,
                apkSizeBytes = apkSizeBytes,
                chains = chains
            )
        } catch (e: Exception) {
            Log.w(TAG, "metadata/endpoint rejected: ${e.message}")
            null
        }
    }

    /**
     * Combines independently parsed stable-release metadata. A canonical asset is always preferred;
     * the legacy app-release.apk is accepted only when no canonical APK asset is present.
     */
    fun createStableReleaseInfo(
        release: RawRelease,
        metadata: VersionMetadata,
        localVersionCode: Long
    ): ReleaseInfo? {
        // Stable releases must never silently admit prerelease/build-suffixed metadata.
        if (!STABLE_VERSION_PATTERN.matches(metadata.versionName)) return null
        if (release.tagName != "v${metadata.versionName}") return null
        if (!isTrustedInitialEndpoint(release.releasePageUrl, EndpointTrust.GITHUB_RELEASE) ||
            !isTrustedInitialEndpoint(release.versionJsonUrl, EndpointTrust.GITHUB_RELEASE)
        ) {
            return null
        }

        val expectedCanonicalName = canonicalApkFileName(metadata.versionName) ?: return null
        val matchingCanonicalAssets = release.canonicalApkAssets.filter { it.name == expectedCanonicalName }
        if (matchingCanonicalAssets.size > 1) return null
        val apkAsset = matchingCanonicalAssets.singleOrNull() ?: run {
            // A mismatched canonical-looking asset is not a safe reason to fall back to legacy.
            if (release.canonicalApkAssets.isNotEmpty()) return null
            release.legacyApkAsset ?: return null
        }
        if (apkAsset.sizeBytes != metadata.apkSizeBytes) return null
        if (!isTrustedInitialEndpoint(apkAsset.url, EndpointTrust.GITHUB_RELEASE)) return null

        return ReleaseInfo(
            channel = Channel.STABLE,
            versionCode = metadata.versionCode,
            versionName = metadata.versionName,
            changelog = metadata.changelog ?: release.changelog,
            apkUrl = apkAsset.url,
            apkSha256 = metadata.apkSha256,
            apkSizeBytes = metadata.apkSizeBytes,
            releasePageUrl = release.releasePageUrl,
            chain = trustedChainOrNull(metadata.chains[localVersionCode])
        )
    }

    fun createBetaReleaseInfo(metadata: VersionMetadata, localVersionCode: Long): ReleaseInfo {
        return ReleaseInfo(
            channel = Channel.BETA,
            versionCode = metadata.versionCode,
            versionName = metadata.versionName,
            changelog = metadata.changelog.orEmpty(),
            apkUrl = "$BETA_APK_URL_PREFIX${metadata.versionCode}.apk",
            apkSha256 = metadata.apkSha256,
            apkSizeBytes = metadata.apkSizeBytes,
            releasePageUrl = null,
            chain = trustedChainOrNull(metadata.chains[localVersionCode])
        )
    }

    /** VersionCode is authoritative; semantic major/minor/patch breaks an equal-code tie. */
    fun decide(
        localVersionCode: Long,
        localVersionName: String,
        remote: ReleaseInfo
    ): CheckOutcome {
        if (localVersionCode <= 0) return CheckOutcome.Error(UpdateCheckError.LOCAL_VERSION_INVALID)
        if (remote.versionCode > localVersionCode) return CheckOutcome.UpdateAvailable(remote)
        if (remote.versionCode < localVersionCode) return CheckOutcome.UpToDate

        val localVersion = SemanticVersion.parse(localVersionName) ?: return CheckOutcome.UpToDate
        val remoteVersion = SemanticVersion.parse(remote.versionName) ?: return CheckOutcome.UpToDate
        return if (isRemoteNewer(remoteVersion, localVersion)) {
            CheckOutcome.UpdateAvailable(remote)
        } else {
            CheckOutcome.UpToDate
        }
    }

    fun isRemoteNewer(remote: SemanticVersion, local: SemanticVersion): Boolean {
        if (remote.major != local.major) return remote.major > local.major
        if (remote.minor != local.minor) return remote.minor > local.minor
        return remote.patch > local.patch
    }

    /** Retained as a convenience for callers that only have semantic version strings. */
    fun isNewer(remote: String, local: String): Boolean {
        val remoteVersion = SemanticVersion.parse(remote.removePrefix("v")) ?: return false
        val localVersion = SemanticVersion.parse(local.removePrefix("v")) ?: return false
        return isRemoteNewer(remoteVersion, localVersion)
    }

    fun canonicalApkFileName(versionName: String): String? {
        return if (SemanticVersion.parse(versionName) != null) {
            "$CANONICAL_APK_PREFIX$versionName.apk"
        } else {
            null
        }
    }

    fun isSafeHttpsUrl(value: String): Boolean = httpsUri(value) != null

    fun isTrustedInitialEndpoint(value: String, trust: EndpointTrust): Boolean {
        val uri = httpsUri(value) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return when (trust) {
            EndpointTrust.GITHUB_API -> host == GITHUB_API_HOST &&
                uri.path == "/repos$GITHUB_REPOSITORY_PATH/releases/latest"
            EndpointTrust.GITHUB_RELEASE -> host == GITHUB_RELEASES_HOST &&
                isGitHubReleasePath(uri.path)
            EndpointTrust.BETA_PAGES -> host == BETA_PAGES_HOST && isBetaPath(uri.path)
        }
    }

    fun isTrustedResolvedEndpoint(value: String, trust: EndpointTrust): Boolean {
        val uri = httpsUri(value) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return when (trust) {
            EndpointTrust.GITHUB_API -> host == GITHUB_API_HOST &&
                uri.path == "/repos$GITHUB_REPOSITORY_PATH/releases/latest"
            EndpointTrust.GITHUB_RELEASE -> (host == GITHUB_RELEASES_HOST &&
                isGitHubReleasePath(uri.path)) ||
                host in GITHUB_RELEASE_OBJECT_STORAGE_HOSTS
            EndpointTrust.BETA_PAGES -> host == BETA_PAGES_HOST && isBetaPath(uri.path)
        }
    }

    private fun trustedChainOrNull(chain: UpdateChain?): UpdateChain? {
        return chain?.takeIf { candidate ->
            candidate.hops.all { hop ->
                isTrustedInitialEndpoint(hop.url, EndpointTrust.GITHUB_RELEASE)
            }
        }
    }

    private fun isGitHubReleasePath(path: String?): Boolean {
        return path == "$GITHUB_REPOSITORY_PATH/releases/latest" ||
            path?.startsWith("$GITHUB_REPOSITORY_PATH/releases/tag/") == true ||
            path?.startsWith("$GITHUB_REPOSITORY_PATH/releases/download/") == true
    }

    private fun isBetaPath(path: String?): Boolean {
        // Pages 只承载 beta 元数据；APK 本体由 beta-archive GitHub Release 提供
        return path == "/qr_code_simple/beta/version.json"
    }

    private fun httpsUri(value: String): URI? {
        return try {
            val uri = URI(value)
            uri.takeIf {
                !uri.host.isNullOrBlank() &&
                    uri.scheme.equals("https", ignoreCase = true) &&
                    uri.userInfo == null
            }
        } catch (e: Exception) {
            Log.w(TAG, "metadata/endpoint rejected: ${e.message}")
            null
        }
    }

    fun isSha256(value: String): Boolean = SHA256_PATTERN.matches(value)

    private fun parseChain(
        fromVersionCode: Long,
        targetVersionCode: Long,
        targetApkSha256: String,
        chainObject: JSONObject
    ): UpdateChain? {
        val fromApkSha256 = requiredSha256(chainObject, "fromApkSha256") ?: return null
        val totalSizeBytes = requiredArtifactSize(chainObject, "totalSize") ?: return null
        val hopsArray = chainObject.opt("hops") as? JSONArray ?: return null
        if (hopsArray.length() !in 1..MAX_PATCH_HOPS) return null

        val hops = mutableListOf<PatchHop>()
        var previousVersionCode = fromVersionCode
        var computedSize = 0L
        for (index in 0 until hopsArray.length()) {
            val hopObject = hopsArray.opt(index) as? JSONObject ?: return null
            val toVersionCode = requiredPositiveLong(hopObject, "toVersionCode") ?: return null
            val url = requiredHttpsUrl(hopObject, "url") ?: return null
            val sizeBytes = requiredArtifactSize(hopObject, "size") ?: return null
            val patchSha256 = requiredSha256(hopObject, "patchSha256") ?: return null
            val resultSha256 = requiredSha256(hopObject, "resultSha256") ?: return null
            if (toVersionCode <= previousVersionCode || toVersionCode > targetVersionCode) return null
            if (computedSize > Long.MAX_VALUE - sizeBytes) return null
            computedSize += sizeBytes
            hops += PatchHop(toVersionCode, url, sizeBytes, patchSha256, resultSha256)
            previousVersionCode = toVersionCode
        }

        if (previousVersionCode != targetVersionCode || computedSize != totalSizeBytes) return null
        if (!hops.last().resultSha256.equals(targetApkSha256, ignoreCase = true)) return null
        return UpdateChain(fromApkSha256, totalSizeBytes, hops)
    }

    private fun parseReleaseAsset(assetObject: JSONObject): ReleaseAsset? {
        val name = requiredString(assetObject, "name") ?: return null
        val url = requiredHttpsUrl(assetObject, "browser_download_url") ?: return null
        val sizeBytes = requiredArtifactSize(assetObject, "size") ?: return null
        return ReleaseAsset(name, url, sizeBytes)
    }

    private fun isCanonicalApkCandidate(name: String): Boolean {
        return name.startsWith(CANONICAL_APK_PREFIX) && name.endsWith(".apk")
    }

    private fun requiredString(objectValue: JSONObject, key: String): String? {
        val value = objectValue.opt(key) as? String ?: return null
        return value.trim().takeIf { it.isNotEmpty() }
    }

    private fun optionalText(objectValue: JSONObject, key: String): String? {
        if (!objectValue.has(key) || objectValue.isNull(key)) return null
        val value = objectValue.opt(key) as? String ?: return null
        return value.takeIf { it.length <= MAX_CHANGELOG_CHARS }
    }

    private fun requiredHttpsUrl(objectValue: JSONObject, key: String): String? {
        val value = requiredString(objectValue, key) ?: return null
        return value.takeIf(::isSafeHttpsUrl)
    }

    private fun requiredPositiveLong(objectValue: JSONObject, key: String): Long? {
        val value = objectValue.opt(key) ?: return null
        val parsed = when (value) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            else -> return null
        }
        return parsed.takeIf { it > 0 }
    }

    private fun requiredArtifactSize(objectValue: JSONObject, key: String): Long? {
        return requiredPositiveLong(objectValue, key)?.takeIf { it <= MAX_ARTIFACT_BYTES }
    }

    private fun requiredSha256(objectValue: JSONObject, key: String): String? {
        val value = requiredString(objectValue, key) ?: return null
        return value.lowercase(Locale.ROOT).takeIf(::isSha256)
    }
}

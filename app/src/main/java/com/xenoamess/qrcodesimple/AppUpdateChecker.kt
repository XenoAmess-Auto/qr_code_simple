package com.xenoamess.qrcodesimple

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Fetches bounded update metadata and delegates all parsing/decisions to [UpdateDecider]. */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_METADATA_BYTES = 1024 * 1024

    const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/XenoAmess-Auto/qr_code_simple/releases/latest"
    const val BETA_VERSION_JSON_URL =
        "https://xenoamess-auto.github.io/qr_code_simple/beta/version.json"

    /** Tests replace this factory for every metadata request; production uses URL.openConnection(). */
    internal var connectionFactoryForTesting: ((URL) -> HttpURLConnection)? = null

    fun checkStable(
        localVersionCode: Long,
        localVersionName: String
    ): UpdateDecider.CheckOutcome {
        if (localVersionCode <= 0) {
            return UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.LOCAL_VERSION_INVALID)
        }
        val releaseText = when (val result = fetchMetadata(
            LATEST_RELEASE_URL,
            UpdateDecider.EndpointTrust.GITHUB_API
        )) {
            is MetadataResult.Success -> result.text
            is MetadataResult.Failure -> return UpdateDecider.CheckOutcome.Error(result.error)
        }
        val release = UpdateDecider.parseStableRelease(releaseText)
            ?: return UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.RELEASE_METADATA_INVALID)
        val versionText = when (val result = fetchMetadata(
            release.versionJsonUrl,
            UpdateDecider.EndpointTrust.GITHUB_RELEASE
        )) {
            is MetadataResult.Success -> result.text
            is MetadataResult.Failure -> return UpdateDecider.CheckOutcome.Error(result.error)
        }
        val metadata = UpdateDecider.parseVersionMetadata(versionText)
            ?: return UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.VERSION_METADATA_INVALID)
        val remote = UpdateDecider.createStableReleaseInfo(release, metadata, localVersionCode)
            ?: return UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.STABLE_ASSET_INVALID)
        return UpdateDecider.decide(localVersionCode, localVersionName, remote)
    }

    /** Beta is deliberately a manual-only channel in the manager/UI, not a different trust model. */
    fun checkBeta(
        localVersionCode: Long,
        localVersionName: String
    ): UpdateDecider.CheckOutcome {
        if (localVersionCode <= 0) {
            return UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.LOCAL_VERSION_INVALID)
        }
        val versionText = when (val result = fetchMetadata(
            BETA_VERSION_JSON_URL,
            UpdateDecider.EndpointTrust.BETA_PAGES
        )) {
            is MetadataResult.Success -> result.text
            is MetadataResult.Failure -> return UpdateDecider.CheckOutcome.Error(result.error)
        }
        val metadata = UpdateDecider.parseVersionMetadata(versionText)
            ?: return UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.VERSION_METADATA_INVALID)
        return UpdateDecider.decide(
            localVersionCode,
            localVersionName,
            UpdateDecider.createBetaReleaseInfo(metadata, localVersionCode)
        )
    }

    fun isNewer(remote: String, local: String): Boolean = UpdateDecider.isNewer(remote, local)

    private sealed interface MetadataResult {
        data class Success(val text: String) : MetadataResult
        data class Failure(val error: UpdateDecider.UpdateCheckError) : MetadataResult
    }

    private fun fetchMetadata(
        url: String,
        endpointTrust: UpdateDecider.EndpointTrust
    ): MetadataResult {
        if (!UpdateDecider.isTrustedInitialEndpoint(url, endpointTrust)) {
            return MetadataResult.Failure(UpdateDecider.UpdateCheckError.HTTP_RESPONSE)
        }
        // 镜像轮询：github.com 等可代理主机的 URL 先走公共加速镜像，最后回退直连。
        // 镜像候选跳过 resolved-endpoint 主机白名单（镜像跳转到自己的 CDN），
        // 安全性由下游 SHA-256/大小/签名校验链兜底；直连候选保持完整端点校验。
        val candidates = UpdateMirrors.candidates(url)
        var lastResult: MetadataResult = MetadataResult.Failure(UpdateDecider.UpdateCheckError.NETWORK)
        candidates.forEachIndexed { index, candidate ->
            val verifyResolvedEndpoint = index == candidates.lastIndex
            var result = fetchMetadataOnce(candidate, endpointTrust, verifyResolvedEndpoint)
            var attempt = 1
            while (result is MetadataResult.Failure &&
                result.error == UpdateDecider.UpdateCheckError.NETWORK &&
                attempt < NetworkUtils.DEFAULT_MAX_ATTEMPTS
            ) {
                NetworkUtils.sleepBackoff(attempt - 1)
                attempt++
                result = fetchMetadataOnce(candidate, endpointTrust, verifyResolvedEndpoint)
            }
            lastResult = result
            if (result is MetadataResult.Success) return result
        }
        return lastResult
    }

    private fun fetchMetadataOnce(
        url: String,
        endpointTrust: UpdateDecider.EndpointTrust,
        verifyResolvedEndpoint: Boolean = true
    ): MetadataResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (connectionFactoryForTesting?.invoke(URL(url))
                ?: URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "qr_code_simple/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                (verifyResolvedEndpoint &&
                    !UpdateDecider.isTrustedResolvedEndpoint(connection.url.toString(), endpointTrust))
            ) {
                return MetadataResult.Failure(UpdateDecider.UpdateCheckError.HTTP_RESPONSE)
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_METADATA_BYTES) {
                return MetadataResult.Failure(UpdateDecider.UpdateCheckError.RESPONSE_TOO_LARGE)
            }
            val bytes = connection.inputStream.use { input ->
                readCapped(input, MAX_METADATA_BYTES)
            } ?: return MetadataResult.Failure(UpdateDecider.UpdateCheckError.RESPONSE_TOO_LARGE)
            val text = decodeUtf8(bytes)
                ?: return MetadataResult.Failure(UpdateDecider.UpdateCheckError.RESPONSE_ENCODING)
            MetadataResult.Success(text)
        } catch (e: Exception) {
            Log.w(TAG, "Update metadata request failed", e)
            MetadataResult.Failure(UpdateDecider.UpdateCheckError.NETWORK)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }
    }
}

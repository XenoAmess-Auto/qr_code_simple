package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Coordinates checking, verified download, unknown-source permission, and installation. */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 10_000
    private const val DOWNLOAD_READ_TIMEOUT_MS = 30_000

    /** Tests can replace checker results without network access. */
    internal var checkerForTesting: ((
        channel: UpdateDecider.Channel,
        localVersionCode: Long,
        localVersionName: String
    ) -> UpdateDecider.CheckOutcome)? = null

    /** Tests can supply deterministic artifact streams while exercising size/hash/file handling. */
    internal var downloadConnectionFactoryForTesting: ((URL) -> HttpURLConnection)? = null

    /** User may grant unknown-source permission outside the app, then return to continue. */
    private var pendingInstall: UpdateDecider.ReleaseInfo? = null
    private var progressDialog: AlertDialog? = null

    fun checkManually(activity: Activity) {
        check(activity, UpdateDecider.Channel.STABLE, manual = true)
    }

    /** Beta is intentionally manual-only and is only wired from About. */
    fun checkBetaUpdate(activity: Activity) {
        check(activity, UpdateDecider.Channel.BETA, manual = true)
    }

    /** Stable-only automatic check with the existing 24-hour preference throttle. */
    fun maybeAutoCheck(activity: Activity) {
        if (!QRCodeApp.isAppUpdateAutoCheckEnabled(activity)) return
        if (!QRCodeApp.tryMarkAppUpdateChecked(activity)) return
        val localVersion = installedVersion(activity) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val outcome = checkForChannel(
                UpdateDecider.Channel.STABLE,
                localVersion.versionCode,
                localVersion.versionName
            )
            if (outcome !is UpdateDecider.CheckOutcome.UpdateAvailable) return@launch
            withContext(Dispatchers.Main) {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showUpdateDialog(activity, outcome.info)
                }
            }
        }
    }

    /** Called from the host Activity's onResume after the unknown-source settings screen. */
    fun onHostResume(activity: Activity) {
        val pending = pendingInstall ?: return
        if (!canInstallPackages(activity)) return
        pendingInstall = null
        downloadAndInstall(activity, pending)
    }

    private fun check(activity: Activity, channel: UpdateDecider.Channel, manual: Boolean) {
        val localVersion = installedVersion(activity)
        if (localVersion == null) {
            if (manual) showCheckFailure(activity, channel)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val outcome = checkForChannel(channel, localVersion.versionCode, localVersion.versionName)
            withContext(Dispatchers.Main) {
                if (activity.isFinishing || activity.isDestroyed) return@withContext
                when (outcome) {
                    is UpdateDecider.CheckOutcome.UpdateAvailable -> showUpdateDialog(activity, outcome.info)
                    UpdateDecider.CheckOutcome.UpToDate -> if (manual) {
                        Toast.makeText(
                            activity,
                            if (channel == UpdateDecider.Channel.BETA) {
                                R.string.beta_update_already_latest
                            } else {
                                R.string.update_already_latest
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is UpdateDecider.CheckOutcome.Error -> {
                        Log.w(TAG, "Update check failed: ${outcome.error}")
                        if (manual) showCheckFailure(activity, channel)
                    }
                }
            }
        }
    }

    private fun checkForChannel(
        channel: UpdateDecider.Channel,
        localVersionCode: Long,
        localVersionName: String
    ): UpdateDecider.CheckOutcome {
        return try {
            checkerForTesting?.invoke(channel, localVersionCode, localVersionName) ?: when (channel) {
                UpdateDecider.Channel.STABLE ->
                    AppUpdateChecker.checkStable(localVersionCode, localVersionName)
                UpdateDecider.Channel.BETA ->
                    AppUpdateChecker.checkBeta(localVersionCode, localVersionName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update checker threw", e)
            UpdateDecider.CheckOutcome.Error(UpdateDecider.UpdateCheckError.NETWORK)
        }
    }

    private fun showCheckFailure(activity: Activity, channel: UpdateDecider.Channel) {
        Toast.makeText(
            activity,
            if (channel == UpdateDecider.Channel.BETA) {
                R.string.beta_update_check_failed
            } else {
                R.string.update_check_failed
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showUpdateDialog(activity: Activity, info: UpdateDecider.ReleaseInfo) {
        val message = info.changelog.trim().take(500)
        val builder = AlertDialog.Builder(activity)
            .setTitle(
                activity.getString(
                    if (info.channel == UpdateDecider.Channel.BETA) {
                        R.string.beta_update_available_title
                    } else {
                        R.string.update_available_title
                    },
                    info.versionName
                )
            )
            .setMessage(message.ifEmpty { null })
            .setPositiveButton(R.string.update_now) { _, _ -> startInstall(activity, info) }
            .setNegativeButton(R.string.update_later, null)
        if (info.channel == UpdateDecider.Channel.STABLE && info.releasePageUrl != null) {
            builder.setNeutralButton(R.string.update_view_release_page) { _, _ ->
                openUrl(activity, info.releasePageUrl)
            }
        }
        builder.show()
    }

    private fun startInstall(activity: Activity, info: UpdateDecider.ReleaseInfo) {
        if (!canInstallPackages(activity)) {
            pendingInstall = info
            requestInstallPermission(activity)
            return
        }
        downloadAndInstall(activity, info)
    }

    private fun canInstallPackages(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    private fun requestInstallPermission(activity: Activity) {
        Toast.makeText(activity, R.string.update_install_permission_needed, Toast.LENGTH_LONG).show()
        try {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open unknown-sources settings", e)
            pendingInstall?.let { showInstallFailure(activity, it) }
            pendingInstall = null
        }
    }

    private fun downloadAndInstall(activity: Activity, info: UpdateDecider.ReleaseInfo) {
        showProgressDialog(activity, info)
        CoroutineScope(Dispatchers.IO).launch {
            val file = acquireUpdateApk(
                activity = activity,
                info = info,
                onProgress = { percent, incremental ->
                    activity.runOnUiThread {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            updateProgressDialog(percent, incremental)
                        }
                    }
                },
                onIncrementalFallback = {
                    activity.runOnUiThread {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showIncrementalFallback()
                        }
                    }
                }
            )
            val archiveVerified = file?.let {
                ApkArchiveVerifier.verify(activity, it, info.versionCode)
            } == true
            withContext(Dispatchers.Main) {
                dismissProgressDialog()
                if (activity.isFinishing || activity.isDestroyed) {
                    file?.delete()
                    return@withContext
                }
                if (file != null && archiveVerified && installApk(activity, file)) {
                    return@withContext
                }
                file?.delete()
                showInstallFailure(activity, info)
            }
        }
    }

    private suspend fun acquireUpdateApk(
        activity: Activity,
        info: UpdateDecider.ReleaseInfo,
        onProgress: (percent: Int, incremental: Boolean) -> Unit,
        onIncrementalFallback: () -> Unit
    ): File? {
        val outputFile = updateOutputFile(activity, info) ?: return null
        val installedApk = ApkPatcher.installedApkFile(activity)
        val localApkSha256 = if (installedApk != null && info.chain != null) {
            runCatching { ApkPatcher.sha256(installedApk) }.getOrNull()
        } else {
            null
        }
        val plan = ChainPlanner.choosePlan(
            chain = info.chain,
            localApkSha256 = localApkSha256,
            remoteApkSizeBytes = info.apkSizeBytes
        )
        if (plan is ChainPlanner.UpdatePlan.Incremental) {
            val updater = IncrementalUpdater(activity).apply {
                downloader = { url, destination, hop, progress ->
                    downloadVerifiedArtifact(
                        url = url,
                        endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
                        destination = destination,
                        expectedSizeBytes = hop.sizeBytes,
                        expectedSha256 = hop.patchSha256,
                        onProgress = progress
                    ) != null
                }
            }
            val incrementalResult = updater.executeChain(
                chain = plan.chain,
                outputFile = outputFile,
                expectedFinalSha256 = info.apkSha256,
                targetApkSizeBytes = info.apkSizeBytes,
                onProgress = { onProgress(it, true) }
            )
            if (incrementalResult != null) return incrementalResult
            onIncrementalFallback()
        }
        return downloadVerifiedArtifact(
            url = info.apkUrl,
            // Stable 与 Beta 的 APK 均来自 GitHub Releases（beta-archive）；Pages 仅存元数据。
            endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
            destination = outputFile,
            expectedSizeBytes = info.apkSizeBytes,
            expectedSha256 = info.apkSha256,
            onProgress = { onProgress(it, false) }
        )
    }

    /**
     * Downloads to destination.part, verifies exact byte count and SHA-256, then publishes it.
     * Existing complete files are left untouched until a new verified artifact is ready.
     */
    internal fun downloadVerifiedArtifact(
        url: String,
        endpointTrust: UpdateDecider.EndpointTrust,
        destination: File,
        expectedSizeBytes: Long,
        expectedSha256: String,
        onProgress: (Int) -> Unit
    ): File? {
        if (!UpdateDecider.isTrustedInitialEndpoint(url, endpointTrust) ||
            expectedSizeBytes !in 1..UpdateDecider.MAX_ARTIFACT_BYTES ||
            !UpdateDecider.isSha256(expectedSha256)
        ) {
            return null
        }
        val directory = destination.parentFile ?: return null
        val partFile = File(directory, "${destination.name}.part")
        var connection: HttpURLConnection? = null
        var completed = false
        var replacedDestination = false
        try {
            if (!directory.mkdirs() && !directory.isDirectory) return null
            partFile.delete()
            connection = (downloadConnectionFactoryForTesting?.invoke(URL(url))
                ?: URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
                readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "qr_code_simple/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                !UpdateDecider.isTrustedResolvedEndpoint(connection.url.toString(), endpointTrust)
            ) {
                return null
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > UpdateDecider.MAX_ARTIFACT_BYTES ||
                (contentLength >= 0 && contentLength != expectedSizeBytes)
            ) {
                return null
            }

            var downloadedBytes = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloadedBytes += read
                        if (downloadedBytes > expectedSizeBytes ||
                            downloadedBytes > UpdateDecider.MAX_ARTIFACT_BYTES
                        ) {
                            return null
                        }
                        output.write(buffer, 0, read)
                        val percent = (downloadedBytes * 100 / expectedSizeBytes).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent.coerceIn(0, 100))
                        }
                    }
                }
            }
            if (downloadedBytes != expectedSizeBytes ||
                !ApkPatcher.sha256(partFile).equals(expectedSha256, ignoreCase = true)
            ) {
                return null
            }
            if (destination.exists() && !destination.delete()) return null
            replacedDestination = true
            if (!partFile.renameTo(destination)) {
                partFile.copyTo(destination, overwrite = true)
                partFile.delete()
            }
            completed = destination.isFile
            return destination.takeIf { completed }
        } catch (e: Exception) {
            Log.w(TAG, "Artifact download failed", e)
            return null
        } finally {
            connection?.disconnect()
            partFile.delete()
            if (!completed && replacedDestination) destination.delete()
        }
    }

    private fun updateOutputFile(context: Context, info: UpdateDecider.ReleaseInfo): File? {
        val name = when (info.channel) {
            UpdateDecider.Channel.STABLE -> UpdateDecider.canonicalApkFileName(info.versionName)
            UpdateDecider.Channel.BETA -> {
                UpdateDecider.canonicalApkFileName(info.versionName)
                    ?.replace("qr-code-simple-", "qr-code-simple-beta-")
            }
        } ?: return null
        return File(File(context.filesDir, "updates"), name)
    }

    private fun installApk(activity: Activity, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
            activity.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME_TYPE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot launch package installer", e)
            false
        }
    }

    private fun showInstallFailure(activity: Activity, info: UpdateDecider.ReleaseInfo) {
        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.update_install_failed_title)
        if (info.channel == UpdateDecider.Channel.STABLE && info.releasePageUrl != null) {
            builder
                .setMessage(R.string.update_install_failed_message)
                .setPositiveButton(R.string.update_view_release_page) { _, _ ->
                    openUrl(activity, info.releasePageUrl)
                }
                .setNegativeButton(R.string.cancel, null)
        } else {
            builder
                .setMessage(R.string.beta_update_install_failed_message)
                .setPositiveButton(R.string.close, null)
        }
        builder.show()
    }

    private fun showProgressDialog(activity: Activity, info: UpdateDecider.ReleaseInfo) {
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val progressBar = ProgressBar(
            activity,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            tag = PROGRESS_BAR_TAG
        }
        val percentText = TextView(activity).apply {
            gravity = Gravity.CENTER
            text = activity.getString(R.string.update_progress_percent, 0)
            tag = PROGRESS_TEXT_TAG
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(
                progressBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                percentText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        progressDialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_downloading, info.versionName))
            .setView(content)
            .setCancelable(false)
            .show()
    }

    private fun updateProgressDialog(percent: Int, incremental: Boolean) {
        val root = progressDialog?.window?.decorView ?: return
        root.findViewWithTag<ProgressBar>(PROGRESS_BAR_TAG)?.progress = percent
        root.findViewWithTag<TextView>(PROGRESS_TEXT_TAG)?.let { text ->
            text.text = text.context.getString(
                if (incremental) {
                    R.string.update_incremental_progress_percent
                } else {
                    R.string.update_progress_percent
                },
                percent
            )
        }
    }

    private fun showIncrementalFallback() {
        val root = progressDialog?.window?.decorView ?: return
        root.findViewWithTag<ProgressBar>(PROGRESS_BAR_TAG)?.progress = 0
        root.findViewWithTag<TextView>(PROGRESS_TEXT_TAG)?.text =
            root.context.getString(R.string.update_incremental_fallback)
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun installedVersion(context: Context): InstalledVersion? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = info.versionName?.takeIf { it.isNotBlank() } ?: return null
            InstalledVersion(info.longVersionCode, versionName)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read installed version", e)
            null
        }
    }

    private fun openUrl(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open release page", e)
        }
    }

    private data class InstalledVersion(
        val versionCode: Long,
        val versionName: String
    )

    private const val PROGRESS_BAR_TAG = "app_update_progress_bar"
    private const val PROGRESS_TEXT_TAG = "app_update_progress_text"
}

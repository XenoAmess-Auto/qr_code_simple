package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Coordinates checking, verified download, unknown-source permission, and installation. */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 10_000
    private const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
    private const val UPDATES_DIRECTORY = "updates"

    /** Tests can replace checker results without network access. */
    internal var checkerForTesting: ((
        channel: UpdateDecider.Channel,
        localVersionCode: Long,
        localVersionName: String
    ) -> UpdateDecider.CheckOutcome)? = null

    /** Tests can supply deterministic artifact streams while exercising size/hash/file handling. */
    internal var downloadConnectionFactoryForTesting: ((URL) -> HttpURLConnection)? = null

    internal var canInstallPackagesForTesting: ((Context) -> Boolean)? = null
    internal var acquireUpdateApkForTesting: (suspend (
        Context,
        UpdateDecider.ReleaseInfo,
        (Int, Boolean) -> Unit,
        () -> Unit
    ) -> File?)? = null
    internal var archiveVerifierForTesting: ((Context, File, Long) -> Boolean)? = null
    internal var installApkForTesting: ((Activity, File) -> Boolean)? = null

    /** External-flow state is process-local: it survives recreation but safely expires on process loss. */
    private val stateLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingInstall: UpdateDecider.ReleaseInfo? = null
    private var pendingInstaller: InstallerAttempt? = null
    private var nextDownloadGeneration = 0L
    private var activeDownload: DownloadSession? = null

    fun checkManually(activity: Activity) {
        cleanupUpdateArtifacts(activity)
        check(activity, UpdateDecider.Channel.STABLE, manual = true)
    }

    /** Beta is intentionally manual-only and is only wired from About. */
    fun checkBetaUpdate(activity: Activity) {
        cleanupUpdateArtifacts(activity)
        check(activity, UpdateDecider.Channel.BETA, manual = true)
    }

    /** Stable-only automatic check with the existing 24-hour preference throttle. */
    fun maybeAutoCheck(activity: Activity) {
        cleanupUpdateArtifacts(activity)
        if (!QRCodeApp.isAppUpdateAutoCheckEnabled(activity)) return
        if (!QRCodeApp.tryMarkAppUpdateChecked(activity)) return
        val localVersion = installedVersion(activity) ?: return
        val host = WeakReference(activity)
        CoroutineScope(Dispatchers.IO).launch {
            val outcome = checkForChannel(
                UpdateDecider.Channel.STABLE,
                localVersion.versionCode,
                localVersion.versionName
            )
            if (outcome !is UpdateDecider.CheckOutcome.UpdateAvailable) return@launch
            withContext(Dispatchers.Main) {
                host.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.let {
                    showUpdateDialog(it, outcome.info)
                }
            }
        }
    }

    /** Called on resume to resolve a package-installer attempt, which has no reliable result callback. */
    fun onHostResume(activity: Activity) {
        consumePendingInstallIfAuthorized(activity)

        val attempt = synchronized(stateLock) {
            pendingInstaller.also { pendingInstaller = null }
        }
        if (attempt != null) {
            attempt.file.delete()
            val installed = installedVersion(activity)
            if (installed == null || installed.versionCode < attempt.info.versionCode) {
                showInstallFailure(activity, attempt.info)
            }
        }
    }

    /** Receives the unknown-source settings result from MainActivity's Activity Result launcher. */
    fun onInstallPermissionResult(activity: Activity) {
        val pending = synchronized(stateLock) {
            pendingInstall.also { pendingInstall = null }
        } ?: return
        if (canInstallPackages(activity)) {
            downloadAndInstall(activity, pending)
        } else {
            Toast.makeText(
                activity,
                R.string.update_install_permission_not_granted,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Releases UI and blocking network work owned by a destroyed Activity. */
    fun onHostDestroy(activity: Activity) {
        val session = synchronized(stateLock) {
            activeDownload?.takeIf { it.host.get() === activity }
        } ?: return
        cancelDownload(session)
    }

    private fun check(activity: Activity, channel: UpdateDecider.Channel, manual: Boolean) {
        val localVersion = installedVersion(activity)
        if (localVersion == null) {
            if (manual) showCheckFailure(activity, channel)
            return
        }
        val host = WeakReference(activity)
        CoroutineScope(Dispatchers.IO).launch {
            val outcome = checkForChannel(channel, localVersion.versionCode, localVersion.versionName)
            withContext(Dispatchers.Main) {
                val currentHost = host.get()?.takeUnless { it.isFinishing || it.isDestroyed }
                    ?: return@withContext
                when (outcome) {
                    is UpdateDecider.CheckOutcome.UpdateAvailable -> showUpdateDialog(currentHost, outcome.info)
                    UpdateDecider.CheckOutcome.UpToDate -> if (manual) {
                        Toast.makeText(
                            currentHost,
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
                        if (manual) showCheckFailure(currentHost, channel)
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
        } catch (e: CancellationException) {
            throw e
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
        val builder = MaterialAlertDialogBuilder(activity)
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
            synchronized(stateLock) { pendingInstall = info }
            requestInstallPermission(activity)
            return
        }
        downloadAndInstall(activity, info)
    }

    private fun consumePendingInstallIfAuthorized(activity: Activity) {
        if (!canInstallPackages(activity)) return
        val pending = synchronized(stateLock) {
            pendingInstall.also { pendingInstall = null }
        } ?: return
        downloadAndInstall(activity, pending)
    }

    internal fun startInstallForTesting(activity: Activity, info: UpdateDecider.ReleaseInfo) {
        startInstall(activity, info)
    }

    private fun canInstallPackages(context: Context): Boolean {
        return canInstallPackagesForTesting?.invoke(context) ?: (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        )
    }

    private fun requestInstallPermission(activity: Activity) {
        Toast.makeText(activity, R.string.update_install_permission_needed, Toast.LENGTH_LONG).show()
        if (activity !is MainActivity) {
            val pending = synchronized(stateLock) {
                pendingInstall.also { pendingInstall = null }
            }
            pending?.let { showInstallFailure(activity, it) }
            return
        }
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.launchUnknownSourcesSettings(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open unknown-sources settings", e)
            val pending = synchronized(stateLock) {
                pendingInstall.also { pendingInstall = null }
            }
            pending?.let { showInstallFailure(activity, it) }
        }
    }

    private fun downloadAndInstall(activity: Activity, info: UpdateDecider.ReleaseInfo) {
        cleanupUpdateArtifacts(activity)
        val lifecycleOwner = activity as? LifecycleOwner ?: run {
            showInstallFailure(activity, info)
            return
        }
        val applicationContext = activity.applicationContext
        val session: DownloadSession
        val previous = synchronized(stateLock) {
            session = DownloadSession(++nextDownloadGeneration, activity)
            activeDownload.also { activeDownload = session }
        }
        previous?.cancel()
        previous?.dismissProgress()
        showProgressDialog(activity, info, session)
        lateinit var job: Job
        job = lifecycleOwner.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            var file: File? = null
            try {
                val archiveVerified = withContext(Dispatchers.IO) {
                    val progressCallback = { percent: Int, incremental: Boolean ->
                        postToSession(session) {
                            updateProgressDialog(session, percent, incremental)
                        }
                    }
                    val fallbackCallback = {
                        postToSession(session) {
                            showIncrementalFallback(session)
                        }
                    }
                    val acquireForTesting = acquireUpdateApkForTesting
                    file = if (acquireForTesting != null) {
                        acquireForTesting(
                            applicationContext,
                            info,
                            progressCallback,
                            fallbackCallback
                        )
                    } else {
                        acquireUpdateApk(
                            applicationContext,
                            info,
                            progressCallback,
                            fallbackCallback,
                            session
                        )
                    }
                    ensureActive()
                    val verified = file?.let {
                        archiveVerifierForTesting?.invoke(applicationContext, it, info.versionCode)
                            ?: ApkArchiveVerifier.verify(applicationContext, it, info.versionCode)
                    } == true
                    ensureActive()
                    verified
                }
                ensureActive()
                if (!isCurrent(session)) throw CancellationException("Update download superseded")
                val host = session.host.get()?.takeUnless { it.isFinishing || it.isDestroyed }
                    ?: throw CancellationException("Update host destroyed")
                if (file != null && archiveVerified && installApk(host, file!!)) {
                    val retained = synchronized(stateLock) {
                        if (activeDownload === session) {
                            pendingInstaller = InstallerAttempt(info, file!!)
                            true
                        } else {
                            false
                        }
                    }
                    if (retained) file = null else file?.delete()
                } else {
                    file?.delete()
                    file = null
                    showInstallFailure(host, info)
                }
            } catch (e: CancellationException) {
                file?.delete()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                file?.delete()
                file = null
                if (isCurrent(session)) {
                    session.host.get()?.takeUnless { it.isFinishing || it.isDestroyed }?.let {
                        showInstallFailure(it, info)
                    }
                }
            } finally {
                completeSession(session)
            }
        }
        if (session.attachJob(job)) job.start()
    }

    private suspend fun acquireUpdateApk(
        context: Context,
        info: UpdateDecider.ReleaseInfo,
        onProgress: (percent: Int, incremental: Boolean) -> Unit,
        onIncrementalFallback: () -> Unit,
        session: DownloadSession
    ): File? {
        val job = currentCoroutineContext()[Job]
        val isCancelled = { job?.isActive == false }
        val outputFile = updateOutputFile(context, info, session.generation) ?: return null
        protectArtifact(session, outputFile)
        protectArtifact(session, File(outputFile.parentFile, "${outputFile.name}.part"))
        val installedApk = ApkPatcher.installedApkFile(context)
        val localApkSha256 = if (installedApk != null && info.chain != null) {
            try {
                ApkPatcher.sha256(installedApk, isCancelled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        val plan = ChainPlanner.choosePlan(
            chain = info.chain,
            localApkSha256 = localApkSha256,
            remoteApkSizeBytes = info.apkSizeBytes
        )
        if (plan is ChainPlanner.UpdatePlan.Incremental) {
            val updater = IncrementalUpdater(context).apply {
                workArtifactListener = { protectArtifact(session, it) }
                downloader = { url, destination, hop, progress ->
                    downloadVerifiedArtifactForSession(
                        url = url,
                        endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
                        destination = destination,
                        expectedSizeBytes = hop.sizeBytes,
                        expectedSha256 = hop.patchSha256,
                        onProgress = progress,
                        isCancelled = isCancelled,
                        session = session
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
            currentCoroutineContext().ensureActive()
            onIncrementalFallback()
        }
        return downloadVerifiedArtifactForSession(
            url = info.apkUrl,
            // Stable 与 Beta 的 APK 均来自 GitHub Releases（beta-archive）；Pages 仅存元数据。
            endpointTrust = UpdateDecider.EndpointTrust.GITHUB_RELEASE,
            destination = outputFile,
            expectedSizeBytes = info.apkSizeBytes,
            expectedSha256 = info.apkSha256,
            onProgress = { onProgress(it, false) },
            isCancelled = isCancelled,
            session = session
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
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean = { false }
    ): File? = downloadVerifiedArtifactForSession(
        url,
        endpointTrust,
        destination,
        expectedSizeBytes,
        expectedSha256,
        onProgress,
        isCancelled,
        null
    )

    private fun downloadVerifiedArtifactForSession(
        url: String,
        endpointTrust: UpdateDecider.EndpointTrust,
        destination: File,
        expectedSizeBytes: Long,
        expectedSha256: String,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
        session: DownloadSession?
    ): File? {
        if (!UpdateDecider.isTrustedInitialEndpoint(url, endpointTrust) ||
            expectedSizeBytes !in 1..UpdateDecider.MAX_ARTIFACT_BYTES ||
            !UpdateDecider.isSha256(expectedSha256)
        ) {
            return null
        }
        // 镜像轮询：可代理主机先走公共加速镜像，最后回退直连。
        // 每个候选的下载结果都必须通过精确大小 + SHA-256 校验才会落盘，
        // 因此镜像只影响可达性，不影响完整性；直连候选保持完整端点校验。
        val candidates = UpdateMirrors.candidates(url)
        candidates.forEachIndexed { index, candidate ->
            if (isCancelled() || session?.isCancelled() == true) {
                throw CancellationException("Update download cancelled")
            }
            val verifyResolvedEndpoint = index == candidates.lastIndex
            val result = downloadVerifiedArtifactOnce(
                candidate, endpointTrust, verifyResolvedEndpoint,
                destination, expectedSizeBytes, expectedSha256, onProgress, isCancelled, session
            )
            if (result != null) return result
        }
        return null
    }

    private fun downloadVerifiedArtifactOnce(
        url: String,
        endpointTrust: UpdateDecider.EndpointTrust,
        verifyResolvedEndpoint: Boolean,
        destination: File,
        expectedSizeBytes: Long,
        expectedSha256: String,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
        session: DownloadSession?
    ): File? {
        val directory = destination.parentFile ?: return null
        val partFile = File(directory, "${destination.name}.part")
        var connection: HttpURLConnection? = null
        var completed = false
        var replacedDestination = false
        try {
            if (isCancelled() || session?.isCancelled() == true) {
                throw CancellationException("Update download cancelled")
            }
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
            if (session != null && !session.attachConnection(connection)) {
                connection.disconnect()
                throw CancellationException("Update download cancelled")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                (verifyResolvedEndpoint &&
                    !UpdateDecider.isTrustedResolvedEndpoint(connection.url.toString(), endpointTrust))
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
                        if (isCancelled() || session?.isCancelled() == true) {
                            throw CancellationException("Update download cancelled")
                        }
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
            if (isCancelled() || session?.isCancelled() == true) {
                throw CancellationException("Update download cancelled")
            }
            if (downloadedBytes != expectedSizeBytes ||
                !ApkPatcher.sha256(
                    partFile,
                    isCancelled = { isCancelled() || session?.isCancelled() == true }
                ).equals(expectedSha256, ignoreCase = true)
            ) {
                return null
            }
            if (isCancelled() || session?.isCancelled() == true) {
                throw CancellationException("Update download cancelled")
            }
            if (destination.exists() && !destination.delete()) return null
            replacedDestination = true
            if (!partFile.renameTo(destination)) {
                partFile.copyTo(destination, overwrite = true)
                partFile.delete()
            }
            completed = destination.isFile
            return destination.takeIf { completed }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Artifact download failed", e)
            return null
        } finally {
            connection?.disconnect()
            connection?.let { session?.clearConnection(it) }
            partFile.delete()
            if (!completed && replacedDestination) destination.delete()
        }
    }

    private fun updateOutputFile(
        context: Context,
        info: UpdateDecider.ReleaseInfo,
        generation: Long
    ): File? {
        val name = when (info.channel) {
            UpdateDecider.Channel.STABLE -> UpdateDecider.canonicalApkFileName(info.versionName)
            UpdateDecider.Channel.BETA -> {
                UpdateDecider.canonicalApkFileName(info.versionName)
                    ?.replace("qr-code-simple-", "qr-code-simple-beta-")
            }
        } ?: return null
        return File(File(context.filesDir, UPDATES_DIRECTORY), "$name.$generation.download")
    }

    /** Removes update artifacts that no live download or installer attempt still owns. */
    internal fun cleanupUpdateArtifacts(context: Context) {
        synchronized(stateLock) {
            val protectedPaths = buildSet {
                activeDownload?.protectedArtifacts()?.forEach { add(it.absolutePath) }
                pendingInstaller?.file?.let { add(it.absolutePath) }
            }
            cleanupUpdateArtifacts(context, protectedPaths)
        }
    }

    internal fun cleanupUpdateArtifacts(context: Context, protectedPaths: Set<String>) {
        val directory = File(context.filesDir, UPDATES_DIRECTORY)
        directory.listFiles().orEmpty().forEach { artifact ->
            if (!isUpdateWorkArtifact(artifact) || isProtected(artifact, protectedPaths)) return@forEach
            if (artifact.isDirectory) artifact.deleteRecursively() else artifact.delete()
        }
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    private fun isUpdateWorkArtifact(file: File): Boolean =
        file.name.endsWith(".download") ||
            file.name.endsWith(".part") ||
            file.name == "incremental" ||
            file.name.startsWith("incremental-") ||
            isLegacyUpdateApk(file)

    private fun isLegacyUpdateApk(file: File): Boolean {
        if (!file.isFile) return false
        val name = file.name
        if (!name.startsWith(UpdateDecider.CANONICAL_APK_PREFIX) || !name.endsWith(".apk")) {
            return false
        }
        val version = name
            .removePrefix(UpdateDecider.CANONICAL_APK_PREFIX)
            .removeSuffix(".apk")
        return UpdateDecider.canonicalApkFileName(version) == name
    }

    private fun isProtected(file: File, protectedPaths: Set<String>): Boolean {
        val path = file.absolutePath
        val childPrefix = "$path${File.separator}"
        return protectedPaths.any { protected -> protected == path || protected.startsWith(childPrefix) }
    }

    private fun protectArtifact(session: DownloadSession, file: File) = synchronized(stateLock) {
        if (activeDownload === session) session.protectArtifact(file)
    }

    private fun installApk(activity: Activity, file: File): Boolean {
        installApkForTesting?.let { return it(activity, file) }
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
        val builder = MaterialAlertDialogBuilder(activity)
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

    private fun showProgressDialog(
        activity: Activity,
        info: UpdateDecider.ReleaseInfo,
        session: DownloadSession
    ) {
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
        session.progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.update_downloading, info.versionName))
            .setView(content)
            .setNegativeButton(R.string.cancel) { _, _ -> cancelDownload(session) }
            .setCancelable(false)
            .show()
    }

    private fun updateProgressDialog(
        session: DownloadSession,
        percent: Int,
        incremental: Boolean
    ) {
        val root = session.progressDialog?.window?.decorView ?: return
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

    private fun showIncrementalFallback(session: DownloadSession) {
        val root = session.progressDialog?.window?.decorView ?: return
        root.findViewWithTag<ProgressBar>(PROGRESS_BAR_TAG)?.progress = 0
        root.findViewWithTag<TextView>(PROGRESS_TEXT_TAG)?.text =
            root.context.getString(R.string.update_incremental_fallback)
    }

    private fun postToSession(session: DownloadSession, action: () -> Unit) {
        mainHandler.post {
            if (isCurrent(session) &&
                session.host.get()?.let { !it.isFinishing && !it.isDestroyed } == true
            ) {
                action()
            }
        }
    }

    private fun isCurrent(session: DownloadSession): Boolean = synchronized(stateLock) {
        activeDownload === session && !session.isCancelled()
    }

    private fun completeSession(session: DownloadSession) {
        synchronized(stateLock) {
            if (activeDownload === session) activeDownload = null
        }
        session.clearConnection()
        session.dismissProgress()
    }

    private fun cancelDownload(session: DownloadSession? = synchronized(stateLock) { activeDownload }) {
        session ?: return
        synchronized(stateLock) {
            if (activeDownload === session) activeDownload = null
        }
        session.cancel()
        session.dismissProgress()
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

    private data class InstallerAttempt(
        val info: UpdateDecider.ReleaseInfo,
        val file: File
    )

    private class DownloadSession(
        val generation: Long,
        activity: Activity
    ) {
        val host = WeakReference(activity)
        private var job: Job? = null
        var progressDialog: AlertDialog? = null
        @Volatile private var cancelled = false
        private var connection: HttpURLConnection? = null
        private val protectedArtifacts = mutableSetOf<File>()

        fun attachJob(value: Job): Boolean = synchronized(this) {
            job = value
            if (cancelled) {
                value.cancel()
                false
            } else {
                true
            }
        }

        fun attachConnection(value: HttpURLConnection): Boolean = synchronized(this) {
            if (cancelled || job?.isActive != true) {
                false
            } else {
                connection = value
                true
            }
        }

        fun clearConnection(value: HttpURLConnection? = null) = synchronized(this) {
            if (value == null || connection === value) connection = null
        }

        fun isCancelled(): Boolean = cancelled || job?.isActive == false

        fun isActive(): Boolean = !cancelled && job?.isActive == true

        fun protectArtifact(file: File) = synchronized(this) {
            protectedArtifacts += file.absoluteFile
        }

        fun protectedArtifacts(): Set<File> = synchronized(this) {
            protectedArtifacts.toSet()
        }

        fun cancel() {
            val activeConnection = synchronized(this) {
                cancelled = true
                connection.also { connection = null }
            }
            job?.cancel()
            activeConnection?.disconnect()
        }

        fun dismissProgress() {
            progressDialog?.dismiss()
            progressDialog = null
            host.clear()
        }
    }

    internal fun resetForTesting() {
        cancelDownload()
        synchronized(stateLock) {
            pendingInstall = null
            pendingInstaller?.file?.delete()
            pendingInstaller = null
        }
        canInstallPackagesForTesting = null
        acquireUpdateApkForTesting = null
        archiveVerifierForTesting = null
        installApkForTesting = null
    }

    internal fun hasPendingInstallForTesting(): Boolean = synchronized(stateLock) {
        pendingInstall != null
    }

    internal fun hasPendingInstallerForTesting(): Boolean = synchronized(stateLock) {
        pendingInstaller != null
    }

    internal fun hasActiveDownloadForTesting(): Boolean = synchronized(stateLock) {
        activeDownload?.isActive() == true
    }

    private const val PROGRESS_BAR_TAG = "app_update_progress_bar"
    private const val PROGRESS_TEXT_TAG = "app_update_progress_text"
}

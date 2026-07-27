package com.xenoamess.qrcodesimple

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用更新编排：检查 → 弹窗 → 下载 APK → 调起系统安装器。
 *
 * 自动安装失败（无安装权限、下载失败、签名校验失败等）时回退到
 * 打开 GitHub Release 页面由用户手动下载。
 */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"

    private const val APK_MAX_BYTES = 1024L * 1024 * 1024
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /** 测试注入点：替换 release 拉取逻辑。生产环境为 null，走 AppUpdateChecker。 */
    internal var fetcherForTesting: (() -> AppUpdateChecker.ReleaseInfo?)? = null

    /** 用户去系统设置授予安装权限期间暂存的待安装 release。 */
    private var pendingInstall: AppUpdateChecker.ReleaseInfo? = null

    private var progressDialog: AlertDialog? = null

    sealed interface CheckOutcome {
        data class UpdateAvailable(val info: AppUpdateChecker.ReleaseInfo) : CheckOutcome
        data object UpToDate : CheckOutcome
        data object Failed : CheckOutcome
    }

    /**
     * 手动检查更新（关于页按钮）。任何结果都会给用户反馈。
     */
    fun checkManually(activity: Activity) {
        val fetcher = fetcherForTesting ?: { AppUpdateChecker.fetchLatestRelease() }
        CoroutineScope(Dispatchers.IO).launch {
            val outcome = runCatching {
                val info = fetcher()
                when {
                    info == null -> CheckOutcome.Failed
                    AppUpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME) ->
                        CheckOutcome.UpdateAvailable(info)
                    else -> CheckOutcome.UpToDate
                }
            }.getOrDefault(CheckOutcome.Failed)
            withContext(Dispatchers.Main) {
                if (activity.isFinishing || activity.isDestroyed) return@withContext
                when (outcome) {
                    is CheckOutcome.UpdateAvailable -> showUpdateDialog(activity, outcome.info)
                    CheckOutcome.UpToDate -> Toast.makeText(
                        activity, R.string.update_already_latest, Toast.LENGTH_SHORT
                    ).show()
                    CheckOutcome.Failed -> Toast.makeText(
                        activity, R.string.update_check_failed, Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 自动检查（应用启动时调用）。开关关闭或距上次检查不足 24h 直接跳过；
     * 只在发现新版本时弹窗，其余情况完全静默。
     */
    fun maybeAutoCheck(activity: Activity) {
        if (!QRCodeApp.isAppUpdateAutoCheckEnabled(activity)) return
        if (!QRCodeApp.tryMarkAppUpdateChecked(activity)) return
        val fetcher = fetcherForTesting ?: { AppUpdateChecker.fetchLatestRelease() }
        CoroutineScope(Dispatchers.IO).launch {
            val info = runCatching { fetcher() }.getOrNull() ?: return@launch
            if (!AppUpdateChecker.isNewer(info.version, BuildConfig.VERSION_NAME)) return@launch
            withContext(Dispatchers.Main) {
                if (activity.isFinishing || activity.isDestroyed) return@withContext
                showUpdateDialog(activity, info)
            }
        }
    }

    /**
     * 宿主 Activity onResume 时调用：用户从系统安装权限设置页返回后继续安装流程。
     */
    fun onHostResume(activity: Activity) {
        val pending = pendingInstall ?: return
        if (!canInstallPackages(activity)) return
        pendingInstall = null
        downloadAndInstall(activity, pending)
    }

    private fun showUpdateDialog(activity: Activity, info: AppUpdateChecker.ReleaseInfo) {
        val message = info.changelog.trim().let {
            if (it.length > 500) it.substring(0, 500) + "…" else it
        }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_available_title, info.version))
            .setMessage(if (message.isEmpty()) null else message)
            .setPositiveButton(R.string.update_now) { _, _ ->
                startInstall(activity, info)
            }
            .setNeutralButton(R.string.update_view_release_page) { _, _ ->
                openUrl(activity, info.htmlUrl)
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun startInstall(activity: Activity, info: AppUpdateChecker.ReleaseInfo) {
        if (info.apkUrl.isNullOrEmpty()) {
            showInstallFailedDialog(activity, info)
            return
        }
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
            Log.w(TAG, "Cannot open unknown sources settings", e)
            pendingInstall?.let { showInstallFailedDialog(activity, it) }
            pendingInstall = null
        }
    }

    private fun downloadAndInstall(activity: Activity, info: AppUpdateChecker.ReleaseInfo) {
        val apkUrl = info.apkUrl ?: return
        showProgressDialog(activity, info)
        CoroutineScope(Dispatchers.IO).launch {
            val file = downloadApk(activity, apkUrl, info.apkSizeBytes) { percent ->
                CoroutineScope(Dispatchers.Main).launch { updateProgressDialog(percent) }
            }
            withContext(Dispatchers.Main) {
                dismissProgressDialog()
                if (activity.isFinishing || activity.isDestroyed) {
                    file?.delete()
                    return@withContext
                }
                if (file != null && installApk(activity, file)) {
                    // 系统安装器接管；安装成功后新进程启动时旧文件无妨，这里尽力清理
                    file.deleteOnExit()
                } else {
                    file?.delete()
                    showInstallFailedDialog(activity, info)
                }
            }
        }
    }

    /** 下载 APK 到应用私有 Downloads 目录；任何失败返回 null。 */
    private fun downloadApk(
        context: Context,
        url: String,
        expectedSize: Long,
        onProgress: (Int) -> Unit
    ): File? {
        var connection: HttpURLConnection? = null
        return try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val out = File(dir, "update-latest.apk")
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "qr_code_simple/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "APK download response: ${connection.responseCode}")
                return null
            }
            val total = if (expectedSize > 0) expectedSize
            else connection.contentLength.toLong().takeIf { it > 0 } ?: 0L
            connection.inputStream.use { input ->
                FileOutputStream(out).use { output ->
                    val chunk = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > APK_MAX_BYTES) {
                            Log.w(TAG, "APK too large, aborting")
                            out.delete()
                            return null
                        }
                        output.write(chunk, 0, read)
                        if (total > 0) {
                            onProgress((downloaded * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (out.length() == 0L) {
                out.delete()
                return null
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "APK download failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** 调起系统安装器；返回 false 表示无法调起。 */
    private fun installApk(activity: Activity, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file
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

    private fun showInstallFailedDialog(activity: Activity, info: AppUpdateChecker.ReleaseInfo) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_install_failed_title)
            .setMessage(R.string.update_install_failed_message)
            .setPositiveButton(R.string.update_view_release_page) { _, _ ->
                openUrl(activity, info.htmlUrl)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showProgressDialog(activity: Activity, info: AppUpdateChecker.ReleaseInfo) {
        val context = activity
        val padding = (24 * resourcesDisplayDensity(context)).toInt()
        val progressBar = ProgressBar(
            context, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
        }
        val percentText = TextView(context).apply {
            gravity = Gravity.CENTER
            text = "0%"
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(progressBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(percentText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        progressDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.update_downloading, info.version))
            .setView(content)
            .setCancelable(false)
            .show()
        progressBar.tag = PROGRESS_BAR_TAG
        percentText.tag = PROGRESS_TEXT_TAG
    }

    private fun updateProgressDialog(percent: Int) {
        val dialog = progressDialog ?: return
        val root = dialog.window?.decorView ?: return
        (root.findViewWithTag<ProgressBar>(PROGRESS_BAR_TAG))?.progress = percent
        (root.findViewWithTag<TextView>(PROGRESS_TEXT_TAG))?.text = "$percent%"
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun resourcesDisplayDensity(context: Context): Float {
        return context.resources.displayMetrics.density
    }

    private fun openUrl(activity: Activity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open url $url", e)
        }
    }

    private const val PROGRESS_BAR_TAG = "app_update_progress_bar"
    private const val PROGRESS_TEXT_TAG = "app_update_progress_text"
}

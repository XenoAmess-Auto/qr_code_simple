package com.xenoamess.qrcodesimple

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * 本地崩溃日志记录器。
 *
 * 捕获未处理异常并写入 filesDir/crash_logs（最多保留 [MAX_LOG_FILES] 份），
 * 然后转交系统默认处理器。不联网、不上报任何数据；用户可在 About 页
 * 查看、分享或清除日志。
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val DIR_NAME = "crash_logs"
    private const val FILE_PREFIX = "crash_"
    private const val MAX_LOG_FILES = 10

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(appContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun write(context: Context, thread: Thread, throwable: Throwable): File {
        val dir = logDir(context).apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val file = File(dir, "$FILE_PREFIX$timestamp.txt")

        val text = buildString {
            append("time: ").append(formatTime(timestamp)).append('\n')
            append("version: ").append(versionText(context)).append('\n')
            append("git: ").append(BuildConfig.GIT_HASH).append('\n')
            append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" sdk=").append(Build.VERSION.SDK_INT).append('\n')
            append("thread: ").append(thread.name).append('\n')
            append('\n')
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            append(sw)
        }
        file.writeText(text)
        prune(dir)
        return file
    }

    private fun versionText(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    private fun logDir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun listLogs(context: Context): List<File> =
        logDir(context).listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun readLatest(context: Context): String? =
        listLogs(context).firstOrNull()?.takeIf { it.isFile }?.readText()

    fun clear(context: Context) {
        listLogs(context).forEach { it.delete() }
    }

    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith(FILE_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_LOG_FILES).forEach { it.delete() }
    }
}

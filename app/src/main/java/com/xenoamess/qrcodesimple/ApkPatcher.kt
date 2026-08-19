package com.xenoamess.qrcodesimple

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/**
 * File helpers for verified APK delta updates (ApkDiffPatch "ZiPat1" format).
 */
object ApkPatcher {

    private const val TAG = "ApkPatcher"

    private const val APKDIFF_PATCH_MAGIC = "ZiPat1"
    // ApkDiffPatch decompression memory ceiling; larger APKs stream through the temp file.
    private const val MAX_UNCOMPRESS_MEMORY_BYTES = 128L * 1024 * 1024
    private const val APKDIFF_THREAD_NUM = 2

    fun installedApkFile(context: Context): File? {
        return try {
            context.applicationInfo.sourceDir
                ?.let(::File)
                ?.takeIf { it.isFile && it.canRead() }
        } catch (e: Exception) {
            Log.w(TAG, "installedApkFile failed: ${e.message}")
            null
        }
    }

    fun sha256(file: File, isCancelled: () -> Boolean = { false }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (isCancelled()) throw CancellationException("APK verification cancelled")
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    /**
     * Applies one ApkDiffPatch patch. The patch must be ZiPat1 format; anything else is
     * rejected so callers fall back to the full APK. Native failures (including a missing
     * libapkpatch.so) are wrapped in a plain IllegalStateException instead of an Error so
     * the recoverable full-download fallback always works.
     */
    fun applyPatch(context: Context, baseApk: File, patchFile: File, outputFile: File) {
        applyPatch(context, baseApk, patchFile, outputFile) { base, patch, output, memory, temp, threads ->
            com.github.sisong.ApkPatch.patch(base, patch, output, memory, temp, threads)
        }
    }

    internal fun applyPatch(
        context: Context,
        baseApk: File,
        patchFile: File,
        outputFile: File,
        nativePatch: (String, String, String, Long, String, Int) -> Int
    ) {
        val magic = readMagic(patchFile)
        if (!magic.startsWith(APKDIFF_PATCH_MAGIC)) {
            throw IllegalArgumentException("unknown patch format: ${magic.take(8)}")
        }
        outputFile.parentFile?.mkdirs()
        val tmp = File.createTempFile("apkpatch-", ".bin", context.cacheDir)
        try {
            val rc = nativePatch(
                baseApk.absolutePath,
                patchFile.absolutePath,
                outputFile.absolutePath,
                MAX_UNCOMPRESS_MEMORY_BYTES,
                tmp.absolutePath,
                APKDIFF_THREAD_NUM
            )
            if (rc != 0) throw IllegalStateException("ApkDiffPatch apply failed rc=$rc")
            if (!outputFile.isFile || outputFile.length() <= 0) {
                throw IllegalStateException("ApkDiffPatch apply produced empty output")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw IllegalStateException(
                "ApkDiffPatch apply failed: ${e.javaClass.simpleName}: ${e.message}",
                e
            )
        } finally {
            tmp.delete()
        }
    }

    private fun readMagic(file: File): String {
        return file.inputStream().buffered().use { input ->
            val buf = ByteArray(8)
            val read = input.read(buf)
            String(buf, 0, read.coerceAtLeast(0), Charsets.US_ASCII)
        }
    }
}

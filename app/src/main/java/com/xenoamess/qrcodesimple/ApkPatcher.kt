package com.xenoamess.qrcodesimple

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * File helpers for verified APK delta updates (ApkDiffPatch "ZiPat1" format).
 */
object ApkPatcher {

    private const val APKDIFF_PATCH_MAGIC = "ZiPat1"
    // ApkDiffPatch decompression memory ceiling; larger APKs stream through the temp file.
    private const val MAX_UNCOMPRESS_MEMORY_BYTES = 128L * 1024 * 1024
    private const val APKDIFF_THREAD_NUM = 2

    fun installedApkFile(context: Context): File? {
        return try {
            context.applicationInfo.sourceDir
                ?.let(::File)
                ?.takeIf { it.isFile && it.canRead() }
        } catch (_: Exception) {
            null
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
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
        val magic = readMagic(patchFile)
        if (!magic.startsWith(APKDIFF_PATCH_MAGIC)) {
            throw IllegalArgumentException("unknown patch format: ${magic.take(8)}")
        }
        outputFile.parentFile?.mkdirs()
        val tmp = File(context.cacheDir, "apkpatch_tmp.bin")
        try {
            val rc = com.github.sisong.ApkPatch.patch(
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

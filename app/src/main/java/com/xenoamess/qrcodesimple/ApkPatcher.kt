package com.xenoamess.qrcodesimple

import io.sigpipe.jbsdiff.Patch
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

/** File helpers for verified APK delta updates. */
object ApkPatcher {

    /**
     * jbsdiff reads both the base APK and a patch into byte arrays. Keep their combined file input
     * below 64 MiB, leaving headroom for the patcher's working buffers. This intentionally excludes
     * the current approximately 122 MiB APK from incremental updates and uses the full APK instead.
     */
    const val MAX_INCREMENTAL_INPUT_BYTES = 64L * 1024L * 1024L

    fun installedApkFile(context: android.content.Context): File? {
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

    fun hasSafeIncrementalInputSize(baseApkSizeBytes: Long, patchInputBytes: Long): Boolean {
        if (baseApkSizeBytes <= 0 || patchInputBytes <= 0) return false
        return baseApkSizeBytes < MAX_INCREMENTAL_INPUT_BYTES - patchInputBytes
    }

    /** Applies one patch after enforcing input and output bounds. */
    fun applyPatch(
        baseApk: File,
        patchFile: File,
        outputFile: File,
        maxOutputBytes: Long
    ) {
        if (!hasSafeIncrementalInputSize(baseApk.length(), patchFile.length())) {
            throw IOException("Incremental patch input exceeds the safe memory ceiling")
        }
        if (maxOutputBytes !in 1..UpdateDecider.MAX_ARTIFACT_BYTES) {
            throw IOException("Incremental patch output ceiling is invalid")
        }
        outputFile.parentFile?.mkdirs()
        val baseBytes = baseApk.readBytes()
        val patchBytes = patchFile.readBytes()
        outputFile.outputStream().buffered().use { output ->
            Patch.patch(baseBytes, patchBytes, BoundedOutputStream(output, maxOutputBytes))
        }
    }

    private class BoundedOutputStream(
        output: OutputStream,
        private val maxBytes: Long
    ) : FilterOutputStream(output) {
        private var writtenBytes = 0L

        override fun write(byte: Int) {
            reserve(1)
            out.write(byte)
            writtenBytes++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length == 0) return
            reserve(length.toLong())
            out.write(bytes, offset, length)
            writtenBytes += length
        }

        private fun reserve(length: Long) {
            if (length < 0 || writtenBytes > maxBytes - length) {
                throw IOException("Incremental patch output exceeds the safe size limit")
            }
        }
    }
}

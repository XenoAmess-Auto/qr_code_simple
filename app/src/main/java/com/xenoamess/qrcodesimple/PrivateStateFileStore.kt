package com.xenoamess.qrcodesimple

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal object PrivateStateFileStore {
    private val tokenPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val directoryPattern = Regex("[a-z0-9-]+")
    private val extensionPattern = Regex("[a-z0-9]+")

    fun newToken(): String = UUID.randomUUID().toString()

    fun validToken(value: String?): String? {
        if (value == null || !tokenPattern.matches(value)) return null
        return runCatching { UUID.fromString(value).toString() }
            .getOrNull()
            ?.takeIf { it == value }
    }

    fun write(
        context: Context,
        directoryName: String,
        bytes: ByteArray,
        maxBytes: Int,
        token: String = newToken(),
        extension: String = "json"
    ): String {
        require(bytes.size <= maxBytes) { "Private state exceeds $maxBytes bytes" }
        val target = file(context, directoryName, token, extension)
        val directory = target.parentFile ?: error("Missing private state directory")
        check(directory.exists() || directory.mkdirs())
        val temporary = File.createTempFile(".${target.name}.", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            replaceAtomically(temporary, target)
            return token
        } finally {
            temporary.delete()
        }
    }

    fun read(
        context: Context,
        directoryName: String,
        token: String?,
        maxBytes: Int,
        extension: String = "json"
    ): ByteArray {
        val validToken = validToken(token) ?: throw IOException("Invalid private state token")
        val source = file(context, directoryName, validToken, extension)
        if (!source.isFile) throw IOException("Private state is missing")
        if (source.length() > maxBytes) throw IOException("Private state exceeds $maxBytes bytes")
        return source.readBytes().also {
            if (it.size > maxBytes) throw IOException("Private state exceeds $maxBytes bytes")
        }
    }

    fun existingFile(
        context: Context,
        directoryName: String,
        token: String?,
        extension: String
    ): File? {
        val validToken = validToken(token) ?: return null
        return file(context, directoryName, validToken, extension).takeIf(File::isFile)
    }

    fun delete(context: Context, directoryName: String, token: String?) {
        val validToken = validToken(token) ?: return
        val directory = directory(context, directoryName)
        directory.listFiles()?.forEach { file ->
            if (file.name.startsWith("$validToken.")) file.delete()
        }
    }

    fun delete(
        context: Context,
        directoryName: String,
        token: String?,
        extension: String
    ) {
        val validToken = validToken(token) ?: return
        file(context, directoryName, validToken, extension).delete()
    }

    fun cleanupExpired(
        context: Context,
        directoryName: String,
        maxAgeMs: Long,
        activeToken: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val active = validToken(activeToken)
        val cutoff = nowMs - maxAgeMs
        directory(context, directoryName).listFiles()?.forEach { file ->
            val belongsToActiveToken = active != null && file.name.startsWith("$active.")
            if (!belongsToActiveToken && file.lastModified() < cutoff) file.delete()
        }
    }

    internal fun file(
        context: Context,
        directoryName: String,
        token: String,
        extension: String = "json"
    ): File {
        requireNotNull(validToken(token)) { "Invalid private state token" }
        require(extensionPattern.matches(extension)) { "Invalid private state extension" }
        return File(directory(context, directoryName), "$token.$extension")
    }

    internal fun directory(context: Context, directoryName: String): File {
        require(directoryPattern.matches(directoryName)) { "Invalid private state directory" }
        return File(context.noBackupFilesDir, directoryName)
    }

    private fun replaceAtomically(temporary: File, target: File) {
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

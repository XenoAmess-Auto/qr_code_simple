package com.xenoamess.qrcodesimple

import android.util.Log
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 WebDAV 客户端（PUT/GET + Basic Auth，HttpURLConnection 实现，无第三方依赖）。
 * 仅用于备份文件的手动上传/恢复；大文件不做断点续传。
 */
object WebDavClient {

    private const val TAG = "WebDavClient"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024

    enum class Result { SUCCESS, AUTH_FAILED, NOT_FOUND, NETWORK_ERROR, TOO_LARGE }

    /** 测试注入点：生产为 null，走真实 URL.openConnection。 */
    internal var connectionFactoryForTesting: ((URL) -> HttpURLConnection)? = null

    fun upload(url: String, username: String, password: CharArray, data: ByteArray): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url).apply {
                requestMethod = "PUT"
                doOutput = true
                setFixedLengthStreamingMode(data.size)
                setRequestProperty("Content-Type", "application/octet-stream")
                applyAuth(this, username, password)
            }
            connection.outputStream.use { it.write(data) }
            when (val code = connection.responseCode) {
                in 200..299 -> Result.SUCCESS
                401, 403 -> Result.AUTH_FAILED
                404 -> Result.NOT_FOUND
                else -> {
                    Log.w(TAG, "webdav upload unexpected code $code")
                    Result.NETWORK_ERROR
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "webdav upload failed: ${e.message}")
            Result.NETWORK_ERROR
        } finally {
            connection?.disconnect()
        }
    }

    fun download(url: String, username: String, password: CharArray): Pair<Result, ByteArray?> {
        var connection: HttpURLConnection? = null
        return try {
            connection = open(url).apply {
                requestMethod = "GET"
                applyAuth(this, username, password)
            }
            when (val code = connection.responseCode) {
                200 -> {
                    val bytes = connection.inputStream.use { input ->
                        readCapped(input, MAX_DOWNLOAD_BYTES)
                    } ?: return Result.TOO_LARGE to null
                    Result.SUCCESS to bytes
                }
                401, 403 -> Result.AUTH_FAILED to null
                404 -> Result.NOT_FOUND to null
                else -> {
                    Log.w(TAG, "webdav download unexpected code $code")
                    Result.NETWORK_ERROR to null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "webdav download failed: ${e.message}")
            Result.NETWORK_ERROR to null
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (connectionFactoryForTesting?.invoke(URL(url))
            ?: URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }

    private fun applyAuth(connection: HttpURLConnection, username: String, password: CharArray) {
        if (username.isEmpty()) return
        val token = "$username:${password.concatToString()}"
        val encoded = Base64.encodeToString(token.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        connection.setRequestProperty("Authorization", "Basic $encoded")
    }

    private fun readCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream(minOf(maxBytes, 8192))
        val chunk = ByteArray(4096)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}

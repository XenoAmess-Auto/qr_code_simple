package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

object BatchResultTransfer {
    const val MAX_ITEMS = 500
    const val MAX_ITEM_CHARACTERS = 8_192
    const val MAX_SERIALIZED_BYTES = 2 * 1024 * 1024
    private const val MAX_LOGO_BYTES = 4 * 1024 * 1024
    private const val DIRECTORY = "batch-result-transfer"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    enum class Limit {
        ITEM_COUNT,
        ITEM_LENGTH,
        TOTAL_BYTES
    }

    data class Payload(
        val items: List<BatchGenerator.BatchItem>,
        val styleJson: String?,
        val logoBitmap: Bitmap?
    )

    internal class Budget {
        private var itemCount = 0
        private var serializedBytes = 2L

        fun add(item: BatchGenerator.BatchItem): Limit? {
            if (itemCount >= MAX_ITEMS) return Limit.ITEM_COUNT
            val fields = listOfNotNull(item.content, item.format.name, item.fileName)
            if (fields.any { it.length > MAX_ITEM_CHARACTERS }) return Limit.ITEM_LENGTH

            val itemBytes = 128L + fields.sumOf(::serializedStringBytes)
            if (serializedBytes + itemBytes > MAX_SERIALIZED_BYTES) return Limit.TOTAL_BYTES
            itemCount++
            serializedBytes += itemBytes
            return null
        }
    }

    fun validate(items: List<BatchGenerator.BatchItem>): Limit? {
        val budget = Budget()
        items.forEach { item -> budget.add(item)?.let { return it } }
        return null
    }

    internal fun serializedStringBytes(value: String): Long {
        var bytes = 2L
        var index = 0
        while (index < value.length) {
            val char = value[index]
            bytes += when {
                char.code < 0x20 -> 6
                char == '"' || char == '\\' || char == '/' -> 2
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                Character.isHighSurrogate(char) &&
                    index + 1 < value.length &&
                    Character.isLowSurrogate(value[index + 1]) -> {
                    index++
                    4
                }
                else -> 3
            }
            index++
        }
        return bytes
    }

    fun write(
        context: Context,
        items: List<BatchGenerator.BatchItem>,
        styleJson: String?,
        logoBitmap: Bitmap?
    ): String {
        require(validate(items) == null) { "Batch payload exceeds its limit" }
        cleanupExpired(context)
        val token = PrivateStateFileStore.newToken()
        try {
            logoBitmap?.let { logo ->
                val output = ByteArrayOutputStream()
                check(logo.compress(Bitmap.CompressFormat.PNG, 100, output))
                PrivateStateFileStore.write(context, DIRECTORY, output.toByteArray(), MAX_LOGO_BYTES, token, "png")
            }
            val payload = JSONObject().apply {
                put("version", 1)
                put("items", JSONArray(itemsJson(items)))
                styleJson?.let { put("style", it) }
                put("hasLogo", logoBitmap != null)
            }.toString().toByteArray(Charsets.UTF_8)
            require(payload.size <= MAX_SERIALIZED_BYTES) { "Batch payload exceeds its limit" }
            PrivateStateFileStore.write(context, DIRECTORY, payload, MAX_SERIALIZED_BYTES, token)
            return token
        } catch (failure: Exception) {
            delete(context, token)
            throw failure
        }
    }

    fun createIntent(
        context: Context,
        items: List<BatchGenerator.BatchItem>,
        styleJson: String? = null,
        logoBitmap: Bitmap? = null
    ): Intent = Intent(context, BatchResultActivity::class.java).putExtra(
        BatchGenerateActivity.EXTRA_BATCH_TOKEN,
        write(context, items, styleJson, logoBitmap)
    )

    fun read(context: Context, token: String?): Payload {
        cleanupExpired(context, token)
        val bytes = PrivateStateFileStore.read(context, DIRECTORY, token, MAX_SERIALIZED_BYTES)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        if (root.optInt("version") != 1) throw IOException("Unsupported batch payload")
        val array = root.getJSONArray("items")
        val items = BatchGenerator.itemsFromJson(array.toString())
        if (items.size != array.length() || validate(items) != null) throw IOException("Invalid batch payload")
        val logo = if (root.optBoolean("hasLogo")) {
            val file = PrivateStateFileStore.existingFile(context, DIRECTORY, token, "png")
                ?: throw IOException("Batch logo is missing")
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?: throw IOException("Batch logo is invalid")
        } else {
            null
        }
        return Payload(items, root.optString("style").takeIf { root.has("style") }, logo)
    }

    fun delete(context: Context, token: String?) =
        PrivateStateFileStore.delete(context, DIRECTORY, token)

    fun cleanupExpired(context: Context, activeToken: String? = null, nowMs: Long = System.currentTimeMillis()) =
        PrivateStateFileStore.cleanupExpired(context, DIRECTORY, MAX_AGE_MS, activeToken, nowMs)

    internal fun file(context: Context, token: String): java.io.File =
        PrivateStateFileStore.file(context, DIRECTORY, token)

    private fun itemsJson(items: List<BatchGenerator.BatchItem>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().apply {
                put("content", item.content)
                put("format", item.format.name)
                item.foregroundColor?.let { put("foregroundColor", it) }
                item.backgroundColor?.let { put("backgroundColor", it) }
                item.fileName?.let { put("fileName", it) }
            })
        }
    }.toString()
}

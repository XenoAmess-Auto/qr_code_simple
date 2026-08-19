package com.xenoamess.qrcodesimple

import android.content.Context
import android.net.Uri
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 历史记录导入/导出工具
 */
object HistoryBackupManager {

    data class BackupResult(
        val success: Boolean,
        val count: Int,
        val message: String
    )

    /**
     * 导出历史记录到 JSON
     */
    suspend fun exportToJson(context: Context): String = withContext(Dispatchers.IO) {
        val repository = HistoryRepository(context)
        val items = repository.allHistory.first()

        val jsonArray = JSONArray()
        
        items.forEach { item ->
            val jsonObject = JSONObject().apply {
                put("id", item.id)
                put("content", item.content)
                put("type", item.type.name)
                put("timestamp", item.timestamp)
                put("isGenerated", item.isGenerated)
                put("barcodeFormat", item.barcodeFormat ?: JSONObject.NULL)
                put("isFavorite", item.isFavorite)
                put("notes", item.notes ?: JSONObject.NULL)
                put("tags", item.tags ?: JSONObject.NULL)
                put("styleJson", item.styleJson ?: JSONObject.NULL)
            }
            jsonArray.put(jsonObject)
        }

        val rootObject = JSONObject().apply {
            put("version", 1)
            put("exportDate", System.currentTimeMillis())
            put("count", items.size)
            put("items", jsonArray)
        }

        rootObject.toString(2)
    }

    /**
     * 判断文本是否为 JSON 备份（导出产物以 `{` 开头；兼容历史 `[` 开头的数组格式）。
     */
    fun looksLikeJson(content: String): Boolean {
        val trimmed = content.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    /** Accept exported CSV headers and legacy headerless backup rows, but not arbitrary text. */
    fun looksLikeCsv(content: String): Boolean {
        return runCatching {
            CSVParser.parse(StringReader(content), BACKUP_CSV_FORMAT).use { parser ->
                val firstRecord = parser.iterator().let { if (it.hasNext()) it.next() else null }
                    ?: return@use false
                isCsvHeader(firstRecord) || runCatching { parseCsvRecord(firstRecord) }.isSuccess
            }
        }.getOrDefault(false)
    }

    /**
     * 导出历史记录为加密备份（AES-256/GCM，密码派生密钥）。
     */
    suspend fun exportEncryptedJson(context: Context, password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        BackupCrypto.encrypt(exportToJson(context).toByteArray(Charsets.UTF_8), password)
    }

    /**
     * 从加密备份导入历史记录。
     * 密码错误或文件损坏时返回失败结果，不抛异常。
     */
    suspend fun importEncrypted(context: Context, data: ByteArray, password: CharArray): BackupResult = withContext(Dispatchers.IO) {
        try {
            val json = BackupCrypto.decrypt(data, password).toString(Charsets.UTF_8)
            importFromJson(context, json)
        } catch (e: Exception) {
            BackupResult(false, 0, context.getString(R.string.backup_decrypt_failed))
        }
    }

    /**
     * 从 JSON 导入历史记录
     */
    suspend fun importFromJson(context: Context, jsonString: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val rootObject = JSONObject(jsonString)
            
            // 检查版本
            val version = rootObject.optionalInt("version", 1)
            if (version > 1) {
                return@withContext BackupResult(
                    false,
                    0,
                    context.getString(R.string.backup_import_unsupported_version, version)
                )
            }

            val itemsArray = rootObject.getJSONArray("items")
            val items = List(itemsArray.length()) { index ->
                parseJsonItem(itemsArray.getJSONObject(index))
            }

            if (items.isEmpty()) {
                return@withContext BackupResult(
                    false,
                    0,
                    context.getString(R.string.backup_import_no_valid_records)
                )
            }
            val writtenCount = HistoryRepository(context).restoreHistoryItems(items)
            BackupResult(true, writtenCount, context.getString(R.string.batch_items_imported, writtenCount))
        } catch (e: Exception) {
            BackupResult(false, 0, context.getString(R.string.backup_import_invalid_structure))
        }
    }

    /**
     * 导出到 CSV
     */
    suspend fun exportToCsv(context: Context): String = withContext(Dispatchers.IO) {
        val repository = HistoryRepository(context)
        val items = repository.allHistory.first()

        StringWriter().use { writer ->
            CSVPrinter(writer, BACKUP_CSV_FORMAT).use { printer ->
                printer.printRecord(*CSV_HEADERS)
                items.forEach { item ->
                    printer.printRecord(
                        item.content,
                        item.type.name,
                        item.timestamp,
                        item.isGenerated,
                        item.barcodeFormat ?: "",
                        item.isFavorite,
                        item.notes ?: "",
                        item.tags ?: "",
                        item.styleJson ?: ""
                    )
                }
            }
            writer.toString()
        }
    }

    /**
     * 导出为 XLSX（二进制 ByteArray，与 CSV 相同的列）。
     */
    suspend fun exportToXlsx(context: Context): ByteArray = withContext(Dispatchers.IO) {
        val repository = HistoryRepository(context)
        val items = repository.allHistory.first()

        val headers = arrayOf(
            "content", "type", "timestamp", "isGenerated", "barcodeFormat",
            "isFavorite", "notes", "tags", "styleJson"
        )
        ByteArrayOutputStream().use { output ->
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("History")
                val headerRow = sheet.createRow(0)
                headers.forEachIndexed { index, header ->
                    headerRow.createCell(index).setCellValue(header)
                }
                items.forEachIndexed { rowIndex, item ->
                    val row = sheet.createRow(rowIndex + 1)
                    row.createCell(0).setCellValue(item.content)
                    row.createCell(1).setCellValue(item.type.name)
                    row.createCell(2).setCellValue(item.timestamp.toDouble())
                    row.createCell(3).setCellValue(item.isGenerated)
                    row.createCell(4).setCellValue(item.barcodeFormat ?: "")
                    row.createCell(5).setCellValue(item.isFavorite)
                    row.createCell(6).setCellValue(item.notes ?: "")
                    row.createCell(7).setCellValue(item.tags ?: "")
                    row.createCell(8).setCellValue(item.styleJson ?: "")
                }
                workbook.write(output)
            }
            output.toByteArray()
        }
    }

    /**
     * 从 CSV 导入
     */
    suspend fun importFromCsv(context: Context, csvString: String): BackupResult =
        importFromCsv(context, StringReader(csvString))

    /** Parses logical CSV records from [reader], including quoted CR/LF fields. */
    suspend fun importFromCsv(context: Context, reader: Reader): BackupResult = withContext(Dispatchers.IO) {
        try {
            val items = mutableListOf<HistoryItem>()
            CSVParser.parse(reader, BACKUP_CSV_FORMAT).use { parser ->
                parser.forEachIndexed { index, record ->
                    if (index == 0 && isCsvHeader(record)) return@forEachIndexed
                    items += parseCsvRecord(record)
                }
            }

            if (items.isEmpty()) {
                return@withContext BackupResult(
                    false,
                    0,
                    context.getString(R.string.backup_import_no_valid_records)
                )
            }
            val writtenCount = HistoryRepository(context).restoreHistoryItems(items)
            BackupResult(true, writtenCount, context.getString(R.string.batch_items_imported, writtenCount))
        } catch (e: Exception) {
            BackupResult(false, 0, context.getString(R.string.backup_import_invalid_structure))
        }
    }

    private fun isCsvHeader(record: CSVRecord): Boolean =
        record.size() in 4..CSV_HEADERS.size &&
            (0 until record.size()).all { record[it] == CSV_HEADERS[it] }

    private fun parseCsvRecord(record: CSVRecord): HistoryItem {
        require(record.size() in 4..CSV_HEADERS.size && record[0].isNotBlank())
        val generated = requireNotNull(record[3].toStrictBoolean())
        val favoriteText = record.getOrNull(5)
        val favorite = if (favoriteText.isNullOrBlank()) {
            false
        } else {
            requireNotNull(favoriteText.toStrictBoolean())
        }
        return HistoryItem(
            content = record[0],
            type = HistoryType.valueOf(record[1]),
            timestamp = record[2].toLong(),
            isGenerated = generated,
            barcodeFormat = record.getOrNull(4)?.takeIf { it.isNotBlank() },
            isFavorite = favorite,
            notes = record.getOrNull(6)?.takeIf { it.isNotBlank() },
            tags = record.getOrNull(7)?.takeIf { it.isNotBlank() },
            styleJson = record.getOrNull(8)?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseJsonItem(item: JSONObject): HistoryItem {
        val content = item.requiredString("content")
        require(content.isNotBlank())
        return HistoryItem(
            content = content,
            type = HistoryType.valueOf(item.requiredString("type")),
            timestamp = item.optionalLong("timestamp", System.currentTimeMillis()),
            isGenerated = item.optionalBoolean("isGenerated", false),
            barcodeFormat = item.optionalString("barcodeFormat"),
            isFavorite = item.optionalBoolean("isFavorite", false),
            notes = item.optionalString("notes"),
            tags = item.optionalString("tags"),
            styleJson = item.optionalString("styleJson")
        )
    }

    private fun JSONObject.requiredString(name: String): String =
        get(name) as? String ?: throw JSONException("$name must be a string")

    private fun JSONObject.optionalString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return get(name) as? String ?: throw JSONException("$name must be a string or null")
    }

    private fun JSONObject.optionalBoolean(name: String, default: Boolean): Boolean {
        if (!has(name)) return default
        return get(name) as? Boolean ?: throw JSONException("$name must be a boolean")
    }

    private fun JSONObject.optionalInt(name: String, default: Int): Int {
        if (!has(name)) return default
        return when (val value = get(name)) {
            is Byte, is Short, is Int -> (value as Number).toInt()
            is Long -> value.toInt().takeIf { it.toLong() == value }
                ?: throw JSONException("$name is outside the integer range")
            else -> throw JSONException("$name must be an integer")
        }
    }

    private fun JSONObject.optionalLong(name: String, default: Long): Long {
        if (!has(name)) return default
        return when (val value = get(name)) {
            is Byte, is Short, is Int, is Long -> (value as Number).toLong()
            else -> throw JSONException("$name must be an integer")
        }
    }

    private fun CSVRecord.getOrNull(index: Int): String? =
        if (index < size()) get(index) else null

    private fun String.toStrictBoolean(): Boolean? = when {
        equals("true", ignoreCase = true) -> true
        equals("false", ignoreCase = true) -> false
        else -> null
    }

    /**
     * 生成备份文件名
     */
    fun generateBackupFileName(format: String = "json"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "qr_backup_$timestamp.$format"
    }

    private val CSV_HEADERS = arrayOf(
        "content", "type", "timestamp", "isGenerated", "barcodeFormat",
        "isFavorite", "notes", "tags", "styleJson"
    )
    private val BACKUP_CSV_FORMAT = CSVFormat.Builder.create(CSVFormat.DEFAULT)
        .setIgnoreEmptyLines(true)
        .get()
}

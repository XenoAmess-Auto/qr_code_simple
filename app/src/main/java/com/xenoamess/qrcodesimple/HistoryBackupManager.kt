package com.xenoamess.qrcodesimple

import android.content.Context
import android.net.Uri
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
     * 从 JSON 导入历史记录
     */
    suspend fun importFromJson(context: Context, jsonString: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val repository = HistoryRepository(context)
            val rootObject = JSONObject(jsonString)
            
            // 检查版本
            val version = rootObject.optInt("version", 1)
            if (version > 1) {
                return@withContext BackupResult(false, 0, "Unsupported backup version: $version")
            }

            val itemsArray = rootObject.getJSONArray("items")
            var importedCount = 0

            for (i in 0 until itemsArray.length()) {
                val itemObject = itemsArray.getJSONObject(i)
                
                val item = HistoryItem(
                    content = itemObject.getString("content"),
                    type = HistoryType.valueOf(itemObject.getString("type")),
                    timestamp = itemObject.optLong("timestamp", System.currentTimeMillis()),
                    isGenerated = itemObject.optBoolean("isGenerated", false),
                    barcodeFormat = itemObject.optString("barcodeFormat").takeIf { it != "null" },
                    isFavorite = itemObject.optBoolean("isFavorite", false),
                    notes = itemObject.optString("notes").takeIf { it != "null" }
                )

                try {
                    repository.insert(item)
                    importedCount++
                } catch (e: Exception) {
                    // 忽略重复项错误
                }
            }

            BackupResult(true, importedCount, "Imported $importedCount items successfully")
        } catch (e: Exception) {
            BackupResult(false, 0, "Import failed: ${e.message}")
        }
    }

    /**
     * 导出到 CSV
     */
    suspend fun exportToCsv(context: Context): String = withContext(Dispatchers.IO) {
        val repository = HistoryRepository(context)
        val items = repository.allHistory.first()

        val csvBuilder = StringBuilder()
        csvBuilder.appendLine("content,type,timestamp,isGenerated,barcodeFormat,isFavorite,notes")

        items.forEach { item ->
            val line = buildString {
                append(escapeCsv(item.content))
                append(",")
                append(item.type.name)
                append(",")
                append(item.timestamp)
                append(",")
                append(item.isGenerated)
                append(",")
                append(item.barcodeFormat ?: "")
                append(",")
                append(item.isFavorite)
                append(",")
                append(escapeCsv(item.notes ?: ""))
            }
            csvBuilder.appendLine(line)
        }

        csvBuilder.toString()
    }

    /**
     * 从 CSV 导入
     */
    suspend fun importFromCsv(context: Context, csvString: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val repository = HistoryRepository(context)
            val lines = csvString.lines()
            
            if (lines.isEmpty()) {
                return@withContext BackupResult(false, 0, "Empty CSV file")
            }

            // 跳过标题行
            val dataLines = if (lines[0].contains("content,type")) lines.drop(1) else lines
            
            var importedCount = 0

            dataLines.filter { it.isNotBlank() }.forEach { line ->
                try {
                    val parts = parseCsvLine(line)
                    if (parts.size >= 4) {
                        val item = HistoryItem(
                            content = parts[0],
                            type = HistoryType.valueOf(parts[1]),
                            timestamp = parts[2].toLongOrNull() ?: System.currentTimeMillis(),
                            isGenerated = parts[3].toBoolean(),
                            barcodeFormat = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                            isFavorite = parts.getOrNull(5)?.toBoolean() ?: false,
                            notes = parts.getOrNull(6)?.takeIf { it.isNotBlank() }
                        )

                        try {
                            repository.insert(item)
                            importedCount++
                        } catch (e: Exception) {
                            // 忽略重复项
                        }
                    }
                } catch (e: Exception) {
                    // 跳过无效行
                }
            }

            BackupResult(true, importedCount, "Imported $importedCount items successfully")
        } catch (e: Exception) {
            BackupResult(false, 0, "Import failed: ${e.message}")
        }
    }

    /**
     * 转义 CSV 字段
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * 解析 CSV 行
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> {
                    if (inQuotes && line.getOrNull(line.indexOf(char) + 1) == '"') {
                        current.append('"')
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * 生成备份文件名
     */
    fun generateBackupFileName(format: String = "json"): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "qr_backup_$timestamp.$format"
    }
}

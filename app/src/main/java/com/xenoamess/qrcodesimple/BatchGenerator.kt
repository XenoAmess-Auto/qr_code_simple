package com.xenoamess.qrcodesimple

import android.content.Context
import android.net.Uri
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.InputStreamReader

/**
 * CSV/Excel 数据导入和批量生成管理器
 */
object BatchGenerator {

    data class BatchItem(
        val content: String,
        val format: BarcodeFormat = BarcodeFormat.QR_CODE,
        val foregroundColor: Int = android.graphics.Color.BLACK,
        val backgroundColor: Int = android.graphics.Color.WHITE,
        val fileName: String? = null
    )

    data class BatchResult(
        val items: List<BatchItem>,
        val errors: List<String>
    )

    /**
     * 从 CSV 文件解析批量生成数据
     * CSV 格式: content,format,fg_color,bg_color,filename
     */
    suspend fun parseCsv(context: Context, uri: Uri): BatchResult = withContext(Dispatchers.IO) {
        val items = mutableListOf<BatchItem>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val csvParser = CSVParser.parse(
                    reader,
                    CSVFormat.DEFAULT
                        .withFirstRecordAsHeader()
                        .withIgnoreHeaderCase()
                        .withTrim()
                )

                var lineNumber = 1
                for (record in csvParser) {
                    lineNumber++
                    try {
                        val item = parseCsvRecord(record)
                        items.add(item)
                    } catch (e: Exception) {
                        errors.add("Line $lineNumber: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to parse CSV: ${e.message}")
        }

        BatchResult(items, errors)
    }

    /**
     * 解析单行 CSV 记录
     */
    private fun parseCsvRecord(record: CSVRecord): BatchItem {
        val content = record.get("content")?.trim()
            ?: throw IllegalArgumentException("Missing 'content' column")

        if (content.isEmpty()) {
            throw IllegalArgumentException("Content cannot be empty")
        }

        val format = try {
            record.get("format")?.let { BarcodeFormat.valueOf(it.uppercase()) }
                ?: BarcodeFormat.QR_CODE
        } catch (e: Exception) {
            BarcodeFormat.QR_CODE
        }

        val fgColor = parseColor(record.get("fg_color"), android.graphics.Color.BLACK)
        val bgColor = parseColor(record.get("bg_color"), android.graphics.Color.WHITE)
        val fileName = record.get("filename")?.trim()

        return BatchItem(content, format, fgColor, bgColor, fileName)
    }

    /**
     * 解析颜色字符串 (#RRGGBB 或颜色名称)
     */
    private fun parseColor(colorStr: String?, defaultColor: Int): Int {
        if (colorStr.isNullOrBlank()) return defaultColor

        return try {
            if (colorStr.startsWith("#")) {
                android.graphics.Color.parseColor(colorStr)
            } else {
                when (colorStr.lowercase()) {
                    "black" -> android.graphics.Color.BLACK
                    "white" -> android.graphics.Color.WHITE
                    "red" -> android.graphics.Color.RED
                    "green" -> android.graphics.Color.GREEN
                    "blue" -> android.graphics.Color.BLUE
                    "cyan" -> android.graphics.Color.CYAN
                    "magenta" -> android.graphics.Color.MAGENTA
                    "yellow" -> android.graphics.Color.YELLOW
                    else -> defaultColor
                }
            }
        } catch (e: Exception) {
            defaultColor
        }
    }

    /**
     * 批量生成条码
     */
    suspend fun generateBatch(
        items: List<BatchItem>,
        onProgress: suspend (current: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Pair<BatchItem, android.graphics.Bitmap?>> = withContext(Dispatchers.Default) {
        items.mapIndexed { index, item ->
            val config = BarcodeGenerator.BarcodeConfig(
                format = item.format,
                width = 800,
                height = 600,
                foregroundColor = item.foregroundColor,
                backgroundColor = item.backgroundColor
            )

            val bitmap = try {
                BarcodeGenerator.generate(item.content, config)
            } catch (e: Exception) {
                null
            }

            onProgress(index + 1, items.size)
            item to bitmap
        }
    }

    /**
     * 生成 CSV 模板内容
     */
    fun generateTemplate(): String {
        return buildString {
            appendLine("content,format,fg_color,bg_color,filename")
            appendLine("https://example.com,QR_CODE,#000000,#FFFFFF,example_qr")
            appendLine("1234567890123,EAN_13,#000000,#FFFFFF,product_ean13")
            appendLine("ABC123,CODE_128,#FF0000,#FFFFFF,code128_red")
            appendLine("Hello World,DATA_MATRIX,#0000FF,#FFFFFF,data_matrix")
        }
    }

    /**
     * 简单的批量生成（无需 CSV，直接输入多行文本）
     */
    fun parseSimpleBatch(
        text: String,
        format: BarcodeFormat = BarcodeFormat.QR_CODE
    ): List<BatchItem> {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, content ->
                BatchItem(
                    content = content,
                    format = format,
                    fileName = "batch_${index + 1}"
                )
            }
    }
}

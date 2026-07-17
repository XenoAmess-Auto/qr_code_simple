package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Color
import android.net.Uri
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStreamReader

/**
 * CSV/Excel 数据导入和批量生成管理器
 */
object BatchGenerator {

    data class BatchItem(
        val content: String,
        val format: BarcodeFormat = BarcodeFormat.QR_CODE,
        val foregroundColor: Int = Color.BLACK,
        val backgroundColor: Int = Color.WHITE,
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
                val csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build()
                val csvParser = CSVParser.parse(reader, csvFormat)

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
     * 从 Excel 文件解析批量生成数据
     * 兼容：
     * 1. 有标题行，列名 content/format/filename/fg_color/bg_color
     * 2. 无标题行，单列内容按顺序读取
     * 3. 列数不足时默认 QR_CODE
     */
    suspend fun parseExcel(context: Context, uri: Uri): BatchResult = withContext(Dispatchers.IO) {
        val items = mutableListOf<BatchItem>()
        val errors = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)

                val rowIterator = sheet.iterator()
                if (!rowIterator.hasNext()) {
                    return@withContext BatchResult(emptyList(), listOf("Empty Excel file"))
                }

                val firstRow = rowIterator.next()
                val headerIndex = parseExcelHeader(firstRow)
                val hasHeader = headerIndex["content"] != null

                var rowNumber = 1

                if (!hasHeader) {
                    try {
                        parseExcelRow(firstRow, headerIndex, false, rowNumber)?.let { item ->
                            items.add(item)
                        }
                    } catch (e: Exception) {
                        errors.add("Row $rowNumber: ${e.message}")
                    }
                }

                while (rowIterator.hasNext()) {
                    rowNumber++
                    val row = rowIterator.next()
                    try {
                        parseExcelRow(row, headerIndex, hasHeader, rowNumber)?.let { item ->
                            items.add(item)
                        }
                    } catch (e: Exception) {
                        errors.add("Row $rowNumber: ${e.message}")
                    }
                }

                workbook.close()
            }
        } catch (e: Exception) {
            errors.add("Failed to parse Excel: ${e.message}")
        }

        BatchResult(items, errors)
    }

    private fun parseExcelHeader(row: Row): Map<String, Int> {
        val headerMap = mutableMapOf<String, Int>()
        for (cell in row) {
            val value = getCellString(cell)?.trim()?.lowercase() ?: continue
            when (value) {
                "content" -> headerMap["content"] = cell.columnIndex
                "format" -> headerMap["format"] = cell.columnIndex
                "filename" -> headerMap["filename"] = cell.columnIndex
                "fg_color", "foreground_color", "foregroundcolor" -> headerMap["fg_color"] = cell.columnIndex
                "bg_color", "background_color", "backgroundcolor" -> headerMap["bg_color"] = cell.columnIndex
            }
        }
        return headerMap
    }

    private fun parseExcelRow(row: Row, headerIndex: Map<String, Int>, hasHeader: Boolean, rowNumber: Int): BatchItem? {
        val content = if (hasHeader) {
            headerIndex["content"]?.let { getCellString(row.getCell(it)) }?.trim()
        } else {
            getCellString(row.getCell(0))?.trim()
        } ?: throw IllegalArgumentException("Missing content")

        if (content.isEmpty()) return null

        val format = if (hasHeader && headerIndex["format"] != null) {
            try {
                BarcodeFormat.valueOf(getCellString(row.getCell(headerIndex["format"]!!))?.uppercase() ?: "")
            } catch (e: Exception) {
                BarcodeFormat.QR_CODE
            }
        } else {
            BarcodeFormat.QR_CODE
        }

        val fileName = if (hasHeader && headerIndex["filename"] != null) {
            getCellString(row.getCell(headerIndex["filename"]!!))?.trim()
        } else null

        val fgColor = if (hasHeader && headerIndex["fg_color"] != null) {
            parseColor(getCellString(row.getCell(headerIndex["fg_color"]!!)), Color.BLACK)
        } else Color.BLACK

        val bgColor = if (hasHeader && headerIndex["bg_color"] != null) {
            parseColor(getCellString(row.getCell(headerIndex["bg_color"]!!)), Color.WHITE)
        } else Color.WHITE

        return BatchItem(content, format, fgColor, bgColor, fileName)
    }

    private fun getCellString(cell: Cell?): String? {
        return when (cell?.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toString()
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> cell.stringCellValue
            else -> null
        }
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

        // 可选列：CSV 表头缺失该列时 record.get(name) 会抛 IllegalArgumentException，
        // 必须先 isMapped 判断，否则只有部分列的用户 CSV 会整行失败。
        fun optionalColumn(name: String): String? =
            if (record.isMapped(name)) record.get(name) else null

        val format = try {
            optionalColumn("format")?.let { BarcodeFormat.valueOf(it.uppercase()) }
                ?: BarcodeFormat.QR_CODE
        } catch (e: Exception) {
            BarcodeFormat.QR_CODE
        }

        val fgColor = parseColor(optionalColumn("fg_color"), Color.BLACK)
        val bgColor = parseColor(optionalColumn("bg_color"), Color.WHITE)
        val fileName = optionalColumn("filename")?.trim()?.takeIf { it.isNotEmpty() }

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

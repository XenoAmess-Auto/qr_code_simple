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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

/**
 * CSV/Excel 数据导入和批量生成管理器
 */
object BatchGenerator {

    internal const val MAX_IMPORT_SOURCE_BYTES = 4 * 1024 * 1024
    internal const val MAX_IMPORT_PHYSICAL_ROWS = 1_000
    internal const val MAX_IMPORT_ERRORS = 100
    private const val MAX_IMPORT_FIELDS = MAX_IMPORT_PHYSICAL_ROWS * 16

    private class ImportLimitException(val limit: BatchResultTransfer.Limit) : Exception()

    private class ImportBudget {
        private var rows = 0
        private var fields = 0
        private var bytes = 0L

        fun addRow(values: List<String>): BatchResultTransfer.Limit? {
            rows++
            if (rows > MAX_IMPORT_PHYSICAL_ROWS) return BatchResultTransfer.Limit.ITEM_COUNT
            fields += values.size
            if (fields > MAX_IMPORT_FIELDS) return BatchResultTransfer.Limit.ITEM_COUNT
            for (value in values) {
                if (value.length > BatchResultTransfer.MAX_ITEM_CHARACTERS) {
                    return BatchResultTransfer.Limit.ITEM_LENGTH
                }
                bytes += BatchResultTransfer.serializedStringBytes(value)
                if (bytes > BatchResultTransfer.MAX_SERIALIZED_BYTES) {
                    return BatchResultTransfer.Limit.TOTAL_BYTES
                }
            }
            return null
        }
    }

    data class BatchItem(
        val content: String,
        val format: BarcodeFormat = BarcodeFormat.QR_CODE,
        /** Null means the batch style/default color should be used. */
        val foregroundColor: Int? = null,
        /** Null means the batch style/default color should be used. */
        val backgroundColor: Int? = null,
        val fileName: String? = null
    )

    data class BatchResult(
        val items: List<BatchItem>,
        val errors: List<String>,
        val limitExceeded: BatchResultTransfer.Limit? = null
    )

    /**
     * 从 CSV 文件解析批量生成数据
     * CSV 格式: content,format,fg_color,bg_color,filename
     */
    suspend fun parseCsv(context: Context, uri: Uri): BatchResult = withContext(Dispatchers.IO) {
        val items = mutableListOf<BatchItem>()
        val errors = mutableListOf<String>()
        val importBudget = ImportBudget()
        val itemBudget = BatchResultTransfer.Budget()

        try {
            val source = readImportSource(context, uri)
            ByteArrayInputStream(source).use { inputStream ->
                val reader = InputStreamReader(inputStream, Charsets.UTF_8)
                val csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .get()
                CSVParser.parse(reader, csvFormat).use { csvParser ->
                    importBudget.addRow(csvParser.headerNames)?.let { limit ->
                        return@withContext BatchResult(items, errors, limit)
                    }

                    var lineNumber = 1
                    for (record in csvParser) {
                        lineNumber++
                        importBudget.addRow(record.toList())?.let { limit ->
                            return@withContext BatchResult(items, errors, limit)
                        }
                        try {
                            val item = parseCsvRecord(record)
                            itemBudget.add(item)?.let { limit ->
                                return@withContext BatchResult(items, errors, limit)
                            }
                            items.add(item)
                        } catch (e: Exception) {
                            if (!addImportError(errors, "Line $lineNumber: ${e.message}")) {
                                return@withContext BatchResult(items, errors, BatchResultTransfer.Limit.ITEM_COUNT)
                            }
                        }
                    }
                }
            }
        } catch (e: ImportLimitException) {
            return@withContext BatchResult(items, errors, e.limit)
        } catch (e: Exception) {
            addImportError(errors, "Failed to parse CSV: ${e.message}")
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
        val importBudget = ImportBudget()
        val itemBudget = BatchResultTransfer.Budget()

        try {
            val source = readImportSource(context, uri)
            ByteArrayInputStream(source).use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                workbook.use {
                    val sheet = workbook.getSheetAt(0)
                    if (sheet.physicalNumberOfRows > MAX_IMPORT_PHYSICAL_ROWS) {
                        return@withContext BatchResult(items, errors, BatchResultTransfer.Limit.ITEM_COUNT)
                    }

                    val rowIterator = sheet.iterator()
                    if (!rowIterator.hasNext()) {
                        return@withContext BatchResult(emptyList(), listOf("Empty Excel file"))
                    }

                    val firstRow = rowIterator.next()
                    importBudget.addRow(excelRowValues(firstRow))?.let { limit ->
                        return@withContext BatchResult(items, errors, limit)
                    }
                    val headerIndex = parseExcelHeader(firstRow)
                    val hasHeader = headerIndex["content"] != null

                    if (!hasHeader) {
                        try {
                            parseExcelRow(firstRow, headerIndex, false)?.let { item ->
                                itemBudget.add(item)?.let { limit ->
                                    return@withContext BatchResult(items, errors, limit)
                                }
                                items.add(item)
                            }
                        } catch (e: Exception) {
                            if (!addImportError(errors, "Row ${firstRow.rowNum + 1}: ${e.message}")) {
                                return@withContext BatchResult(items, errors, BatchResultTransfer.Limit.ITEM_COUNT)
                            }
                        }
                    }

                    while (rowIterator.hasNext()) {
                        val row = rowIterator.next()
                        importBudget.addRow(excelRowValues(row))?.let { limit ->
                            return@withContext BatchResult(items, errors, limit)
                        }
                        try {
                            parseExcelRow(row, headerIndex, hasHeader)?.let { item ->
                                itemBudget.add(item)?.let { limit ->
                                    return@withContext BatchResult(items, errors, limit)
                                }
                                items.add(item)
                            }
                        } catch (e: Exception) {
                            if (!addImportError(errors, "Row ${row.rowNum + 1}: ${e.message}")) {
                                return@withContext BatchResult(items, errors, BatchResultTransfer.Limit.ITEM_COUNT)
                            }
                        }
                    }
                }
            }
        } catch (e: ImportLimitException) {
            return@withContext BatchResult(items, errors, e.limit)
        } catch (e: Exception) {
            addImportError(errors, "Failed to parse Excel: ${e.message}")
        }

        BatchResult(items, errors)
    }

    private fun readImportSource(context: Context, uri: Uri): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open import file")
        input.use {
            var total = 0
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_IMPORT_SOURCE_BYTES) {
                    throw ImportLimitException(BatchResultTransfer.Limit.TOTAL_BYTES)
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun addImportError(errors: MutableList<String>, error: String): Boolean {
        if (errors.size >= MAX_IMPORT_ERRORS) return false
        errors.add(error)
        return true
    }

    private fun excelRowValues(row: Row): List<String> = row.mapNotNull(::getCellString)

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

    private fun parseExcelRow(row: Row, headerIndex: Map<String, Int>, hasHeader: Boolean): BatchItem? {
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
            parseColor(getCellString(row.getCell(headerIndex["fg_color"]!!)))
        } else null

        val bgColor = if (hasHeader && headerIndex["bg_color"] != null) {
            parseColor(getCellString(row.getCell(headerIndex["bg_color"]!!)))
        } else null

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

        val fgColor = parseColor(optionalColumn("fg_color"))
        val bgColor = parseColor(optionalColumn("bg_color"))
        val fileName = optionalColumn("filename")?.trim()?.takeIf { it.isNotEmpty() }

        return BatchItem(content, format, fgColor, bgColor, fileName)
    }

    /**
     * 解析颜色字符串 (#RRGGBB 或颜色名称)
     */
    private fun parseColor(colorStr: String?): Int? {
        if (colorStr.isNullOrBlank()) return null

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
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
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
                foregroundColor = item.foregroundColor ?: Color.BLACK,
                backgroundColor = item.backgroundColor ?: Color.WHITE
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
    ): List<BatchItem> = buildList {
        for (line in text.lineSequence()) {
            val content = line.trim()
            if (content.isEmpty()) continue
            add(BatchItem(content = content, format = format, fileName = "batch_${size + 1}"))
            if (size > BatchResultTransfer.MAX_ITEMS) break
        }
    }

    fun resolveBatchInput(
        text: String,
        selectedFormat: BarcodeFormat,
        importedItems: List<BatchItem>?
    ): List<BatchItem> = importedItems
        ?.takeIf { imported -> text == imported.joinToString("\n") { it.content } }
        ?: parseSimpleBatch(text, selectedFormat)

    /** Compact, primitive-only representation safe for Intent and process recreation. */
    fun itemsToJson(items: List<BatchItem>): String = JSONArray().apply {
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

    fun itemsFromJson(json: String?): List<BatchItem> = runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
            val value = array.getJSONObject(index)
            BatchItem(
                content = value.getString("content"),
                format = BarcodeFormat.valueOf(value.optString("format", BarcodeFormat.QR_CODE.name)),
                foregroundColor = value.takeIf { it.has("foregroundColor") }?.getInt("foregroundColor"),
                backgroundColor = value.takeIf { it.has("backgroundColor") }?.getInt("backgroundColor"),
                fileName = value.optString("fileName").takeIf { it.isNotEmpty() }
            )
        }
    }.getOrDefault(emptyList())
}

package com.xenoamess.qrcodesimple

import org.apache.poi.xssf.usermodel.XSSFWorkbook

/** Small, format-neutral exporters for transient scan sessions. */
object ScanSessionExporter {
    data class Row(val content: String, val format: String, val timestamp: Long, val saved: Boolean)

    fun csv(rows: List<Row>): String = buildString {
        append("content,format,timestamp,saved\n")
        rows.forEach { append(listOf(it.content, it.format, it.timestamp, it.saved).joinToString(",") { csvField(it.toString()) }).append('\n') }
    }

    fun json(rows: List<Row>): String = rows.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") {
        "  {\"content\":${jsonString(it.content)},\"format\":${jsonString(it.format)},\"timestamp\":${it.timestamp},\"saved\":${it.saved}}"
    }

    fun xlsx(rows: List<Row>): ByteArray = XSSFWorkbook().use { workbook ->
        val sheet = workbook.createSheet("Scan session")
        sheet.createRow(0).apply { listOf("content", "format", "timestamp", "saved").forEachIndexed { index, value -> createCell(index).setCellValue(value) } }
        rows.forEachIndexed { index, row ->
            sheet.createRow(index + 1).apply {
                createCell(0).setCellValue(row.content)
                createCell(1).setCellValue(row.format)
                createCell(2).setCellValue(row.timestamp.toDouble())
                createCell(3).setCellValue(row.saved)
            }
        }
        java.io.ByteArrayOutputStream().use { output -> workbook.write(output); output.toByteArray() }
    }

    private fun csvField(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"${value.replace("\"", "\"\"")}\"" else value

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach {
            append(when (it) {
                '\\' -> "\\\\"
                '"' -> "\\\""
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> if (it < ' ') "\\u%04x".format(it.code) else it
            })
        }
        append('"')
    }
}

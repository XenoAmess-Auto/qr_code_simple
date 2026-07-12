package com.xenoamess.qrcodesimple

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import kotlinx.coroutines.runBlocking
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BatchGenerator 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BatchGeneratorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createCsvFile(content: String, filename: String = "test.csv"): Uri {
        val file = File(context.cacheDir, filename)
        file.writeText(content)
        return Uri.fromFile(file)
    }

    private fun createExcelFile(rows: List<List<String?>>, filename: String = "test.xlsx"): Uri {
        val workbook: Workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        rows.forEachIndexed { rowIndex, rowData ->
            val row = sheet.createRow(rowIndex)
            rowData.forEachIndexed { colIndex, value ->
                if (value != null) {
                    row.createCell(colIndex).setCellValue(value)
                }
            }
        }

        val file = File(context.cacheDir, filename)
        FileOutputStream(file).use { out ->
            workbook.write(out)
        }
        workbook.close()
        return Uri.fromFile(file)
    }

    @Test
    fun `parseCsv with valid data returns items`() {
        runBlocking {
            val csv = """
                content,format,fg_color,bg_color,filename
                https://example.com,QR_CODE,#000000,#FFFFFF,example_qr
                1234567890123,EAN_13,#FF0000,#FFFFFF,ean13
            """.trimIndent()

            val result = BatchGenerator.parseCsv(context, createCsvFile(csv))

            assertEquals(2, result.items.size)
            assertEquals(0, result.errors.size)
            assertEquals("https://example.com", result.items[0].content)
            assertEquals(BarcodeFormat.QR_CODE, result.items[0].format)
            assertEquals(android.graphics.Color.parseColor("#000000"), result.items[0].foregroundColor)
            assertEquals(android.graphics.Color.parseColor("#FFFFFF"), result.items[0].backgroundColor)
            assertEquals("example_qr", result.items[0].fileName)
        }
    }

    @Test
    fun `parseCsv with missing optional fields uses defaults`() {
        runBlocking {
            val csv = """
                content,format,fg_color,bg_color,filename
                hello world,QR_CODE,,,
            """.trimIndent()

            val result = BatchGenerator.parseCsv(context, createCsvFile(csv))

            assertEquals(1, result.items.size)
            assertEquals(android.graphics.Color.BLACK, result.items[0].foregroundColor)
            assertEquals(android.graphics.Color.WHITE, result.items[0].backgroundColor)
            assertEquals(null, result.items[0].fileName)
        }
    }

    @Test
    fun `parseCsv with invalid format falls back to QR_CODE`() {
        runBlocking {
            val csv = """
                content,format,fg_color,bg_color,filename
                test,INVALID_FORMAT,,,
            """.trimIndent()

            val result = BatchGenerator.parseCsv(context, createCsvFile(csv))
            assertEquals(1, result.items.size)
            assertEquals(BarcodeFormat.QR_CODE, result.items[0].format)
        }
    }

    @Test
    fun `parseCsv reports line errors for empty content`() {
        runBlocking {
            val csv = """
                content,format
                ,QR_CODE
            """.trimIndent()

            val result = BatchGenerator.parseCsv(context, createCsvFile(csv))
            assertEquals(0, result.items.size)
            assertTrue(result.errors.isNotEmpty())
        }
    }

    @Test
    fun `parseCsv with color names returns correct colors`() {
        runBlocking {
            val csv = """
                content,format,fg_color,bg_color,filename
                test,QR_CODE,red,blue,
            """.trimIndent()

            val result = BatchGenerator.parseCsv(context, createCsvFile(csv))
            assertEquals(1, result.items.size)
            assertEquals(android.graphics.Color.RED, result.items[0].foregroundColor)
            assertEquals(android.graphics.Color.BLUE, result.items[0].backgroundColor)
        }
    }

    @Test
    fun `parseExcel with header row returns items`() {
        runBlocking {
            val rows = listOf(
                listOf("content", "format", "filename", "fg_color", "bg_color"),
                listOf("https://example.com", "QR_CODE", "qr", "#000000", "#FFFFFF"),
                listOf("12345", "CODE_128", "code128", "#00FF00", "#000000")
            )
            val uri = createExcelFile(rows)

            val result = BatchGenerator.parseExcel(context, uri)

            assertEquals(2, result.items.size)
            assertEquals(0, result.errors.size)
            assertEquals("https://example.com", result.items[0].content)
            assertEquals(BarcodeFormat.QR_CODE, result.items[0].format)
            assertEquals("qr", result.items[0].fileName)
        }
    }

    @Test
    fun `parseExcel without header row reads first column`() {
        runBlocking {
            val rows = listOf(
                listOf("hello world"),
                listOf("second item"),
                listOf("third item")
            )
            val uri = createExcelFile(rows)

            val result = BatchGenerator.parseExcel(context, uri)

            assertEquals(3, result.items.size)
            assertEquals(BarcodeFormat.QR_CODE, result.items[0].format)
            assertEquals("hello world", result.items[0].content)
        }
    }

    @Test
    fun `parseExcel empty file returns error`() {
        runBlocking {
            val rows = listOf(listOf("content"))
            val uri = createExcelFile(rows)
            val result = BatchGenerator.parseExcel(context, uri)
            assertEquals(0, result.items.size)
        }
    }

    @Test
    fun `parseSimpleBatch splits lines and filters empties`() {
        val text = """
            line 1

            line 2
            
        """.trimIndent()

        val items = BatchGenerator.parseSimpleBatch(text, BarcodeFormat.CODE_128)
        assertEquals(2, items.size)
        assertEquals(BarcodeFormat.CODE_128, items[0].format)
        assertEquals("batch_1", items[0].fileName)
    }

    @Test
    fun `generateBatch produces bitmaps for valid items`() {
        runBlocking {
            val items = listOf(
                BatchGenerator.BatchItem("https://example.com", BarcodeFormat.QR_CODE),
                BatchGenerator.BatchItem("1234567890128", BarcodeFormat.EAN_13)
            )

            var progressCalls = 0
            val results = BatchGenerator.generateBatch(items) { _, _ -> progressCalls++ }

            assertEquals(2, results.size)
            assertEquals(2, progressCalls)
            assertNotNull(results[0].second)
            assertNotNull(results[1].second)
        }
    }

    @Test
    fun `generateBatch returns null for invalid content`() {
        runBlocking {
            val items = listOf(
                BatchGenerator.BatchItem("invalid ean content", BarcodeFormat.EAN_13)
            )

            val results = BatchGenerator.generateBatch(items)
            assertEquals(1, results.size)
            assertEquals(null, results[0].second)
        }
    }

    @Test
    fun `generateTemplate returns example csv content`() {
        val template = BatchGenerator.generateTemplate()
        assertTrue(template.contains("content,format,fg_color,bg_color,filename"))
        assertTrue(template.contains("https://example.com"))
        assertTrue(template.contains("QR_CODE"))
    }
}

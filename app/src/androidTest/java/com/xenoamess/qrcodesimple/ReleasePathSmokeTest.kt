package com.xenoamess.qrcodesimple

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Release-path smoke tests on the emulator: exercises the R8-sensitive flows
 * (SVG export, CSV/Excel batch import, batch ZIP export via MediaStore) that
 * Robolectric unit tests cannot cover end to end.
 */
@RunWith(AndroidJUnit4::class)
class ReleasePathSmokeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun svgExportProducesValidMarkup() {
        val qrSvg = SvgQRCodeGenerator.generateSVG(
            "https://example.com/release-smoke",
            BarcodeFormat.QR_CODE
        )
        assertTrue("QR SVG must be well-formed", qrSvg.startsWith("<?xml") && qrSvg.contains("<svg") && qrSvg.contains("</svg>"))

        val eanSvg = SvgQRCodeGenerator.generateSVG(
            "5901234123457",
            BarcodeFormat.EAN_13
        )
        assertTrue("EAN-13 SVG must be well-formed", eanSvg.startsWith("<?xml") && eanSvg.contains("<svg") && eanSvg.contains("</svg>"))
    }

    @Test
    fun batchCsvImportParsesRows() {
        runBlocking {
        val csvFile = File(context.filesDir, "release-smoke-batch.csv")
        csvFile.writeText(
            "content,format\n" +
                "https://csv-1.example.com,QR_CODE\n" +
                "https://csv-2.example.com,QR_CODE\n"
        )

        val result = BatchGenerator.parseCsv(context, Uri.fromFile(csvFile))

        assertEquals("both CSV rows must parse", 2, result.items.size)
        assertEquals(0, result.errors.size)
        assertEquals("https://csv-1.example.com", result.items[0].content)
        assertEquals(BarcodeFormat.QR_CODE, result.items[0].format)
        csvFile.delete()
        }
    }

    @Test
    fun batchExcelImportParsesRows() {
        runBlocking {
        val xlsxFile = File(context.filesDir, "release-smoke-batch.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Batch")
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("content")
            header.createCell(1).setCellValue("format")
            val row1 = sheet.createRow(1)
            row1.createCell(0).setCellValue("https://xlsx-1.example.com")
            row1.createCell(1).setCellValue("QR_CODE")
            val row2 = sheet.createRow(2)
            row2.createCell(0).setCellValue("https://xlsx-2.example.com")
            row2.createCell(1).setCellValue("QR_CODE")
            workbook.write(xlsxFile.outputStream())
        }

        val result = BatchGenerator.parseExcel(context, Uri.fromFile(xlsxFile))

        assertEquals("both Excel rows must parse", 2, result.items.size)
        assertEquals(0, result.errors.size)
        assertEquals("https://xlsx-1.example.com", result.items[0].content)
        xlsxFile.delete()
        }
    }

    @Test
    fun batchZipExportWritesMediaStore() {
        val intent = android.content.Intent(context, BatchResultActivity::class.java).apply {
            putStringArrayListExtra(
                BatchGenerateActivity.EXTRA_CONTENTS,
                arrayListOf("https://zip-1.example.com", "https://zip-2.example.com")
            )
            putExtra(BatchGenerateActivity.EXTRA_FORMAT, BarcodeFormat.QR_CODE.name)
        }
        ActivityScenario.launch<BatchResultActivity>(intent).use { scenario ->
            // Wait until the batch generation finished (progress text shows the count).
            assertTrue(
                "batch generation must finish",
                waitUntil { progressText(scenario)?.contains("Generated: 2/2") == true }
            )

            scenario.onActivity { activity ->
                activity.saveAllAsZip()
            }

            // The ZIP must be queryable through the MediaStore Downloads collection.
            assertTrue(
                "ZIP export must persist via MediaStore",
                waitUntil {
                    context.contentResolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                        "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                        arrayOf("batch_qr_%"),
                        null
                    ).use { cursor ->
                        cursor != null && cursor.moveToFirst()
                    }
                }
            )
        }
    }

    private fun progressText(scenario: ActivityScenario<BatchResultActivity>): String? {
        var text: String? = null
        scenario.onActivity { activity ->
            text = activity.findViewById<TextView>(R.id.tvProgress).text.toString()
        }
        return text
    }

    private fun waitUntil(maxMs: Long = 15_000, condition: () -> Boolean): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs) {
            if (condition()) return true
            android.os.SystemClock.sleep(200)
        }
        return condition()
    }
}

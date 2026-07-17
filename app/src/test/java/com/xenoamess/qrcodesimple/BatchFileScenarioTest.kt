package com.xenoamess.qrcodesimple

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * 批量生成用户场景：真实 CSV/Excel 文件导入、批量结果 ZIP/PNG 落盘。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BatchFileScenarioTest {

    private var generateScenario: ActivityScenario<BatchGenerateActivity>? = null
    private var resultScenario: ActivityScenario<BatchResultActivity>? = null

    @Before
    fun setup() {
        clearFileProviderCache()
    }

    @After
    fun tearDown() {
        generateScenario?.close()
        resultScenario?.close()
    }

    private fun clearFileProviderCache() {
        val field = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        field.isAccessible = true
        (field.get(null) as MutableMap<*, *>).clear()
    }

    private fun idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun waitUntil(timeoutMs: Long = 8000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            if (predicate()) return
            Thread.sleep(50)
        }
        idleMain()
    }

    private fun tempCsv(content: String): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "batch_${System.nanoTime()}.csv")
        file.writeText(content)
        return file
    }

    @Test
    fun `import csv file fills content editor with items`() {
        generateScenario = ActivityScenario.launch(BatchGenerateActivity::class.java)
        val csv = tempCsv("content,format\nhttps://a.com,QR_CODE\nhttps://b.com,QR_CODE\n")

        generateScenario?.onActivity { activity ->
            activity.importFromFile(Uri.fromFile(csv))
        }
        waitUntil {
            var filled = false
            generateScenario?.onActivity { activity ->
                filled = activity.findViewById<EditText>(R.id.etContent).text.toString().contains("https://a.com")
            }
            filled
        }

        generateScenario?.onActivity { activity ->
            val text = activity.findViewById<EditText>(R.id.etContent).text.toString()
            assertTrue(text.contains("https://a.com"))
            assertTrue(text.contains("https://b.com"))
        }
    }

    @Test
    fun `import excel file fills content editor with items`() {
        generateScenario = ActivityScenario.launch(BatchGenerateActivity::class.java)

        // 用 POI 生成真实 xlsx
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val xlsx = File(context.cacheDir, "batch_${System.nanoTime()}.xlsx")
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("data")
            val header = sheet.createRow(0)
            header.createCell(0).setCellValue("content")
            listOf("excel-item-1", "excel-item-2").forEachIndexed { i, v ->
                sheet.createRow(i + 1).createCell(0).setCellValue(v)
            }
            FileOutputStream(xlsx).use { wb.write(it) }
        }

        generateScenario?.onActivity { activity ->
            activity.importFromFile(Uri.fromFile(xlsx))
        }
        waitUntil {
            var filled = false
            generateScenario?.onActivity { activity ->
                filled = activity.findViewById<EditText>(R.id.etContent).text.toString().contains("excel-item-1")
            }
            filled
        }

        generateScenario?.onActivity { activity ->
            val text = activity.findViewById<EditText>(R.id.etContent).text.toString()
            assertTrue(text.contains("excel-item-1"))
            assertTrue(text.contains("excel-item-2"))
        }
    }

    @Test
    fun `save all as zip writes png entries to downloads`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), BatchResultActivity::class.java).apply {
            putStringArrayListExtra(BatchGenerateActivity.EXTRA_CONTENTS, arrayListOf("zip-item-1", "zip-item-2"))
            putExtra(BatchGenerateActivity.EXTRA_FORMAT, "QR_CODE")
        }
        resultScenario = ActivityScenario.launch<BatchResultActivity>(intent)

        // 等批量生成完成
        waitUntil {
            var ready = false
            resultScenario?.onActivity { activity ->
                val field = BatchResultActivity::class.java.getDeclaredField("results")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val results = field.get(activity) as List<BatchResultActivity.BatchResult>
                ready = results.isNotEmpty() && results.all { it.bitmap != null }
            }
            ready
        }

        resultScenario?.onActivity { it.saveAllAsZip() }

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        waitUntil {
            downloads.listFiles()?.any { it.name.startsWith("batch_qr_") && it.name.endsWith(".zip") } == true
        }

        val zip = downloads.listFiles()!!
            .filter { it.name.startsWith("batch_qr_") && it.name.endsWith(".zip") }
            .maxByOrNull { it.lastModified() }!!
        ZipFile(zip).use { zipFile ->
            val names = zipFile.entries().toList().map { it.name }
            assertEquals(2, names.size)
            assertTrue(names.all { it.endsWith(".png") })
        }
    }

    @Test
    fun `save single image writes png to pictures dir`() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), BatchResultActivity::class.java).apply {
            putStringArrayListExtra(BatchGenerateActivity.EXTRA_CONTENTS, arrayListOf("single-item"))
            putExtra(BatchGenerateActivity.EXTRA_FORMAT, "QR_CODE")
        }
        resultScenario = ActivityScenario.launch<BatchResultActivity>(intent)

        waitUntil {
            var ready = false
            resultScenario?.onActivity { activity ->
                val field = BatchResultActivity::class.java.getDeclaredField("results")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val results = field.get(activity) as List<BatchResultActivity.BatchResult>
                ready = results.isNotEmpty() && results.first().bitmap != null
            }
            ready
        }

        resultScenario?.onActivity { activity ->
            val field = BatchResultActivity::class.java.getDeclaredField("results")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val results = field.get(activity) as List<BatchResultActivity.BatchResult>
            activity.saveSingleImage(results.first().bitmap!!, results.first().fileName)
        }

        val pictures = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "BatchQR"
        )
        waitUntil { pictures.listFiles()?.isNotEmpty() == true }

        val png = pictures.listFiles()!!.maxByOrNull { it.lastModified() }!!
        assertTrue(png.name.endsWith(".png"))
        assertTrue(png.length() > 0)
    }
}

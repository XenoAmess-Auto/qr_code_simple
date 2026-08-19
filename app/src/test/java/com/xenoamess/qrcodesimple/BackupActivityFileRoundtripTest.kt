package com.xenoamess.qrcodesimple

import android.net.Uri
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * BackupActivity 真实文件导入导出 roundtrip 场景测试：
 * 通过 file:// Uri 走 activity 的真实读写路径（SAF 之外的同一套代码）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], application = QRCodeApp::class)
class BackupActivityFileRoundtripTest {

    private lateinit var repository: HistoryRepository
    private var scenario: ActivityScenario<BackupActivity>? = null

    @Before
    fun setup() {
        repository = HistoryRepository(ApplicationProvider.getApplicationContext())
        runBlocking { repository.deleteAll() }
        scenario = ActivityScenario.launch(BackupActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        runBlocking { repository.deleteAll() }
    }

    private fun idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    /** 轮询等待异步条件（Dispatchers.IO 协程写文件不受主 Looper 控制）。 */
    private fun waitUntil(timeoutMs: Long = 5000, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            if (predicate()) return true
            Thread.sleep(50)
        }
        idleMain()
        return predicate()
    }

    private fun tempFile(ext: String): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return File(context.cacheDir, "backup_test_${System.nanoTime()}.$ext")
    }

    private fun seedHistory() = runBlocking {
        repository.insert(
            HistoryItem(
                content = "roundtrip-content",
                type = HistoryType.QR_CODE,
                isGenerated = true,
                barcodeFormat = "QR_CODE",
                tags = "tagA"
            )
        )
    }

    private fun assertImportRejected(file: File) {
        scenario?.onActivity { activity -> activity.importData(Uri.fromFile(file)) }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(waitUntil {
            ShadowToast.getTextOfLatestToast() == context.getString(
                R.string.import_failed,
                context.getString(R.string.backup_import_unsupported)
            )
        })
        assertEquals(0, runBlocking { repository.allHistory.first().size })
    }

    private fun selectExport(buttonId: Int) {
        scenario?.onActivity { activity ->
            activity.findViewById<android.widget.Button>(buttonId).performClick()
        }
        idleMain()
    }

    @Test
    fun `json export then import restores history through real files`() {
        seedHistory()
        val file = tempFile("json")

        scenario?.onActivity { activity ->
            activity.exportData(Uri.fromFile(file), PendingExportKind.JSON)
        }
        waitUntil { file.exists() && file.length() > 0 }
        assertTrue(file.exists() && file.length() > 0)

        runBlocking { repository.deleteAll() }
        assertEquals(0, runBlocking { repository.allHistory.first().size })

        scenario?.onActivity { activity ->
            activity.importData(Uri.fromFile(file))
        }
        waitUntil { runBlocking { repository.allHistory.first().isNotEmpty() } }

        val items = runBlocking { repository.allHistory.first() }
        assertEquals(1, items.size)
        assertEquals("roundtrip-content", items[0].content)
        assertEquals("tagA", items[0].tags)
    }

    @Test
    fun `csv export then import restores history through real files`() {
        seedHistory()
        val file = tempFile("csv")

        scenario?.onActivity { activity ->
            activity.exportData(Uri.fromFile(file), PendingExportKind.CSV)
        }
        waitUntil { file.exists() && file.length() > 0 }
        assertTrue(file.exists() && file.length() > 0)

        runBlocking { repository.deleteAll() }

        scenario?.onActivity { activity ->
            activity.importData(Uri.fromFile(file))
        }
        waitUntil { runBlocking { repository.allHistory.first().isNotEmpty() } }

        val items = runBlocking { repository.allHistory.first() }
        assertEquals(1, items.size)
        assertEquals("roundtrip-content", items[0].content)
    }

    @Test
    fun `csv file roundtrip restores quoted multiline fields`() {
        val expectedContent = "line one\r\nline two"
        val expectedNotes = "notes\rwith\nall endings"
        runBlocking {
            repository.insert(
                HistoryItem(
                    content = expectedContent,
                    type = HistoryType.QR_CODE,
                    timestamp = 123L,
                    notes = expectedNotes,
                    tags = "first\r\nsecond"
                )
            )
        }
        val file = tempFile("csv")
        scenario?.onActivity { it.exportData(Uri.fromFile(file), PendingExportKind.CSV) }
        assertTrue(waitUntil { file.length() > 0 })
        runBlocking { repository.deleteAll() }

        scenario?.onActivity { it.importData(Uri.fromFile(file)) }
        assertTrue(waitUntil { runBlocking { repository.allHistory.first().isNotEmpty() } })

        val restored = runBlocking { repository.allHistory.first().single() }
        assertEquals(expectedContent, restored.content)
        assertEquals(expectedNotes, restored.notes)
        assertEquals("first\r\nsecond", restored.tags)
    }

    @Test
    fun `selected csv format is used even when returned uri ends in json`() {
        seedHistory()
        val file = tempFile("json")
        selectExport(R.id.btnExportCsv)

        scenario?.onActivity { activity -> activity.consumePendingExport(Uri.fromFile(file)) }
        waitUntil { file.exists() && file.length() > 0 }

        assertTrue(file.readText().startsWith("content,type,timestamp"))
        assertFalse(HistoryBackupManager.looksLikeJson(file.readText()))
    }

    @Test
    fun `selected json format is used even when returned uri ends in csv`() {
        seedHistory()
        val file = tempFile("csv")
        selectExport(R.id.btnExportJson)

        scenario?.onActivity { activity -> activity.consumePendingExport(Uri.fromFile(file)) }
        waitUntil { file.exists() && file.length() > 0 }

        assertTrue(HistoryBackupManager.looksLikeJson(file.readText()))
    }

    @Test
    fun `selected xlsx format is used even when returned uri has no xlsx suffix`() {
        seedHistory()
        val file = tempFile("json")
        selectExport(R.id.btnExportExcel)

        scenario?.onActivity { activity -> activity.consumePendingExport(Uri.fromFile(file)) }
        waitUntil { file.exists() && file.length() > 0 }

        val bytes = file.readBytes()
        assertTrue(bytes.size > 4)
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
    }

    @Test
    fun `encrypted export writes magic header and import prompts for password`() {
        seedHistory()
        val file = tempFile("qrbak")

        scenario?.onActivity { activity ->
            activity.exportData(
                Uri.fromFile(file),
                PendingExportKind.ENCRYPTED,
                "pw123".toCharArray()
            )
        }
        waitUntil { file.exists() && file.length() > 0 }

        assertTrue(file.exists() && file.length() > 0)
        val bytes = file.readBytes()
        assertTrue(BackupCrypto.isEncrypted(bytes))

        runBlocking { repository.deleteAll() }

        scenario?.onActivity { activity ->
            activity.importData(Uri.fromFile(file))
        }
        // 等待协程读完文件并弹出密码框
        waitUntil { org.robolectric.shadows.ShadowDialog.getLatestDialog() != null }

        // 弹出密码输入框（对话框视图本身是 setView 传入的 EditText）
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        assertTrue(dialog.isShowing)

        var passwordField: android.widget.EditText? = null
        fun walk(v: android.view.View) {
            if (v is android.widget.EditText) passwordField = v
            if (v is android.view.ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
        }
        dialog.window?.decorView?.let { walk(it) }
        assertTrue(passwordField != null)

        passwordField!!.setText("pw123")
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick()
        waitUntil { runBlocking { repository.allHistory.first().isNotEmpty() } }

        val items = runBlocking { repository.allHistory.first() }
        assertEquals(1, items.size)
        assertEquals("roundtrip-content", items[0].content)
    }

    @Test
    fun `wrong encrypted import password keeps dialog open and accepts correction`() {
        seedHistory()
        val file = tempFile("qrbak")
        val encrypted = runBlocking {
            HistoryBackupManager.exportEncryptedJson(
                ApplicationProvider.getApplicationContext(),
                "correct".toCharArray()
            )
        }
        file.writeBytes(encrypted)
        runBlocking { repository.deleteAll() }

        scenario?.onActivity { activity -> activity.importData(Uri.fromFile(file)) }
        waitUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        var passwordField: EditText? = null
        fun walk(view: android.view.View) {
            if (view is EditText) passwordField = view
            if (view is android.view.ViewGroup) {
                (0 until view.childCount).forEach { walk(view.getChildAt(it)) }
            }
        }
        dialog.window?.decorView?.let(::walk)

        passwordField!!.setText("wrong")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        waitUntil { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled }
        assertTrue(dialog.isShowing)
        assertEquals(0, runBlocking { repository.allHistory.first().size })

        passwordField!!.setText("correct")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        waitUntil {
            !dialog.isShowing && runBlocking { repository.allHistory.first().isNotEmpty() }
        }
        assertFalse(dialog.isShowing)
        assertEquals("roundtrip-content", runBlocking { repository.allHistory.first().single().content })
    }

    @Test
    fun `empty ordinary backup is rejected`() {
        val file = tempFile("json").apply { writeBytes(ByteArray(0)) }
        assertImportRejected(file)
    }

    @Test
    fun `xlsx report is rejected as an import backup`() {
        seedHistory()
        val bytes = runBlocking {
            HistoryBackupManager.exportToXlsx(ApplicationProvider.getApplicationContext())
        }
        runBlocking { repository.deleteAll() }
        val file = tempFile("xlsx").apply { writeBytes(bytes) }
        assertImportRejected(file)
    }

    @Test
    fun `random binary file is rejected instead of parsed as csv`() {
        val file = tempFile("bin").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3, 0x7f, 0x80.toByte(), 0xff.toByte()))
        }
        assertImportRejected(file)
    }

    @Test
    fun `null input stream reports import failure`() {
        val uri = Uri.parse("content://backup-test/null-input")
        scenario?.onActivity { activity ->
            Shadows.shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) { null }
            activity.importData(uri)
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(waitUntil {
            ShadowToast.getTextOfLatestToast()?.toString()?.startsWith(
                context.getString(R.string.import_failed, "").substringBefore(":")
            ) == true
        })
        assertEquals(0, runBlocking { repository.allHistory.first().size })
    }

    @Test
    fun `oversized provider stream is rejected at the import limit`() {
        val uri = Uri.parse("content://backup-test/oversized")
        scenario?.onActivity { activity ->
            Shadows.shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) {
                object : InputStream() {
                    private var remaining = 8 * 1024 * 1024 + 1

                    override fun read(): Int = if (remaining-- > 0) 'x'.code else -1

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (remaining <= 0) return -1
                        val count = minOf(remaining, length)
                        buffer.fill('x'.code.toByte(), offset, offset + count)
                        remaining -= count
                        return count
                    }
                }
            }
            activity.importData(uri)
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(waitUntil {
            ShadowToast.getTextOfLatestToast() == context.getString(
                R.string.import_failed,
                context.getString(R.string.backup_import_too_large, 8)
            )
        })
        assertEquals(0, runBlocking { repository.allHistory.first().size })
    }

    @Test
    fun `provider read exception reports failure without importing`() {
        val uri = Uri.parse("content://backup-test/read-exception")
        scenario?.onActivity { activity ->
            Shadows.shadowOf(activity.contentResolver).registerInputStreamSupplier(uri) {
                object : InputStream() {
                    override fun read(): Int = throw IOException("provider read failed")
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                        throw IOException("provider read failed")
                }
            }
            activity.importData(uri)
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(waitUntil {
            ShadowToast.getTextOfLatestToast() == context.getString(
                R.string.import_failed,
                "provider read failed"
            )
        })
        assertEquals(0, runBlocking { repository.allHistory.first().size })
    }
}

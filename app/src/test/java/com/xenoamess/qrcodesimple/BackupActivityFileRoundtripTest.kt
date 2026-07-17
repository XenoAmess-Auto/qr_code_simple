package com.xenoamess.qrcodesimple

import android.net.Uri
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

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
    private fun waitUntil(timeoutMs: Long = 5000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idleMain()
            if (predicate()) return
            Thread.sleep(50)
        }
        idleMain()
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

    @Test
    fun `json export then import restores history through real files`() {
        seedHistory()
        val file = tempFile("json")

        scenario?.onActivity { activity ->
            activity.exportData(Uri.fromFile(file))
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
            activity.exportData(Uri.fromFile(file))
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
    fun `encrypted export writes magic header and import prompts for password`() {
        seedHistory()
        val file = tempFile("qrbak")

        scenario?.onActivity { activity ->
            // 模拟密码对话框流程：设置待导出密码后走加密路径
            val field = BackupActivity::class.java.getDeclaredField("pendingExportPassword")
            field.isAccessible = true
            field.set(activity, "pw123".toCharArray())
            activity.exportData(Uri.fromFile(file))
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
}

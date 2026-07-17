package com.xenoamess.qrcodesimple

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xenoamess.qrcodesimple.data.AppDatabase
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * SQLCipher 真机验证：真实设备上数据库应为加密存储
 * （Robolectric 单测回退到未加密数据库，此路径只能在真机验证）。
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherDeviceTest {

    @Test
    fun databaseFileIsEncryptedOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getDatabase(context)

        // 写入并读回，确认数据库功能正常
        val item = HistoryItem(content = "cipher-test-content", type = HistoryType.QR_CODE)
        db.historyDao().insert(item)
        val found = db.historyDao().getAllHistory().first()
            .any { it.content == "cipher-test-content" }
        assertEquals(true, found)

        // 关闭数据库后检查文件头：明文 SQLite 以 "SQLite format 3\0" 开头，
        // SQLCipher 加密库的文件头是随机的
        db.close()
        val dbFile: File = context.getDatabasePath("qr_code_history_db_encrypted")
        if (dbFile.exists()) {
            val header = ByteArray(16)
            dbFile.inputStream().use { it.read(header) }
            val headerStr = String(header, Charsets.ISO_8859_1)
            assertFalse(
                "database file should NOT have a plain SQLite header on device",
                headerStr.startsWith("SQLite format 3")
            )
        }
        Unit
    }
}

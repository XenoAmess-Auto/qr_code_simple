package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 加密备份（HistoryBackupManager + BackupCrypto）端到端测试。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EncryptedBackupTest {

    private lateinit var context: Context
    private lateinit var repository: HistoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = HistoryRepository(context)
        runBlocking { repository.deleteAll() }
    }

    @Test
    fun `encrypted export import roundtrip restores items`() = runBlocking {
        repository.insert(
            HistoryItem(
                content = "encrypted-secret-content",
                type = HistoryType.QR_CODE,
                isGenerated = true,
                barcodeFormat = "QR_CODE",
                tags = "tagA,tagB"
            )
        )

        val data = HistoryBackupManager.exportEncryptedJson(context, "pw123".toCharArray())
        assertTrue(BackupCrypto.isEncrypted(data))

        repository.deleteAll()
        assertEquals(0, repository.allHistory.first().size)

        val result = HistoryBackupManager.importEncrypted(context, data, "pw123".toCharArray())
        assertTrue(result.success)
        assertEquals(1, result.count)

        val items = repository.allHistory.first()
        assertEquals(1, items.size)
        assertEquals("encrypted-secret-content", items[0].content)
        assertEquals("tagA,tagB", items[0].tags)
    }

    @Test
    fun `import with wrong password returns failure without touching history`() = runBlocking {
        repository.insert(HistoryItem(content = "keep-me", type = HistoryType.QR_CODE))
        val data = HistoryBackupManager.exportEncryptedJson(context, "right".toCharArray())

        val result = HistoryBackupManager.importEncrypted(context, data, "wrong".toCharArray())
        assertFalse(result.success)

        val items = repository.allHistory.first()
        assertEquals(1, items.size)
        assertEquals("keep-me", items[0].content)
    }

    @Test
    fun `looksLikeJson detects object array and csv`() {
        assertTrue(HistoryBackupManager.looksLikeJson("{\"version\":1}"))
        assertTrue(HistoryBackupManager.looksLikeJson("  [1,2,3]"))
        assertFalse(HistoryBackupManager.looksLikeJson("content,type,timestamp\nfoo,QR_CODE,1"))
        assertFalse(HistoryBackupManager.looksLikeJson(""))
    }
}

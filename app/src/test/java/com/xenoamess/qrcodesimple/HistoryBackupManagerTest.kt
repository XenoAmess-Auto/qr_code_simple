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
import kotlin.test.assertTrue

/**
 * HistoryBackupManager 单元测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoryBackupManagerTest {

    private lateinit var context: Context
    private lateinit var repository: HistoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = HistoryRepository(context)
        runBlocking {
            repository.deleteAll()
        }
    }

    @Test
    fun `export to json contains version and items`() = runBlocking {
        repository.insert(
            HistoryItem(
                content = "test content",
                type = HistoryType.QR_CODE,
                isGenerated = true,
                barcodeFormat = "QR_CODE",
                notes = "note",
                tags = "tag1,tag2"
            )
        )

        val json = HistoryBackupManager.exportToJson(context)
        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("test content"))
        assertTrue(json.contains("tag1,tag2"))
    }

    @Test
    fun `import from json restores items`() = runBlocking {
        val json = """
            {
              "version": 1,
              "count": 1,
              "items": [
                {
                  "id": 1,
                  "content": "imported content",
                  "type": "QR_CODE",
                  "timestamp": 1234567890,
                  "isGenerated": true,
                  "barcodeFormat": "QR_CODE",
                  "isFavorite": false,
                  "notes": "imported note",
                  "tags": "tag1"
                }
              ]
            }
        """.trimIndent()

        val result = HistoryBackupManager.importFromJson(context, json)
        assertTrue(result.success)
        assertEquals(1, result.count)

        val items = repository.allHistory.first()
        assertEquals(1, items.size)
        assertEquals("imported content", items[0].content)
        assertEquals("tag1", items[0].tags)
    }

    @Test
    fun `import from json merges duplicate content`() = runBlocking {
        repository.insert(
            HistoryItem(
                content = "duplicate content",
                type = HistoryType.QR_CODE,
                isGenerated = true,
                barcodeFormat = "QR_CODE",
                notes = "old note",
                tags = "old"
            )
        )

        val json = """
            {
              "version": 1,
              "count": 1,
              "items": [
                {
                  "id": 1,
                  "content": "duplicate content",
                  "type": "BARCODE",
                  "timestamp": 1234567890,
                  "isGenerated": true,
                  "barcodeFormat": "CODE_128",
                  "isFavorite": true,
                  "notes": "new note",
                  "tags": "new",
                  "styleJson": "{}"
                }
              ]
            }
        """.trimIndent()

        val result = HistoryBackupManager.importFromJson(context, json)
        assertTrue(result.success)
        assertEquals(1, result.count)

        val items = repository.allHistory.first()
        assertEquals(1, items.size)
        assertEquals("duplicate content", items[0].content)
        assertEquals(HistoryType.BARCODE, items[0].type)
        assertEquals("CODE_128", items[0].barcodeFormat)
        assertEquals("{}", items[0].styleJson)
        assertEquals("new note", items[0].notes)
        assertEquals("new", items[0].tags)
        assertEquals(true, items[0].isFavorite)
    }

    @Test
    fun `export to csv contains headers and fields`() = runBlocking {
        repository.insert(
            HistoryItem(
                content = "csv content",
                type = HistoryType.BARCODE,
                isGenerated = false,
                barcodeFormat = "CODE_128",
                notes = "a note",
                tags = "a,b"
            )
        )

        val csv = HistoryBackupManager.exportToCsv(context)
        assertTrue(csv.contains("content,type,timestamp,isGenerated,barcodeFormat,isFavorite,notes,tags,styleJson"))
        assertTrue(csv.contains("csv content"))
        assertTrue(csv.contains("CODE_128"))
    }

    @Test
    fun `import from csv restores items`() = runBlocking {
        val csv = """
            content,type,timestamp,isGenerated,barcodeFormat,isFavorite,notes,tags,styleJson
            "csv,content",BARCODE,1234567890,false,CODE_128,false,,"tag1,tag2"
        """.trimIndent()

        val result = HistoryBackupManager.importFromCsv(context, csv)
        assertTrue(result.success)
        assertEquals(1, result.count)

        val items = repository.allHistory.first()
        assertEquals(1, items.size)
        assertEquals("csv,content", items[0].content)
        assertEquals("tag1,tag2", items[0].tags)
    }

    @Test
    fun `import from csv merges duplicate content`() = runBlocking {
        repository.insert(
            HistoryItem(
                content = "csv,content",
                type = HistoryType.BARCODE,
                isGenerated = false,
                barcodeFormat = "CODE_128",
                notes = "old note",
                tags = "old"
            )
        )

        val csv = """
            content,type,timestamp,isGenerated,barcodeFormat,isFavorite,notes,tags,styleJson
            "csv,content",QR_CODE,1234567891,false,EAN_13,true,"new note","new","{}"
        """.trimIndent()

        val result = HistoryBackupManager.importFromCsv(context, csv)
        assertTrue(result.success)
        assertEquals(1, result.count)

        val items = repository.allHistory.first()
        assertEquals(1, items.size)
        assertEquals("csv,content", items[0].content)
        assertEquals(HistoryType.QR_CODE, items[0].type)
        assertEquals(false, items[0].isGenerated)
        assertEquals("EAN_13", items[0].barcodeFormat)
        assertEquals("{}", items[0].styleJson)
        assertEquals("new note", items[0].notes)
        assertEquals("new", items[0].tags)
        assertEquals(true, items[0].isFavorite)
    }

    @Test
    fun `generate backup file name has correct prefix and extension`() {
        val fileName = HistoryBackupManager.generateBackupFileName("json")
        assertTrue(fileName.startsWith("qr_backup_"))
        assertTrue(fileName.endsWith(".json"))
    }
}

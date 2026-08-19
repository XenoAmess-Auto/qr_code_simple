package com.xenoamess.qrcodesimple

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.AppDatabase
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
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `export to xlsx writes readable workbook with items`() = runBlocking {
        repository.insert(
            HistoryItem(
                content = "xlsx content",
                type = HistoryType.QR_CODE,
                isGenerated = false,
                barcodeFormat = "QR_CODE",
                notes = "xlsx note",
                tags = "x,y"
            )
        )

        val data = HistoryBackupManager.exportToXlsx(context)
        assertTrue(data.isNotEmpty())

        org.apache.poi.xssf.usermodel.XSSFWorkbook(java.io.ByteArrayInputStream(data)).use { workbook ->
            val sheet = workbook.getSheet("History")
            val header = sheet.getRow(0).getCell(0).stringCellValue
            val content = sheet.getRow(1).getCell(0).stringCellValue
            val format = sheet.getRow(1).getCell(4).stringCellValue
            val note = sheet.getRow(1).getCell(6).stringCellValue
            assertEquals("content", header)
            assertEquals("xlsx content", content)
            assertEquals("QR_CODE", format)
            assertEquals("xlsx note", note)
        }
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

    @Test
    fun `import from json rejects unsupported version`() = runBlocking {
        val json = """
            {
              "version": 99,
              "items": []
            }
        """.trimIndent()

        val result = HistoryBackupManager.importFromJson(context, json)
        assertFalse(result.success)
        assertEquals(0, result.count)
    }

    @Test
    fun `import from json handles invalid json gracefully`() = runBlocking {
        val result = HistoryBackupManager.importFromJson(context, "not json")
        assertFalse(result.success)
        assertEquals(0, result.count)
    }

    @Test
    fun `export to csv escapes commas quotes and newlines`() = runBlocking {
        repository.insert(
            HistoryItem(
                content = "a,b",
                type = HistoryType.QR_CODE,
                isGenerated = false,
                notes = "note with \"quotes\"",
                tags = "tag1,tag2"
            )
        )

        val csv = HistoryBackupManager.exportToCsv(context)
        assertTrue(csv.contains("\"a,b\""))
        assertTrue(csv.contains("\"note with \"\"quotes\"\"\""))
    }

    @Test
    fun `exported csv roundtrips quoted CR and LF fields as complete records`() = runBlocking {
        val expected = HistoryItem(
            content = "first line\r\nsecond line",
            type = HistoryType.QR_CODE,
            timestamp = 123456789L,
            isGenerated = true,
            barcodeFormat = "QR_CODE",
            isFavorite = true,
            notes = "note with CR\rand LF\nend",
            tags = "tag one\r\ntag two",
            styleJson = "{\n  \"shape\": \"square\"\r\n}"
        )
        repository.insert(expected)

        val csv = HistoryBackupManager.exportToCsv(context)
        repository.deleteAll()
        val result = HistoryBackupManager.importFromCsv(context, StringReader(csv))

        assertTrue(result.success)
        assertEquals(1, result.count)
        val restored = repository.allHistory.first().single()
        assertEquals(expected.content, restored.content)
        assertEquals(expected.notes, restored.notes)
        assertEquals(expected.tags, restored.tags)
        assertEquals(expected.styleJson, restored.styleJson)
    }

    @Test
    fun `import from csv handles empty rows and no header`() = runBlocking {
        val csv = """
            hello,QR_CODE,123,true

        """.trimIndent()

        val result = HistoryBackupManager.importFromCsv(context, csv)
        assertTrue(result.success)
        assertEquals(1, result.count)
    }

    @Test
    fun `import from empty csv returns failure`() = runBlocking {
        val result = HistoryBackupManager.importFromCsv(context, "")
        assertFalse(result.success)
    }

    @Test
    fun `header-only and all-invalid CSV do not report success`() = runBlocking {
        val headerOnly = HistoryBackupManager.importFromCsv(
            context,
            "content,type,timestamp,isGenerated,barcodeFormat,isFavorite,notes,tags,styleJson\r\n"
        )
        val invalidRecords = HistoryBackupManager.importFromCsv(
            context,
            "content,type,timestamp,isGenerated\r\nvalue,NOT_A_TYPE,nope,maybe\r\n"
        )

        assertFalse(headerOnly.success)
        assertFalse(invalidRecords.success)
        assertEquals(0, repository.allHistory.first().size)
    }

    @Test
    fun `structurally malformed CSV does not report success`() = runBlocking {
        val result = HistoryBackupManager.importFromCsv(
            context,
            StringReader("content,type,timestamp,isGenerated\r\n\"unterminated,QR_CODE,123,true")
        )

        assertFalse(result.success)
        assertEquals(context.getString(R.string.backup_import_invalid_structure), result.message)
        assertEquals(0, repository.allHistory.first().size)
    }

    @Test
    fun `valid CSV record followed by unterminated quote imports nothing`() = runBlocking {
        val csv = """
            content,type,timestamp,isGenerated
            valid,QR_CODE,123,true
            "unterminated,QR_CODE,124,true
        """.trimIndent()

        val result = HistoryBackupManager.importFromCsv(context, StringReader(csv))

        assertFalse(result.success)
        assertEquals(0, result.count)
        assertEquals(0, repository.allHistory.first().size)
    }

    @Test
    fun `valid CSV record followed by invalid field imports nothing`() = runBlocking {
        val csv = """
            content,type,timestamp,isGenerated
            valid,QR_CODE,123,true
            invalid,QR_CODE,not-a-timestamp,true
        """.trimIndent()

        val result = HistoryBackupManager.importFromCsv(context, csv)

        assertFalse(result.success)
        assertEquals(0, result.count)
        assertEquals(0, repository.allHistory.first().size)
    }

    @Test
    fun `successful CSV import writes every validated record`() = runBlocking {
        val csv = """
            content,type,timestamp,isGenerated
            first,QR_CODE,123,true
            second,BARCODE,124,false
        """.trimIndent()

        val result = HistoryBackupManager.importFromCsv(context, csv)

        assertTrue(result.success)
        assertEquals(2, result.count)
        assertEquals(setOf("first", "second"), repository.allHistory.first().map { it.content }.toSet())
    }

    @Test
    fun `privacy mode allows atomic backup restore without changing privacy setting`() = runBlocking {
        QRCodeApp.setPrivacyMode(context, true)
        try {
            val result = HistoryBackupManager.importFromJson(
                context,
                """{"version":1,"items":[{"content":"first","type":"QR_CODE"},{"content":"second","type":"BARCODE"}]}"""
            )

            assertTrue(result.success)
            assertEquals(2, result.count)
            assertEquals(setOf("first", "second"), repository.allHistory.first().map { it.content }.toSet())
            assertTrue(QRCodeApp.isPrivacyMode(context))
        } finally {
            QRCodeApp.setPrivacyMode(context, false)
        }
    }

    @Test
    fun `database failure rolls back the entire CSV import transaction`() = runBlocking {
        val database = AppDatabase.getDatabase(context)
        database.openHelper.writableDatabase.execSQL(
            """
                CREATE TRIGGER fail_second_backup_item
                BEFORE INSERT ON history
                WHEN NEW.content = 'second'
                BEGIN
                    SELECT RAISE(ABORT, 'forced import failure');
                END
            """.trimIndent()
        )
        QRCodeApp.setPrivacyMode(context, true)
        try {
            val result = HistoryBackupManager.importFromCsv(
                context,
                "first,QR_CODE,123,true\nsecond,QR_CODE,124,true"
            )

            assertFalse(result.success)
            assertEquals(0, result.count)
            assertEquals(0, repository.allHistory.first().size)
            assertTrue(QRCodeApp.isPrivacyMode(context))
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_second_backup_item")
            QRCodeApp.setPrivacyMode(context, false)
        }
    }

    @Test
    fun `import from empty json returns failure`() = runBlocking {
        val result = HistoryBackupManager.importFromJson(context, "")
        assertFalse(result.success)
        assertEquals(0, result.count)
    }

    @Test
    fun `JSON with invalid structure fields or zero records does not report success`() = runBlocking {
        val invalidStructure = HistoryBackupManager.importFromJson(context, "{\"version\":1,\"items\":{}}")
        val emptyItems = HistoryBackupManager.importFromJson(context, "{\"version\":1,\"items\":[]}")
        val invalidItems = HistoryBackupManager.importFromJson(
            context,
            """{"version":1,"items":[{"content":"","type":"QR_CODE"},{"content":"x","type":"NOPE"}]}"""
        )

        assertFalse(invalidStructure.success)
        assertEquals(context.getString(R.string.backup_import_invalid_structure), invalidStructure.message)
        assertFalse(emptyItems.success)
        assertEquals(context.getString(R.string.backup_import_no_valid_records), emptyItems.message)
        assertFalse(invalidItems.success)
        assertEquals(context.getString(R.string.backup_import_invalid_structure), invalidItems.message)
        assertEquals(0, repository.allHistory.first().size)
    }

    @Test
    fun `valid JSON item followed by invalid item imports nothing`() = runBlocking {
        val result = HistoryBackupManager.importFromJson(
            context,
            """{"version":1,"items":[{"content":"valid","type":"QR_CODE"},{"content":"bad","type":"NOPE"}]}"""
        )

        assertFalse(result.success)
        assertEquals(0, result.count)
        assertEquals(0, repository.allHistory.first().size)
    }
}

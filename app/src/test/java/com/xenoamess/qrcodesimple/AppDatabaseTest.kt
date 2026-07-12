package com.xenoamess.qrcodesimple

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xenoamess.qrcodesimple.data.AppDatabase
import com.xenoamess.qrcodesimple.data.HistoryItem
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
 * AppDatabase 迁移与重置测试
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppDatabaseTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createRawDatabase(dbName: String, version: Int, includeTags: Boolean = false, includeStyleJson: Boolean = false) {
        context.deleteDatabase(dbName)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        val columns = mutableListOf(
            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
            "content TEXT NOT NULL",
            "type TEXT NOT NULL",
            "timestamp INTEGER NOT NULL",
            "isGenerated INTEGER NOT NULL",
            "barcodeFormat TEXT",
            "isFavorite INTEGER NOT NULL",
            "notes TEXT"
        )
        if (includeTags) columns.add("tags TEXT")
        if (includeStyleJson) columns.add("styleJson TEXT")

        db.execSQL("CREATE TABLE history (${columns.joinToString(", ")})")
        db.version = version
        db.close()
    }

    private fun columnExists(dbName: String, columnName: String): Boolean {
        val db = SQLiteDatabase.openDatabase(
            context.getDatabasePath(dbName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        val cursor = db.rawQuery("PRAGMA table_info(history)", null)
        var exists = false
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (name == columnName) {
                exists = true
                break
            }
        }
        cursor.close()
        db.close()
        return exists
    }

    private fun forceMigrate(dbName: String, vararg migrations: androidx.room.migration.Migration): AppDatabase {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*migrations)
            .build()
        db.openHelper.writableDatabase
        return db
    }

    @Test
    fun `migration 1 to 4 applies all migrations and preserves data`() {
        val dbName = "migration_1_4_test"
        createRawDatabase(dbName, 1)

        val rawDb = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        rawDb.execSQL(
            """
            INSERT INTO history (content, type, timestamp, isGenerated, barcodeFormat, isFavorite, notes)
            VALUES ('legacy content', 'QR_CODE', 1234567890, 0, 'QR_CODE', 0, 'legacy note')
            """.trimIndent()
        )
        rawDb.close()

        val db = forceMigrate(
            dbName,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        )

        assertTrue(columnExists(dbName, "tags"))
        assertTrue(columnExists(dbName, "styleJson"))

        val items = runBlocking { db.historyDao().getAllHistory().first() }
        assertEquals(1, items.size)
        assertEquals("legacy content", items[0].content)
        assertEquals(HistoryType.QR_CODE, items[0].type)
        assertEquals("legacy note", items[0].notes)
        db.close()
    }

    @Test
    fun `migration 2 to 3 adds tags column`() {
        val dbName = "migration_2_3_test"
        createRawDatabase(dbName, 2)

        val db = forceMigrate(
            dbName,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4
        )

        assertTrue(columnExists(dbName, "tags"))
        db.close()
    }

    @Test
    fun `migration 3 to 4 adds styleJson column`() {
        val dbName = "migration_3_4_test"
        createRawDatabase(dbName, 3, includeTags = true)

        val db = forceMigrate(dbName, AppDatabase.MIGRATION_3_4)

        assertTrue(columnExists(dbName, "styleJson"))
        db.close()
    }

    @Test
    fun `resetDatabase clears data and allows recreation`() = runBlocking {
        val dbName = "qr_code_history_db_encrypted"
        context.deleteDatabase(dbName)
        AppDatabase.resetDatabase(context)

        val db = AppDatabase.getDatabase(context)
        db.historyDao().insert(
            HistoryItem(
                content = "to be deleted",
                type = HistoryType.QR_CODE
            )
        )

        var items = db.historyDao().getAllHistory().first()
        assertEquals(1, items.size)

        AppDatabase.resetDatabase(context)

        val newDb = AppDatabase.getDatabase(context)
        items = newDb.historyDao().getAllHistory().first()
        assertEquals(0, items.size)

        newDb.historyDao().insert(
            HistoryItem(
                content = "after reset",
                type = HistoryType.BARCODE
            )
        )
        items = newDb.historyDao().getAllHistory().first()
        assertEquals(1, items.size)
        assertEquals("after reset", items[0].content)
    }
}

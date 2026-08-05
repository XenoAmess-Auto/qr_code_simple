package com.xenoamess.qrcodesimple

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xenoamess.qrcodesimple.data.AppDatabase
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 历史保留策略（deleteOlderThan）DAO 测试。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class HistoryRetentionTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun item(content: String, timestamp: Long, favorite: Boolean = false) = HistoryItem(
        content = content,
        type = HistoryType.QR_CODE,
        timestamp = timestamp,
        isGenerated = false,
        isFavorite = favorite
    )

    @Test
    fun `stats queries count scans within window and rank contents`() = runBlocking {
        val now = System.currentTimeMillis()
        val dao = db.historyDao()
        dao.insert(item("https://hot.example.com", now - 1L * 24 * 60 * 60 * 1000))
        dao.insert(item("https://hot.example.com", now - 6L * 24 * 60 * 60 * 1000))
        dao.insert(item("https://old.example.com", now - 40L * 24 * 60 * 60 * 1000))
        // 生成记录不应计入扫码统计
        dao.insert(
            HistoryItem(
                content = "generated content",
                type = HistoryType.QR_CODE,
                timestamp = now,
                isGenerated = true
            )
        )

        val count7 = dao.countScannedSince(now - 7L * 24 * 60 * 60 * 1000)
        val count30 = dao.countScannedSince(now - 30L * 24 * 60 * 60 * 1000)
        assertEquals(2, count7)
        assertEquals(2, count30)

        val top = dao.topScannedContents(3)
        assertEquals(2, top.size)
        assertEquals("https://hot.example.com", top[0].content)
        assertEquals(2, top[0].cnt)
    }

    @Test
    fun `deleteOlderThan removes expired non-favorite items only`() = runBlocking {
        val now = 1_700_000_000_000L
        val cutoff = now - 30L * 24 * 60 * 60 * 1000

        db.historyDao().insert(item("recent", now - 1000))
        db.historyDao().insert(item("expired", cutoff - 1000))
        db.historyDao().insert(item("expired-favorite", cutoff - 2000, favorite = true))
        db.historyDao().insert(item("boundary-kept", cutoff))

        val deleted = db.historyDao().deleteOlderThan(cutoff)
        assertEquals(1, deleted)

        val remaining = db.historyDao().getAllHistory().first().map { it.content }.toSet()
        assertEquals(setOf("recent", "expired-favorite", "boundary-kept"), remaining)
    }

    @Test
    fun `deleteOlderThan with empty table returns zero`() = runBlocking {
        assertEquals(0, db.historyDao().deleteOlderThan(System.currentTimeMillis()))
    }
}

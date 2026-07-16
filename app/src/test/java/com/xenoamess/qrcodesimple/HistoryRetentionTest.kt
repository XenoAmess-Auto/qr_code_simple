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

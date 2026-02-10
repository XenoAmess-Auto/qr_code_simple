package com.xenoamess.qrcodesimple.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 历史记录 DAO
 */
@Dao
interface HistoryDao {
    
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>
    
    @Query("SELECT * FROM history WHERE isGenerated = 0 ORDER BY timestamp DESC")
    fun getScannedHistory(): Flow<List<HistoryItem>>
    
    @Query("SELECT * FROM history WHERE isGenerated = 1 ORDER BY timestamp DESC")
    fun getGeneratedHistory(): Flow<List<HistoryItem>>
    
    @Insert
    suspend fun insert(item: HistoryItem): Long
    
    @Delete
    suspend fun delete(item: HistoryItem)
    
    @Update
    suspend fun update(item: HistoryItem)
    
    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM history")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM history WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): HistoryItem?

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HistoryItem?

    @Query("UPDATE history SET content = :newContent WHERE id = :id")
    suspend fun updateContent(id: Long, newContent: String)
}
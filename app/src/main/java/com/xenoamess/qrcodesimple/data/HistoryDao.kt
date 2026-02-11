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

    // ===== 搜索功能 =====

    @Query("SELECT * FROM history WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchHistory(query: String): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE type = :type ORDER BY timestamp DESC")
    fun getHistoryByType(type: HistoryType): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE barcodeFormat = :format ORDER BY timestamp DESC")
    fun getHistoryByBarcodeFormat(format: String): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getHistoryByTimeRange(startTime: Long, endTime: Long): Flow<List<HistoryItem>>

    @Query("SELECT * FROM history WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteHistory(): Flow<List<HistoryItem>>

    @Query("SELECT DISTINCT type FROM history")
    suspend fun getAllTypes(): List<HistoryType>

    @Query("SELECT DISTINCT barcodeFormat FROM history WHERE barcodeFormat IS NOT NULL")
    suspend fun getAllBarcodeFormats(): List<String>
}
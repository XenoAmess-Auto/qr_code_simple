package com.xenoamess.qrcodesimple.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 历史记录 DAO
 */
@Dao
interface HistoryDao {
    @Query("SELECT COUNT(*) FROM history WHERE timestamp >= :since AND isGenerated = 0")
    suspend fun countScannedSince(since: Long): Int

    @Query("SELECT timestamp FROM history WHERE timestamp >= :since AND isGenerated = 0")
    suspend fun scannedTimestampsSince(since: Long): List<Long>

    
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>
    
    @Query("SELECT * FROM history WHERE isGenerated = 0 ORDER BY timestamp DESC")
    fun getScannedHistory(): Flow<List<HistoryItem>>
    
    @Query("SELECT * FROM history WHERE isGenerated = 1 ORDER BY timestamp DESC")
    fun getGeneratedHistory(): Flow<List<HistoryItem>>
    
    @Insert
    suspend fun insert(item: HistoryItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: HistoryItem): Long

    @Query("""
        UPDATE history SET type = :type, timestamp = :timestamp, barcodeFormat = :barcodeFormat,
            styleJson = :styleJson, isFavorite = :isFavorite, notes = :notes, tags = :tags
        WHERE content = :content AND isGenerated = :isGenerated
    """)
    suspend fun updateByContentAndGenerated(
        content: String,
        isGenerated: Boolean,
        type: HistoryType,
        timestamp: Long,
        barcodeFormat: String?,
        styleJson: String?,
        isFavorite: Boolean,
        notes: String?,
        tags: String?
    )

    @Transaction
    suspend fun upsert(item: HistoryItem): Long {
        val insertedId = insertIgnore(item)
        if (insertedId != -1L) return insertedId
        updateByContentAndGenerated(
            item.content, item.isGenerated, item.type, item.timestamp, item.barcodeFormat,
            item.styleJson, item.isFavorite, item.notes, item.tags
        )
        return findByContentAndGenerated(item.content, item.isGenerated)?.id ?: -1L
    }
    
    @Delete
    suspend fun delete(item: HistoryItem)
    
    @Update
    suspend fun update(item: HistoryItem)
    
    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM history")
    suspend fun deleteAll()

    /**
     * 删除早于指定时间戳的历史记录（收藏豁免），返回删除条数。
     */
    @Query("DELETE FROM history WHERE timestamp < :cutoff AND isFavorite = 0")
    suspend fun deleteOlderThan(cutoff: Long): Int
    
    @Query("SELECT * FROM history WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): HistoryItem?

    @Query("SELECT * FROM history WHERE content = :content AND isGenerated = :isGenerated LIMIT 1")
    suspend fun findByContentAndGenerated(content: String, isGenerated: Boolean): HistoryItem?

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HistoryItem?

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: Long): Flow<HistoryItem?>

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

    @Query("SELECT * FROM history WHERE tags LIKE '%' || :tag || '%' ORDER BY timestamp DESC")
    fun getHistoryByTag(tag: String): Flow<List<HistoryItem>>

    @Query("""
        SELECT * FROM history
        WHERE (:search = '' OR content LIKE '%' || :search || '%')
          AND (:tag IS NULL OR tags = :tag OR tags LIKE :tag || ',%' OR tags LIKE '%,' || :tag OR tags LIKE '%,' || :tag || ',%')
          AND (:isGenerated IS NULL OR isGenerated = :isGenerated)
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:type IS NULL OR type = :type)
          AND (:barcodeFormat IS NULL OR barcodeFormat = :barcodeFormat)
          AND (:startTime IS NULL OR timestamp >= :startTime)
        ORDER BY
          CASE WHEN :newestFirst THEN timestamp END DESC,
          CASE WHEN NOT :newestFirst THEN timestamp END ASC
    """)
    fun getHistory(
        search: String,
        tag: String?,
        isGenerated: Boolean?,
        favoritesOnly: Boolean,
        type: HistoryType?,
        barcodeFormat: String?,
        startTime: Long?,
        newestFirst: Boolean
    ): Flow<List<HistoryItem>>

    @Query("SELECT DISTINCT tags FROM history WHERE tags IS NOT NULL AND tags != ''")
    suspend fun getAllTags(): List<String>

    @Query("UPDATE history SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String?)

    @Query("UPDATE history SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    @Query("UPDATE history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
}

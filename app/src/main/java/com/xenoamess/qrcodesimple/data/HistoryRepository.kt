package com.xenoamess.qrcodesimple.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 历史记录仓库
 */
class HistoryRepository(context: Context) {
    
    private val historyDao = AppDatabase.getDatabase(context).historyDao()
    
    val allHistory: Flow<List<HistoryItem>> = historyDao.getAllHistory()
    val scannedHistory: Flow<List<HistoryItem>> = historyDao.getScannedHistory()
    val generatedHistory: Flow<List<HistoryItem>> = historyDao.getGeneratedHistory()
    
    suspend fun insert(item: HistoryItem): Long {
        return historyDao.insert(item)
    }
    
    suspend fun insertScan(content: String, type: HistoryType = HistoryType.QR_CODE) {
        // 检查是否已存在
        val existing = historyDao.findByContent(content)
        if (existing == null) {
            historyDao.insert(HistoryItem(content = content, type = type, isGenerated = false))
        }
    }
    
    suspend fun insertGenerate(content: String, type: HistoryType = HistoryType.QR_CODE) {
        // 检查是否已存在
        val existing = historyDao.findByContent(content)
        if (existing == null) {
            historyDao.insert(HistoryItem(content = content, type = type, isGenerated = true))
        }
    }
    
    suspend fun delete(item: HistoryItem) {
        historyDao.delete(item)
    }
    
    suspend fun update(item: HistoryItem) {
        historyDao.update(item)
    }
    
    suspend fun updateContent(id: Long, newContent: String) {
        historyDao.updateContent(id, newContent)
    }
    
    suspend fun deleteById(id: Long) {
        historyDao.deleteById(id)
    }
    
    suspend fun deleteAll() {
        historyDao.deleteAll()
    }

    // ===== 搜索功能 =====

    fun searchHistory(query: String): Flow<List<HistoryItem>> {
        return historyDao.searchHistory(query)
    }

    fun getHistoryByType(type: HistoryType): Flow<List<HistoryItem>> {
        return historyDao.getHistoryByType(type)
    }

    fun getHistoryByBarcodeFormat(format: String): Flow<List<HistoryItem>> {
        return historyDao.getHistoryByBarcodeFormat(format)
    }

    fun getFavoriteHistory(): Flow<List<HistoryItem>> {
        return historyDao.getFavoriteHistory()
    }

    suspend fun toggleFavorite(item: HistoryItem) {
        historyDao.update(item.copy(isFavorite = !item.isFavorite))
    }

    suspend fun addNotes(id: Long, notes: String) {
        val item = historyDao.getById(id)
        item?.let {
            historyDao.update(it.copy(notes = notes))
        }
    }

    suspend fun getAllTypes(): List<HistoryType> {
        return historyDao.getAllTypes()
    }

    suspend fun getAllBarcodeFormats(): List<String> {
        return historyDao.getAllBarcodeFormats()
    }
}
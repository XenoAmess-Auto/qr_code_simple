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
}
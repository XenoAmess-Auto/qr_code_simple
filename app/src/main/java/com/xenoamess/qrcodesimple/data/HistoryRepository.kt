package com.xenoamess.qrcodesimple.data

import android.content.Context
import com.xenoamess.qrcodesimple.QRCodeApp
import com.xenoamess.qrcodesimple.TagManager
import kotlinx.coroutines.flow.Flow

/**
 * 历史记录仓库
 */
class HistoryRepository(private val context: Context) {
    
    private val historyDao = AppDatabase.getDatabase(context).historyDao()
    
    val allHistory: Flow<List<HistoryItem>> = historyDao.getAllHistory()
    val scannedHistory: Flow<List<HistoryItem>> = historyDao.getScannedHistory()
    val generatedHistory: Flow<List<HistoryItem>> = historyDao.getGeneratedHistory()
    
    /**
     * 检查是否处于隐私模式
     */
    private fun isPrivacyMode(): Boolean {
        return QRCodeApp.isPrivacyMode(context)
    }
    
    suspend fun insert(item: HistoryItem): Long {
        // 隐私模式下不保存
        if (isPrivacyMode()) return -1
        return historyDao.insert(item)
    }
    
    suspend fun insertScan(content: String, type: HistoryType = HistoryType.QR_CODE) {
        // 隐私模式下不保存
        if (isPrivacyMode()) return
        
        // 检查是否已存在
        val existing = historyDao.findByContent(content)
        if (existing == null) {
            historyDao.insert(HistoryItem(content = content, type = type, isGenerated = false))
        }
    }
    
    suspend fun insertGenerate(content: String, type: HistoryType = HistoryType.QR_CODE) {
        // 隐私模式下不保存
        if (isPrivacyMode()) return
        
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

    suspend fun addTags(id: Long, tags: String) {
        val item = historyDao.getById(id)
        item?.let {
            val currentTags = TagManager.parseTags(it.tags).toMutableSet()
            currentTags.addAll(TagManager.parseTags(tags))
            historyDao.updateTags(id, TagManager.tagsToString(currentTags.toList()))
        }
    }

    suspend fun removeTag(id: Long, tag: String) {
        val item = historyDao.getById(id)
        item?.let {
            val currentTags = TagManager.parseTags(it.tags).toMutableList()
            currentTags.remove(tag)
            historyDao.updateTags(id, if (currentTags.isEmpty()) null else TagManager.tagsToString(currentTags))
        }
    }

    suspend fun setTags(id: Long, tags: List<String>) {
        historyDao.updateTags(id, if (tags.isEmpty()) null else TagManager.tagsToString(tags))
    }

    fun getHistoryByTag(tag: String): Flow<List<HistoryItem>> {
        return historyDao.getHistoryByTag(tag)
    }

    suspend fun getAllTags(): List<String> {
        return historyDao.getAllTags().flatMap { TagManager.parseTags(it) }.distinct()
    }

    suspend fun updateNotes(id: Long, notes: String?) {
        historyDao.updateNotes(id, notes)
    }

    suspend fun updateFavorite(id: Long, isFavorite: Boolean) {
        historyDao.updateFavorite(id, isFavorite)
    }
}
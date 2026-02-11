package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * 标签管理器
 */
object TagManager {

    private const val PREFS_NAME = "tag_manager"
    private const val KEY_ALL_TAGS = "all_tags"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取所有标签
     */
    fun getAllTags(): Set<String> {
        return prefs.getStringSet(KEY_ALL_TAGS, emptySet()) ?: emptySet()
    }

    /**
     * 添加标签
     */
    fun addTag(tag: String) {
        val tags = getAllTags().toMutableSet()
        tags.add(tag.trim())
        prefs.edit().putStringSet(KEY_ALL_TAGS, tags).apply()
    }

    /**
     * 删除标签
     */
    fun removeTag(tag: String) {
        val tags = getAllTags().toMutableSet()
        tags.remove(tag)
        prefs.edit().putStringSet(KEY_ALL_TAGS, tags).apply()
    }

    /**
     * 重命名标签
     */
    fun renameTag(oldTag: String, newTag: String) {
        val tags = getAllTags().toMutableSet()
        tags.remove(oldTag)
        tags.add(newTag.trim())
        prefs.edit().putStringSet(KEY_ALL_TAGS, tags).apply()
    }

    /**
     * 解析标签字符串
     */
    fun parseTags(tagsString: String?): List<String> {
        if (tagsString.isNullOrBlank()) return emptyList()
        return tagsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 将标签列表转换为字符串
     */
    fun tagsToString(tags: List<String>): String {
        return tags.joinToString(",")
    }

    /**
     * 获取建议标签
     */
    fun getSuggestedTags(query: String): List<String> {
        return getAllTags().filter { it.contains(query, ignoreCase = true) }
    }

    /**
     * 获取常用标签
     */
    fun getPopularTags(limit: Int = 10): Flow<List<String>> = flow {
        // 这里可以根据使用频率排序，暂时返回所有标签
        emit(getAllTags().take(limit).toList())
    }.flowOn(Dispatchers.IO)
}

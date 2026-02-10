package com.xenoamess.qrcodesimple.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 历史记录实体
 */
@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val type: HistoryType,
    val timestamp: Long = System.currentTimeMillis(),
    val isGenerated: Boolean = false
)

enum class HistoryType {
    QR_CODE,      // 二维码
    BARCODE,      // 条形码
    TEXT          // 纯文本
}
package com.xenoamess.qrcodesimple.data

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val type: HistoryType,
    val timestamp: Long = System.currentTimeMillis(),
    val isGenerated: Boolean = false,
    val barcodeFormat: String? = null,  // 条码格式（如 EAN_13, CODE_128 等）
    val styleJson: String? = null,      // 生成样式参数 JSON
    val isFavorite: Boolean = false,    // 是否收藏
    val notes: String? = null,            // 备注
    val tags: String? = null              // 标签（逗号分隔）
)

enum class HistoryType {
    QR_CODE,            // 二维码
    BARCODE,            // 一维码
    DATA_MATRIX,        // Data Matrix
    AZTEC,              // Aztec Code
    PDF417,             // PDF417
    RSS_14,             // RSS-14 / GS1 DataBar
    RSS_EXPANDED,       // RSS Expanded / GS1 DataBar Expanded
    MAXICODE,           // MaxiCode
    MICRO_QR,           // Micro QR Code
    UPC_EAN_EXTENSION,  // UPC/EAN Extension
    PHARMACODE,         // Pharmacode
    PLESSEY,            // Plessey Code
    MSI_PLESSEY,        // MSI Plessey
    TELEPEN,            // Telepen
    HAN_XIN,            // Han Xin Code
    GENERATED_ONLY,     // 仅支持生成的格式（Okapi 新增格式）
    TEXT                // 纯文本
}

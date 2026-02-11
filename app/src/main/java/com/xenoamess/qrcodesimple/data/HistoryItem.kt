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
    val isGenerated: Boolean = false,
    val barcodeFormat: String? = null,  // 条码格式（如 EAN_13, CODE_128 等）
    val isFavorite: Boolean = false,     // 是否收藏
    val notes: String? = null,            // 备注
    val tags: String? = null              // 标签（逗号分隔）
)

enum class HistoryType {
    QR_CODE,      // 二维码
    BARCODE,      // 一维码
    DATA_MATRIX,  // Data Matrix
    AZTEC,        // Aztec Code
    PDF417,       // PDF417
    TEXT          // 纯文本
}

/**
 * 条码格式枚举
 */
enum class BarcodeFormat(val displayName: String) {
    QR_CODE("QR Code"),
    DATA_MATRIX("Data Matrix"),
    AZTEC("Aztec"),
    PDF417("PDF417"),
    CODE_128("Code 128"),
    CODE_39("Code 39"),
    CODE_93("Code 93"),
    EAN_13("EAN-13"),
    EAN_8("EAN-8"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E"),
    CODABAR("Codabar"),
    ITF("ITF"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(format: String): BarcodeFormat {
            return try {
                valueOf(format.uppercase())
            } catch (e: Exception) {
                UNKNOWN
            }
        }
    }
}
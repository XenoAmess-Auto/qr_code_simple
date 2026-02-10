package com.xenoamess.qrcodesimple.data

import androidx.room.TypeConverter

/**
 * Room 类型转换器
 */
class Converters {
    
    @TypeConverter
    fun fromHistoryType(type: HistoryType): String {
        return type.name
    }
    
    @TypeConverter
    fun toHistoryType(name: String): HistoryType {
        return HistoryType.valueOf(name)
    }
}
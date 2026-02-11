package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryRepository
import com.xenoamess.qrcodesimple.data.HistoryType
import kotlinx.coroutines.flow.first

/**
 * 动态快捷方式管理器
 */
object AppShortcutManager {

    private const val MAX_DYNAMIC_SHORTCUTS = 2

    /**
     * 更新动态快捷方式 - 添加最近的历史记录
     */
    suspend fun updateDynamicShortcuts(context: Context) {
        val repository = HistoryRepository(context)
        
        // 获取最近的历史记录
        val recentItems = repository.allHistory.first().take(MAX_DYNAMIC_SHORTCUTS)
        
        val shortcuts = recentItems.mapIndexed { index, item ->
            createHistoryShortcut(context, item, index)
        }

        // 设置动态快捷方式
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /**
     * 创建历史记录快捷方式
     */
    private fun createHistoryShortcut(context: Context, item: HistoryItem, index: Int): ShortcutInfoCompat {
        val shortcutId = "history_${item.id}"
        
        // 截断内容作为标题
        val shortLabel = if (item.content.length > 15) {
            item.content.substring(0, 15) + "..."
        } else {
            item.content
        }

        // 根据类型选择图标
        val iconRes = when (item.type) {
            HistoryType.QR_CODE -> R.drawable.ic_qr_code
            HistoryType.BARCODE -> R.drawable.ic_barcode
            HistoryType.DATA_MATRIX -> R.drawable.ic_qr_code
            HistoryType.AZTEC -> R.drawable.ic_qr_code
            HistoryType.PDF417 -> R.drawable.ic_qr_code
            HistoryType.TEXT -> R.drawable.ic_text
        }

        return ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(shortLabel)
            .setLongLabel("Open: ${item.content}")
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setIntent(
                Intent(context, GenerateActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("content", item.content)
                }
            )
            .setRank(index)
            .build()
    }

    /**
     * 报告快捷方式使用统计
     */
    fun reportShortcutUsed(context: Context, shortcutId: String) {
        ShortcutManagerCompat.reportShortcutUsed(context, shortcutId)
    }

    /**
     * 移除所有动态快捷方式
     */
    fun removeAllDynamicShortcuts(context: Context) {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }
}

package com.xenoamess.qrcodesimple.decoder

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * 一维条码扫描辅助工具：将 Bitmap 转换为二值条空序列。
 */
object BarcodeScanUtils {

    /**
     * 从 Bitmap 中提取水平扫描线的条空宽度序列（true = 条，false = 空）。
     * 取图像中间行并做简单自适应二值化。
     */
    fun extractBars(bitmap: Bitmap): List<Boolean> {
        val width = bitmap.width
        val height = bitmap.height
        val y = height / 2
        val pixels = IntArray(width)
        bitmap.getPixels(pixels, 0, width, 0, y, width, 1)

        val grayscale = pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
        }

        val threshold = grayscale.average().toInt()
        return grayscale.map { it < threshold }
    }

    /**
     * 将条空序列按连续相同值分组，返回每组的值和宽度。
     */
    fun groupBars(bars: List<Boolean>): List<Pair<Boolean, Int>> {
        if (bars.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<Boolean, Int>>()
        var current = bars[0]
        var count = 1
        for (i in 1 until bars.size) {
            if (bars[i] == current) {
                count++
            } else {
                result.add(current to count)
                current = bars[i]
                count = 1
            }
        }
        result.add(current to count)
        return result
    }

    /**
     * 将绝对宽度序列归一化为相对模块宽度（基于最窄条/空的宽度）。
     */
    fun normalizeWidths(groups: List<Pair<Boolean, Int>>): List<Pair<Boolean, Int>> {
        if (groups.size < 2) return groups
        val minWidth = groups.minOf { it.second }.coerceAtLeast(1)
        return groups.map { it.first to (it.second.toDouble() / minWidth).roundToInt().coerceAtLeast(1) }
    }
}

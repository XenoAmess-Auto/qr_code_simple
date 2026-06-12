package com.xenoamess.qrcodesimple.decoder

import android.graphics.Bitmap

/**
 * Pharmacode 一维条码解码器。
 *
 * Pharmacode 使用 2 条和 2 空表示一个字符，每个字符由 4 个模块组成。
 * 条空宽度为 1 或 2 个模块，因此每个字符编码为 4 位二进制。
 * 编码值 = sum(b_i * 2^i)，其中条=1，空=0，从右到左读取。
 * 有效编码值为 3 到 131070。
 */
object PharmacodeDecoder {

    fun decode(bitmap: Bitmap): String? {
        val bars = BarcodeScanUtils.extractBars(bitmap)
        val groups = BarcodeScanUtils.groupBars(bars)
        val normalized = BarcodeScanUtils.normalizeWidths(groups)

        // Pharmacode 以条开始、以条结束，因此条空组数为 2n + 1
        if (normalized.size < 5 || normalized.size % 2 == 0) return null

        // 去掉前导空和尾随空
        val trimmed = normalized.dropWhile { !it.first }.dropLastWhile { !it.first }
        if (trimmed.size < 5 || trimmed.size % 2 == 0) return null

        // 每两个条空组构成一个字符（条 + 空）
        val value = mutableListOf<Int>()
        var i = 0
        while (i < trimmed.size - 1) {
            val barWidth = trimmed[i].second
            val spaceWidth = trimmed[i + 1].second
            if (barWidth !in 1..2 || spaceWidth !in 1..2) return null
            val bit = when {
                barWidth == 1 && spaceWidth == 1 -> 0
                barWidth == 1 && spaceWidth == 2 -> 1
                barWidth == 2 && spaceWidth == 1 -> 2
                barWidth == 2 && spaceWidth == 2 -> 3
                else -> return null
            }
            value.add(bit)
            i += 2
        }

        // 从右到左解释：最低位在最右边
        var code = 0
        for (bit in value.reversed()) {
            code = code * 4 + bit
        }

        if (code < 3 || code > 131070) return null
        return code.toString()
    }
}

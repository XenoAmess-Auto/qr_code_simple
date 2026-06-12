package com.xenoamess.qrcodesimple.decoder

import android.graphics.Bitmap

/**
 * Telepen 一维条码解码器。
 *
 * Telepen 每个字符由 8 个模块组成，交替出现条和空。
 * 条空宽度为 1 或 2 个模块，8 个模块宽度之和固定为 8。
 * 编码值 = 读取的 8 位二进制值，条=1，空=0。
 */
object TelepenDecoder {

    fun decode(bitmap: Bitmap): String? {
        val bars = BarcodeScanUtils.extractBars(bitmap)
        val groups = BarcodeScanUtils.groupBars(bars)
        val normalized = BarcodeScanUtils.normalizeWidths(groups)

        // Telepen 以条开始、以条结束，每组 8 个条空组表示一个字符
        if (normalized.size < 16 || normalized.size % 2 != 0) return null

        val trimmed = normalized.dropWhile { !it.first }.dropLastWhile { !it.first }
        if (trimmed.size % 2 != 0) return null

        val builder = StringBuilder()
        var i = 0
        while (i + 7 < trimmed.size) {
            var value = 0
            var moduleSum = 0
            for (j in 0 until 8) {
                val width = trimmed[i + j].second
                if (width !in 1..2) return null
                moduleSum += width
                value = value shl 1
                if (trimmed[i + j].first) {
                    value = value or 1
                }
            }
            if (moduleSum != 8) return null
            builder.append(value.toChar())
            i += 8
        }

        val result = builder.toString()
        return if (result.isNotEmpty()) result else null
    }
}

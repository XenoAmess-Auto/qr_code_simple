package com.xenoamess.qrcodesimple.decoder

import android.graphics.Bitmap

/**
 * Plessey Code / MSI Plessey 一维条码解码器。
 *
 * Plessey 使用 4 位二进制编码，每位由条（1）或空（0）的 8 个模块宽度表示，
 * 每位后跟一个窄条作为分隔符。
 * MSI Plessey 在 Plessey 基础上可选择性地添加校验位。
 *
 * 这里实现的是基础 Plessey/MSI 扫描解码，返回纯数字字符串。
 */
object PlesseyDecoder {

    private val PLESSEY_PATTERNS = mapOf(
        "0000" to '0',
        "0001" to '1',
        "0010" to '2',
        "0011" to '3',
        "0100" to '4',
        "0101" to '5',
        "0110" to '6',
        "0111" to '7',
        "1000" to '8',
        "1001" to '9',
        "1010" to 'A',
        "1011" to 'B',
        "1100" to 'C',
        "1101" to 'D',
        "1110" to 'E',
        "1111" to 'F'
    )

    fun decode(bitmap: Bitmap): String? {
        val bars = BarcodeScanUtils.extractBars(bitmap)
        val groups = BarcodeScanUtils.groupBars(bars)
        val normalized = BarcodeScanUtils.normalizeWidths(groups)

        // Plessey 条码结构：起始符（1101）+ 数据 + 终止符（1101）
        if (normalized.size < 20) return null

        val bits = normalized.map { it.first }
        val builder = StringBuilder()

        // 简单按位解析：每个 1 视为 bit 1，0 视为 bit 0，忽略分隔符
        var i = 0
        while (i < bits.size) {
            // 跳过前导和尾随条
            if (i + 4 > bits.size) break
            val chunk = bits.subList(i, i + 4).joinToString("") { if (it) "1" else "0" }
            PLESSEY_PATTERNS[chunk]?.let { builder.append(it) }
            i += 5 // 4 数据位 + 1 分隔条
        }

        val result = builder.toString()
        return if (result.length >= 4) result else null
    }
}

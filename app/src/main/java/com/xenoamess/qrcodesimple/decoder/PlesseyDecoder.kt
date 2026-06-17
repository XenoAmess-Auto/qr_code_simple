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

        if (normalized.size < 20) return null

        // Plessey 条码：bit 0 = 窄条 (1)，bit 1 = 宽条 (2)；空统一为 1 个模块
        val bits = StringBuilder()
        var i = 0
        while (i < normalized.size) {
            val (isBar, width) = normalized[i]
            if (isBar) {
                when (width) {
                    1 -> bits.append('0')
                    2 -> bits.append('1')
                    else -> return null
                }
            }
            i++
        }

        val bitStr = bits.toString()
        val startIndex = bitStr.indexOf("1101")
        val stopIndex = bitStr.lastIndexOf("1101")
        if (startIndex < 0 || stopIndex < 0 || stopIndex <= startIndex) return null

        val dataBits = bitStr.substring(startIndex + 4, stopIndex)
        if (dataBits.length < 8 || (dataBits.length - 8) % 4 != 0) return null

        val contentBits = dataBits.substring(0, dataBits.length - 8)
        val crcBits = dataBits.substring(dataBits.length - 8)

        val builder = StringBuilder()
        for (j in contentBits.indices step 4) {
            val chunk = contentBits.substring(j, j + 4)
            builder.append(PLESSEY_PATTERNS[chunk] ?: return null)
        }

        // 可选：校验 CRC（这里仅返回识别内容）
        return builder.toString()
    }
}

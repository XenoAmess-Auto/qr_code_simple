package com.xenoamess.qrcodesimple.decoder

import android.graphics.Bitmap

/**
 * MSI Plessey 一维条码解码器。
 *
 * MSI Plessey 使用 8 模块模式表示每个数字：
 * - 起始符：21（宽条-窄空）
 * - 数据位：每个数字对应 8 个条空模块
 * - 停止符：121（窄条-宽空-窄条）
 *
 * 支持可选校验位：mod-10、double mod-10、mod-11、mod-11+mod-10。
 * 这里先返回识别到的数字字符串，调用方可自行校验。
 */
object MsiPlesseyDecoder {

    private val MSI_PATTERNS = mapOf(
        "12121212" to '0',
        "12121221" to '1',
        "12122112" to '2',
        "12122121" to '3',
        "12211212" to '4',
        "12211221" to '5',
        "12212112" to '6',
        "12212121" to '7',
        "21121212" to '8',
        "21121221" to '9'
    )

    fun decode(bitmap: Bitmap): String? {
        val bars = BarcodeScanUtils.extractBars(bitmap)
        val groups = BarcodeScanUtils.groupBars(bars)
        val normalized = BarcodeScanUtils.normalizeWidths(groups)

        if (normalized.size < 14) return null

        val pattern = normalized.joinToString("") {
            val c = when {
                it.first && it.second == 1 -> '1'
                it.first && it.second == 2 -> '2'
                !it.first && it.second == 1 -> '1'
                !it.first && it.second == 2 -> '2'
                else -> return null
            }
            c.toString()
        }

        if (!pattern.startsWith("21")) return null
        if (!pattern.endsWith("121")) return null

        val dataPattern = pattern.substring(2, pattern.length - 3)
        if (dataPattern.length % 8 != 0) return null

        val builder = StringBuilder()
        for (i in dataPattern.indices step 8) {
            val chunk = dataPattern.substring(i, i + 8)
            val digit = MSI_PATTERNS[chunk] ?: return null
            builder.append(digit)
        }

        val result = builder.toString()
        return if (result.length >= 1) result else null
    }
}

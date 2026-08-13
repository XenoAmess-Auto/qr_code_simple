package com.xenoamess.qrcodesimple

/**
 * 批量生成的样式交接。BatchGenerateActivity 在启动 BatchResultActivity 前
 * 写入，BatchResultActivity 读取后立即清除。进程内传递，避免把
 * logoBitmap 等不可序列化字段塞进 Intent 或 JSON。
 */
object BatchStyleHolder {

    @Volatile
    var style: AdvancedBarcodeGenerator.StyleConfig? = null

    fun consume(): AdvancedBarcodeGenerator.StyleConfig? {
        val current = style
        style = null
        return current
    }
}

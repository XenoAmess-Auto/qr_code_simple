package com.xenoamess.qrcodesimple.decoder

import android.graphics.Bitmap

/**
 * 自定义一维条码扫描入口。
 *
 * 依次尝试 Pharmacode、Plessey/MSI Plessey、Telepen。
 */
object CustomLinearBarcodeScanner {

    enum class Format {
        PHARMACODE,
        PLESSEY,
        TELEPEN
    }

    data class Result(
        val text: String,
        val format: Format
    )

    fun scan(bitmap: Bitmap): List<Result> {
        PharmacodeDecoder.decode(bitmap)?.let {
            return listOf(Result(it, Format.PHARMACODE))
        }
        PlesseyDecoder.decode(bitmap)?.let {
            return listOf(Result(it, Format.PLESSEY))
        }
        TelepenDecoder.decode(bitmap)?.let {
            return listOf(Result(it, Format.TELEPEN))
        }
        return emptyList()
    }
}

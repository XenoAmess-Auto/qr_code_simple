package com.xenoamess.qrcodesimple.decoder

import android.graphics.Bitmap
import boofcv.abst.fiducial.MicroQrCodeDetector
import boofcv.factory.fiducial.ConfigMicroQrCode
import boofcv.factory.fiducial.FactoryFiducial
import boofcv.struct.image.GrayU8
import boofcv.android.ConvertBitmap
import georegression.struct.shapes.Polygon2D_F64

/**
 * 基于 BoofCV 的 Micro QR Code 扫描器
 */
object MicroQrCodeScanner {

    fun scan(bitmap: Bitmap): List<Result> {
        val gray = ConvertBitmap.bitmapToGray(bitmap, null as GrayU8?, null)
            ?: return emptyList()

        val config = ConfigMicroQrCode()
        val detector: MicroQrCodeDetector<GrayU8> = FactoryFiducial.microqr(config, GrayU8::class.java)
        detector.process(gray)

        return detector.detections.map {
            Result(
                text = it.message ?: "",
                bounds = it.bounds
            )
        }.filter { it.text.isNotEmpty() }
    }

    data class Result(
        val text: String,
        val bounds: Polygon2D_F64
    )
}

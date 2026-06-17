package com.xenoamess.qrcodesimple.decoder

import android.graphics.Bitmap
import boofcv.abst.fiducial.MicroQrCodeDetector
import boofcv.factory.fiducial.ConfigMicroQrCode
import boofcv.factory.fiducial.FactoryFiducial
import boofcv.struct.image.GrayU8
import georegression.struct.shapes.Polygon2D_F64

/**
 * 基于 BoofCV 的 Micro QR Code 扫描器
 */
object MicroQrCodeScanner {

    fun scan(bitmap: Bitmap): List<Result> {
        val gray = bitmapToGray(bitmap) ?: return emptyList()

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

    private fun bitmapToGray(bitmap: Bitmap): GrayU8? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = GrayU8(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = ((r + g + b) / 3)
                gray.set(x, y, if (luminance < 128) 0 else 255)
            }
        }
        return gray
    }

    data class Result(
        val text: String,
        val bounds: Polygon2D_F64
    )
}

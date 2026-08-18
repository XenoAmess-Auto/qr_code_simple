package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/** Copies YUV_420_888 using each plane's row/pixel stride before CameraX releases it. */
object Yuv420Converter {
    data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

    fun toBitmap(image: ImageProxy): Bitmap = toBitmap(
        image.cropRect.left, image.cropRect.top, image.cropRect.width(), image.cropRect.height(),
        image.width, image.height,
        image.planes.map { Plane(it.buffer.duplicate(), it.rowStride, it.pixelStride) }
    )

    internal fun toBitmap(
        cropLeft: Int, cropTop: Int, cropWidth: Int, cropHeight: Int,
        imageWidth: Int, imageHeight: Int, planes: List<Plane>
    ): Bitmap {
        require(planes.size == 3)
        val pixels = argbPixels(cropLeft, cropTop, cropWidth, cropHeight, planes)
        return Bitmap.createBitmap(pixels, cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
    }

    internal fun argbPixels(cropLeft: Int, cropTop: Int, cropWidth: Int, cropHeight: Int, planes: List<Plane>): IntArray {
        val pixels = IntArray(cropWidth * cropHeight)
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val sourceX = cropLeft + x
                val sourceY = cropTop + y
                val luminance = sample(planes[0], sourceX, sourceY)
                val chromaX = sourceX / 2
                val chromaY = sourceY / 2
                val u = sample(planes[1], chromaX, chromaY) - 128
                val v = sample(planes[2], chromaX, chromaY) - 128
                val r = (luminance + 1.402 * v).toInt().coerceIn(0, 255)
                val g = (luminance - 0.344136 * u - 0.714136 * v).toInt().coerceIn(0, 255)
                val b = (luminance + 1.772 * u).toInt().coerceIn(0, 255)
                pixels[y * cropWidth + x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }

    private fun sample(plane: Plane, x: Int, y: Int): Int {
        val offset = y * plane.rowStride + x * plane.pixelStride
        return plane.buffer.get(offset).toInt() and 0xff
    }
}

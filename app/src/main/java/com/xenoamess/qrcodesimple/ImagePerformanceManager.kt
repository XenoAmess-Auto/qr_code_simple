package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 图片性能优化管理器
 * 处理大图加载、内存优化
 */
object ImagePerformanceManager {

    private const val TAG = "ImagePerformance"
    private const val MAX_IMAGE_DIMENSION = 2048
    private const val MAX_MEMORY_SIZE = 1024 * 1024 * 4L  // 4MB
    private const val SAMPLE_SIZE_THRESHOLD = 1024

    data class ImageLoadOptions(
        val maxWidth: Int = MAX_IMAGE_DIMENSION,
        val maxHeight: Int = MAX_IMAGE_DIMENSION,
        val compressQuality: Int = 90,
        val preferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    )

    /**
     * 优化加载图片
     */
    suspend fun loadOptimizedBitmap(
        data: ByteArray,
        options: ImageLoadOptions = ImageLoadOptions()
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val decodeBounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, decodeBounds)

            val (width, height) = decodeBounds.outWidth to decodeBounds.outHeight
            val sampleSize = calculateInSampleSize(width, height, options.maxWidth, options.maxHeight)
            val scaledWidth = width / sampleSize
            val scaledHeight = height / sampleSize

            Log.d(TAG, "Loading: ${width}x$height -> ${scaledWidth}x$scaledHeight (sample=$sampleSize)")

            val estimatedMemory = scaledWidth.toLong() * scaledHeight * 4L
            if (estimatedMemory > MAX_MEMORY_SIZE) {
                Log.w(TAG, "Image too large, applying compression")
                return@withContext loadWithHeavyCompression(data, options)
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = options.preferredConfig
                inPurgeable = true
                inInputShareable = true
            }

            BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)?.let { bitmap ->
                scaleBitmapIfNeeded(bitmap, options.maxWidth, options.maxHeight)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load optimized bitmap", e)
            null
        }
    }

    private fun calculateInSampleSize(
        width: Int, height: Int, maxWidth: Int, maxHeight: Int
    ): Int {
        var inSampleSize = 1
        if (width > maxWidth || height > maxHeight) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            while ((halfWidth / inSampleSize) >= maxWidth &&
                (halfHeight / inSampleSize) >= maxHeight
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    }

    private fun loadWithHeavyCompression(data: ByteArray, options: ImageLoadOptions): Bitmap? {
        return try {
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = 4
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)?.let { bitmap ->
                compressToSize(bitmap, MAX_MEMORY_SIZE / 2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heavy compression failed", e)
            null
        }
    }

    private fun compressToSize(bitmap: Bitmap, maxSize: Long): Bitmap {
        var quality = 90
        var compressed = bitmap

        while (quality > 30) {
            val stream = ByteArrayOutputStream()
            compressed.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val size = stream.size().toLong()

            if (size <= maxSize) break

            quality -= 10
            stream.reset()

            if (quality <= 50 && compressed.width > 512) {
                val newWidth = compressed.width / 2
                val newHeight = compressed.height / 2
                val scaled = Bitmap.createScaledBitmap(compressed, newWidth, newHeight, true)
                if (scaled != compressed) compressed.recycle()
                compressed = scaled
            }
        }
        return compressed
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it != bitmap) bitmap.recycle()
        }
    }

    fun getBitmapMemorySize(bitmap: Bitmap): Long = bitmap.byteCount.toLong()

    fun safeRecycle(bitmap: Bitmap?) {
        try {
            if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error recycling bitmap", e)
        }
    }
}

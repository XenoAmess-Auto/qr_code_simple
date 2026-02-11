package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 二维码修复管理器
 * 尝试修复模糊、低对比度的二维码
 */
object QRCodeRestorationManager {

    private const val TAG = "QRCodeRestore"

    data class RestorationOptions(
        val tryGrayscale: Boolean = true,
        val tryContrastEnhancement: Boolean = true,
        val trySharpening: Boolean = true,
        val tryBinarization: Boolean = true,
        val tryScaling: Boolean = true
    )

    /**
     * 尝试修复二维码
     * 返回多个处理后的图片，增加识别成功率
     */
    suspend fun restoreQRCode(
        original: Bitmap,
        options: RestorationOptions = RestorationOptions()
    ): List<Bitmap> = withContext(Dispatchers.Default) {
        val results = mutableListOf<Bitmap>()

        try {
            // 原始图片
            results.add(original)

            // 1. 灰度处理
            if (options.tryGrayscale) {
                toGrayscale(original)?.let { results.add(it) }
            }

            // 2. 对比度增强
            if (options.tryContrastEnhancement) {
                enhanceContrast(original, 1.5f)?.let { results.add(it) }
                enhanceContrast(original, 2.0f)?.let { results.add(it) }
            }

            // 3. 锐化
            if (options.trySharpening) {
                sharpen(original)?.let { results.add(it) }
            }

            // 4. 二值化
            if (options.tryBinarization) {
                binarize(original, 128)?.let { results.add(it) }
                binarize(original, 160)?.let { results.add(it) }
            }

            // 5. 缩放
            if (options.tryScaling) {
                scale(original, 1.5f)?.let { results.add(it) }
                scale(original, 2.0f)?.let { results.add(it) }
            }

            // 6. 组合处理：灰度 + 二值化
            if (options.tryGrayscale && options.tryBinarization) {
                toGrayscale(original)?.let { gray ->
                    binarize(gray, 128)?.let { results.add(it) }
                }
            }

            // 7. 组合处理：增强对比度 + 二值化
            if (options.tryContrastEnhancement && options.tryBinarization) {
                enhanceContrast(original, 1.8f)?.let { enhanced ->
                    binarize(enhanced, 128)?.let { results.add(it) }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Restoration failed", e)
        }

        results.distinct()
    }

    /**
     * 转换为灰度图
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height

            val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(grayscale)

            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    setSaturation(0f)
                })
            }

            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            grayscale
        } catch (e: Exception) {
            Log.e(TAG, "Grayscale conversion failed", e)
            null
        }
    }

    /**
     * 增强对比度
     */
    private fun enhanceContrast(bitmap: Bitmap, contrast: Float): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height

            val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(enhanced)

            val matrix = ColorMatrix().apply {
                // 对比度矩阵
                val scale = contrast
                val translate = (-0.5f * scale + 0.5f) * 255f

                set(floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                ))
            }

            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }

            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            enhanced
        } catch (e: Exception) {
            Log.e(TAG, "Contrast enhancement failed", e)
            null
        }
    }

    /**
     * 锐化
     */
    private fun sharpen(bitmap: Bitmap): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height

            val sharpened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sharpened)

            // 简单的锐化矩阵
            val sharpenMatrix = ColorMatrix().apply {
                set(floatArrayOf(
                    0f, -1f, 0f, 0f, 0f,
                    -1f, 5f, -1f, 0f, 0f,
                    0f, -1f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }

            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(sharpenMatrix)
            }

            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            sharpened
        } catch (e: Exception) {
            Log.e(TAG, "Sharpening failed", e)
            null
        }
    }

    /**
     * 二值化
     */
    private fun binarize(bitmap: Bitmap, threshold: Int): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height

            val binary = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val pixel = bitmap.getPixel(x, y)
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)

                    // 计算灰度值
                    val gray = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()

                    // 二值化
                    val binaryColor = if (gray >= threshold) Color.WHITE else Color.BLACK
                    binary.setPixel(x, y, binaryColor)
                }
            }

            binary
        } catch (e: Exception) {
            Log.e(TAG, "Binarization failed", e)
            null
        }
    }

    /**
     * 缩放
     */
    private fun scale(bitmap: Bitmap, scale: Float): Bitmap? {
        return try {
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()

            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            Log.e(TAG, "Scaling failed", e)
            null
        }
    }

    /**
     * 预处理图片以提高识别率
     * 快速模式，只应用最有效的处理
     */
    suspend fun quickPreprocess(bitmap: Bitmap): List<Bitmap> = withContext(Dispatchers.Default) {
        val results = mutableListOf<Bitmap>()

        results.add(bitmap)

        // 灰度
        toGrayscale(bitmap)?.let { results.add(it) }

        // 轻度对比度增强
        enhanceContrast(bitmap, 1.3f)?.let { results.add(it) }

        results
    }
}

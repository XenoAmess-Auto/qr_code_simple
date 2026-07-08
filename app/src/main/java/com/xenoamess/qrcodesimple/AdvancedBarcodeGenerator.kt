package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat

/**
 * 高级条码生成器 - 支持样式定制
 * 所有像素操作均使用 Bitmap.setPixel / setPixels，避免 Robolectric 下 Canvas 绘制不可靠的问题。
 */
object AdvancedBarcodeGenerator {

    data class StyleConfig(
        val foregroundColor: Int = Color.BLACK,
        val backgroundColor: Int = Color.WHITE,
        val foregroundBitmap: Bitmap? = null,
        val backgroundBitmap: Bitmap? = null,
        /**
         * 整张条码图片的外圆角半径（0~1，占图片短边的一半）。
         * 0 = 直角，1 = 完全圆形。
         * 仅对 1D 条码生效；QR/2D 码保持直角以保证定位图案完整。
         */
        val cornerRadius: Float = 0f,
        val logoBitmap: Bitmap? = null,
        val logoScale: Float = 0.2f,
        val gradientStartColor: Int? = null,
        val gradientEndColor: Int? = null,
        val gradientDirection: GradientDirection = GradientDirection.HORIZONTAL
    )

    enum class GradientDirection {
        HORIZONTAL, VERTICAL, DIAGONAL
    }

    fun generateStyled(
        content: String,
        format: BarcodeFormat = BarcodeFormat.QR_CODE,
        size: Int = 800,
        style: StyleConfig = StyleConfig()
    ): Bitmap? {
        return try {
            when (format) {
                BarcodeFormat.QR_CODE -> generateStyledQR(content, size, style)
                else -> generateGenericWithStyle(content, format, size, style)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateStyledQR(content: String, size: Int, styleConfig: StyleConfig): Bitmap {
        val hints = hashMapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )

        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        fillBackground(output, styleConfig)

        val logoRect = computeLogoRect(output, styleConfig)
        val cellSize = size.toFloat() / bitMatrix.width

        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                if (bitMatrix.get(x, y)) {
                    val startX = (x * cellSize).toInt()
                    val endX = ((x + 1) * cellSize).toInt().coerceAtMost(size)
                    val startY = (y * cellSize).toInt()
                    val endY = ((y + 1) * cellSize).toInt().coerceAtMost(size)
                    fillModule(output, startX, endX, startY, endY, styleConfig, logoRect)
                }
            }
        }

        styleConfig.logoBitmap?.let { logo ->
            drawLogoOnBitmap(output, logo, logoRect)
        }

        return applyOuterCornerRadius(output, styleConfig)
    }

    private fun fillBackground(bitmap: Bitmap, styleConfig: StyleConfig) {
        val width = bitmap.width
        val height = bitmap.height
        when {
            styleConfig.backgroundBitmap != null -> {
                val scaled = Bitmap.createScaledBitmap(styleConfig.backgroundBitmap, width, height, true)
                bitmap.setPixels(scaled.getPixelsArray(), 0, width, 0, 0, width, height)
                if (scaled !== styleConfig.backgroundBitmap) scaled.recycle()
            }
            else -> {
                bitmap.eraseColor(styleConfig.backgroundColor)
            }
        }
    }

    private fun Bitmap.getPixelsArray(): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    private fun sampleBitmap(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Int {
        val bx = (x.toFloat() / width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val by = (y.toFloat() / height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(bx, by)
    }

    private fun fillModule(
        output: Bitmap,
        startX: Int,
        endX: Int,
        startY: Int,
        endY: Int,
        styleConfig: StyleConfig,
        logoRect: Rect?
    ) {
        val foregroundBitmap = styleConfig.foregroundBitmap
        val hasGradient = styleConfig.gradientStartColor != null && styleConfig.gradientEndColor != null
        for (x in startX until endX) {
            for (y in startY until endY) {
                if (logoRect?.contains(x, y) == true) continue
                val color = when {
                    foregroundBitmap != null -> sampleBitmap(foregroundBitmap, x, y, output.width, output.height)
                    hasGradient -> resolveGradientColor(x + 0.5f, y + 0.5f, output.width, output.height, styleConfig)
                    else -> styleConfig.foregroundColor
                }
                output.setPixel(x, y, color)
            }
        }
    }

    private fun generateGenericWithStyle(
        content: String,
        format: BarcodeFormat,
        size: Int,
        styleConfig: StyleConfig
    ): Bitmap {
        val config = BarcodeGenerator.BarcodeConfig(
            format = format,
            width = size,
            height = size,
            foregroundColor = Color.BLACK,
            backgroundColor = Color.WHITE
        )

        val original = BarcodeGenerator.generate(content, config)
            ?: throw IllegalStateException("Failed to generate barcode")

        val logoRect = computeLogoRect(original, styleConfig)
        val styled = applyStyle(original, styleConfig, logoRect)

        styleConfig.logoBitmap?.let { logo ->
            drawLogoOnBitmap(styled, logo, logoRect)
        }

        return applyOuterCornerRadius(styled, styleConfig)
    }

    private fun applyOuterCornerRadius(bitmap: Bitmap, styleConfig: StyleConfig): Bitmap {
        val ratio = styleConfig.cornerRadius.coerceIn(0f, 1f)
        if (ratio <= 0f) return bitmap
        val maxRadius = minOf(bitmap.width, bitmap.height) / 2f
        val radiusPx = ratio * maxRadius
        return addRoundedCorners(bitmap, radiusPx)
    }

    private fun applyStyle(bitmap: Bitmap, styleConfig: StyleConfig, logoRect: Rect?): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val foregroundBitmap = styleConfig.foregroundBitmap
        val backgroundBitmap = styleConfig.backgroundBitmap
        val hasGradient = styleConfig.gradientStartColor != null && styleConfig.gradientEndColor != null

        for (i in pixels.indices) {
            val x = i % width
            val y = i / width
            if (logoRect?.contains(x, y) == true) {
                output.setPixel(x, y, resolveBackgroundColor(x, y, width, height, styleConfig))
                continue
            }
            val gray = (Color.red(pixels[i]) + Color.green(pixels[i]) + Color.blue(pixels[i])) / 3
            val isDark = gray < 128
            val color = when {
                isDark && foregroundBitmap != null -> sampleBitmap(foregroundBitmap, x, y, width, height)
                isDark && hasGradient -> resolveGradientColor(x + 0.5f, y + 0.5f, width, height, styleConfig)
                isDark -> styleConfig.foregroundColor
                backgroundBitmap != null -> sampleBitmap(backgroundBitmap, x, y, width, height)
                else -> styleConfig.backgroundColor
            }
            output.setPixel(x, y, color)
        }

        return output
    }

    private fun resolveBackgroundColor(x: Int, y: Int, width: Int, height: Int, styleConfig: StyleConfig): Int {
        return if (styleConfig.backgroundBitmap != null) {
            sampleBitmap(styleConfig.backgroundBitmap, x, y, width, height)
        } else {
            styleConfig.backgroundColor
        }
    }

    private fun resolveGradientColor(cx: Float, cy: Float, width: Int, height: Int, styleConfig: StyleConfig): Int {
        return if (styleConfig.gradientStartColor != null && styleConfig.gradientEndColor != null) {
            val fraction = when (styleConfig.gradientDirection) {
                GradientDirection.HORIZONTAL -> cx / width
                GradientDirection.VERTICAL -> cy / height
                GradientDirection.DIAGONAL -> (cx + cy) / (width + height)
            }
            interpolateColor(styleConfig.gradientStartColor, styleConfig.gradientEndColor, fraction.coerceIn(0f, 1f))
        } else {
            styleConfig.foregroundColor
        }
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val inverse = 1 - fraction
        val a = (Color.alpha(startColor) * inverse + Color.alpha(endColor) * fraction).toInt()
        val r = (Color.red(startColor) * inverse + Color.red(endColor) * fraction).toInt()
        val g = (Color.green(startColor) * inverse + Color.green(endColor) * fraction).toInt()
        val b = (Color.blue(startColor) * inverse + Color.blue(endColor) * fraction).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun computeLogoRect(bitmap: Bitmap, styleConfig: StyleConfig): Rect? {
        val logo = styleConfig.logoBitmap ?: return null
        val maxLogoSize = (bitmap.width * 0.3f).toInt().coerceAtLeast(50)
        val requested = (bitmap.width * styleConfig.logoScale.coerceIn(0f, 1f)).toInt().coerceAtLeast(50)
        val logoSize = requested.coerceAtMost(maxLogoSize)
        val left = (bitmap.width - logoSize) / 2
        val top = (bitmap.height - logoSize) / 2
        return Rect(left, top, left + logoSize, top + logoSize)
    }

    private fun drawLogoOnBitmap(output: Bitmap, logo: Bitmap, logoRect: Rect?) {
        if (logoRect == null) return
        val logoSize = logoRect.width()
        val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
        for (y in 0 until logoSize) {
            for (x in 0 until logoSize) {
                val color = scaledLogo.getPixel(x, y)
                if (Color.alpha(color) > 0) {
                    output.setPixel(logoRect.left + x, logoRect.top + y, color)
                }
            }
        }
        if (scaledLogo !== logo) {
            scaledLogo.recycle()
        }
    }

    fun addRoundedCorners(bitmap: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return bitmap
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        return output
    }

    object ColorSchemes {
        val CLASSIC = StyleConfig(foregroundColor = Color.BLACK, backgroundColor = Color.WHITE)
        val BLUE = StyleConfig(foregroundColor = Color.parseColor("#1976D2"), backgroundColor = Color.WHITE)
        val GREEN = StyleConfig(foregroundColor = Color.parseColor("#388E3C"), backgroundColor = Color.WHITE)
        val RED = StyleConfig(foregroundColor = Color.parseColor("#D32F2F"), backgroundColor = Color.WHITE)
        val PURPLE = StyleConfig(foregroundColor = Color.parseColor("#7B1FA2"), backgroundColor = Color.WHITE)
        val ORANGE = StyleConfig(foregroundColor = Color.parseColor("#F57C00"), backgroundColor = Color.WHITE)
        val DARK = StyleConfig(foregroundColor = Color.WHITE, backgroundColor = Color.parseColor("#212121"))
        val CYAN = StyleConfig(foregroundColor = Color.parseColor("#00BCD4"), backgroundColor = Color.WHITE)
    }
}

package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat

/**
 * 高级条码生成器 - 支持样式定制
 */
object AdvancedBarcodeGenerator {

    data class StyleConfig(
        val foregroundColor: Int = Color.BLACK,
        val backgroundColor: Int = Color.WHITE,
        /**
         * 整张条码图片的外圆角半径（0~1，占图片短边的一半）。
         * 0 = 直角，1 = 完全圆形。
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
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF417 -> generateStyledZXing2D(content, format, size, style)
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
        output.eraseColor(styleConfig.backgroundColor)

        val cellSize = size.toFloat() / bitMatrix.width
        val hasGradient = styleConfig.gradientStartColor != null && styleConfig.gradientEndColor != null

        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                if (bitMatrix.get(x, y)) {
                    val startX = (x * cellSize).toInt()
                    val endX = ((x + 1) * cellSize).toInt().coerceAtMost(size)
                    val startY = (y * cellSize).toInt()
                    val endY = ((y + 1) * cellSize).toInt().coerceAtMost(size)
                    fillModule(output, startX, endX, startY, endY, styleConfig, hasGradient)
                }
            }
        }

        styleConfig.logoBitmap?.let { logo ->
            addLogoToCenter(Canvas(output), output, logo, styleConfig.logoScale, styleConfig.backgroundColor)
        }

        return output
    }

    private fun generateStyledZXing2D(
        content: String,
        format: BarcodeFormat,
        size: Int,
        styleConfig: StyleConfig
    ): Bitmap {
        val zxingFormat = when (format) {
            BarcodeFormat.DATA_MATRIX -> com.google.zxing.BarcodeFormat.DATA_MATRIX
            BarcodeFormat.AZTEC -> com.google.zxing.BarcodeFormat.AZTEC
            BarcodeFormat.PDF417 -> com.google.zxing.BarcodeFormat.PDF_417
            else -> throw IllegalArgumentException("Unsupported format: $format")
        }

        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, zxingFormat, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        output.eraseColor(styleConfig.backgroundColor)

        val cellSize = size.toFloat() / bitMatrix.width
        val hasGradient = styleConfig.gradientStartColor != null && styleConfig.gradientEndColor != null

        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                if (bitMatrix.get(x, y)) {
                    val startX = (x * cellSize).toInt()
                    val endX = ((x + 1) * cellSize).toInt().coerceAtMost(size)
                    val startY = (y * cellSize).toInt()
                    val endY = ((y + 1) * cellSize).toInt().coerceAtMost(size)
                    fillModule(output, startX, endX, startY, endY, styleConfig, hasGradient)
                }
            }
        }

        styleConfig.logoBitmap?.let { logo ->
            addLogoToCenter(Canvas(output), output, logo, styleConfig.logoScale, styleConfig.backgroundColor)
        }

        return output
    }

    private fun fillModule(
        output: Bitmap,
        startX: Int,
        endX: Int,
        startY: Int,
        endY: Int,
        styleConfig: StyleConfig,
        hasGradient: Boolean
    ) {
        if (hasGradient) {
            for (x in startX until endX) {
                for (y in startY until endY) {
                    val color = resolveForegroundColor(x + 0.5f, y + 0.5f, output.width, output.height, styleConfig)
                    output.setPixel(x, y, color)
                }
            }
        } else {
            val color = styleConfig.foregroundColor
            for (x in startX until endX) {
                for (y in startY until endY) {
                    output.setPixel(x, y, color)
                }
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
            height = size / 2,
            foregroundColor = Color.BLACK,
            backgroundColor = Color.WHITE
        )

        val original = BarcodeGenerator.generate(content, config)
            ?: throw IllegalStateException("Failed to generate barcode")

        val styled = applyStyle(original, styleConfig)

        styleConfig.logoBitmap?.let { logo ->
            addLogoToCenter(Canvas(styled), styled, logo, styleConfig.logoScale, styleConfig.backgroundColor)
        }

        return applyOuterCornerRadius(styled, styleConfig)
    }

    /**
     * 把整张条码图片的外圆角按 styleConfig.cornerRadius 裁剪。
     * 用 BitmapShader + drawRoundRect 实现，避免 clipPath 在硬件加速下的不稳定。
     */
    private fun applyOuterCornerRadius(bitmap: Bitmap, styleConfig: StyleConfig): Bitmap {
        val ratio = styleConfig.cornerRadius.coerceIn(0f, 1f)
        if (ratio <= 0f) return bitmap
        val maxRadius = minOf(bitmap.width, bitmap.height) / 2f
        val radiusPx = ratio * maxRadius
        return addRoundedCorners(bitmap, radiusPx)
    }

    private fun applyStyle(bitmap: Bitmap, styleConfig: StyleConfig): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(styleConfig.backgroundColor)

        val mask = bitmapToMask(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (styleConfig.gradientStartColor != null && styleConfig.gradientEndColor != null) {
            val shader = createGradientShader(width, height, styleConfig)
            paint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawBitmap(mask, 0f, 0f, paint)
        } else {
            paint.color = styleConfig.foregroundColor
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(mask, 0f, 0f, paint)
        }

        return output
    }

    private fun bitmapToMask(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            mask.setPixel(i % width, i / width, if (gray < 128) Color.BLACK else Color.TRANSPARENT)
        }
        return mask
    }

    private fun createGradientShader(width: Int, height: Int, styleConfig: StyleConfig): Shader {
        val start = styleConfig.gradientStartColor ?: styleConfig.foregroundColor
        val end = styleConfig.gradientEndColor ?: styleConfig.foregroundColor
        return when (styleConfig.gradientDirection) {
            GradientDirection.HORIZONTAL -> LinearGradient(
                0f, 0f, width.toFloat(), 0f, start, end, Shader.TileMode.CLAMP
            )
            GradientDirection.VERTICAL -> LinearGradient(
                0f, 0f, 0f, height.toFloat(), start, end, Shader.TileMode.CLAMP
            )
            GradientDirection.DIAGONAL -> LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(), start, end, Shader.TileMode.CLAMP
            )
        }
    }

    private fun resolveForegroundColor(cx: Float, cy: Float, width: Int, height: Int, styleConfig: StyleConfig): Int {
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

    private fun addLogoToCenter(
        canvas: Canvas,
        barcodeBitmap: Bitmap,
        logo: Bitmap,
        scale: Float,
        backgroundColor: Int
    ) {
        // Logo 最大占条码宽度的 30%，确保 QR 码仍有足够容错能力。
        val maxLogoSize = (barcodeBitmap.width * 0.3f).toInt().coerceAtLeast(50)
        val requested = (barcodeBitmap.width * scale.coerceIn(0f, 1f)).toInt().coerceAtLeast(50)
        val logoSize = requested.coerceAtMost(maxLogoSize)
        val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
        val left = (barcodeBitmap.width - logoSize) / 2f
        val top = (barcodeBitmap.height - logoSize) / 2f
        val padding = 10f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            left - padding,
            top - padding,
            left + logoSize + padding,
            top + logoSize + padding,
            10f,
            10f,
            bgPaint
        )
        canvas.drawBitmap(scaledLogo, left, top, null)

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

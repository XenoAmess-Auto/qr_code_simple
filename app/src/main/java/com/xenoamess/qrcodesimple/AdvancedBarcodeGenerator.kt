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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 高级条码生成器 - 支持样式定制
 * 所有像素操作均使用 Bitmap.setPixel / setPixels，避免 Robolectric 下 Canvas 绘制不可靠的问题。
 */
object AdvancedBarcodeGenerator {

    enum class ModuleShape { SQUARE, CIRCLE, ROUNDED }
    enum class PositionPatternShape { SQUARE, CIRCLE, FOLLOW_MODULE }
    enum class GradientType { LINEAR }

    data class ColorStop(val position: Float, val color: Int)

    data class GradientBounds(
        val dx: Float,
        val dy: Float,
        val min: Float,
        val max: Float
    )

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
        val ecLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.H,
        val moduleShape: ModuleShape = ModuleShape.SQUARE,
        val moduleFillRatio: Float = 0.8f,
        val positionPatternShape: PositionPatternShape = PositionPatternShape.SQUARE,
        val gradientAngle: Float = 0f,
        val gradientStops: List<ColorStop> = emptyList(),
        val gradientType: GradientType = GradientType.LINEAR
    )

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
            EncodeHintType.ERROR_CORRECTION to styleConfig.ecLevel,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )

        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        fillBackground(output, styleConfig)

        val logoRect = computeLogoRect(output, styleConfig)
        val cellSize = size.toFloat() / bitMatrix.width
        val gradientBounds = computeGradientBounds(output.width, output.height, styleConfig)

        val patternPositions = listOf(
            Pair(0, 0),
            Pair(bitMatrix.width - 7, 0),
            Pair(0, bitMatrix.height - 7)
        )
        for ((px, py) in patternPositions) {
            drawPositionPattern(output, bitMatrix, px, py, cellSize, styleConfig, logoRect, gradientBounds)
        }

        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                if (isPositionPatternCell(x, y, bitMatrix.width, bitMatrix.height)) continue
                if (bitMatrix.get(x, y)) {
                    val startX = (x * cellSize).toInt()
                    val endX = ((x + 1) * cellSize).toInt().coerceAtMost(size)
                    val startY = (y * cellSize).toInt()
                    val endY = ((y + 1) * cellSize).toInt().coerceAtMost(size)
                    fillModule(output, startX, endX, startY, endY, cellSize, x, y, styleConfig, logoRect, gradientBounds)
                }
            }
        }

        styleConfig.logoBitmap?.let { logo ->
            drawLogoOnBitmap(output, logo, logoRect)
        }

        return applyOuterCornerRadius(output, styleConfig)
    }

    private fun isPositionPatternCell(x: Int, y: Int, width: Int, height: Int): Boolean {
        return (x in 0..6 && y in 0..6) ||
                (x in (width - 7) until width && y in 0..6) ||
                (x in 0..6 && y in (height - 7) until height)
    }

    private fun drawPositionPattern(
        output: Bitmap,
        bitMatrix: com.google.zxing.common.BitMatrix,
        px: Int,
        py: Int,
        cellSize: Float,
        styleConfig: StyleConfig,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        when (styleConfig.positionPatternShape) {
            PositionPatternShape.SQUARE -> {
                for (x in 0 until 7) {
                    for (y in 0 until 7) {
                        if (bitMatrix.get(px + x, py + y)) {
                            fillCell(output, px + x, py + y, cellSize, styleConfig, logoRect, gradientBounds)
                        }
                    }
                }
            }
            PositionPatternShape.CIRCLE -> {
                val startX = (px * cellSize).toInt()
                val endX = ((px + 7) * cellSize).toInt().coerceAtMost(output.width)
                val startY = (py * cellSize).toInt()
                val endY = ((py + 7) * cellSize).toInt().coerceAtMost(output.height)
                val centerX = (px + 3.5f) * cellSize
                val centerY = (py + 3.5f) * cellSize

                for (x in startX until endX) {
                    for (y in startY until endY) {
                        if (logoRect?.contains(x, y) == true) continue
                        val dist = hypot(x + 0.5f - centerX, y + 0.5f - centerY) / cellSize
                        when {
                            dist <= 1.5f -> output.setPixel(x, y, resolveColor(x + 0.5f, y + 0.5f, output.width, output.height, styleConfig, gradientBounds))
                            dist <= 2.5f -> output.setPixel(x, y, resolveBackgroundColor(x, y, output.width, output.height, styleConfig))
                            dist <= 3.5f -> output.setPixel(x, y, resolveColor(x + 0.5f, y + 0.5f, output.width, output.height, styleConfig, gradientBounds))
                        }
                    }
                }
            }
            PositionPatternShape.FOLLOW_MODULE -> {
                for (x in 0 until 7) {
                    for (y in 0 until 7) {
                        if (bitMatrix.get(px + x, py + y)) {
                            val startX = ((px + x) * cellSize).toInt()
                            val endX = ((px + x + 1) * cellSize).toInt().coerceAtMost(output.width)
                            val startY = ((py + y) * cellSize).toInt()
                            val endY = ((py + y + 1) * cellSize).toInt().coerceAtMost(output.height)
                            fillModule(output, startX, endX, startY, endY, cellSize, px + x, py + y, styleConfig, logoRect, gradientBounds)
                        }
                    }
                }
            }
        }
    }

    private fun fillCell(
        output: Bitmap,
        cellX: Int,
        cellY: Int,
        cellSize: Float,
        styleConfig: StyleConfig,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        val startX = (cellX * cellSize).toInt()
        val endX = ((cellX + 1) * cellSize).toInt().coerceAtMost(output.width)
        val startY = (cellY * cellSize).toInt()
        val endY = ((cellY + 1) * cellSize).toInt().coerceAtMost(output.height)
        for (x in startX until endX) {
            for (y in startY until endY) {
                if (logoRect?.contains(x, y) == true) continue
                output.setPixel(x, y, resolveColor(x + 0.5f, y + 0.5f, output.width, output.height, styleConfig, gradientBounds))
            }
        }
    }

    private fun fillModule(
        output: Bitmap,
        startX: Int,
        endX: Int,
        startY: Int,
        endY: Int,
        cellSize: Float,
        cellX: Int,
        cellY: Int,
        styleConfig: StyleConfig,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        val cx = (cellX + 0.5f) * cellSize
        val cy = (cellY + 0.5f) * cellSize
        val radius = cellSize * styleConfig.moduleFillRatio.coerceIn(0.01f, 1f) / 2f
        val cornerRadius = if (styleConfig.moduleShape == ModuleShape.ROUNDED) radius else 0f

        for (x in startX until endX) {
            for (y in startY until endY) {
                if (logoRect?.contains(x, y) == true) continue
                val inShape = when (styleConfig.moduleShape) {
                    ModuleShape.SQUARE -> true
                    ModuleShape.CIRCLE -> hypot(x + 0.5f - cx, y + 0.5f - cy) <= radius
                    ModuleShape.ROUNDED -> isInsideRoundedRect(
                        x + 0.5f, y + 0.5f,
                        startX.toFloat(), startY.toFloat(),
                        endX.toFloat(), endY.toFloat(), cornerRadius
                    )
                }
                if (inShape) {
                    output.setPixel(x, y, resolveColor(x + 0.5f, y + 0.5f, output.width, output.height, styleConfig, gradientBounds))
                }
            }
        }
    }

    private fun isInsideRoundedRect(
        px: Float, py: Float,
        left: Float, top: Float,
        right: Float, bottom: Float,
        radius: Float
    ): Boolean {
        if (px < left || px > right || py < top || py > bottom) return false
        val innerLeft = left + radius
        val innerRight = right - radius
        val innerTop = top + radius
        val innerBottom = bottom - radius
        if (px in innerLeft..innerRight || py in innerTop..innerBottom) return true
        val cornerX = if (px < innerLeft) innerLeft else innerRight
        val cornerY = if (py < innerTop) innerTop else innerBottom
        return hypot(px - cornerX, py - cornerY) <= radius
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
        val gradientBounds = computeGradientBounds(width, height, styleConfig)

        for (i in pixels.indices) {
            val x = i % width
            val y = i / width
            if (logoRect?.contains(x, y) == true) {
                output.setPixel(x, y, resolveBackgroundColor(x, y, width, height, styleConfig))
                continue
            }
            val gray = (Color.red(pixels[i]) + Color.green(pixels[i]) + Color.blue(pixels[i])) / 3
            val isDark = gray < 128
            val color = if (isDark) {
                resolveColor(x + 0.5f, y + 0.5f, width, height, styleConfig, gradientBounds)
            } else {
                resolveBackgroundColor(x, y, width, height, styleConfig)
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

    private fun resolveColor(
        x: Float, y: Float,
        width: Int, height: Int,
        styleConfig: StyleConfig,
        gradientBounds: GradientBounds
    ): Int {
        return when {
            styleConfig.foregroundBitmap != null -> sampleBitmap(styleConfig.foregroundBitmap, x.toInt(), y.toInt(), width, height)
            styleConfig.gradientStops.size >= 2 -> resolveGradientColor(x, y, styleConfig, gradientBounds)
            else -> styleConfig.foregroundColor
        }
    }

    private fun computeGradientBounds(width: Int, height: Int, styleConfig: StyleConfig): GradientBounds {
        val angleRad = Math.toRadians(styleConfig.gradientAngle.toDouble())
        val dx = cos(angleRad).toFloat()
        val dy = sin(angleRad).toFloat()
        val projections = listOf(
            0f * dx + 0f * dy,
            width * dx + 0f * dy,
            0f * dx + height * dy,
            width * dx + height * dy
        )
        return GradientBounds(dx, dy, projections.minOrNull() ?: 0f, projections.maxOrNull() ?: 1f)
    }

    private fun gradientFraction(x: Float, y: Float, bounds: GradientBounds): Float {
        val projection = x * bounds.dx + y * bounds.dy
        return if (bounds.max == bounds.min) 0f else ((projection - bounds.min) / (bounds.max - bounds.min)).coerceIn(0f, 1f)
    }

    private fun resolveGradientColor(x: Float, y: Float, styleConfig: StyleConfig, gradientBounds: GradientBounds): Int {
        val stops = styleConfig.gradientStops.sortedBy { it.position }
        if (stops.size < 2) return styleConfig.foregroundColor
        val t = gradientFraction(x, y, gradientBounds)
        for (i in 0 until stops.size - 1) {
            if (t <= stops[i + 1].position) {
                val start = stops[i]
                val end = stops[i + 1]
                val segmentT = if (end.position == start.position) 0f else
                    (t - start.position) / (end.position - start.position)
                return interpolateColor(start.color, end.color, segmentT.coerceIn(0f, 1f))
            }
        }
        return stops.last().color
    }

    internal fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
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
        val QQ = StyleConfig(
            backgroundColor = Color.WHITE,
            gradientStops = listOf(
                ColorStop(0f, Color.parseColor("#00E5FF")),
                ColorStop(0.5f, Color.parseColor("#2196F3")),
                ColorStop(1f, Color.parseColor("#9C27B0"))
            ),
            moduleShape = ModuleShape.CIRCLE,
            moduleFillRatio = 0.85f,
            positionPatternShape = PositionPatternShape.CIRCLE,
            gradientAngle = 0f
        )
    }
}

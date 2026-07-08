package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
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
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )

        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        fillBackground(output, styleConfig)

        val logoRect = computeLogoRect(output, styleConfig)
        val moduleLayout = computeModuleLayout(bitMatrix, size)
        val gradientBounds = computeGradientBounds(output.width, output.height, styleConfig)

        // 定位图案
        val patternPositions = listOf(
            Pair(0, 0),
            Pair(moduleLayout.qrDimension - 7, 0),
            Pair(0, moduleLayout.qrDimension - 7)
        )
        for ((px, py) in patternPositions) {
            drawPositionPattern(output, px, py, moduleLayout, styleConfig, logoRect, gradientBounds)
        }

        // 数据模块
        for (mx in 0 until moduleLayout.qrDimension) {
            for (my in 0 until moduleLayout.qrDimension) {
                if (isPositionPatternCell(mx, my, moduleLayout.qrDimension)) continue
                if (bitMatrix.get(
                        moduleLayout.leftPadding + (mx * moduleLayout.moduleSize).toInt(),
                        moduleLayout.topPadding + (my * moduleLayout.moduleSize).toInt()
                    )) {
                    drawModule(output, mx, my, moduleLayout, styleConfig, logoRect, gradientBounds)
                }
            }
        }

        styleConfig.logoBitmap?.let { logo ->
            drawLogoOnBitmap(output, logo, logoRect)
        }

        return applyOuterCornerRadius(output, styleConfig)
    }

    private data class ModuleLayout(
        val moduleSize: Float,
        val leftPadding: Int,
        val topPadding: Int,
        val qrDimension: Int
    )

    private fun computeModuleLayout(bitMatrix: com.google.zxing.common.BitMatrix, outputSize: Int): ModuleLayout {
        // 找到第一个包含黑点的行，该行第一个连续黑区就是定位图案外环，宽度 = 7 * moduleSize
        var firstBlackRow = -1
        for (y in 0 until bitMatrix.height) {
            for (x in 0 until bitMatrix.width) {
                if (bitMatrix.get(x, y)) {
                    firstBlackRow = y
                    break
                }
            }
            if (firstBlackRow != -1) break
        }

        var startX = -1
        var endX = -1
        if (firstBlackRow != -1) {
            for (x in 0 until bitMatrix.width) {
                if (bitMatrix.get(x, firstBlackRow)) {
                    if (startX == -1) startX = x
                } else {
                    if (startX != -1) {
                        endX = x
                        break
                    }
                }
            }
        }

        val moduleSize = if (startX != -1 && endX != -1 && endX > startX) {
            (endX - startX) / 7f
        } else {
            outputSize.toFloat() / bitMatrix.width
        }
        val leftPadding = if (startX != -1) startX else 0
        val topPadding = if (firstBlackRow != -1) firstBlackRow else 0
        val qrDimension = ((bitMatrix.width - 2 * leftPadding) / moduleSize).toInt()
        return ModuleLayout(moduleSize, leftPadding, topPadding, qrDimension.coerceAtLeast(1))
    }

    private fun isPositionPatternCell(mx: Int, my: Int, qrDimension: Int): Boolean {
        return (mx in 0..6 && my in 0..6) ||
                (mx in (qrDimension - 7) until qrDimension && my in 0..6) ||
                (mx in 0..6 && my in (qrDimension - 7) until qrDimension)
    }

    private fun drawPositionPattern(
        output: Bitmap,
        px: Int,
        py: Int,
        layout: ModuleLayout,
        styleConfig: StyleConfig,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        when (styleConfig.positionPatternShape) {
            PositionPatternShape.SQUARE -> {
                for (x in 0 until 7) {
                    for (y in 0 until 7) {
                        if (isPositionPatternDark(x, y)) {
                            drawModule(output, px + x, py + y, layout, styleConfig, logoRect, gradientBounds)
                        }
                    }
                }
            }
            PositionPatternShape.CIRCLE -> {
                val startX = layout.leftPadding + (px * layout.moduleSize).toInt()
                val endX = (layout.leftPadding + ((px + 7) * layout.moduleSize)).toInt().coerceAtMost(output.width)
                val startY = layout.topPadding + (py * layout.moduleSize).toInt()
                val endY = (layout.topPadding + ((py + 7) * layout.moduleSize)).toInt().coerceAtMost(output.height)
                val centerX = layout.leftPadding + (px + 3.5f) * layout.moduleSize
                val centerY = layout.topPadding + (py + 3.5f) * layout.moduleSize

                for (x in startX until endX) {
                    for (y in startY until endY) {
                        if (logoRect?.contains(x, y) == true) continue
                        val dist = hypot(x + 0.5f - centerX, y + 0.5f - centerY) / layout.moduleSize
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
                        if (isPositionPatternDark(x, y)) {
                            drawModule(output, px + x, py + y, layout, styleConfig, logoRect, gradientBounds)
                        }
                    }
                }
            }
        }
    }

    private fun isPositionPatternDark(x: Int, y: Int): Boolean {
        return (x == 0 || x == 6 || y == 0 || y == 6) || (x in 2..4 && y in 2..4)
    }

    private fun drawModule(
        output: Bitmap,
        mx: Int,
        my: Int,
        layout: ModuleLayout,
        styleConfig: StyleConfig,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        val startX = layout.leftPadding + (mx * layout.moduleSize).toInt()
        val endX = (layout.leftPadding + ((mx + 1) * layout.moduleSize)).toInt().coerceAtMost(output.width)
        val startY = layout.topPadding + (my * layout.moduleSize).toInt()
        val endY = (layout.topPadding + ((my + 1) * layout.moduleSize)).toInt().coerceAtMost(output.height)
        val cx = layout.leftPadding + (mx + 0.5f) * layout.moduleSize
        val cy = layout.topPadding + (my + 0.5f) * layout.moduleSize
        val radius = layout.moduleSize * styleConfig.moduleFillRatio.coerceIn(0.01f, 1f) / 2f
        val cornerRadius = if (styleConfig.moduleShape == ModuleShape.ROUNDED) radius else 0f

        for (x in startX until endX) {
            for (y in startY until endY) {
                if (logoRect?.contains(x, y) == true) continue
                val px = x + 0.5f
                val py = y + 0.5f
                when (styleConfig.moduleShape) {
                    ModuleShape.SQUARE -> {
                        output.setPixel(x, y, resolveColor(px, py, output.width, output.height, styleConfig, gradientBounds))
                    }
                    ModuleShape.CIRCLE -> {
                        val dist = hypot(px - cx, py - cy)
                        val coverage = circleCoverage(dist, radius)
                        if (coverage > 0f) {
                            val fg = resolveColor(px, py, output.width, output.height, styleConfig, gradientBounds)
                            val color = if (coverage >= 1f) {
                                fg
                            } else {
                                val bg = resolveBackgroundColor(x, y, output.width, output.height, styleConfig)
                                blendColors(fg, bg, coverage)
                            }
                            output.setPixel(x, y, color)
                        }
                    }
                    ModuleShape.ROUNDED -> {
                        if (isInsideRoundedRect(px, py, startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), cornerRadius)) {
                            output.setPixel(x, y, resolveColor(px, py, output.width, output.height, styleConfig, gradientBounds))
                        }
                    }
                }
            }
        }
    }

    private fun circleCoverage(dist: Float, radius: Float): Float {
        val inner = radius - 0.5f
        val outer = radius + 0.5f
        return when {
            dist <= inner -> 1f
            dist >= outer -> 0f
            else -> (outer - dist) / (outer - inner)
        }
    }

    private fun blendColors(fg: Int, bg: Int, fgRatio: Float): Int {
        val ratio = fgRatio.coerceIn(0f, 1f)
        val inv = 1 - ratio
        val a = (Color.alpha(fg) * ratio + Color.alpha(bg) * inv).toInt()
        val r = (Color.red(fg) * ratio + Color.red(bg) * inv).toInt()
        val g = (Color.green(fg) * ratio + Color.green(bg) * inv).toInt()
        val b = (Color.blue(fg) * ratio + Color.blue(bg) * inv).toInt()
        return Color.argb(a, r, g, b)
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
        val maxRadius = min(bitmap.width, bitmap.height) / 2f
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

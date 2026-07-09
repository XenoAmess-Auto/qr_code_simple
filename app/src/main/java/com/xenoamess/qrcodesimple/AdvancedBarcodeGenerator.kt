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
        val moduleFillRatio: Float = 1.0f,
        val positionPatternShape: PositionPatternShape = PositionPatternShape.SQUARE,
        val gradientAngle: Float = 0f,
        val gradientStops: List<ColorStop> = emptyList(),
        val gradientType: GradientType = GradientType.LINEAR
    )

    /**
     * 描述某种条码格式支持哪些样式配置项。
     */
    data class FormatStyleCapabilities(
        val foregroundColor: Boolean = true,
        val backgroundColor: Boolean = true,
        val foregroundBitmap: Boolean = true,
        val backgroundBitmap: Boolean = true,
        val gradient: Boolean = true,
        val logo: Boolean = true,
        val cornerRadius: Boolean = true,
        val ecLevel: Boolean = false,
        val moduleShape: Boolean = false,
        val moduleFillRatio: Boolean = false,
        val positionPatternShape: Boolean = false
    ) {
        companion object {
            val DEFAULT = FormatStyleCapabilities()

            val QR_CODE = FormatStyleCapabilities(
                ecLevel = true,
                moduleShape = true,
                moduleFillRatio = true,
                positionPatternShape = true
            )

            val EC_SUPPORTED = FormatStyleCapabilities(ecLevel = true)

            fun forFormat(format: BarcodeFormat): FormatStyleCapabilities = when (format) {
                BarcodeFormat.QR_CODE -> QR_CODE
                BarcodeFormat.GRID_MATRIX -> EC_SUPPORTED
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.AZTEC,
                BarcodeFormat.PDF417,
                BarcodeFormat.MAXICODE,
                BarcodeFormat.MICRO_QR,
                BarcodeFormat.HAN_XIN,
                BarcodeFormat.SWISS_QR_CODE,
                BarcodeFormat.UPN_QR_CODE -> FormatStyleCapabilities(
                    ecLevel = true,
                    moduleShape = true,
                    moduleFillRatio = true,
                    positionPatternShape = true
                )
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODABAR,
                BarcodeFormat.ITF,
                BarcodeFormat.PHARMACODE,
                BarcodeFormat.PLESSEY,
                BarcodeFormat.MSI_PLESSEY,
                BarcodeFormat.TELEPEN,
                BarcodeFormat.RSS_14,
                BarcodeFormat.RSS_EXPANDED -> FormatStyleCapabilities(
                    moduleShape = true,
                    moduleFillRatio = true,
                    positionPatternShape = true
                )
                else -> DEFAULT
            }
        }
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

    /**
     * 根据指定格式清洗 [StyleConfig]，不支持的字段回退为默认值。
     * 原配置保留不变，返回新的清洗后实例。
     */
    fun sanitize(style: StyleConfig, format: BarcodeFormat): StyleConfig {
        return style.sanitized(FormatStyleCapabilities.forFormat(format))
    }

    /**
     * 根据 [FormatStyleCapabilities] 清洗 [StyleConfig]，不支持的字段回退为默认值。
     * 原配置保留不变，返回新的清洗后实例。
     */
    private fun StyleConfig.sanitized(capabilities: FormatStyleCapabilities): StyleConfig {
        return copy(
            foregroundColor = if (capabilities.foregroundColor) foregroundColor else Color.BLACK,
            backgroundColor = if (capabilities.backgroundColor) backgroundColor else Color.WHITE,
            foregroundBitmap = if (capabilities.foregroundBitmap) foregroundBitmap else null,
            backgroundBitmap = if (capabilities.backgroundBitmap) backgroundBitmap else null,
            cornerRadius = if (capabilities.cornerRadius) cornerRadius else 0f,
            logoBitmap = if (capabilities.logo) logoBitmap else null,
            logoScale = if (capabilities.logo) logoScale else 0.2f,
            ecLevel = if (capabilities.ecLevel) ecLevel else ErrorCorrectionLevel.H,
            moduleShape = if (capabilities.moduleShape) moduleShape else ModuleShape.SQUARE,
            moduleFillRatio = if (capabilities.moduleFillRatio) moduleFillRatio else 1.0f,
            positionPatternShape = if (capabilities.positionPatternShape) positionPatternShape else PositionPatternShape.SQUARE,
            gradientAngle = if (capabilities.gradient) gradientAngle else 0f,
            gradientStops = if (capabilities.gradient) gradientStops else emptyList(),
            gradientType = if (capabilities.gradient) gradientType else GradientType.LINEAR
        )
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
            backgroundColor = Color.WHITE,
            ecLevel = styleConfig.ecLevel
        )

        val result = BarcodeGenerator.generateWithLayout(content, config)
            ?: throw IllegalStateException("Failed to generate barcode")

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        fillBackground(output, styleConfig)
        val logoRect = computeLogoRect(output, styleConfig)
        val gradientBounds = computeGradientBounds(output.width, output.height, styleConfig)

        when (val layout = result.layout) {
            is BarcodeLayout.GridLayout -> renderGridLayout(output, layout, styleConfig, logoRect, gradientBounds)
            is BarcodeLayout.LinearLayout -> renderLinearLayout(output, layout, styleConfig, logoRect, gradientBounds)
            is BarcodeLayout.MaxiCodeLayout -> renderMaxiCodeLayout(output, layout, styleConfig, logoRect, gradientBounds)
            is BarcodeLayout.Fallback -> renderFallback(output, result.bitmap, styleConfig, logoRect)
        }

        styleConfig.logoBitmap?.let { logo ->
            drawLogoOnBitmap(output, logo, logoRect)
        }

        return applyOuterCornerRadius(output, styleConfig)
    }

    private fun renderFallback(
        output: Bitmap,
        raw: Bitmap,
        styleConfig: StyleConfig,
        logoRect: Rect?
    ) {
        val scaled = scaleToFit(raw, output.width)
        val styled = applyStyle(scaled, styleConfig, logoRect)
        val left = (output.width - styled.width) / 2
        val top = (output.height - styled.height) / 2
        drawBitmapOnto(output, styled, left, top)
        styled.recycle()
        scaled.recycle()
    }

    private fun scaleToFit(source: Bitmap, size: Int): Bitmap {
        if (source.width == size && source.height == size) return source
        val scale = minOf(size.toFloat() / source.width, size.toFloat() / source.height)
        val newWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, false)
    }

    private fun drawBitmapOnto(target: Bitmap, source: Bitmap, left: Int, top: Int) {
        val copyWidth = minOf(source.width, target.width - left)
        val copyHeight = minOf(source.height, target.height - top)
        if (copyWidth <= 0 || copyHeight <= 0 || left < 0 || top < 0) return
        val pixels = IntArray(copyWidth * copyHeight)
        source.getPixels(pixels, 0, copyWidth, 0, 0, copyWidth, copyHeight)
        target.setPixels(pixels, 0, copyWidth, left, top, copyWidth, copyHeight)
    }

    private fun computeLayoutScale(layout: BarcodeLayout, outputSize: Int): Triple<Float, Float, Float> {
        val (layoutWidth, layoutHeight) = when (layout) {
            is BarcodeLayout.GridLayout -> {
                val w = layout.bitMatrix.width * layout.moduleSize + 2 * layout.padding
                val h = layout.bitMatrix.height * layout.moduleSize + 2 * layout.padding
                w.toFloat() to h.toFloat()
            }
            is BarcodeLayout.LinearLayout -> {
                layout.width.toFloat() to layout.height.toFloat()
            }
            is BarcodeLayout.MaxiCodeLayout -> {
                val w = (layout.hexagons.maxOfOrNull { it.x + it.size } ?: 0f)
                    .coerceAtLeast(layout.targets.maxOfOrNull { it.cx + it.radius } ?: 0f)
                val h = (layout.hexagons.maxOfOrNull { it.y + it.size } ?: 0f)
                    .coerceAtLeast(layout.targets.maxOfOrNull { it.cy + it.radius } ?: 0f)
                w to h
            }
            is BarcodeLayout.Fallback -> layout.bitmap.width.toFloat() to layout.bitmap.height.toFloat()
        }
        if (layoutWidth <= 0 || layoutHeight <= 0) return Triple(1f, 0f, 0f)
        val scale = minOf(outputSize / layoutWidth, outputSize / layoutHeight)
        val offsetX = (outputSize - layoutWidth * scale) / 2f
        val offsetY = (outputSize - layoutHeight * scale) / 2f
        return Triple(scale, offsetX, offsetY)
    }

    private fun renderGridLayout(
        output: Bitmap,
        layout: BarcodeLayout.GridLayout,
        styleConfig: StyleConfig,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        val layoutWidth = layout.bitMatrix.width * layout.moduleSize + 2 * layout.padding
        val layoutHeight = layout.bitMatrix.height * layout.moduleSize + 2 * layout.padding
        if (layoutWidth <= 0 || layoutHeight <= 0) return
        val scale = minOf(output.width / layoutWidth, output.height / layoutHeight).coerceAtLeast(1)
        val offsetX = (output.width - layoutWidth * scale) / 2f
        val offsetY = (output.height - layoutHeight * scale) / 2f
        val moduleSize = layout.moduleSize * scale
        val paddingOffset = layout.padding * scale
        for (my in 0 until layout.bitMatrix.height) {
            for (mx in 0 until layout.bitMatrix.width) {
                if (!layout.bitMatrix.get(mx, my)) continue
                val left = (offsetX + paddingOffset + mx * moduleSize).toInt()
                val top = (offsetY + paddingOffset + my * moduleSize).toInt()
                val right = left + moduleSize
                val bottom = top + moduleSize
                if (right <= left || bottom <= top) continue
                val isPosition = layout.positionPatterns.any { rect ->
                    mx in rect.left until rect.right && my in rect.top until rect.bottom
                }
                drawGridModule(output, left, top, right, bottom, styleConfig, isPosition, logoRect, gradientBounds)
            }
        }
    }

    private fun drawGridModule(
        output: Bitmap,
        left: Int, top: Int, right: Int, bottom: Int,
        styleConfig: StyleConfig,
        isPosition: Boolean,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        val shape = if (isPosition) {
            when (styleConfig.positionPatternShape) {
                PositionPatternShape.SQUARE -> ModuleShape.SQUARE
                PositionPatternShape.CIRCLE -> ModuleShape.CIRCLE
                PositionPatternShape.FOLLOW_MODULE -> styleConfig.moduleShape
            }
        } else {
            styleConfig.moduleShape
        }
        val fillRatio = styleConfig.moduleFillRatio.coerceIn(0.01f, 1f)
        val moduleWidth = right - left
        val moduleHeight = bottom - top
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val radius = minOf(moduleWidth, moduleHeight) * fillRatio / 2f
        val cornerRadius = if (shape == ModuleShape.ROUNDED) radius else 0f

        for (x in left until right) {
            for (y in top until bottom) {
                if (logoRect?.contains(x, y) == true) continue
                val px = x + 0.5f
                val py = y + 0.5f
                val inside = when (shape) {
                    ModuleShape.SQUARE -> true
                    ModuleShape.CIRCLE -> hypot(px - cx, py - cy) <= radius
                    ModuleShape.ROUNDED -> isInsideRoundedRect(
                        px, py, left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), cornerRadius
                    )
                }
                if (inside) {
                    output.setPixel(x, y, resolveColor(px, py, output.width, output.height, styleConfig, gradientBounds))
                }
            }
        }
    }

    private fun renderLinearLayout(
        output: Bitmap,
        layout: BarcodeLayout.LinearLayout,
        styleConfig: StyleConfig,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        val (scale, offsetX, offsetY) = computeLayoutScale(layout, output.width)
        for (run in layout.barRuns) {
            val left = (offsetX + run.left * scale).toInt()
            val top = (offsetY + run.top * scale).toInt()
            val right = (offsetX + run.right * scale).toInt()
            val bottom = (offsetY + run.bottom * scale).toInt()
            if (right <= left || bottom <= top) continue
            val isGuard = run.kind == BarcodeLayout.LinearLayout.BarRun.Kind.GUARD
            drawLinearBar(output, left, top, right, bottom, styleConfig, isGuard, logoRect, gradientBounds)
        }
    }

    private fun drawLinearBar(
        output: Bitmap,
        left: Int, top: Int, right: Int, bottom: Int,
        styleConfig: StyleConfig,
        isGuard: Boolean,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        val shape = if (isGuard) {
            when (styleConfig.positionPatternShape) {
                PositionPatternShape.SQUARE -> ModuleShape.SQUARE
                PositionPatternShape.CIRCLE -> ModuleShape.CIRCLE
                PositionPatternShape.FOLLOW_MODULE -> styleConfig.moduleShape
            }
        } else {
            styleConfig.moduleShape
        }
        val fillRatio = styleConfig.moduleFillRatio.coerceIn(0.01f, 1f)
        val barWidth = right - left
        val barHeight = bottom - top
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val effectiveWidth = (barWidth * fillRatio).coerceAtLeast(1f)
        val effectiveLeft = (cx - effectiveWidth / 2f).toInt().coerceAtLeast(left)
        val effectiveRight = (cx + effectiveWidth / 2f).toInt().coerceAtMost(right)
        if (effectiveRight <= effectiveLeft) return
        val radius = minOf(effectiveWidth, barHeight.toFloat()) / 2f
        val cornerRadius = if (shape == ModuleShape.ROUNDED) radius else 0f

        for (x in effectiveLeft until effectiveRight) {
            for (y in top until bottom) {
                if (logoRect?.contains(x, y) == true) continue
                val px = x + 0.5f
                val py = y + 0.5f
                val inside = when (shape) {
                    ModuleShape.SQUARE -> true
                    ModuleShape.CIRCLE -> {
                        val dx = px - cx
                        val dy = (py - cy).coerceIn(-barHeight / 2f, barHeight / 2f)
                        hypot(dx, dy) <= radius
                    }
                    ModuleShape.ROUNDED -> isInsideRoundedRect(
                        px, py, effectiveLeft.toFloat(), top.toFloat(), effectiveRight.toFloat(), bottom.toFloat(), cornerRadius
                    )
                }
                if (inside) {
                    output.setPixel(x, y, resolveColor(px, py, output.width, output.height, styleConfig, gradientBounds))
                }
            }
        }
    }

    private fun renderMaxiCodeLayout(
        output: Bitmap,
        layout: BarcodeLayout.MaxiCodeLayout,
        styleConfig: StyleConfig,
        logoRect: Rect?,
        gradientBounds: GradientBounds
    ) {
        val (scale, offsetX, offsetY) = computeLayoutScale(layout, output.width)

        for (target in layout.targets) {
            val cx = offsetX + target.cx * scale
            val cy = offsetY + target.cy * scale
            val radius = target.radius * scale
            val minX = (cx - radius).toInt().coerceAtLeast(0)
            val maxX = (cx + radius).toInt().coerceAtMost(output.width - 1)
            val minY = (cy - radius).toInt().coerceAtLeast(0)
            val maxY = (cy + radius).toInt().coerceAtMost(output.height - 1)
            val r2 = radius * radius
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    if (logoRect?.contains(x, y) == true) continue
                    val dx = x + 0.5f - cx
                    val dy = y + 0.5f - cy
                    if (dx * dx + dy * dy <= r2) {
                        output.setPixel(x, y, resolveColor(x + 0.5f, y + 0.5f, output.width, output.height, styleConfig, gradientBounds))
                    }
                }
            }
        }

        for (hex in layout.hexagons) {
            val vertices = List(6) { i ->
                val angle = Math.PI / 3 * i - Math.PI / 2
                val hx = offsetX + (hex.x + hex.size * kotlin.math.cos(angle).toFloat()) * scale
                val hy = offsetY + (hex.y + hex.size * kotlin.math.sin(angle).toFloat()) * scale
                hx to hy
            }
            val minX = vertices.minOf { it.first }.toInt().coerceAtLeast(0)
            val maxX = vertices.maxOf { it.first }.toInt().coerceAtMost(output.width - 1)
            val minY = vertices.minOf { it.second }.toInt().coerceAtLeast(0)
            val maxY = vertices.maxOf { it.second }.toInt().coerceAtMost(output.height - 1)
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    if (logoRect?.contains(x, y) == true) continue
                    if (pointInPolygon(x + 0.5f, y + 0.5f, vertices)) {
                        output.setPixel(x, y, resolveColor(x + 0.5f, y + 0.5f, output.width, output.height, styleConfig, gradientBounds))
                    }
                }
            }
        }
    }

    private fun pointInPolygon(x: Float, y: Float, vertices: List<Pair<Float, Float>>): Boolean {
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val xi = vertices[i].first
            val yi = vertices[i].second
            val xj = vertices[j].first
            val yj = vertices[j].second
            val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
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
            gradientAngle = 45f
        )
    }
}

package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.graphics.withSave
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat

/**
 * 高级条码生成器 - 支持样式定制
 */
object AdvancedBarcodeGenerator {

    data class StyleConfig(
        val foregroundColor: Int = Color.BLACK,
        val backgroundColor: Int = Color.WHITE,
        val cornerRadius: Float = 0f,           // 圆角半径 (0 = 方形)
        val dotScale: Float = 1f,               // 点阵比例 (1 = 填满)
        val logoBitmap: Bitmap? = null,         // 中心 Logo
        val logoScale: Float = 0.2f,            // Logo 占二维码比例
        val gradientStartColor: Int? = null,    // 渐变起始色
        val gradientEndColor: Int? = null,      // 渐变结束色
        val gradientDirection: GradientDirection = GradientDirection.HORIZONTAL
    )

    enum class GradientDirection {
        HORIZONTAL, VERTICAL, DIAGONAL
    }

    /**
     * 生成带样式的条码
     */
    fun generateStyled(
        content: String,
        format: BarcodeFormat = BarcodeFormat.QR_CODE,
        size: Int = 800,
        style: StyleConfig = StyleConfig()
    ): Bitmap? {
        return try {
            when (format) {
                BarcodeFormat.QR_CODE -> generateStyledQR(content, size, style)
                else -> generateLinearWithStyle(content, format, size, style)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成带样式的二维码
     */
    private fun generateStyledQR(content: String, size: Int, style: StyleConfig): Bitmap {
        // 生成基础二维码（高容错级别以支持 Logo）
        val hints = hashMapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )

        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)

        // 创建输出位图
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 背景
        canvas.drawColor(style.backgroundColor)

        val cellSize = size.toFloat() / bitMatrix.width
        val radius = cellSize * style.dotScale / 2
        val cornerRadius = style.cornerRadius.coerceIn(0f, radius)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.foregroundColor
            this.style = Paint.Style.FILL
        }

        // 绘制 QR 码单元格
        for (x in 0 until bitMatrix.width) {
            for (y in 0 until bitMatrix.height) {
                if (bitMatrix.get(x, y)) {
                    val cx = x * cellSize + cellSize / 2
                    val cy = y * cellSize + cellSize / 2

                    if (cornerRadius > 0) {
                        // 圆角矩形
                        val rect = RectF(
                            cx - radius,
                            cy - radius,
                            cx + radius,
                            cy + radius
                        )
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                    } else {
                        // 圆形点阵
                        canvas.drawCircle(cx, cy, radius, paint)
                    }
                }
            }
        }

        // 添加 Logo
        style.logoBitmap?.let { logo ->
            addLogoToQR(canvas, output, logo, style.logoScale)
        }

        return output
    }

    /**
     * 添加 Logo 到二维码中心
     */
    private fun addLogoToQR(canvas: Canvas, qrBitmap: Bitmap, logo: Bitmap, scale: Float) {
        val logoSize = (qrBitmap.width * scale).toInt().coerceIn(50, qrBitmap.width / 3)
        
        // 缩放 Logo
        val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)

        val left = (qrBitmap.width - logoSize) / 2f
        val top = (qrBitmap.height - logoSize) / 2f

        // 绘制白色背景（确保 Logo 区域可读）
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val padding = 10f
        canvas.drawRoundRect(
            left - padding,
            top - padding,
            left + logoSize + padding,
            top + logoSize + padding,
            10f,
            10f,
            bgPaint
        )

        // 绘制 Logo
        canvas.drawBitmap(scaledLogo, left, top, null)

        // 回收临时位图
        if (scaledLogo !== logo) {
            scaledLogo.recycle()
        }
    }

    /**
     * 生成带样式的一维条码
     */
    private fun generateLinearWithStyle(
        content: String,
        format: BarcodeFormat,
        size: Int,
        style: StyleConfig
    ): Bitmap {
        val config = BarcodeGenerator.BarcodeConfig(
            format = format,
            width = size,
            height = size / 2,
            foregroundColor = style.foregroundColor,
            backgroundColor = style.backgroundColor
        )

        return BarcodeGenerator.generate(content, config)
            ?: throw IllegalStateException("Failed to generate barcode")
    }

    /**
     * 为条码添加圆角
     */
    fun addRoundedCorners(bitmap: Bitmap, radius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        canvas.withSave {
            val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            val path = android.graphics.Path().apply {
                addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CCW)
            }
            clipPath(path)
            drawBitmap(bitmap, 0f, 0f, paint)
        }

        return output
    }

    /**
     * 预定义颜色方案
     */
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

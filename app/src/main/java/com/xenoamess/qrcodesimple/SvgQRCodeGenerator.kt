package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat

/**
 * SVG 矢量二维码生成器
 */
object SvgQRCodeGenerator {

    data class SvgConfig(
        val size: Int = 512,
        val foregroundColor: String = "#000000",
        val backgroundColor: String = "#FFFFFF",
        val moduleSize: Float = 10f
    )

    /**
     * 生成 QR Code SVG 字符串
     */
    fun generateSVG(content: String, config: SvgConfig = SvgConfig()): String {
        val hints = hashMapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, config.size, config.size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        
        // 计算实际的 SVG 大小
        val actualSize = width * config.moduleSize
        
        val svgBuilder = StringBuilder()
        
        // SVG 头部
        svgBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
<svg width="${actualSize}" height="${actualSize}" viewBox="0 0 $width $height" xmlns="http://www.w3.org/2000/svg">
  <rect width="$width" height="$height" fill="${config.backgroundColor}"/>
""")

        // 生成黑色模块
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitMatrix.get(x, y)) {
                    svgBuilder.append("  <rect x=\"$x\" y=\"$y\" width=\"1\" height=\"1\" fill=\"${config.foregroundColor}\"/>\n")
                }
            }
        }

        // SVG 尾部
        svgBuilder.append("</svg>")

        return svgBuilder.toString()
    }

    /**
     * 生成带样式的 QR Code SVG
     */
    fun generateStyledSVG(
        content: String,
        config: SvgConfig = SvgConfig(),
        cornerRadius: Float = 0f
    ): String {
        val hints = hashMapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, config.size, config.size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        
        val actualSize = width * config.moduleSize
        
        val svgBuilder = StringBuilder()
        
        // SVG 头部
        svgBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
<svg width="${actualSize}" height="${actualSize}" viewBox="0 0 $width $height" xmlns="http://www.w3.org/2000/svg">
  <rect width="$width" height="$height" fill="${config.backgroundColor}" rx="${cornerRadius / config.moduleSize}"/>
""")

        // 生成模块（使用圆角矩形）
        val radius = if (cornerRadius > 0) " rx=\"${0.2}\" ry=\"${0.2}\"" else ""
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitMatrix.get(x, y)) {
                    svgBuilder.append("  <rect x=\"$x\" y=\"$y\" width=\"0.9\" height=\"0.9\"$radius fill=\"${config.foregroundColor}\"/>\n")
                }
            }
        }

        svgBuilder.append("</svg>")

        return svgBuilder.toString()
    }

    /**
     * 生成包含 Logo 的 SVG
     */
    fun generateSVGWithLogo(
        content: String,
        config: SvgConfig = SvgConfig(),
        logoSvg: String? = null
    ): String {
        val baseSvg = generateSVG(content, config)
        
        if (logoSvg == null) return baseSvg
        
        // 在中心位置添加 Logo
        val size = config.size
        val logoSize = size / 5
        val logoX = (size - logoSize) / 2
        val logoY = (size - logoSize) / 2
        
        // 在 SVG 中添加 Logo 层
        return baseSvg.replace(
            "</svg>",
            """  <g transform="translate($logoX, $logoY)">
    <rect width="$logoSize" height="$logoSize" fill="${config.backgroundColor}"/>
    $logoSvg
  </g>
</svg>"""
        )
    }

    /**
     * 获取文件扩展名
     */
    fun getFileExtension(format: BarcodeFormat): String {
        return when (format) {
            BarcodeFormat.QR_CODE -> "qr.svg"
            else -> "barcode.svg"
        }
    }

    /**
     * 生成文件名
     */
    fun generateFileName(content: String, format: BarcodeFormat): String {
        val timestamp = System.currentTimeMillis()
        val prefix = when (format) {
            BarcodeFormat.QR_CODE -> "qrcode"
            BarcodeFormat.DATA_MATRIX -> "datamatrix"
            BarcodeFormat.AZTEC -> "aztec"
            BarcodeFormat.PDF417 -> "pdf417"
            else -> "barcode"
        }
        return "${prefix}_${timestamp}.svg"
    }
}

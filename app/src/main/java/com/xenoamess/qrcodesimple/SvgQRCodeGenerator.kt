package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat

/**
 * SVG 矢量条码生成器
 */
object SvgQRCodeGenerator {

    data class SvgConfig(
        val width: Int = 512,
        val height: Int = 512,
        val size: Int = 512,
        val foregroundColor: String = "#000000",
        val backgroundColor: String = "#FFFFFF",
        val moduleSize: Float = 10f
    )

    /**
     * 生成条码 SVG 字符串（支持全部格式）
     */
    fun generateSVG(content: String, format: BarcodeFormat, config: SvgConfig = SvgConfig()): String {
        return when (format) {
            BarcodeFormat.QR_CODE -> generateSVG(content, config)
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODABAR,
            BarcodeFormat.ITF -> generateZXingSvg(content, format, config)
            else -> generateBitmapSvg(content, format, config)
        }
    }

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

        val actualSize = width * config.moduleSize

        val svgBuilder = StringBuilder()

        svgBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
<svg width="${actualSize}" height="${actualSize}" viewBox="0 0 $width $height" xmlns="http://www.w3.org/2000/svg">
  <rect width="$width" height="$height" fill="${config.backgroundColor}"/>
""")

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitMatrix.get(x, y)) {
                    svgBuilder.append("  <rect x=\"$x\" y=\"$y\" width=\"1\" height=\"1\" fill=\"${config.foregroundColor}\"/>\n")
                }
            }
        }

        svgBuilder.append("</svg>")

        return svgBuilder.toString()
    }

    private fun generateZXingSvg(content: String, format: BarcodeFormat, config: SvgConfig): String {
        val zxingFormat = when (format) {
            BarcodeFormat.DATA_MATRIX -> com.google.zxing.BarcodeFormat.DATA_MATRIX
            BarcodeFormat.AZTEC -> com.google.zxing.BarcodeFormat.AZTEC
            BarcodeFormat.PDF417 -> com.google.zxing.BarcodeFormat.PDF_417
            BarcodeFormat.CODE_128 -> com.google.zxing.BarcodeFormat.CODE_128
            BarcodeFormat.CODE_39 -> com.google.zxing.BarcodeFormat.CODE_39
            BarcodeFormat.CODE_93 -> com.google.zxing.BarcodeFormat.CODE_93
            BarcodeFormat.EAN_13 -> com.google.zxing.BarcodeFormat.EAN_13
            BarcodeFormat.EAN_8 -> com.google.zxing.BarcodeFormat.EAN_8
            BarcodeFormat.UPC_A -> com.google.zxing.BarcodeFormat.UPC_A
            BarcodeFormat.UPC_E -> com.google.zxing.BarcodeFormat.UPC_E
            BarcodeFormat.CODABAR -> com.google.zxing.BarcodeFormat.CODABAR
            BarcodeFormat.ITF -> com.google.zxing.BarcodeFormat.ITF
            else -> com.google.zxing.BarcodeFormat.QR_CODE
        }

        val hints = hashMapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )

        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, zxingFormat, config.width, config.height, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val backgroundColor = config.backgroundColor
        val foregroundColor = config.foregroundColor

        val svgBuilder = StringBuilder()
        svgBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
<svg width="$width" height="$height" viewBox="0 0 $width $height" xmlns="http://www.w3.org/2000/svg">
  <rect width="$width" height="$height" fill="$backgroundColor"/>
""")

        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                if (bitMatrix.get(x, y)) {
                    val start = x
                    while (x < width && bitMatrix.get(x, y)) x++
                    svgBuilder.append("  <rect x=\"$start\" y=\"$y\" width=\"${x - start}\" height=\"1\" fill=\"$foregroundColor\"/>\n")
                } else {
                    x++
                }
            }
        }

        svgBuilder.append("</svg>")
        return svgBuilder.toString()
    }

    private fun generateBitmapSvg(content: String, format: BarcodeFormat, config: SvgConfig): String {
        val barcodeConfig = BarcodeGenerator.BarcodeConfig(
            format = format,
            width = config.width,
            height = config.height,
            foregroundColor = parseColor(config.foregroundColor),
            backgroundColor = parseColor(config.backgroundColor)
        )
        val bitmap = BarcodeGenerator.generate(content, barcodeConfig)
            ?: throw IllegalArgumentException("Failed to generate SVG for $format")
        return bitmapToSvg(bitmap, config)
    }

    private fun bitmapToSvg(bitmap: Bitmap, config: SvgConfig): String {
        val width = bitmap.width
        val height = bitmap.height
        val backgroundColor = parseColor(config.backgroundColor)
        val foregroundColor = config.foregroundColor

        val svgBuilder = StringBuilder()
        svgBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
<svg width="$width" height="$height" viewBox="0 0 $width $height" xmlns="http://www.w3.org/2000/svg">
  <rect width="$width" height="$height" fill="${config.backgroundColor}"/>
""")

        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                if (bitmap.getPixel(x, y) != backgroundColor) {
                    val start = x
                    while (x < width && bitmap.getPixel(x, y) != backgroundColor) x++
                    svgBuilder.append("  <rect x=\"$start\" y=\"$y\" width=\"${x - start}\" height=\"1\" fill=\"$foregroundColor\"/>\n")
                } else {
                    x++
                }
            }
        }

        svgBuilder.append("</svg>")
        return svgBuilder.toString()
    }

    private fun parseColor(color: String): Int {
        return Color.parseColor(color)
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

        svgBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
<svg width="${actualSize}" height="${actualSize}" viewBox="0 0 $width $height" xmlns="http://www.w3.org/2000/svg">
  <rect width="$width" height="$height" fill="${config.backgroundColor}" rx="${cornerRadius / config.moduleSize}"/>
""")

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

        val size = config.size
        val logoSize = size / 5
        val logoX = (size - logoSize) / 2
        val logoY = (size - logoSize) / 2

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

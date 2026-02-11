package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat as AppBarcodeFormat

/**
 * 条码生成器 - 支持多种条码格式
 */
object BarcodeGenerator {

    data class BarcodeConfig(
        val format: AppBarcodeFormat = AppBarcodeFormat.QR_CODE,
        val width: Int = 600,
        val height: Int = 600,
        val foregroundColor: Int = Color.BLACK,
        val backgroundColor: Int = Color.WHITE
    )

    /**
     * 生成条码
     * @param content 条码内容
     * @param config 条码配置
     * @return 生成的条码 Bitmap，如果生成失败返回 null
     */
    fun generate(content: String, config: BarcodeConfig): Bitmap? {
        return try {
            when (config.format) {
                AppBarcodeFormat.QR_CODE -> generateQRCode(content, config)
                AppBarcodeFormat.DATA_MATRIX -> generateDataMatrix(content, config)
                AppBarcodeFormat.AZTEC -> generateAztec(content, config)
                AppBarcodeFormat.PDF417 -> generatePDF417(content, config)
                AppBarcodeFormat.CODE_128,
                AppBarcodeFormat.CODE_39,
                AppBarcodeFormat.CODE_93,
                AppBarcodeFormat.EAN_13,
                AppBarcodeFormat.EAN_8,
                AppBarcodeFormat.UPC_A,
                AppBarcodeFormat.UPC_E,
                AppBarcodeFormat.CODABAR,
                AppBarcodeFormat.ITF -> generateLinearBarcode(content, config)
                AppBarcodeFormat.UNKNOWN -> generateQRCode(content, config)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成二维码
     */
    private fun generateQRCode(content: String, config: BarcodeConfig): Bitmap {
        val hints = hashMapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2
        )

        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(
            content,
            BarcodeFormat.QR_CODE,
            config.width,
            config.height,
            hints
        )

        return createBitmap(bitMatrix, config)
    }

    /**
     * 生成 Data Matrix
     */
    private fun generateDataMatrix(content: String, config: BarcodeConfig): Bitmap {
        // Data Matrix 需要方形尺寸
        val size = minOf(config.width, config.height)
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(
            content,
            BarcodeFormat.DATA_MATRIX,
            size,
            size
        )
        return createBitmap(bitMatrix, config)
    }

    /**
     * 生成 Aztec Code
     */
    private fun generateAztec(content: String, config: BarcodeConfig): Bitmap {
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(
            content,
            BarcodeFormat.AZTEC,
            config.width,
            config.height
        )
        return createBitmap(bitMatrix, config)
    }

    /**
     * 生成 PDF417
     */
    private fun generatePDF417(content: String, config: BarcodeConfig): Bitmap {
        val hints = hashMapOf(
            EncodeHintType.ERROR_CORRECTION to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val writer = MultiFormatWriter()
        // PDF417 需要横向更宽的尺寸
        val bitMatrix = writer.encode(
            content,
            BarcodeFormat.PDF_417,
            config.width,
            config.height / 2,
            hints
        )
        return createBitmap(bitMatrix, config.copy(height = config.height / 2))
    }

    /**
     * 生成一维条码（Code 128, EAN, UPC 等）
     */
    private fun generateLinearBarcode(content: String, config: BarcodeConfig): Bitmap {
        val zxingFormat = getZXingFormat(config.format)
        
        // 一维条码需要调整尺寸比例
        val barcodeWidth = config.width
        val barcodeHeight = config.height / 3  // 一维条码高度较矮
        
        val hints = hashMapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(
            content,
            zxingFormat,
            barcodeWidth,
            barcodeHeight,
            hints
        )
        
        return createLinearBarcodeBitmap(bitMatrix, config, content)
    }

    /**
     * 创建标准位图
     */
    private fun createBitmap(bitMatrix: BitMatrix, config: BarcodeConfig): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y)) config.foregroundColor else config.backgroundColor
                )
            }
        }

        return bitmap
    }

    /**
     * 创建一维条码位图（带文字）
     */
    private fun createLinearBarcodeBitmap(
        bitMatrix: BitMatrix,
        config: BarcodeConfig,
        content: String
    ): Bitmap {
        val barcodeWidth = bitMatrix.width
        val barcodeHeight = bitMatrix.height
        val textHeight = 40
        val padding = 10
        
        val totalHeight = barcodeHeight + textHeight + padding * 2
        val bitmap = Bitmap.createBitmap(barcodeWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 背景
        canvas.drawColor(config.backgroundColor)
        
        // 绘制条码
        for (x in 0 until barcodeWidth) {
            for (y in 0 until barcodeHeight) {
                if (bitMatrix.get(x, y)) {
                    bitmap.setPixel(x, y, config.foregroundColor)
                }
            }
        }
        
        // 绘制文字
        val paint = Paint().apply {
            color = config.foregroundColor
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        canvas.drawText(
            content,
            barcodeWidth / 2f,
            barcodeHeight + textHeight.toFloat(),
            paint
        )
        
        return bitmap
    }

    /**
     * 将应用条码格式转换为 ZXing 格式
     */
    private fun getZXingFormat(format: AppBarcodeFormat): BarcodeFormat {
        return when (format) {
            AppBarcodeFormat.QR_CODE -> BarcodeFormat.QR_CODE
            AppBarcodeFormat.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
            AppBarcodeFormat.AZTEC -> BarcodeFormat.AZTEC
            AppBarcodeFormat.PDF417 -> BarcodeFormat.PDF_417
            AppBarcodeFormat.CODE_128 -> BarcodeFormat.CODE_128
            AppBarcodeFormat.CODE_39 -> BarcodeFormat.CODE_39
            AppBarcodeFormat.CODE_93 -> BarcodeFormat.CODE_93
            AppBarcodeFormat.EAN_13 -> BarcodeFormat.EAN_13
            AppBarcodeFormat.EAN_8 -> BarcodeFormat.EAN_8
            AppBarcodeFormat.UPC_A -> BarcodeFormat.UPC_A
            AppBarcodeFormat.UPC_E -> BarcodeFormat.UPC_E
            AppBarcodeFormat.CODABAR -> BarcodeFormat.CODABAR
            AppBarcodeFormat.ITF -> BarcodeFormat.ITF
            AppBarcodeFormat.UNKNOWN -> BarcodeFormat.QR_CODE
        }
    }

    /**
     * 验证内容是否符合条码格式要求
     */
    fun validateContent(content: String, format: AppBarcodeFormat): ValidationResult {
        return when (format) {
            AppBarcodeFormat.EAN_13 -> validateEAN13(content)
            AppBarcodeFormat.EAN_8 -> validateEAN8(content)
            AppBarcodeFormat.UPC_A -> validateUPCA(content)
            AppBarcodeFormat.UPC_E -> validateUPCE(content)
            AppBarcodeFormat.CODE_39 -> validateCode39(content)
            else -> ValidationResult(true)
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    private fun validateEAN13(content: String): ValidationResult {
        val digitsOnly = content.filter { it.isDigit() }
        return if (digitsOnly.length == 13) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "EAN-13 requires exactly 13 digits")
        }
    }

    private fun validateEAN8(content: String): ValidationResult {
        val digitsOnly = content.filter { it.isDigit() }
        return if (digitsOnly.length == 8) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "EAN-8 requires exactly 8 digits")
        }
    }

    private fun validateUPCA(content: String): ValidationResult {
        val digitsOnly = content.filter { it.isDigit() }
        return if (digitsOnly.length == 12) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "UPC-A requires exactly 12 digits")
        }
    }

    private fun validateUPCE(content: String): ValidationResult {
        val digitsOnly = content.filter { it.isDigit() }
        return if (digitsOnly.length == 6 || digitsOnly.length == 8) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "UPC-E requires 6 or 8 digits")
        }
    }

    private fun validateCode39(content: String): ValidationResult {
        val validChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"
        // Code 39 不区分大小写，但验证应检查内容是否能被编码
        val isValid = content.all { it.uppercaseChar() in validChars }
        return if (isValid) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 39 only supports: 0-9, A-Z, -, ., space, $, /, +, %")
        }
    }
}
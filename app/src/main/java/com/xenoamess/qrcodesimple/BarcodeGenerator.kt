package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import boofcv.alg.fiducial.microqr.MicroQrCodeEncoder
import boofcv.alg.fiducial.microqr.MicroQrCodeGenerator
import boofcv.android.ConvertBitmap
import boofcv.struct.image.GrayU8
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat as AppBarcodeFormat
import uk.org.okapibarcode.backend.DataBar14
import uk.org.okapibarcode.backend.DataBarExpanded
import uk.org.okapibarcode.backend.MaxiCode
import uk.org.okapibarcode.backend.Symbol

/**
 * 条码生成器 - 支持全部 21 种条码格式
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
                AppBarcodeFormat.UPC_EAN_EXTENSION -> generateUpcEanExtension(content, config)
                AppBarcodeFormat.RSS_14 -> generateRss14(content, config)
                AppBarcodeFormat.RSS_EXPANDED -> generateRssExpanded(content, config)
                AppBarcodeFormat.MAXICODE -> generateMaxiCode(content, config)
                AppBarcodeFormat.MICRO_QR -> generateMicroQr(content, config)
                AppBarcodeFormat.PHARMACODE -> generatePharmacode(content, config)
                AppBarcodeFormat.PLESSEY -> generatePlessey(content, config)
                AppBarcodeFormat.MSI_PLESSEY -> generateMsiPlessey(content, config)
                AppBarcodeFormat.TELEPEN -> generateTelepen(content, config)
                AppBarcodeFormat.UNKNOWN -> generateQRCode(content, config)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ==================== ZXing 生成的格式 ====================

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

    private fun generateDataMatrix(content: String, config: BarcodeConfig): Bitmap {
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

    private fun generatePDF417(content: String, config: BarcodeConfig): Bitmap {
        val hints = hashMapOf(
            EncodeHintType.ERROR_CORRECTION to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(
            content,
            BarcodeFormat.PDF_417,
            config.width,
            config.height / 2,
            hints
        )
        return createBitmap(bitMatrix, config.copy(height = config.height / 2))
    }

    private fun generateLinearBarcode(content: String, config: BarcodeConfig): Bitmap {
        val zxingFormat = getZXingFormat(config.format)
        val barcodeWidth = config.width
        val barcodeHeight = config.height / 3

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

    // ==================== OkapiBarcode 生成的格式 ====================

    private fun generateRss14(content: String, config: BarcodeConfig): Bitmap {
        val symbol = DataBar14()
        symbol.content = content
        return symbolToBitmap(symbol, config)
    }

    private fun generateRssExpanded(content: String, config: BarcodeConfig): Bitmap {
        val symbol = DataBarExpanded()
        symbol.content = content
        return symbolToBitmap(symbol, config)
    }

    private fun generateMaxiCode(content: String, config: BarcodeConfig): Bitmap {
        val symbol = MaxiCode()
        symbol.content = content
        return symbolToBitmap(symbol, config)
    }

    private fun symbolToBitmap(symbol: Symbol, config: BarcodeConfig): Bitmap {
        val width = symbol.width
        val height = symbol.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(config.backgroundColor)
        val paint = Paint().apply { color = config.foregroundColor }

        for (rect in symbol.rectangles) {
            canvas.drawRect(
                rect.x.toFloat(),
                rect.y.toFloat(),
                (rect.x + rect.width).toFloat(),
                (rect.y + rect.height).toFloat(),
                paint
            )
        }
        for (hex in symbol.hexagons) {
            val path = android.graphics.Path()
            for (i in 0 until 6) {
                val hx = hex.getX(i).toFloat()
                val hy = hex.getY(i).toFloat()
                if (i == 0) path.moveTo(hx, hy) else path.lineTo(hx, hy)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
        for (circle in symbol.target) {
            canvas.drawCircle(
                circle.centreX.toFloat(),
                circle.centreY.toFloat(),
                circle.radius.toFloat(),
                paint
            )
        }

        return scaleAndPad(bitmap, config)
    }

    private fun scaleAndPad(source: Bitmap, config: BarcodeConfig): Bitmap {
        if (source.width >= config.width && source.height >= config.height) return source
        val scale = minOf(
            config.width.toFloat() / source.width,
            config.height.toFloat() / source.height
        )
        val newWidth = (source.width * scale).toInt()
        val newHeight = (source.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
        if (newWidth == config.width && newHeight == config.height) return scaled

        val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(config.backgroundColor)
        val left = (config.width - newWidth) / 2f
        val top = (config.height - newHeight) / 2f
        canvas.drawBitmap(scaled, left, top, null)
        scaled.recycle()
        source.recycle()
        return bitmap
    }

    // ==================== BoofCV 生成的格式 ====================

    private fun generateMicroQr(content: String, config: BarcodeConfig): Bitmap {
        val encoder = MicroQrCodeEncoder()
        encoder.addAutomatic(content)
        val qr = encoder.fixate()
        val gray: GrayU8 = MicroQrCodeGenerator.renderImage(8, 2, qr)
        val bitmap = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until gray.height) {
            for (x in 0 until gray.width) {
                val value = gray.get(x, y)
                val color = if (value == 0) config.backgroundColor else config.foregroundColor
                bitmap.setPixel(x, y, color)
            }
        }
        return scaleAndPad(bitmap, config)
    }

    // ==================== 自定义一维码生成 ====================

    private fun generatePharmacode(content: String, config: BarcodeConfig): Bitmap {
        val value = content.toInt()
        val elements = mutableListOf<Char>()
        var v = value
        while (v != 0) {
            if (v % 2 == 0) {
                elements.add('W')
                v = (v - 2) / 2
            } else {
                elements.add('N')
                v = (v - 1) / 2
            }
        }
        elements.reverse()

        val bars = mutableListOf<Int>()
        for (e in elements) {
            when (e) {
                'N' -> {
                    bars.add(1)
                    bars.add(2)
                }
                'W' -> {
                    bars.add(3)
                    bars.add(2)
                }
            }
        }
        return createGenericLinearBitmap(bars, config, content)
    }

    private fun generateUpcEanExtension(content: String, config: BarcodeConfig): Bitmap {
        val digits = content.filter { it.isDigit() }
        require(digits.length == 2 || digits.length == 5) { "UPC/EAN Extension must be 2 or 5 digits" }

        val patterns = mutableListOf<Int>()
        patterns.addAll(listOf(1, 1, 1, 0, 1)) // guard 1011

        val digitPatterns = mapOf(
            '0' to listOf(0, 0, 0, 1, 1, 0, 1),
            '1' to listOf(0, 0, 1, 1, 0, 0, 1),
            '2' to listOf(0, 0, 1, 0, 0, 1, 1),
            '3' to listOf(0, 1, 1, 1, 1, 0, 1),
            '4' to listOf(0, 1, 0, 0, 0, 1, 1),
            '5' to listOf(0, 1, 1, 0, 0, 0, 1),
            '6' to listOf(0, 1, 0, 1, 1, 1, 1),
            '7' to listOf(0, 1, 1, 1, 0, 1, 1),
            '8' to listOf(0, 1, 1, 0, 1, 1, 1),
            '9' to listOf(0, 0, 0, 1, 0, 1, 1)
        )

        for ((i, ch) in digits.withIndex()) {
            if (i > 0) patterns.addAll(listOf(1, 1)) // separator
            val pattern = digitPatterns[ch] ?: error("Invalid digit")
            for (bit in pattern) {
                patterns.add(if (bit == 1) 1 else 2)
            }
        }

        return createGenericLinearBitmap(patterns, config, content)
    }

    private fun generateTelepen(content: String, config: BarcodeConfig): Bitmap {
        val telepenTable = arrayOf(
            "313111113111", "311311113111", "313111111311", "311311111311",
            "313131113111", "311331113111", "313131111311", "311331111311",
            "313111131111", "311311131111", "313111133111", "311311133111",
            "313131131111", "311331131111", "313131133111", "311331133111",
            "313113113111", "311313113111", "313113111311", "311313111311",
            "313133113111", "311333113111", "313133111311", "311333111311",
            "313113131111", "311313131111", "313113133111", "311313133111",
            "313133131111", "311333131111", "313133133111", "311333133111",
            "313111113131", "311311113131", "313111111331", "311311111331",
            "313131113131", "311331113131", "313131111331", "311331111331",
            "313111131131", "311311131131", "313111133131", "311311133131",
            "313131131131", "311331131131", "313131133131", "311331133131",
            "313113113131", "311313113131", "313113111331", "311313111331",
            "313133113131", "311333113131", "313133111331", "311333111331",
            "313113131131", "311313131131", "313113133131", "311313133131",
            "313133131131", "311333131131", "313133133131", "311333133131",
            "313111113113", "311311113113", "313111111313", "311311111313",
            "313131113113", "311331113113", "313131111313", "311331111313",
            "313111131113", "311311131113", "313111133113", "311311133113",
            "313131131113", "311331131113", "313131133113", "311331133113",
            "313113113113", "311313113113", "313113111313", "311313111313",
            "313133113113", "311333113113", "313133111313", "311333111313",
            "313113131113", "311313131113", "313113133113", "311313133113",
            "313133131113", "311333131113", "313133133113", "311333133113",
            "313131313111", "311331313111", "313131311311", "311331311311",
            "313131331111", "311331331111", "313131333111", "311331333111",
            "313131313131", "311331313131", "313131311331", "311331311331",
            "313131331131", "311331331131", "313131333131", "311331333131",
            "313131313113", "311331313113", "313131311313", "311331311313",
            "313131331113", "311331331113", "313131333113", "311331333113",
            "313131313311", "311331313311", "313131313331", "311331313331"
        )

        var sum = 0
        val bars = mutableListOf<Int>()
        bars.addAll(telepenTable['_'.code].map { it - '0' })
        sum += '_'.code

        for (ch in content) {
            val code = ch.code
            require(code in 0..127) { "Telepen supports ASCII 0-127" }
            bars.addAll(telepenTable[code].map { it - '0' })
            sum += code
        }

        val check = if (sum % 127 == 0) 0 else 127 - (sum % 127)
        bars.addAll(telepenTable[check].map { it - '0' })
        bars.addAll(telepenTable['z'.code].map { it - '0' })

        return createGenericLinearBitmap(bars, config, content)
    }

    private fun generatePlessey(content: String, config: BarcodeConfig): Bitmap {
        require(content.all { it in '0'..'9' || it in 'A'..'F' }) { "Plessey supports hex digits" }
        val upper = content.uppercase()

        val start = "21211221"
        val stop = "20121211212"
        val bitToPattern = mapOf(
            '0' to "12",
            '1' to "21"
        )
        val digitToBits = mapOf(
            '0' to "0000", '1' to "0001", '2' to "0010", '3' to "0011",
            '4' to "0100", '5' to "0101", '6' to "0110", '7' to "0111",
            '8' to "1000", '9' to "1001", 'A' to "1010", 'B' to "1011",
            'C' to "1100", 'D' to "1101", 'E' to "1110", 'F' to "1111"
        )

        val bits = StringBuilder()
        bits.append(start)
        for (ch in upper) {
            bits.append(digitToBits[ch])
        }

        val crc = calculatePlesseyCrc(upper)
        val crcLow = (crc and 0x0F).toString(16).uppercase()[0]
        val crcHigh = ((crc shr 4) and 0x0F).toString(16).uppercase()[0]
        bits.append(digitToBits[crcLow])
        bits.append(digitToBits[crcHigh])
        bits.append(stop)

        val bars = mutableListOf<Int>()
        for (bit in bits.toString()) {
            val pattern = bitToPattern[bit] ?: continue
            bars.add(pattern[0] - '0')
            bars.add(pattern[1] - '0')
        }

        return createGenericLinearBitmap(bars, config, content)
    }

    private fun calculatePlesseyCrc(content: String): Int {
        var crc = 0x00
        for (ch in content) {
            val nibble = ch.digitToInt(16)
            for (i in 0 until 4) {
                val bit = (nibble shr (3 - i)) and 1
                val feedback = (crc shr 7) and 1
                crc = ((crc shl 1) or bit) and 0xFF
                if (feedback == 1) {
                    crc = crc xor 0x2F
                }
            }
        }
        return crc
    }

    private fun generateMsiPlessey(content: String, config: BarcodeConfig): Bitmap {
        require(content.all { it.isDigit() }) { "MSI Plessey supports digits only" }

        val digitPatterns = mapOf(
            '0' to "12121212",
            '1' to "12121221",
            '2' to "12122112",
            '3' to "12122121",
            '4' to "12211212",
            '5' to "12211221",
            '6' to "12212112",
            '7' to "12212121",
            '8' to "21121212",
            '9' to "21121221"
        )

        val dataWithCheck = content + calculateMod10(content)
        val pattern = StringBuilder()
        pattern.append("21") // start
        for (ch in dataWithCheck) {
            pattern.append(digitPatterns[ch])
        }
        pattern.append("121") // stop

        val bars = mutableListOf<Int>()
        for (c in pattern.toString()) {
            bars.add(c - '0')
        }

        return createGenericLinearBitmap(bars, config, content)
    }

    private fun calculateMod10(content: String): Int {
        var sum = 0
        var double = false
        for (i in content.length - 1 downTo 0) {
            var digit = content[i].digitToInt()
            if (double) {
                digit *= 2
                sum += digit / 10 + digit % 10
            } else {
                sum += digit
            }
            double = !double
        }
        return (10 - (sum % 10)) % 10
    }

    // ==================== 通用渲染工具 ====================

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

        canvas.drawColor(config.backgroundColor)

        for (x in 0 until barcodeWidth) {
            for (y in 0 until barcodeHeight) {
                if (bitMatrix.get(x, y)) {
                    bitmap.setPixel(x, y, config.foregroundColor)
                }
            }
        }

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

    private fun createGenericLinearBitmap(
        bars: List<Int>,
        config: BarcodeConfig,
        content: String,
        moduleWidth: Int = 4
    ): Bitmap {
        val quietZone = moduleWidth * 10
        val barcodeWidth = bars.sum() * moduleWidth + quietZone * 2
        val barcodeHeight = config.height / 3
        val textHeight = 40
        val padding = 10
        val totalHeight = barcodeHeight + textHeight + padding * 2

        val bitmap = Bitmap.createBitmap(barcodeWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(config.backgroundColor)

        var x = quietZone
        var isBar = true
        val rect = RectF()
        for (width in bars) {
            val w = width * moduleWidth
            if (isBar) {
                rect.set(x.toFloat(), 0f, (x + w).toFloat(), barcodeHeight.toFloat())
                canvas.drawRect(rect, Paint().apply { color = config.foregroundColor })
            }
            x += w
            isBar = !isBar
        }

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
            else -> BarcodeFormat.QR_CODE
        }
    }

    // ==================== 校验逻辑 ====================

    fun validateContent(content: String, format: AppBarcodeFormat): ValidationResult {
        return when (format) {
            AppBarcodeFormat.EAN_13 -> validateEAN13(content)
            AppBarcodeFormat.EAN_8 -> validateEAN8(content)
            AppBarcodeFormat.UPC_A -> validateUPCA(content)
            AppBarcodeFormat.UPC_E -> validateUPCE(content)
            AppBarcodeFormat.CODE_39 -> validateCode39(content)
            AppBarcodeFormat.RSS_14 -> validateRss14(content)
            AppBarcodeFormat.MICRO_QR -> validateMicroQr(content)
            AppBarcodeFormat.PHARMACODE -> validatePharmacode(content)
            AppBarcodeFormat.PLESSEY -> validatePlessey(content)
            AppBarcodeFormat.MSI_PLESSEY -> validateMsiPlessey(content)
            AppBarcodeFormat.TELEPEN -> validateTelepen(content)
            AppBarcodeFormat.UPC_EAN_EXTENSION -> validateUpcEanExtension(content)
            AppBarcodeFormat.MAXICODE -> validateMaxiCode(content)
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
        val isValid = content.all { it.uppercaseChar() in validChars }
        return if (isValid) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 39 only supports: 0-9, A-Z, -, ., space, $, /, +, %")
        }
    }

    private fun validateRss14(content: String): ValidationResult {
        val digitsOnly = content.filter { it.isDigit() }
        return if (digitsOnly.length in 1..13) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "RSS-14 requires 1 to 13 digits")
        }
    }

    private fun validateMicroQr(content: String): ValidationResult {
        return if (content.length <= 35) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Micro QR content too long")
        }
    }

    private fun validatePharmacode(content: String): ValidationResult {
        val value = content.toIntOrNull()
        return if (value != null && value in 3..131070) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Pharmacode requires an integer between 3 and 131070")
        }
    }

    private fun validatePlessey(content: String): ValidationResult {
        val isValid = content.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
        return if (isValid) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Plessey supports hex digits only")
        }
    }

    private fun validateMsiPlessey(content: String): ValidationResult {
        val isValid = content.all { it.isDigit() }
        return if (isValid) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "MSI Plessey supports digits only")
        }
    }

    private fun validateTelepen(content: String): ValidationResult {
        val isValid = content.all { it.code in 0..127 }
        return if (isValid) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Telepen supports ASCII characters only")
        }
    }

    private fun validateUpcEanExtension(content: String): ValidationResult {
        val digitsOnly = content.filter { it.isDigit() }
        return if (digitsOnly.length == 2 || digitsOnly.length == 5) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "UPC/EAN Extension requires 2 or 5 digits")
        }
    }

    private fun validateMaxiCode(content: String): ValidationResult {
        return if (content.isNotEmpty()) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "MaxiCode requires non-empty content")
        }
    }
}

package com.xenoamess.qrcodesimple

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.RectF
import boofcv.alg.fiducial.microqr.MicroQrCodeEncoder
import boofcv.alg.fiducial.microqr.MicroQrCodeGenerator
import boofcv.struct.image.GrayU8
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.xenoamess.qrcodesimple.data.BarcodeFormat as AppBarcodeFormat
import com.xenoamess.qrcodesimple.decoder.hanxin.HanXinEncoder
import uk.org.okapibarcode.backend.*
import uk.org.okapibarcode.graphics.TextAlignment
import kotlin.math.max
import kotlin.math.min

/**
 * 条码生成器 - 支持全部 OkapiBarcode 能生成的格式。
 *
 * 策略：
 * - 现有 22 种已支持扫描的格式继续沿用原有生成器（ZXing / 自定义 / BoofCV / HanXin），
 *   保证扫描回环不被破坏。
 * - Data Matrix 改由 OkapiBarcode 生成，通过 ECI 支持 UTF-8 / 中文。
 * - 所有新增格式统一走 OkapiBarcode，渲染时自动绘制人眼可读数字。
 */
object BarcodeGenerator {

    data class BarcodeConfig(
        val format: AppBarcodeFormat = AppBarcodeFormat.QR_CODE,
        val width: Int = 600,
        val height: Int = 600,
        val foregroundColor: Int = Color.BLACK,
        val backgroundColor: Int = Color.WHITE
    )

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
                AppBarcodeFormat.HAN_XIN -> generateHanXin(content, config)
                // Okapi 新增格式
                AppBarcodeFormat.SWISS_QR_CODE -> generateSwissQrCode(content, config)
                AppBarcodeFormat.UPN_QR_CODE -> generateUpnQrCode(content, config)
                AppBarcodeFormat.AZTEC_RUNE -> generateAztecRune(content, config)
                AppBarcodeFormat.CODE_ONE -> generateCodeOne(content, config)
                AppBarcodeFormat.GRID_MATRIX -> generateGridMatrix(content, config)
                AppBarcodeFormat.CODE_39_EXTENDED -> generateCode39Extended(content, config)
                AppBarcodeFormat.ITF_14 -> generateItf14(content, config)
                AppBarcodeFormat.CODE_2_OF_5_STANDARD -> generateCode2Of5(content, config, Code2Of5.ToFMode.INTERLEAVED)
                AppBarcodeFormat.CODE_2_OF_5_MATRIX -> generateCode2Of5(content, config, Code2Of5.ToFMode.MATRIX)
                AppBarcodeFormat.CODE_2_OF_5_INDUSTRIAL -> generateCode2Of5(content, config, Code2Of5.ToFMode.INDUSTRIAL)
                AppBarcodeFormat.CODE_2_OF_5_IATA -> generateCode2Of5(content, config, Code2Of5.ToFMode.IATA)
                AppBarcodeFormat.CODE_2_OF_5_DATALOGIC -> generateCode2Of5(content, config, Code2Of5.ToFMode.DATA_LOGIC)
                AppBarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_LEITCODE -> generateCode2Of5(content, config, Code2Of5.ToFMode.DP_LEITCODE)
                AppBarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE -> generateCode2Of5(content, config, Code2Of5.ToFMode.DP_IDENTCODE)
                AppBarcodeFormat.CODE_11 -> generateCode11(content, config)
                AppBarcodeFormat.CODE_16K -> generateCode16k(content, config)
                AppBarcodeFormat.CODE_32 -> generateCode32(content, config)
                AppBarcodeFormat.CODE_49 -> generateCode49(content, config)
                AppBarcodeFormat.CODABLOCK_F -> generateCodablockF(content, config)
                AppBarcodeFormat.CHANNEL_CODE -> generateChannelCode(content, config)
                AppBarcodeFormat.LOGMARS -> generateLogmars(content, config)
                AppBarcodeFormat.NVE_18 -> generateNve18(content, config)
                AppBarcodeFormat.DPD_CODE -> generateDpdCode(content, config)
                AppBarcodeFormat.PHARMACODE_2_TRACK -> generatePharmacode2Track(content, config)
                AppBarcodeFormat.PHARMAZENTRALNUMMER -> generatePharmazentralnummer(content, config)
                AppBarcodeFormat.TELEPEN_NUMERIC -> generateTelepenNumeric(content, config)
                AppBarcodeFormat.POSTNET -> generatePostnet(content, config)
                AppBarcodeFormat.ROYAL_MAIL_4_STATE -> generateRoyalMail4State(content, config)
                AppBarcodeFormat.USPS_ONE_CODE -> generateUspsOneCode(content, config)
                AppBarcodeFormat.USPS_PACKAGE -> generateUspsPackage(content, config)
                AppBarcodeFormat.JAPAN_POST -> generateJapanPost(content, config)
                AppBarcodeFormat.KIX_CODE -> generateKixCode(content, config)
                AppBarcodeFormat.KOREA_POST -> generateKoreaPost(content, config)
                AppBarcodeFormat.AUSTRALIA_POST -> generateAustraliaPost(content, config)
                AppBarcodeFormat.DATA_BAR_LIMITED -> generateDataBarLimited(content, config)
                AppBarcodeFormat.COMPOSITE -> generateComposite(content, config)
                AppBarcodeFormat.EAN_UPC_ADD_ON -> generateEanUpcAddOn(content, config)
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
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, config.width, config.height, hints)
        return createPaddedBitmap(bitMatrix, config)
    }

    private fun generateAztec(content: String, config: BarcodeConfig): Bitmap {
        val hints = hashMapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.AZTEC, config.width, config.height, hints)
        return createPaddedBitmap(bitMatrix, config)
    }

    private fun generatePDF417(content: String, config: BarcodeConfig): Bitmap {
        val hints = hashMapOf(
            EncodeHintType.ERROR_CORRECTION to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.PDF_417, config.width, config.height / 2, hints)
        return createPaddedBitmap(bitMatrix, config.copy(height = config.height / 2))
    }

    private fun generateLinearBarcode(content: String, config: BarcodeConfig): Bitmap {
        return when (config.format) {
            AppBarcodeFormat.CODE_128 -> generateCode128(content, config)
            AppBarcodeFormat.CODE_39 -> generateCode39(content, config)
            AppBarcodeFormat.CODE_93 -> generateCode93(content, config)
            AppBarcodeFormat.EAN_13 -> generateEAN13(content, config)
            AppBarcodeFormat.EAN_8 -> generateEAN8(content, config)
            AppBarcodeFormat.UPC_A -> generateUPCA(content, config)
            AppBarcodeFormat.UPC_E -> generateUPCE(content, config)
            AppBarcodeFormat.CODABAR -> generateCodabar(content, config)
            AppBarcodeFormat.ITF -> generateITF(content, config)
            else -> generateCode128(content, config)
        }
    }

    private fun generateCode128(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.CODE_128, config)
    private fun generateCode39(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.CODE_39, config)
    private fun generateCode93(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.CODE_93, config)
    private fun generateEAN13(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.EAN_13, config)
    private fun generateEAN8(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.EAN_8, config)
    private fun generateUPCA(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.UPC_A, config)
    private fun generateUPCE(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.UPC_E, config)
    private fun generateCodabar(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.CODABAR, config)
    private fun generateITF(content: String, config: BarcodeConfig): Bitmap = createZXingLinear(content, BarcodeFormat.ITF, config)

    private fun createZXingLinear(
        content: String,
        zxingFormat: BarcodeFormat,
        config: BarcodeConfig
    ): Bitmap {
        val hints = hashMapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, zxingFormat, config.width, config.height / 3, hints)
        return createLinearBarcodeBitmap(bitMatrix, config, content)
    }

    // ==================== OkapiBarcode 生成的格式 ====================

    private fun generateDataMatrix(content: String, config: BarcodeConfig): Bitmap {
        // 可扫描场景：ASCII 内容仍走 ZXing，保证 roundtrip 成功。
        // 非 ASCII 内容使用 OkapiBarcode 的 DataMatrix 并启用 ECI 26（UTF-8）。
        return if (content.all { it.code in 0..127 }) {
            generateZXing2D(content, BarcodeFormat.DATA_MATRIX, config)
        } else {
            val symbol = DataMatrix().apply {
                setEciMode(26)
                setContent(content)
            }
            symbolToBitmap(symbol, config)
        }
    }

    private fun generateZXing2D(content: String, zxingFormat: BarcodeFormat, config: BarcodeConfig): Bitmap {
        val hints = hashMapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, zxingFormat, config.width, config.height, hints)
        return createPaddedBitmap(bitMatrix, config)
    }

    private fun generateRss14(content: String, config: BarcodeConfig): Bitmap {
        val symbol = DataBar14().apply { setContent(content) }
        return symbolToBitmap(symbol, config)
    }

    private fun generateRssExpanded(content: String, config: BarcodeConfig): Bitmap {
        val symbol = DataBarExpanded().apply {
            dataType = Symbol.DataType.GS1
            setContent(content.replace("(", "[").replace(")", "]"))
        }
        return symbolToBitmap(symbol, config)
    }

    private fun generateMaxiCode(content: String, config: BarcodeConfig): Bitmap {
        val symbol = MaxiCode().apply { setContent(content) }
        return symbolToBitmap(symbol, config)
    }

    private fun generateSwissQrCode(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<SwissQrCode>(content, config)
    }

    private fun generateUpnQrCode(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<UpnQr>(content, config)
    }

    private fun generateAztecRune(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<AztecRune>(content, config)
    }

    private fun generateCodeOne(content: String, config: BarcodeConfig): Bitmap {
        // OkapiBarcode 0.5.6 的 Code One 自动选择版本时存在数组越界问题，
        // 按 S/T/A/B/C/D/E/F/G/H 顺序尝试固定版本，可绕过该 bug。
        val versions = listOf(
            CodeOne.Version.S,
            CodeOne.Version.T,
            CodeOne.Version.A,
            CodeOne.Version.B,
            CodeOne.Version.C,
            CodeOne.Version.D,
            CodeOne.Version.E,
            CodeOne.Version.F,
            CodeOne.Version.G,
            CodeOne.Version.H
        )
        for (version in versions) {
            val symbol = CodeOne().apply {
                setPreferredVersion(version)
            }
            try {
                symbol.setContent(content)
                return symbolToBitmap(symbol, config)
            } catch (e: Exception) {
                // 该版本无法容纳内容或仍触发编码问题，继续尝试下一版本
            }
        }
        throw IllegalArgumentException("Unable to encode Code One for content: $content")
    }

    private fun generateGridMatrix(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<GridMatrix>(content, config)
    }

    private fun generateCode39Extended(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Code3Of9Extended>(content, config)
    }

    private fun generateItf14(content: String, config: BarcodeConfig): Bitmap {
        return generateCode2Of5(content, config, Code2Of5.ToFMode.ITF14)
    }

    private fun generateCode2Of5(content: String, config: BarcodeConfig, mode: Code2Of5.ToFMode): Bitmap {
        return generateOkapi<Code2Of5>(content, config) {
            setMode(mode)
            setModuleWidthRatio(3.0)
        }
    }

    private fun generateCode11(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Code11>(content, config)
    }

    private fun generateCode16k(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Code16k>(content, config)
    }

    private fun generateCode32(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Code32>(content, config)
    }

    private fun generateCode49(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Code49>(content, config)
    }

    private fun generateCodablockF(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<CodablockF>(content, config)
    }

    private fun generateChannelCode(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<ChannelCode>(content, config)
    }

    private fun generateLogmars(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Logmars>(content, config)
    }

    private fun generateNve18(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Nve18>(content, config)
    }

    private fun generateDpdCode(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<DpdCode>(content, config)
    }

    private fun generatePharmacode2Track(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Pharmacode2Track>(content, config)
    }

    private fun generatePharmazentralnummer(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Pharmazentralnummer>(content, config)
    }

    private fun generateTelepenNumeric(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Telepen>(content, config) {
            setMode(Telepen.Mode.NUMERIC)
        }
    }

    private fun generatePostnet(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<Postnet>(content, config)
    }

    private fun generateRoyalMail4State(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<RoyalMail4State>(content, config)
    }

    private fun generateUspsOneCode(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<UspsOneCode>(content, config)
    }

    private fun generateUspsPackage(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<UspsPackage>(content, config)
    }

    private fun generateJapanPost(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<JapanPost>(content, config)
    }

    private fun generateKixCode(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<KixCode>(content, config)
    }

    private fun generateKoreaPost(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<KoreaPost>(content, config)
    }

    private fun generateAustraliaPost(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<AustraliaPost>(content, config)
    }

    private fun generateDataBarLimited(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<DataBarLimited>(content, config)
    }

    private fun generateComposite(content: String, config: BarcodeConfig): Bitmap {
        val symbol = Composite().apply {
            setSymbology(Composite.LinearEncoding.CODE_128)
            setLinearDataType(Symbol.DataType.GS1)
            setLinearContent("[01]12345678901231")
            setContent(content)
        }
        return symbolToBitmap(symbol, config)
    }

    private fun generateEanUpcAddOn(content: String, config: BarcodeConfig): Bitmap {
        return generateOkapi<EanUpcAddOn>(content, config)
    }

    private inline fun <reified T : Symbol> generateOkapi(
        content: String,
        config: BarcodeConfig,
        configure: T.() -> Unit = {}
    ): Bitmap {
        val symbol = T::class.java.getDeclaredConstructor().newInstance().apply(configure)
        symbol.setContent(content)
        return symbolToBitmap(symbol, config)
    }

    private fun symbolToBitmap(symbol: Symbol, config: BarcodeConfig): Bitmap {
        val scale = 8
        val width = symbol.width * scale
        val height = symbol.height * scale
        val padding = 40
        val totalWidth = width + padding * 2
        val totalHeight = height + padding * 2
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        fillBitmap(bitmap, config.backgroundColor)

        for (rect in symbol.rectangles) {
            val x0 = (rect.x * scale).toInt() + padding
            val y0 = (rect.y * scale).toInt() + padding
            val x1 = ((rect.x + rect.width) * scale).toInt() + padding
            val y1 = ((rect.y + rect.height) * scale).toInt() + padding
            drawRectPixels(bitmap, x0, y0, x1, y1, config.foregroundColor)
        }
        for (hex in symbol.hexagons) {
            val vertices = List(6) { i ->
                val hx = hex.getX(i).toFloat() * scale + padding
                val hy = hex.getY(i).toFloat() * scale + padding
                hx to hy
            }
            val minX = vertices.minOf { it.first }.toInt().coerceAtLeast(0)
            val maxX = vertices.maxOf { it.first }.toInt().coerceAtMost(bitmap.width - 1)
            val minY = vertices.minOf { it.second }.toInt().coerceAtLeast(0)
            val maxY = vertices.maxOf { it.second }.toInt().coerceAtMost(bitmap.height - 1)
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    if (pointInPolygon(x + 0.5f, y + 0.5f, vertices)) {
                        bitmap.setPixel(x, y, config.foregroundColor)
                    }
                }
            }
        }
        for (circle in symbol.target) {
            val cx = circle.centreX.toFloat() * scale + padding
            val cy = circle.centreY.toFloat() * scale + padding
            val radius = circle.radius.toFloat() * scale
            val minX = (cx - radius).toInt().coerceAtLeast(0)
            val maxX = (cx + radius).toInt().coerceAtMost(bitmap.width - 1)
            val minY = (cy - radius).toInt().coerceAtLeast(0)
            val maxY = (cy + radius).toInt().coerceAtMost(bitmap.height - 1)
            val r2 = radius * radius
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val dx = x + 0.5f - cx
                    val dy = y + 0.5f - cy
                    if (dx * dx + dy * dy <= r2) {
                        bitmap.setPixel(x, y, config.foregroundColor)
                    }
                }
            }
        }

        if (symbol.texts.isNotEmpty()) {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = config.foregroundColor
                typeface = Typeface.DEFAULT
                textSize = (symbol.fontSize * scale).toFloat()
            }
            for (textBox in symbol.texts) {
                paint.textAlign = when (textBox.alignment) {
                    TextAlignment.LEFT -> Paint.Align.LEFT
                    TextAlignment.RIGHT -> Paint.Align.RIGHT
                    TextAlignment.CENTER, TextAlignment.JUSTIFY -> Paint.Align.CENTER
                    else -> Paint.Align.CENTER
                }
                val x = (textBox.x * scale).toFloat() + padding
                val y = (textBox.y * scale).toFloat() + padding
                val boxWidth = (textBox.width * scale).toFloat()
                val textX = when (paint.textAlign) {
                    Paint.Align.LEFT -> x
                    Paint.Align.RIGHT -> x + boxWidth
                    Paint.Align.CENTER -> x + boxWidth / 2f
                }
                canvas.drawText(textBox.text, textX, y, paint)
            }
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
        val scaled = Bitmap.createScaledBitmap(source, newWidth, newHeight, false)
        if (newWidth == config.width && newHeight == config.height) return scaled

        val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
        fillBitmap(bitmap, config.backgroundColor)
        val left = (config.width - newWidth) / 2
        val top = (config.height - newHeight) / 2
        drawBitmapOnto(bitmap, scaled, left, top)
        scaled.recycle()
        source.recycle()
        return bitmap
    }

    // ==================== BoofCV 生成的格式 ====================

    private fun generateMicroQr(content: String, config: BarcodeConfig): Bitmap {
        val encoder = MicroQrCodeEncoder()
        encoder.addAutomatic(content)
        val qr = encoder.fixate()
        val gray: GrayU8 = MicroQrCodeGenerator.renderImage(4, 0, qr)
        val quietZone = 40
        val paddedWidth = gray.width + quietZone * 2
        val paddedHeight = gray.height + quietZone * 2
        val bitmap = Bitmap.createBitmap(paddedWidth, paddedHeight, Bitmap.Config.ARGB_8888)
        fillBitmap(bitmap, config.backgroundColor)
        for (y in 0 until gray.height) {
            for (x in 0 until gray.width) {
                val value = gray.get(x, y)
                val color = if (value == 0) config.foregroundColor else config.backgroundColor
                bitmap.setPixel(x + quietZone, y + quietZone, color)
            }
        }
        return scaleAndPad(bitmap, config)
    }

    // ==================== 自定义一维码生成 ====================

    private fun generatePharmacode(content: String, config: BarcodeConfig): Bitmap {
        val value = content.toInt()
        val digitToSymbol = mapOf(
            0 to listOf(1, 2),
            1 to listOf(2, 2),
            2 to listOf(1, 1),
            3 to listOf(2, 1)
        )
        val bars = mutableListOf<Int>()
        var v = value
        while (v > 0) {
            val digit = v % 4
            v /= 4
            bars.addAll(digitToSymbol[digit]!!)
        }
        bars.add(1)
        return createGenericLinearBitmap(bars, config, content)
    }

    private fun generateUpcEanExtension(content: String, config: BarcodeConfig): Bitmap {
        val digits = content.filter { it.isDigit() }
        require(digits.length == 2 || digits.length == 5) { "UPC/EAN Extension must be 2 or 5 digits" }
        val mainContent = "1234567890128"
        val mainBitmapFull = generateEAN13(mainContent, config)
            ?: return generateGenericLinearUpcEanExtension(digits, config)

        val padding = 40
        val mainWidth = (mainBitmapFull.width - padding * 2).coerceAtLeast(1)
        val mainBitmap = Bitmap.createBitmap(mainBitmapFull, padding, 0, mainWidth, mainBitmapFull.height)
        mainBitmapFull.recycle()

        val extBitmap = generateGenericLinearUpcEanExtension(digits, config)
        val totalWidth = mainBitmap.width + extBitmap.width
        val height = maxOf(mainBitmap.height, extBitmap.height)
        val bitmap = Bitmap.createBitmap(totalWidth, height, Bitmap.Config.ARGB_8888)
        fillBitmap(bitmap, config.backgroundColor)
        drawBitmapOnto(bitmap, mainBitmap, 0, 0)
        drawBitmapOnto(bitmap, extBitmap, mainBitmap.width, 0)
        mainBitmap.recycle()
        extBitmap.recycle()
        return bitmap
    }

    private fun generateGenericLinearUpcEanExtension(content: String, config: BarcodeConfig): Bitmap {
        val digits = content.filter { it.isDigit() }
        require(digits.length == 2 || digits.length == 5) { "UPC/EAN Extension must be 2 or 5 digits" }

        val lPatterns = arrayOf(
            "0001101", "0011001", "0010011", "0111101", "0100011",
            "0110001", "0101111", "0111011", "0110111", "0001011"
        )
        val gPatterns = arrayOf(
            "0100111", "0110011", "0011011", "0100001", "0011101",
            "0111001", "0000101", "0010001", "0001001", "0010111"
        )
        val ean5Parity = intArrayOf(24, 20, 18, 17, 12, 6, 3, 10, 9, 5)

        val bits = StringBuilder()
        bits.append("1011")

        if (digits.length == 5) {
            val d = digits.map { it.digitToInt() }
            var check = 0
            for (i in 0 until 5) {
                check += (if (i % 2 == 0) 3 else 9) * d[i]
            }
            check %= 10
            val parity = ean5Parity[check]
            for (i in 0 until 5) {
                val useG = ((parity shr (4 - i)) and 1) == 1
                val pattern = if (useG) gPatterns[d[i]] else lPatterns[d[i]]
                bits.append(pattern)
                if (i < 4) bits.append("01")
            }
        } else {
            val d = digits.map { it.digitToInt() }
            val check = d[0] * 10 + d[1]
            val parity = when (check % 4) {
                0 -> "LL"
                1 -> "LG"
                2 -> "GL"
                else -> "GG"
            }
            for (i in 0 until 2) {
                val pattern = if (parity[i] == 'L') lPatterns[d[i]] else gPatterns[d[i]]
                bits.append(pattern)
                if (i < 1) bits.append("01")
            }
        }

        val pattern = bits.toString()
        val moduleWidth = 4
        val rightQuietZone = moduleWidth * 10
        val barcodeWidth = pattern.length * moduleWidth
        val height = config.height / 3
        val bitmap = Bitmap.createBitmap(barcodeWidth + rightQuietZone, height, Bitmap.Config.ARGB_8888)
        fillBitmap(bitmap, config.backgroundColor)

        var x = 0
        for (bit in pattern) {
            if (bit == '1') {
                drawRectPixels(bitmap, x, 0, x + moduleWidth, height, config.foregroundColor)
            }
            x += moduleWidth
        }

        return bitmap
    }

    private fun combineBarcodesHorizontal(first: Bitmap, second: Bitmap, config: BarcodeConfig): Bitmap {
        val spacing = 80
        val totalWidth = first.width + second.width + spacing
        val height = maxOf(first.height, second.height)
        val bitmap = Bitmap.createBitmap(totalWidth, height, Bitmap.Config.ARGB_8888)
        fillBitmap(bitmap, config.backgroundColor)
        drawBitmapOnto(bitmap, first, 0, 0)
        drawBitmapOnto(bitmap, second, first.width + spacing, 0)
        first.recycle()
        second.recycle()
        return bitmap
    }

    private fun generateHanXin(content: String, config: BarcodeConfig): Bitmap {
        val result = HanXinEncoder.encode(
            content = content,
            width = config.width,
            height = config.height,
            foreground = config.foregroundColor,
            background = config.backgroundColor
        )
        return result?.bitmap ?: throw IllegalArgumentException("Failed to generate Han Xin Code")
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
        bars.add(1)

        return createGenericLinearBitmap(bars, config, content)
    }

    private fun generatePlessey(content: String, config: BarcodeConfig): Bitmap {
        require(content.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) { "Plessey supports hex digits" }
        val upper = content.uppercase()

        val start = "1101"
        val stop = "1101"
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
            if (bit == '0') {
                bars.add(1)
                bars.add(1)
            } else {
                bars.add(2)
                bars.add(1)
            }
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
        pattern.append("21")
        for (ch in dataWithCheck) {
            pattern.append(digitPatterns[ch])
        }
        pattern.append("121")

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

    private fun fillBitmap(bitmap: Bitmap, color: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height) { color }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun drawRectPixels(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        color: Int
    ) {
        val drawLeft = left.coerceIn(0, bitmap.width)
        val drawTop = top.coerceIn(0, bitmap.height)
        val drawRight = right.coerceIn(0, bitmap.width)
        val drawBottom = bottom.coerceIn(0, bitmap.height)
        val rowWidth = drawRight - drawLeft
        if (rowWidth <= 0 || drawBottom <= drawTop) return
        val row = IntArray(rowWidth) { color }
        for (y in drawTop until drawBottom) {
            bitmap.setPixels(row, 0, rowWidth, drawLeft, y, rowWidth, 1)
        }
    }

    private fun drawBitmapOnto(target: Bitmap, source: Bitmap, left: Int, top: Int) {
        val copyWidth = minOf(source.width, target.width - left)
        val copyHeight = minOf(source.height, target.height - top)
        if (copyWidth <= 0 || copyHeight <= 0 || left < 0 || top < 0) return
        val pixels = IntArray(copyWidth * copyHeight)
        source.getPixels(pixels, 0, copyWidth, 0, 0, copyWidth, copyHeight)
        target.setPixels(pixels, 0, copyWidth, left, top, copyWidth, copyHeight)
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

    private fun createPaddedBitmap(bitMatrix: BitMatrix, config: BarcodeConfig): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val padding = 40
        val totalWidth = width + padding * 2
        val totalHeight = height + padding * 2
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)

        for (x in 0 until totalWidth) {
            for (y in 0 until totalHeight) {
                bitmap.setPixel(x, y, config.backgroundColor)
            }
        }

        for (x in 0 until width) {
            for (y in 0 until height) {
                if (bitMatrix.get(x, y)) {
                    bitmap.setPixel(x + padding, y + padding, config.foregroundColor)
                }
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
        val barcodeHeight = config.height / 3
        val textHeight = 40
        val padding = 40

        val totalWidth = barcodeWidth + padding * 2
        val totalHeight = barcodeHeight + textHeight + padding * 2
        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)

        fillBitmap(bitmap, config.backgroundColor)

        for (x in 0 until barcodeWidth) {
            for (y in 0 until barcodeHeight) {
                if (bitMatrix.get(x, y)) {
                    bitmap.setPixel(x + padding, y + padding, config.foregroundColor)
                }
            }
        }

        val paint = Paint().apply {
            color = config.foregroundColor
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val canvas = Canvas(bitmap)
        canvas.drawText(
            content,
            totalWidth / 2f,
            barcodeHeight + padding + textHeight.toFloat(),
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
        val totalHeight = barcodeHeight + textHeight + 40 * 2

        val bitmap = Bitmap.createBitmap(barcodeWidth, totalHeight, Bitmap.Config.ARGB_8888)
        fillBitmap(bitmap, config.backgroundColor)

        var x = quietZone
        var isBar = true
        for (width in bars) {
            val w = width * moduleWidth
            if (isBar) {
                drawRectPixels(bitmap, x, 0, x + w, barcodeHeight, config.foregroundColor)
            }
            x += w
            isBar = !isBar
        }

        val textPaint = Paint().apply {
            color = config.foregroundColor
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val canvas = Canvas(bitmap)
        canvas.drawText(
            content,
            barcodeWidth / 2f,
            barcodeHeight + textHeight.toFloat(),
            textPaint
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
            AppBarcodeFormat.CODE_128 -> validateCode128(content)
            AppBarcodeFormat.CODE_39 -> validateCode39(content)
            AppBarcodeFormat.CODE_39_EXTENDED -> validateCode39Extended(content)
            AppBarcodeFormat.CODE_93 -> validateCode93(content)
            AppBarcodeFormat.CODABAR -> validateCodabar(content)
            AppBarcodeFormat.ITF -> validateITF(content)
            AppBarcodeFormat.ITF_14 -> validateITF14(content)
            AppBarcodeFormat.RSS_14 -> validateRss14(content)
            AppBarcodeFormat.RSS_EXPANDED -> validateRssExpanded(content)
            AppBarcodeFormat.MICRO_QR -> validateMicroQr(content)
            AppBarcodeFormat.PHARMACODE -> validatePharmacode(content)
            AppBarcodeFormat.PLESSEY -> validatePlessey(content)
            AppBarcodeFormat.MSI_PLESSEY -> validateMsiPlessey(content)
            AppBarcodeFormat.TELEPEN -> validateTelepen(content)
            AppBarcodeFormat.TELEPEN_NUMERIC -> validateTelepenNumeric(content)
            AppBarcodeFormat.UPC_EAN_EXTENSION -> validateUpcEanExtension(content)
            AppBarcodeFormat.EAN_UPC_ADD_ON -> validateEanUpcAddOn(content)
            AppBarcodeFormat.DATA_MATRIX -> validateDataMatrix(content)
            AppBarcodeFormat.AZTEC -> validateAztec(content)
            AppBarcodeFormat.PDF417 -> validatePDF417(content)
            AppBarcodeFormat.MAXICODE -> validateMaxiCode(content)
            AppBarcodeFormat.HAN_XIN -> validateHanXin(content)
            AppBarcodeFormat.QR_CODE -> validateQRCode(content)
            AppBarcodeFormat.SWISS_QR_CODE -> validateSwissQrCode(content)
            AppBarcodeFormat.UPN_QR_CODE -> validateUpnQrCode(content)
            // 新增 Okapi 格式：基本非空校验，让 Okapi 在生成时拒绝非法内容
            AppBarcodeFormat.CODE_2_OF_5_STANDARD,
            AppBarcodeFormat.CODE_2_OF_5_MATRIX,
            AppBarcodeFormat.CODE_2_OF_5_INDUSTRIAL,
            AppBarcodeFormat.CODE_2_OF_5_IATA,
            AppBarcodeFormat.CODE_2_OF_5_DATALOGIC,
            AppBarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_LEITCODE,
            AppBarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE -> validateCode2Of5(content)
            AppBarcodeFormat.CODE_11 -> validateCode11(content)
            AppBarcodeFormat.CODE_16K -> validateCode16k(content)
            AppBarcodeFormat.CODE_32 -> validateCode32(content)
            AppBarcodeFormat.CODE_49 -> validateCode49(content)
            AppBarcodeFormat.CODABLOCK_F -> validateCodablockF(content)
            AppBarcodeFormat.CHANNEL_CODE -> validateChannelCode(content)
            AppBarcodeFormat.LOGMARS -> validateLogmars(content)
            AppBarcodeFormat.NVE_18 -> validateNve18(content)
            AppBarcodeFormat.DPD_CODE -> validateDpdCode(content)
            AppBarcodeFormat.PHARMACODE_2_TRACK -> validatePharmacode2Track(content)
            AppBarcodeFormat.PHARMAZENTRALNUMMER -> validatePharmazentralnummer(content)
            AppBarcodeFormat.POSTNET -> validatePostnet(content)
            AppBarcodeFormat.ROYAL_MAIL_4_STATE -> validateRoyalMail4State(content)
            AppBarcodeFormat.USPS_ONE_CODE -> validateUspsOneCode(content)
            AppBarcodeFormat.USPS_PACKAGE -> validateUspsPackage(content)
            AppBarcodeFormat.JAPAN_POST -> validateJapanPost(content)
            AppBarcodeFormat.KIX_CODE -> validateKixCode(content)
            AppBarcodeFormat.KOREA_POST -> validateKoreaPost(content)
            AppBarcodeFormat.AUSTRALIA_POST -> validateAustraliaPost(content)
            AppBarcodeFormat.DATA_BAR_LIMITED -> validateDataBarLimited(content)
            AppBarcodeFormat.COMPOSITE -> validateComposite(content)
            AppBarcodeFormat.AZTEC_RUNE -> validateAztecRune(content)
            AppBarcodeFormat.CODE_ONE -> validateCodeOne(content)
            AppBarcodeFormat.GRID_MATRIX -> validateGridMatrix(content)
            AppBarcodeFormat.UNKNOWN -> ValidationResult(true)
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

    private fun validateCode39Extended(content: String): ValidationResult {
        return if (content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 39 Extended only supports ASCII characters")
        }
    }

    private fun validateCode128(content: String): ValidationResult {
        return if (content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 128 only supports ASCII characters")
        }
    }

    private fun validateCode93(content: String): ValidationResult {
        val validChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"
        return if (content.all { it in validChars }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 93 only supports: 0-9, A-Z, -, ., space, $, /, +, %")
        }
    }

    private fun validateCodabar(content: String): ValidationResult {
        val validChars = "0123456789-$:/.+"
        return if (content.all { it in validChars }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Codabar only supports: 0-9, -, $, :, /, ., +")
        }
    }

    private fun validateITF(content: String): ValidationResult {
        if (content.isEmpty()) {
            return ValidationResult(false, "ITF content cannot be empty")
        }
        if (!content.all { it.isDigit() }) {
            return ValidationResult(false, "ITF only supports digits")
        }
        return if (content.length % 2 == 0) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "ITF requires an even number of digits")
        }
    }

    private fun validateITF14(content: String): ValidationResult {
        if (content.isEmpty()) return ValidationResult(false, "ITF-14 content cannot be empty")
        if (!content.all { it.isDigit() }) return ValidationResult(false, "ITF-14 only supports digits")
        return if (content.length <= 13) ValidationResult(true) else ValidationResult(false, "ITF-14 input too long")
    }

    private fun validateRss14(content: String): ValidationResult {
        val digitsOnly = content.filter { it.isDigit() }
        return if (digitsOnly.length in 1..14) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "RSS-14 requires 1 to 14 digits")
        }
    }

    private fun validateRssExpanded(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.code in 0..127 } && content.any { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "RSS Expanded content must be ASCII and contain at least one digit")
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

    private fun validateTelepenNumeric(content: String): ValidationResult {
        return if (content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Telepen Numeric supports digits only")
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

    private fun validateEanUpcAddOn(content: String): ValidationResult {
        val digitsOnly = content.filter { it.isDigit() }
        return if (digitsOnly.length in 2..5) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "EAN/UPC Add-On requires 2 to 5 digits")
        }
    }

    private fun validateDataMatrix(content: String): ValidationResult {
        return if (content.isNotEmpty()) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Data Matrix content cannot be empty")
        }
    }

    private fun validateAztec(content: String): ValidationResult {
        return if (content.isNotEmpty()) ValidationResult(true) else ValidationResult(false, "Aztec content cannot be empty")
    }

    private fun validatePDF417(content: String): ValidationResult {
        return if (content.isNotEmpty()) ValidationResult(true) else ValidationResult(false, "PDF417 content cannot be empty")
    }

    private fun validateMaxiCode(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "MaxiCode only supports ASCII characters")
        }
    }

    private fun validateHanXin(content: String): ValidationResult {
        return if (content.isNotEmpty()) ValidationResult(true) else ValidationResult(false, "Han Xin Code requires non-empty content")
    }

    private fun validateQRCode(content: String): ValidationResult {
        return if (content.isNotEmpty()) ValidationResult(true) else ValidationResult(false, "QR Code content cannot be empty")
    }

    private fun validateSwissQrCode(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.length <= 997 && content.all { it.code in 0..255 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Swiss QR Code content must be Latin-1 and up to 997 characters")
        }
    }

    private fun validateUpnQrCode(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "UPN QR Code only supports ISO-8859-2 characters")
        }
    }

    private fun validateCode2Of5(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 2 of 5 requires digits only")
        }
    }

    private fun validateCode11(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() || it == '-' }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 11 supports digits and hyphens")
        }
    }

    private fun validateCode16k(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 16K supports ASCII characters")
        }
    }

    private fun validateCode32(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.length <= 8 && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 32 requires up to 8 digits")
        }
    }

    private fun validateCode49(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code 49 supports ASCII characters")
        }
    }

    private fun validateCodablockF(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Codablock F supports ASCII characters")
        }
    }

    private fun validateChannelCode(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Channel Code requires digits")
        }
    }

    private fun validateLogmars(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "LOGMARS supports ASCII characters")
        }
    }

    private fun validateNve18(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() || it.isUpperCase() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "NVE-18 supports digits and uppercase letters")
        }
    }

    private fun validateDpdCode(content: String): ValidationResult {
        return if (content.isNotEmpty()
            && content.length in 27..28
            && content.matches(Regex(".?[0-9A-Z]{11}[0-9]{16}"))
        ) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "DPD Code requires 27 or 28 characters: optional prefix + 11 alphanumerics + 16 digits")
        }
    }

    private fun validatePharmacode2Track(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Pharmacode Two-Track requires digits")
        }
    }

    private fun validatePharmazentralnummer(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.length <= 7 && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Pharmazentralnummer requires up to 7 digits")
        }
    }

    private fun validatePostnet(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Postnet requires digits")
        }
    }

    private fun validateRoyalMail4State(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() || it.isUpperCase() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Royal Mail 4-State supports digits and uppercase letters")
        }
    }

    private fun validateUspsOneCode(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "USPS OneCode requires digits")
        }
    }

    private fun validateUspsPackage(content: String): ValidationResult {
        // USPS Package (IMpb) 使用 GS1-128 编码，内容需以 AI 开头且总长度为偶数
        return if (content.isNotEmpty()
            && content.matches(Regex("[0-9\\[\\]]+"))
            && content.length % 2 == 0
            && content.contains("[")
        ) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "USPS Package requires GS1 Application Identifier data with brackets and an even number of characters")
        }
    }

    private fun validateJapanPost(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.code in 0..127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Japan Post supports ASCII characters")
        }
    }

    private fun validateKixCode(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() || it.isUpperCase() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "KIX Code supports digits and uppercase letters")
        }
    }

    private fun validateKoreaPost(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Korea Post requires digits")
        }
    }

    private fun validateAustraliaPost(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() || it.isUpperCase() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Australia Post supports digits and uppercase letters")
        }
    }

    private fun validateDataBarLimited(content: String): ValidationResult {
        return if (content.isNotEmpty() && content.all { it.isDigit() }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "GS1 DataBar Limited requires digits")
        }
    }

    private fun validateComposite(content: String): ValidationResult {
        return if (content.isNotEmpty()
            && content.all { it.code in 0..127 }
            && content.contains("[")
        ) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Composite 2D component must be ASCII and contain GS1 Application Identifiers in brackets")
        }
    }

    private fun validateAztecRune(content: String): ValidationResult {
        val value = content.toIntOrNull()
        return if (value != null && value in 0..255) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Aztec Rune requires an integer between 0 and 255")
        }
    }

    private fun validateCodeOne(content: String): ValidationResult {
        // Code One 仅支持 Latin-1 (ISO-8859-1) 字符集
        return if (content.isNotEmpty() && content.all { it.code in 0..255 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Code One only supports ISO-8859-1 characters")
        }
    }

    private fun validateGridMatrix(content: String): ValidationResult {
        // Grid Matrix 在 OkapiBarcode 0.5.6 中对纯 ASCII 内容存在编码 bug，
        // 至少包含一个非 ASCII 字符（通常是中文）可避免该问题。
        return if (content.isNotEmpty() && content.any { it.code > 127 }) {
            ValidationResult(true)
        } else {
            ValidationResult(false, "Grid Matrix requires at least one non-ASCII character")
        }
    }
}

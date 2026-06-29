package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.king.wechat.qrcode.WeChatQRCodeDetector
import com.xenoamess.qrcodesimple.data.BarcodeFormat as AppBarcodeFormat
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.decoder.CustomLinearBarcodeScanner
import com.xenoamess.qrcodesimple.decoder.MicroQrCodeScanner
import com.xenoamess.qrcodesimple.decoder.hanxin.HanXinDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.opencv.core.Mat
import java.util.ArrayList
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 将 ZXing 条码格式映射到历史记录类型
 */
fun BarcodeFormat.toHistoryType(): HistoryType {
    return when (this) {
        BarcodeFormat.QR_CODE -> HistoryType.QR_CODE
        BarcodeFormat.DATA_MATRIX -> HistoryType.DATA_MATRIX
        BarcodeFormat.AZTEC -> HistoryType.AZTEC
        BarcodeFormat.PDF_417 -> HistoryType.PDF417
        BarcodeFormat.RSS_14 -> HistoryType.RSS_14
        BarcodeFormat.RSS_EXPANDED -> HistoryType.RSS_EXPANDED
        BarcodeFormat.MAXICODE -> HistoryType.MAXICODE
        else -> HistoryType.BARCODE
    }
}

/**
 * 将应用条码格式映射到历史记录类型
 */
fun AppBarcodeFormat.toHistoryType(): HistoryType {
    return when (this) {
        AppBarcodeFormat.QR_CODE -> HistoryType.QR_CODE
        AppBarcodeFormat.DATA_MATRIX -> HistoryType.DATA_MATRIX
        AppBarcodeFormat.AZTEC -> HistoryType.AZTEC
        AppBarcodeFormat.PDF417 -> HistoryType.PDF417
        AppBarcodeFormat.RSS_14 -> HistoryType.RSS_14
        AppBarcodeFormat.RSS_EXPANDED -> HistoryType.RSS_EXPANDED
        AppBarcodeFormat.MAXICODE -> HistoryType.MAXICODE
        AppBarcodeFormat.MICRO_QR -> HistoryType.MICRO_QR
        AppBarcodeFormat.UPC_EAN_EXTENSION -> HistoryType.UPC_EAN_EXTENSION
        AppBarcodeFormat.PHARMACODE -> HistoryType.PHARMACODE
        AppBarcodeFormat.PLESSEY -> HistoryType.PLESSEY
        AppBarcodeFormat.MSI_PLESSEY -> HistoryType.MSI_PLESSEY
        AppBarcodeFormat.TELEPEN -> HistoryType.TELEPEN
        AppBarcodeFormat.HAN_XIN -> HistoryType.HAN_XIN
        else -> HistoryType.BARCODE
    }
}

/**
 * 将自定义一维码格式映射到 ZXing 条码格式（用于扫描结果展示）
 */
private fun CustomLinearBarcodeScanner.Format.toZXingFormat(): BarcodeFormat {
    return when (this) {
        CustomLinearBarcodeScanner.Format.PHARMACODE -> BarcodeFormat.CODE_128
        CustomLinearBarcodeScanner.Format.PLESSEY -> BarcodeFormat.CODE_128
        CustomLinearBarcodeScanner.Format.MSI_PLESSEY -> BarcodeFormat.CODE_128
        CustomLinearBarcodeScanner.Format.TELEPEN -> BarcodeFormat.CODE_128
    }
}

/**
 * 多库二维码扫描管理器
 * 按优先级顺序尝试：WeChatQRCode -> ZXing -> ML Kit
 */
object QRCodeScanner {

    private const val TAG = "QRCodeScanner"

    /**
     * 单次扫描总超时（毫秒）。防止某个解码器挂起导致页面/后台线程被无限期占用。
     */
    private const val TOTAL_SCAN_TIMEOUT_MS = 15000L

    /**
     * 单个引擎超时（毫秒）。每个库最多只能占用这么长时间。
     */
    private const val PER_ENGINE_TIMEOUT_MS = 5000L

    /**
     * 扫描前缩放的最大边长。过大的图片会占用大量内存并显著降低解码速度。
     */
    private const val MAX_SCAN_DIMENSION = 1280

    private val scanningEnabled = AtomicBoolean(true)

    /**
     * 紧急制动：当应用发生严重错误（如 OOM）后，可调用此接口暂时禁用所有扫描，
     * 避免继续占用资源导致其它页面（如生成页）也无法使用。
     */
    fun setScanningEnabled(enabled: Boolean) {
        scanningEnabled.set(enabled)
    }

    /**
     * 将 Bitmap 缩放到合适的尺寸，减少内存占用并加快解码。
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = maxOf(width, height)
        if (maxDim <= MAX_SCAN_DIMENSION) return bitmap

        val scale = MAX_SCAN_DIMENSION.toFloat() / maxDim
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 在协程中执行一个解码引擎，带有单独超时和异常隔离。
     * 即使该引擎抛错或超时，也不会影响其它引擎或调用方。
     */
    private suspend fun <T> runEngineSafely(
        name: String,
        block: suspend () -> List<T>
    ): List<T> = try {
        withTimeoutOrNull(PER_ENGINE_TIMEOUT_MS) {
            block()
        } ?: emptyList()
    } catch (e: Throwable) {
        Log.e(TAG, "$name engine failed", e)
        emptyList()
    }

    data class ScanResult(
        val text: String,
        val library: Library,
        val format: BarcodeFormat = BarcodeFormat.QR_CODE,
        val resultMetadata: Map<com.google.zxing.ResultMetadataType, Any>? = null
    )

    enum class Library {
        WECHAT_QR,
        ZXING,
        ML_KIT,
        BOOFCV,
        HAN_XIN,
        CUSTOM_LINEAR
    }

    /**
     * 扫描位图中的所有二维码
     * 按顺序尝试各个库，直到获得结果
     */
    suspend fun scan(context: Context, bitmap: Bitmap): List<ScanResult> = try {
        withTimeoutOrNull(TOTAL_SCAN_TIMEOUT_MS) {
            withContext(Dispatchers.Default) {
                if (!scanningEnabled.get()) {
                    Log.w(TAG, "Scanning is disabled")
                    return@withContext emptyList<ScanResult>()
                }

                val processedBitmap = preprocessBitmap(bitmap)
                val shouldRecycleProcessed = processedBitmap !== bitmap
                val results = mutableListOf<ScanResult>()

                try {
                    // 1. 尝试 WeChatQRCode
                    if (QRCodeApp.isWeChatQRCodeInitialized && isActive) {
                        val wechatResults = runEngineSafely("WeChatQRCode") {
                            scanWithWeChatQRCode(processedBitmap)
                        }
                        if (wechatResults.isNotEmpty()) {
                            results.addAll(wechatResults)
                            Log.d(TAG, "WeChatQRCode detected ${wechatResults.size} codes")
                        }
                    }

                    // 2. 尝试 ZXing
                    if (results.isEmpty() && isActive) {
                        val zxingResults = runEngineSafely("ZXing") {
                            scanWithZXing(processedBitmap)
                        }
                        if (zxingResults.isNotEmpty()) {
                            results.addAll(zxingResults)
                            Log.d(TAG, "ZXing detected ${zxingResults.size} codes")
                        }
                    }

                    // 3. 尝试 ML Kit
                    if (results.isEmpty() && isActive) {
                        val mlKitResults = runEngineSafely("ML Kit") {
                            scanWithMLKit(processedBitmap)
                        }
                        if (mlKitResults.isNotEmpty()) {
                            results.addAll(mlKitResults)
                            Log.d(TAG, "ML Kit detected ${mlKitResults.size} codes")
                        }
                    }

                    // 4. 尝试 Micro QR Code (BoofCV)
                    if (results.isEmpty() && isActive) {
                        val microQrResults = runEngineSafely("BoofCV Micro QR") {
                            MicroQrCodeScanner.scan(processedBitmap)
                                .map { ScanResult(it.text, Library.BOOFCV, BarcodeFormat.QR_CODE) }
                        }
                        if (microQrResults.isNotEmpty()) {
                            results.addAll(microQrResults)
                            Log.d(TAG, "BoofCV Micro QR detected ${microQrResults.size} codes")
                            return@withContext results
                        }
                    }

                    // 5. 尝试 Han Xin Code
                    if (results.isEmpty() && isActive) {
                        val hanXinResults = runEngineSafely("Han Xin") {
                            HanXinDecoder.decode(processedBitmap)?.let {
                                listOf(ScanResult(it.text, Library.HAN_XIN, BarcodeFormat.QR_CODE))
                            } ?: emptyList()
                        }
                        if (hanXinResults.isNotEmpty()) {
                            results.addAll(hanXinResults)
                            Log.d(TAG, "Han Xin decoder detected 1 code")
                            return@withContext results
                        }
                    }

                    // 6. 尝试自定义一维码解码器（Pharmacode / Plessey / MSI Plessey / Telepen）
                    if (results.isEmpty() && isActive) {
                        val customResults = runEngineSafely("CustomLinear") {
                            CustomLinearBarcodeScanner.scan(processedBitmap)
                                .map { ScanResult(it.text, Library.CUSTOM_LINEAR, it.format.toZXingFormat()) }
                        }
                        if (customResults.isNotEmpty()) {
                            results.addAll(customResults)
                            Log.d(TAG, "Custom linear decoder detected ${customResults.size} codes")
                            return@withContext results
                        }
                    }

                    Log.d(TAG, "Total detected: ${results.size} codes")
                    results
                } finally {
                    if (shouldRecycleProcessed && !processedBitmap.isRecycled) {
                        processedBitmap.recycle()
                    }
                }
                if (QRCodeApp.isWeChatQRCodeInitialized && isActive) {
                    val wechatResults = runEngineSafely("WeChatQRCode") {
                        scanWithWeChatQRCode(processedBitmap)
                    }
                    if (wechatResults.isNotEmpty()) {
                        results.addAll(wechatResults)
                        Log.d(TAG, "WeChatQRCode detected ${wechatResults.size} codes")
                    }
                }

                // 2. 尝试 ZXing
                if (results.isEmpty() && isActive) {
                    val zxingResults = runEngineSafely("ZXing") {
                        scanWithZXing(processedBitmap)
                    }
                    if (zxingResults.isNotEmpty()) {
                        results.addAll(zxingResults)
                        Log.d(TAG, "ZXing detected ${zxingResults.size} codes")
                    }
                }

                // 3. 尝试 ML Kit
                if (results.isEmpty() && isActive) {
                    val mlKitResults = runEngineSafely("ML Kit") {
                        scanWithMLKit(processedBitmap)
                    }
                    if (mlKitResults.isNotEmpty()) {
                        results.addAll(mlKitResults)
                        Log.d(TAG, "ML Kit detected ${mlKitResults.size} codes")
                    }
                }

                // 4. 尝试 Micro QR Code (BoofCV)
                if (results.isEmpty() && isActive) {
                    val microQrResults = runEngineSafely("BoofCV Micro QR") {
                        MicroQrCodeScanner.scan(processedBitmap)
                            .map { ScanResult(it.text, Library.BOOFCV, BarcodeFormat.QR_CODE) }
                    }
                    if (microQrResults.isNotEmpty()) {
                        results.addAll(microQrResults)
                        Log.d(TAG, "BoofCV Micro QR detected ${microQrResults.size} codes")
                        return@withContext results
                    }
                }

                // 5. 尝试 Han Xin Code
                if (results.isEmpty() && isActive) {
                    val hanXinResults = runEngineSafely("Han Xin") {
                        HanXinDecoder.decode(processedBitmap)?.let {
                            listOf(ScanResult(it.text, Library.HAN_XIN, BarcodeFormat.QR_CODE))
                        } ?: emptyList()
                    }
                    if (hanXinResults.isNotEmpty()) {
                        results.addAll(hanXinResults)
                        Log.d(TAG, "Han Xin decoder detected 1 code")
                        return@withContext results
                    }
                }

                // 6. 尝试自定义一维码解码器（Pharmacode / Plessey / MSI Plessey / Telepen）
                if (results.isEmpty() && isActive) {
                    val customResults = runEngineSafely("CustomLinear") {
                        CustomLinearBarcodeScanner.scan(processedBitmap)
                            .map { ScanResult(it.text, Library.CUSTOM_LINEAR, it.format.toZXingFormat()) }
                    }
                    if (customResults.isNotEmpty()) {
                        results.addAll(customResults)
                        Log.d(TAG, "Custom linear decoder detected ${customResults.size} codes")
                        return@withContext results
                    }
                }

                Log.d(TAG, "Total detected: ${results.size} codes")
                results
            }
        } ?: emptyList()
    } catch (e: Throwable) {
        Log.e(TAG, "Scan pipeline crashed", e)
        emptyList()
    }

    /**
     * 使用 WeChatQRCode 扫描
     */
    private fun scanWithWeChatQRCode(bitmap: Bitmap): List<ScanResult> {
        val points = ArrayList<Mat>()
        return try {
            val results = WeChatQRCodeDetector.detectAndDecode(bitmap, points)
            points.forEach { it.release() }
            results.map { ScanResult(it, Library.WECHAT_QR, BarcodeFormat.QR_CODE) }
        } catch (e: Exception) {
            points.forEach { it.release() }
            throw e
        }
    }

    /**
     * 使用 ZXing 扫描 - 优化版本，支持条形码
     */
    private fun scanWithZXing(bitmap: Bitmap): List<ScanResult> {
        // 尝试多种配置
        val allFormats = listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.UPC_EAN_EXTENSION,
            BarcodeFormat.CODABAR,
            BarcodeFormat.ITF,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED,
            BarcodeFormat.MAXICODE
        )
        val linearFormats = listOf(
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.UPC_EAN_EXTENSION,
            BarcodeFormat.CODABAR,
            BarcodeFormat.ITF,
            BarcodeFormat.RSS_14,
            BarcodeFormat.RSS_EXPANDED,
            BarcodeFormat.MAXICODE
        )
        val configs = listOf(
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.POSSIBLE_FORMATS, allFormats)
            },
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.POSSIBLE_FORMATS, linearFormats)
            }
        )

        // 尝试原始图像
        for (config in configs) {
            val result = tryDecode(bitmap, config)
            if (result != null) return listOf(result)
        }

        // 尝试旋转图像（条形码可能在不同方向）
        val rotatedBitmap = rotateBitmap(bitmap, 90f)
        for (config in configs) {
            val result = tryDecode(rotatedBitmap, config)
            if (result != null) {
                rotatedBitmap.recycle()
                return listOf(result)
            }
        }
        rotatedBitmap.recycle()

        return emptyList()
    }

    private fun tryDecode(bitmap: Bitmap, hints: EnumMap<DecodeHintType, Any>): ScanResult? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        val reader = MultiFormatReader()
        reader.setHints(hints)

        return try {
            val result = reader.decode(binaryBitmap)
            ScanResult(result.text, Library.ZXING, result.barcodeFormat, result.resultMetadata)
        } catch (e: Exception) {
            // 尝试全局直方图二值化（对某些图像效果更好）
            try {
                val globalSource = RGBLuminanceSource(width, height, pixels)
                val globalBinaryBitmap = BinaryBitmap(
                    com.google.zxing.common.GlobalHistogramBinarizer(globalSource)
                )
                val globalResult = reader.decode(globalBinaryBitmap)
                ScanResult(globalResult.text, Library.ZXING, globalResult.barcodeFormat)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 使用 ML Kit 扫描
     */
    private suspend fun scanWithMLKit(bitmap: Bitmap): List<ScanResult> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                // 二维码格式
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_PDF417,
                // 一维条码格式
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_ITF
            )
            .build()

        val scanner = BarcodeScanning.getClient(options)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val results = barcodes.mapNotNull { barcode ->
                    barcode.rawValue?.let {
                        ScanResult(it, Library.ML_KIT, mapMlKitFormat(barcode.format))
                    }
                }
                continuation.resume(results)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit processing failed", e)
                continuation.resume(emptyList())
            }
            .addOnCompleteListener {
                scanner.close()
            }

        continuation.invokeOnCancellation {
            scanner.close()
        }
    }

    /**
     * 将 ML Kit 条码格式映射为 ZXing 条码格式
     */
    private fun mapMlKitFormat(mlKitFormat: Int): BarcodeFormat {
        return when (mlKitFormat) {
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
            Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
            Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
            Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
            Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
            Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
            Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
            Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
            Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
            Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
            Barcode.FORMAT_ITF -> BarcodeFormat.ITF
            else -> BarcodeFormat.QR_CODE
        }
    }

    /**
     * 同步扫描（用于非协程环境，如视频扫描）
     * 支持条形码扫描
     */
    fun scanSync(context: Context, bitmap: Bitmap): List<ScanResult> = try {
        runBlocking(Dispatchers.Default) {
            scan(context, bitmap)
        }
    } catch (e: Throwable) {
        Log.e(TAG, "Sync scan pipeline crashed", e)
        emptyList()
    }
}

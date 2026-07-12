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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.opencv.core.Mat
import java.util.ArrayList
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
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
        AppBarcodeFormat.QR_CODE,
        AppBarcodeFormat.SWISS_QR_CODE,
        AppBarcodeFormat.UPN_QR_CODE -> HistoryType.QR_CODE
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
        else -> HistoryType.GENERATED_ONLY
    }
}

/**
 * 判断该格式是否能被当前扫描栈识别。
 */
fun AppBarcodeFormat.isScannable(): Boolean = this.isScannable

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
 * 扫描配置。不同使用场景（图片扫描、相机实时扫描）可配置不同的超时与缩放策略。
 */
data class ScanConfig(
    val totalTimeoutMs: Long,
    val perEngineTimeoutMs: Long,
    val maxDimension: Int
) {
    init {
        require(totalTimeoutMs > 0)
        require(perEngineTimeoutMs > 0)
        require(maxDimension > 0)
    }
}

/**
 * 多库二维码扫描管理器
 * 6 个引擎并行执行，任一返回结果即可通过 Flow emit；也可等待全部结束后统一返回。
 */
object QRCodeScanner {

    private const val TAG = "QRCodeScanner"

    /**
     * 图片扫描配置：大图识别通常需要更长时间，因此总超时和单引擎超时都较长。
     */
    val IMAGE_SCAN_CONFIG = ScanConfig(
        totalTimeoutMs = 120_000L,
        perEngineTimeoutMs = 60_000L,
        maxDimension = 1280
    )

    /**
     * 相机/视频实时扫描配置：帧率高，单帧不能占用太久。
     */
    val CAMERA_SCAN_CONFIG = ScanConfig(
        totalTimeoutMs = 15_000L,
        perEngineTimeoutMs = 5_000L,
        maxDimension = 1280
    )

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
    private fun preprocessBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = maxOf(width, height)
        if (maxDim <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxDim
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
        timeoutMs: Long,
        block: suspend () -> List<T>
    ): List<T> = try {
        withTimeoutOrNull(timeoutMs) {
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
    ) {
        /**
         * 去重键：相同内容 + 相同格式视为同一条码，保留最先识别到的引擎标签。
         */
        val deduplicationKey: String
            get() = "$text#${format.name}"
    }

    enum class Library {
        WECHAT_QR,
        ZXING,
        ML_KIT,
        BOOFCV,
        HAN_XIN,
        CUSTOM_LINEAR
    }

    /**
     * 引擎事件：Result 表示一批识别结果，Completed 表示该引擎已结束。
     */
    private sealed class EngineEvent {
        data class Result(val results: List<ScanResult>) : EngineEvent()
        object Completed : EngineEvent()
    }

    /**
     * 并行扫描位图中的所有条码，以 Flow 形式逐步返回结果。
     * 6 个引擎同时启动，任一引擎识别到结果即 emit 一次；
     * 后续引擎返回的新结果（按 text+format 去重）会继续追加 emit。
     *
     * @param config 扫描配置，图片扫描建议使用 [IMAGE_SCAN_CONFIG]，相机扫描使用 [CAMERA_SCAN_CONFIG]。
     */
    fun scanAsFlow(
        context: Context,
        bitmap: Bitmap,
        config: ScanConfig = IMAGE_SCAN_CONFIG
    ): Flow<List<ScanResult>> = channelFlow {
        if (!scanningEnabled.get()) {
            Log.w(TAG, "Scanning is disabled")
            send(emptyList())
            return@channelFlow
        }

        val processedBitmap = preprocessBitmap(bitmap, config.maxDimension)
        val shouldRecycleProcessed = processedBitmap !== bitmap

        // 使用独立的线程池启动引擎，避免阻塞型/不响应取消的引擎占满 Dispatchers.Default 或 Dispatchers.IO。
        // 线程池里的线程设为 daemon，即使引擎 ignore 取消，也不会阻止 JVM/进程退出。
        val engineExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        ) { runnable ->
            Thread(runnable, "QRCodeScanner-engine").apply { isDaemon = true }
        }
        val scope = CoroutineScope(engineExecutor.asCoroutineDispatcher() + SupervisorJob())
        val eventChannel = Channel<EngineEvent>(Channel.UNLIMITED)
        val engineJobs = mutableListOf<Job>()
        var allCompleted = false
        var engineCount = 0

        fun launchEngine(name: String, block: suspend () -> List<ScanResult>) {
            engineJobs += scope.launch {
                val start = System.currentTimeMillis()
                Log.d(TAG, "Engine start: $name")
                try {
                    val results = runEngineSafely(name, config.perEngineTimeoutMs, block)
                    Log.d(TAG, "Engine end: $name, ${System.currentTimeMillis() - start}ms, results=${results.size}")
                    eventChannel.trySend(EngineEvent.Result(results))
                } catch (e: CancellationException) {
                    Log.d(TAG, "Engine cancelled: $name, ${System.currentTimeMillis() - start}ms")
                } finally {
                    Log.d(TAG, "Engine completed event: $name")
                    eventChannel.trySend(EngineEvent.Completed)
                }
            }
        }

        try {
            Log.d(TAG, "Starting scanAsFlow with engines: WeChatQRCode=${QRCodeApp.isWeChatQRCodeInitialized}, ZXing, ML Kit, BoofCV, HanXin, CustomLinear")
            // 1. WeChatQRCode
            if (QRCodeApp.isWeChatQRCodeInitialized) {
                launchEngine("WeChatQRCode") { scanWithWeChatQRCode(processedBitmap) }
            }

            // 2. ZXing
            launchEngine("ZXing") { scanWithZXing(processedBitmap) }

            // 3. ML Kit
            launchEngine("ML Kit") { scanWithMLKit(processedBitmap) }

            // 4. BoofCV Micro QR
            launchEngine("BoofCV Micro QR") {
                MicroQrCodeScanner.scan(processedBitmap)
                    .map { ScanResult(it.text, Library.BOOFCV, BarcodeFormat.QR_CODE) }
            }

            // 5. Han Xin Code
            launchEngine("Han Xin") {
                HanXinDecoder.decode(processedBitmap)?.let {
                    listOf(ScanResult(it.text, Library.HAN_XIN, BarcodeFormat.QR_CODE))
                } ?: emptyList()
            }

            // 6. 自定义一维码
            launchEngine("CustomLinear") {
                CustomLinearBarcodeScanner.scan(processedBitmap)
                    .map { ScanResult(it.text, Library.CUSTOM_LINEAR, it.format.toZXingFormat()) }
            }

            engineCount = engineJobs.size

            withTimeoutOrNull(config.totalTimeoutMs) {
                val seenKeys = mutableSetOf<String>()
                var completedCount = 0
                while (completedCount < engineCount) {
                    when (val event = eventChannel.receiveCatching().getOrNull()) {
                        is EngineEvent.Result -> {
                            val newResults = event.results.filter { seenKeys.add(it.deduplicationKey) }
                            if (newResults.isNotEmpty()) {
                                send(newResults)
                            }
                        }
                        is EngineEvent.Completed -> {
                            Log.d(TAG, "Engine completed received: ${completedCount + 1}/$engineCount")
                            completedCount++
                        }
                        null -> break
                    }
                }
                allCompleted = true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Scan flow crashed", e)
        } finally {
            if (!allCompleted) {
                Log.d(TAG, "Total timeout reached, abandoning $engineCount engines")
            } else {
                Log.d(TAG, "scanAsFlow completed normally")
            }
            eventChannel.close()
            scope.cancel()
            engineExecutor.shutdownNow()
            if (allCompleted && shouldRecycleProcessed && !processedBitmap.isRecycled) {
                processedBitmap.recycle()
            }
        }
    }

    /**
     * 并行扫描，等待所有引擎结束后返回完整结果列表（已去重）。
     * 主要用于相机/视频实时扫描，保持原有调用方语义不变。
     */
    suspend fun scan(context: Context, bitmap: Bitmap): List<ScanResult> {
        val allResults = mutableListOf<ScanResult>()
        val seenKeys = mutableSetOf<String>()
        scanAsFlow(context, bitmap, CAMERA_SCAN_CONFIG).collect { batch ->
            allResults.addAll(batch.filter { seenKeys.add(it.deduplicationKey) })
        }
        return allResults
    }

    /**
     * 同步扫描（用于非协程环境，如视频扫描）。
     * 内部并行执行，但等待全部引擎结束后返回完整列表，保持调用方语义不变。
     */
    fun scanSync(context: Context, bitmap: Bitmap): List<ScanResult> = try {
        runBlocking {
            scan(context, bitmap)
        }
    } catch (e: Throwable) {
        Log.e(TAG, "Sync scan pipeline crashed", e)
        emptyList()
    }

    /**
     * 使用 WeChatQRCode 扫描
     */
    private fun scanWithWeChatQRCode(bitmap: Bitmap): List<ScanResult> {
        val start = System.currentTimeMillis()
        val points = ArrayList<Mat>()
        return try {
            val results = WeChatQRCodeDetector.detectAndDecode(bitmap, points)
            points.forEach { it.release() }
            results.map { ScanResult(it, Library.WECHAT_QR, BarcodeFormat.QR_CODE) }
        } catch (e: Exception) {
            points.forEach { it.release() }
            throw e
        }.also {
            Log.d(TAG, "WeChatQRCode engine finished in ${System.currentTimeMillis() - start}ms, results=${it.size}")
        }
    }

    /**
     * 使用 ZXing 扫描 - 优化版本，支持条形码
     */
    private fun scanWithZXing(bitmap: Bitmap): List<ScanResult> {
        val start = System.currentTimeMillis()
        Log.d(TAG, "ZXing engine started")
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
            if (result != null) {
                Log.d(TAG, "ZXing engine finished in ${System.currentTimeMillis() - start}ms, results=1")
                return listOf(result)
            }
        }

        // 尝试旋转图像（条形码可能在不同方向）
        Log.d(TAG, "ZXing engine trying rotated image")
        val rotatedBitmap = rotateBitmap(bitmap, 90f)
        for (config in configs) {
            val result = tryDecode(rotatedBitmap, config)
            if (result != null) {
                rotatedBitmap.recycle()
                Log.d(TAG, "ZXing engine finished in ${System.currentTimeMillis() - start}ms, results=1")
                return listOf(result)
            }
        }
        rotatedBitmap.recycle()

        Log.d(TAG, "ZXing engine finished in ${System.currentTimeMillis() - start}ms, results=0")
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
        val start = System.currentTimeMillis()
        Log.d(TAG, "ML Kit engine started")
        val image = InputImage.fromBitmap(bitmap, 0)
        val resumed = AtomicBoolean(false)

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
                Log.d(TAG, "ML Kit engine finished in ${System.currentTimeMillis() - start}ms, results=${results.size}")
                if (resumed.compareAndSet(false, true)) {
                    try {
                        continuation.resume(results)
                    } catch (e: IllegalStateException) {
                        // already cancelled or resumed; ignore
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "ML Kit processing failed", e)
                Log.d(TAG, "ML Kit engine finished in ${System.currentTimeMillis() - start}ms, results=0")
                if (resumed.compareAndSet(false, true)) {
                    try {
                        continuation.resume(emptyList())
                    } catch (e: IllegalStateException) {
                        // already cancelled or resumed; ignore
                    }
                }
            }
            .addOnCompleteListener {
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
}

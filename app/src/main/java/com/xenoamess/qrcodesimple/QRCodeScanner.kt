package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.king.wechat.qrcode.WeChatQRCodeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.opencv.core.Mat
import java.util.ArrayList
import java.util.EnumMap
import kotlin.coroutines.resume

/**
 * 多库二维码扫描管理器
 * 按优先级顺序尝试：WeChatQRCode -> ZXing -> ML Kit
 */
object QRCodeScanner {
    
    private const val TAG = "QRCodeScanner"
    
    data class ScanResult(
        val text: String,
        val library: Library
    )
    
    enum class Library {
        WECHAT_QR,
        ZXING,
        ML_KIT
    }
    
    /**
     * 扫描位图中的所有二维码
     * 按顺序尝试各个库，直到获得结果
     */
    suspend fun scan(context: Context, bitmap: Bitmap): List<ScanResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<ScanResult>()
        
        // 1. 尝试 WeChatQRCode
        if (QRCodeApp.isWeChatQRCodeInitialized) {
            try {
                val wechatResults = scanWithWeChatQRCode(bitmap)
                if (wechatResults.isNotEmpty()) {
                    results.addAll(wechatResults.map { ScanResult(it, Library.WECHAT_QR) })
                    Log.d(TAG, "WeChatQRCode detected ${wechatResults.size} codes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "WeChatQRCode scan failed", e)
            }
        }
        
        // 2. 尝试 ZXing
        if (results.isEmpty()) {
            try {
                val zxingResults = scanWithZXing(bitmap)
                if (zxingResults.isNotEmpty()) {
                    results.addAll(zxingResults.map { ScanResult(it, Library.ZXING) })
                    Log.d(TAG, "ZXing detected ${zxingResults.size} codes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "ZXing scan failed", e)
            }
        }
        
        // 3. 尝试 ML Kit
        if (results.isEmpty()) {
            try {
                val mlKitResults = scanWithMLKit(bitmap)
                if (mlKitResults.isNotEmpty()) {
                    results.addAll(mlKitResults.map { ScanResult(it, Library.ML_KIT) })
                    Log.d(TAG, "ML Kit detected ${mlKitResults.size} codes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit scan failed", e)
            }
        }
        
        Log.d(TAG, "Total detected: ${results.size} codes")
        results
    }
    
    /**
     * 使用 WeChatQRCode 扫描
     */
    private fun scanWithWeChatQRCode(bitmap: Bitmap): List<String> {
        val points = ArrayList<Mat>()
        return try {
            val results = WeChatQRCodeDetector.detectAndDecode(bitmap, points)
            points.forEach { it.release() }
            results
        } catch (e: Exception) {
            points.forEach { it.release() }
            throw e
        }
    }
    
    /**
     * 使用 ZXing 扫描 - 优化版本，支持条形码
     */
    private fun scanWithZXing(bitmap: Bitmap): List<String> {
        // 尝试多种配置
        val configs = listOf(
            // 配置1: 所有格式
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    com.google.zxing.BarcodeFormat.DATA_MATRIX,
                    com.google.zxing.BarcodeFormat.AZTEC,
                    com.google.zxing.BarcodeFormat.PDF_417,
                    com.google.zxing.BarcodeFormat.CODE_128,
                    com.google.zxing.BarcodeFormat.CODE_39,
                    com.google.zxing.BarcodeFormat.CODE_93,
                    com.google.zxing.BarcodeFormat.EAN_13,
                    com.google.zxing.BarcodeFormat.EAN_8,
                    com.google.zxing.BarcodeFormat.UPC_A,
                    com.google.zxing.BarcodeFormat.UPC_E,
                    com.google.zxing.BarcodeFormat.CODABAR,
                    com.google.zxing.BarcodeFormat.ITF
                ))
            },
            // 配置2: 仅一维条码（更宽松）
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.TRY_HARDER, true)
                put(DecodeHintType.POSSIBLE_FORMATS, listOf(
                    com.google.zxing.BarcodeFormat.CODE_128,
                    com.google.zxing.BarcodeFormat.CODE_39,
                    com.google.zxing.BarcodeFormat.EAN_13,
                    com.google.zxing.BarcodeFormat.UPC_A
                ))
            }
        )
        
        // 尝试原始图像
        for (config in configs) {
            val result = tryDecode(bitmap, config)
            if (result.isNotEmpty()) return result
        }
        
        // 尝试旋转图像（条形码可能在不同方向）
        val rotatedBitmap = rotateBitmap(bitmap, 90f)
        for (config in configs) {
            val result = tryDecode(rotatedBitmap, config)
            if (result.isNotEmpty()) {
                rotatedBitmap.recycle()
                return result
            }
        }
        rotatedBitmap.recycle()
        
        return emptyList()
    }
    
    private fun tryDecode(bitmap: Bitmap, hints: EnumMap<DecodeHintType, Any>): List<String> {
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
            listOf(result.text)
        } catch (e: Exception) {
            // 尝试全局直方图二值化（对某些图像效果更好）
            try {
                val globalSource = RGBLuminanceSource(width, height, pixels)
                val globalBinaryBitmap = BinaryBitmap(
                    com.google.zxing.common.GlobalHistogramBinarizer(globalSource)
                )
                val globalResult = reader.decode(globalBinaryBitmap)
                listOf(globalResult.text)
            } catch (e2: Exception) {
                emptyList()
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
    private suspend fun scanWithMLKit(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { continuation ->
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
                val results = barcodes.mapNotNull { it.rawValue }
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
     * 同步扫描（用于非协程环境，如视频扫描）
     * 支持条形码扫描
     */
    fun scanSync(context: Context, bitmap: Bitmap): List<String> {
        // 1. 尝试 WeChatQRCode（只支持二维码）
        if (QRCodeApp.isWeChatQRCodeInitialized) {
            try {
                val results = scanWithWeChatQRCode(bitmap)
                if (results.isNotEmpty()) {
                    Log.d(TAG, "WeChatQRCode detected ${results.size} codes")
                    return results
                }
            } catch (e: Exception) {
                Log.e(TAG, "WeChatQRCode scan failed", e)
            }
        }
        
        // 2. 尝试 ZXing（支持二维码和条形码）
        try {
            val results = scanWithZXing(bitmap)
            if (results.isNotEmpty()) {
                Log.d(TAG, "ZXing detected ${results.size} codes")
                return results
            }
        } catch (e: Exception) {
            Log.e(TAG, "ZXing scan failed", e)
        }
        
        // 3. 尝试 ML Kit（支持二维码和条形码）
        // 使用 runBlocking 在同步环境中调用异步 API
        try {
            val results = runBlocking(Dispatchers.Default) {
                withTimeoutOrNull(5000) {
                    scanWithMLKit(bitmap)
                } ?: emptyList()
            }
            if (results.isNotEmpty()) {
                Log.d(TAG, "ML Kit detected ${results.size} codes")
                return results
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit scan failed", e)
        }
        
        Log.d(TAG, "No codes detected by any library")
        return emptyList()
    }
}
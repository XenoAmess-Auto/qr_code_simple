package com.xenoamess.qrcodesimple.scanner

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.xenoamess.qrcodesimple.QRCodeScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * ML Kit 扫描引擎。F-Droid 构建（-Pfdroid）用同包同名 stub 替换此文件，
 * 使 proprietary ML Kit 依赖完全不出现在 F-Droid 产物中。
 */
internal object MlKitEngine {

    private const val TAG = "QRCodeScanner"

    suspend fun scan(bitmap: Bitmap): List<QRCodeScanner.ScanResult> = suspendCancellableCoroutine { continuation ->
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
                        QRCodeScanner.ScanResult(it, QRCodeScanner.Library.ML_KIT, mapMlKitFormat(barcode.format))
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

package com.xenoamess.qrcodesimple.utils.test

import com.google.zxing.BarcodeFormat
import com.xenoamess.qrcodesimple.ContinuousScanActivity
import com.xenoamess.qrcodesimple.QRCodeScanner
import com.xenoamess.qrcodesimple.data.HistoryItem
import com.xenoamess.qrcodesimple.data.HistoryType
import com.xenoamess.qrcodesimple.ui.result.QRResult

/**
 * 测试用假数据工厂，统一构造 UI/Adapter 测试所需的数据对象。
 */
object TestDataFactory {

    private var nextId = 1L

    fun historyItem(
        id: Long = nextId++,
        content: String = "https://example.com",
        type: HistoryType = HistoryType.QR_CODE,
        timestamp: Long = System.currentTimeMillis(),
        isGenerated: Boolean = false,
        barcodeFormat: String? = null,
        styleJson: String? = null,
        isFavorite: Boolean = false,
        notes: String? = null,
        tags: String? = null
    ): HistoryItem = HistoryItem(
        id = id,
        content = content,
        type = type,
        timestamp = timestamp,
        isGenerated = isGenerated,
        barcodeFormat = barcodeFormat,
        styleJson = styleJson,
        isFavorite = isFavorite,
        notes = notes,
        tags = tags
    )

    fun qrScannerResult(
        text: String = "https://example.com",
        library: QRCodeScanner.Library = QRCodeScanner.Library.ZXING,
        format: BarcodeFormat = BarcodeFormat.QR_CODE,
        resultMetadata: Map<com.google.zxing.ResultMetadataType, Any>? = null
    ): QRCodeScanner.ScanResult = QRCodeScanner.ScanResult(
        text = text,
        library = library,
        format = format,
        resultMetadata = resultMetadata
    )

    fun qrResult(
        text: String = "https://example.com",
        isSelected: Boolean = false,
        library: QRCodeScanner.Library? = QRCodeScanner.Library.ZXING,
        format: BarcodeFormat = BarcodeFormat.QR_CODE
    ): QRResult = QRResult(
        text = text,
        isSelected = isSelected,
        library = library,
        format = format
    )

    fun continuousScanResult(
        content: String = "https://example.com",
        type: HistoryType = HistoryType.QR_CODE,
        timestamp: Long = System.currentTimeMillis(),
        isSaved: Boolean = false
    ): ContinuousScanActivity.ScanResult = ContinuousScanActivity.ScanResult(
        content = content,
        type = type,
        timestamp = timestamp,
        isSaved = isSaved
    )

    fun urlQrResult(url: String = "https://example.com") = qrResult(text = url)

    fun textQrResult(text: String = "plain text") = qrResult(
        text = text,
        library = null
    )

    fun wifiQrResult(
        ssid: String = "MyWiFi",
        password: String = "secret",
        encryption: String = "WPA"
    ): QRResult = qrResult(
        text = "WIFI:S:$ssid;T:$encryption;P:$password;;"
    )

    fun phoneQrResult(number: String = "1234567890"): QRResult =
        qrResult(text = "tel:$number")
}

package com.xenoamess.qrcodesimple.data

/**
 * 条码格式枚举
 *
 * 覆盖 ZXing/ML Kit/BoofCV/OkapiBarcode/HanXinDecoder 支持的全部格式。
 * [isScannable] 表示当前扫描栈（ZXing/ML Kit/BoofCV/WeChatQR/HanXin/自定义解码器）能否识别该格式。
 * 只能生成、无法扫描的格式会在生成页面提示用户。
 */
enum class BarcodeFormat(
    val displayName: String,
    val isScannable: Boolean = false
) {
    // ==================== 2D 矩阵码 ====================
    QR_CODE("QR Code", isScannable = true),
    DATA_MATRIX("Data Matrix", isScannable = true),
    AZTEC("Aztec", isScannable = true),
    PDF417("PDF417", isScannable = true),
    MAXICODE("MaxiCode", isScannable = true),
    MICRO_QR("Micro QR", isScannable = true),
    HAN_XIN("Han Xin", isScannable = true),

    // QR / 2D 变体（底层仍是 QR 或 2D 矩阵，但应用层面作为独立格式暴露）
    SWISS_QR_CODE("Swiss QR Code", isScannable = true),
    UPN_QR_CODE("UPN QR Code", isScannable = true),
    AZTEC_RUNE("Aztec Rune", isScannable = false),
    CODE_ONE("Code One", isScannable = false),
    GRID_MATRIX("Grid Matrix", isScannable = false),

    // ==================== 1D / 线性条码 ====================
    CODE_128("Code 128", isScannable = true),
    CODE_39("Code 39", isScannable = true),
    CODE_39_EXTENDED("Code 39 Extended", isScannable = false),
    CODE_93("Code 93", isScannable = true),

    EAN_13("EAN-13", isScannable = true),
    EAN_8("EAN-8", isScannable = true),
    UPC_A("UPC-A", isScannable = true),
    UPC_E("UPC-E", isScannable = true),
    EAN_UPC_ADD_ON("EAN/UPC Add-On", isScannable = false),

    CODABAR("Codabar", isScannable = true),

    ITF("ITF", isScannable = true),
    ITF_14("ITF-14", isScannable = false),
    CODE_2_OF_5_STANDARD("Code 2 of 5 Standard", isScannable = false),
    CODE_2_OF_5_MATRIX("Code 2 of 5 Matrix", isScannable = false),
    CODE_2_OF_5_INDUSTRIAL("Code 2 of 5 Industrial", isScannable = false),
    CODE_2_OF_5_IATA("Code 2 of 5 IATA", isScannable = false),
    CODE_2_OF_5_DATALOGIC("Code 2 of 5 Datalogic", isScannable = false),
    CODE_2_OF_5_DEUTSCHE_POST_LEITCODE("Code 2 of 5 Deutsche Post Leitcode", isScannable = false),
    CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE("Code 2 of 5 Deutsche Post Identcode", isScannable = false),

    CODE_11("Code 11", isScannable = false),
    CODE_16K("Code 16K", isScannable = false),
    CODE_32("Code 32", isScannable = false),
    CODE_49("Code 49", isScannable = false),
    CODABLOCK_F("Codablock F", isScannable = false),
    CHANNEL_CODE("Channel Code", isScannable = false),
    LOGMARS("LOGMARS", isScannable = false),
    NVE_18("NVE-18", isScannable = false),
    DPD_CODE("DPD Code", isScannable = false),

    PHARMACODE("Pharmacode", isScannable = true),
    PHARMACODE_2_TRACK("Pharmacode Two-Track", isScannable = false),
    PHARMAZENTRALNUMMER("Pharmazentralnummer", isScannable = false),
    PLESSEY("Plessey", isScannable = true),
    MSI_PLESSEY("MSI Plessey", isScannable = true),
    TELEPEN("Telepen", isScannable = true),
    TELEPEN_NUMERIC("Telepen Numeric", isScannable = false),

    // 邮政码
    POSTNET("Postnet", isScannable = false),
    ROYAL_MAIL_4_STATE("Royal Mail 4-State", isScannable = false),
    USPS_ONE_CODE("USPS OneCode", isScannable = false),
    USPS_PACKAGE("USPS Package", isScannable = false),
    JAPAN_POST("Japan Post", isScannable = false),
    KIX_CODE("KIX Code", isScannable = false),
    KOREA_POST("Korea Post", isScannable = false),
    AUSTRALIA_POST("Australia Post", isScannable = false),

    // GS1 DataBar
    RSS_14("RSS-14 / GS1 DataBar", isScannable = true),
    RSS_EXPANDED("RSS Expanded", isScannable = true),
    DATA_BAR_LIMITED("GS1 DataBar Limited", isScannable = false),
    COMPOSITE("Composite", isScannable = false),

    UPC_EAN_EXTENSION("UPC/EAN Extension", isScannable = true),

    UNKNOWN("Unknown", isScannable = false);

    companion object {
        fun fromString(format: String): BarcodeFormat {
            return try {
                valueOf(format.uppercase())
            } catch (e: Exception) {
                UNKNOWN
            }
        }
    }
}

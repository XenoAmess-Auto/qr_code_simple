package com.xenoamess.qrcodesimple.generator

import com.xenoamess.qrcodesimple.data.BarcodeFormat

/**
 * 为单元测试提供每种条码格式的合法测试内容。
 */
object BarcodeFormatTestFixtures {

    fun validContent(format: BarcodeFormat): String = when (format) {
        BarcodeFormat.QR_CODE -> "https://example.com"
        BarcodeFormat.DATA_MATRIX -> "Hello DM"
        BarcodeFormat.AZTEC -> "Aztec Test"
        BarcodeFormat.PDF417 -> "PDF417 Test"
        BarcodeFormat.CODE_128 -> "CODE128-TEST"
        BarcodeFormat.CODE_39 -> "ABC-123"
        BarcodeFormat.CODE_39_EXTENDED -> "ABC-123-extended"
        BarcodeFormat.CODE_93 -> "CODE93-TEST"
        BarcodeFormat.EAN_13 -> "1234567890128"
        BarcodeFormat.EAN_8 -> "12345670"
        BarcodeFormat.UPC_A -> "123456789012"
        BarcodeFormat.UPC_E -> "01234565"
        BarcodeFormat.CODABAR -> "12345"
        BarcodeFormat.ITF -> "1234567890"
        BarcodeFormat.ITF_14 -> "1234567890123"
        BarcodeFormat.CODE_2_OF_5_STANDARD -> "1234567890"
        BarcodeFormat.CODE_2_OF_5_MATRIX -> "1234567890"
        BarcodeFormat.CODE_2_OF_5_INDUSTRIAL -> "1234567890"
        BarcodeFormat.CODE_2_OF_5_IATA -> "1234567890"
        BarcodeFormat.CODE_2_OF_5_DATALOGIC -> "1234567890"
        BarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_LEITCODE -> "1234567890123"
        BarcodeFormat.CODE_2_OF_5_DEUTSCHE_POST_IDENTCODE -> "12345678901"
        BarcodeFormat.UPC_EAN_EXTENSION -> "12"
        BarcodeFormat.RSS_14 -> "1234567890123"
        BarcodeFormat.RSS_EXPANDED -> "(01)12345678901231"
        BarcodeFormat.MAXICODE -> "[)>>\u001E01\u001D961Z00004952\u001DUPSN\u001D"
        BarcodeFormat.MICRO_QR -> "MicroQR"
        BarcodeFormat.PHARMACODE -> "1234"
        BarcodeFormat.PLESSEY -> "1A2B"
        BarcodeFormat.MSI_PLESSEY -> "12345"
        BarcodeFormat.TELEPEN -> "TELEPEN"
        BarcodeFormat.TELEPEN_NUMERIC -> "12345"
        BarcodeFormat.HAN_XIN -> "汉信码"
        BarcodeFormat.SWISS_QR_CODE -> "TEST123"
        BarcodeFormat.UPN_QR_CODE -> "TEST123"
        BarcodeFormat.AZTEC_RUNE -> "100"
        BarcodeFormat.CODE_ONE -> "12345"
        BarcodeFormat.GRID_MATRIX -> "格矩阵测试"
        BarcodeFormat.CODE_11 -> "123-45"
        BarcodeFormat.CODE_16K -> "CODE16K-TEST"
        BarcodeFormat.CODE_32 -> "12345678"
        BarcodeFormat.CODE_49 -> "CODE49-TEST"
        BarcodeFormat.CODABLOCK_F -> "CODABLOCKF-TEST"
        BarcodeFormat.CHANNEL_CODE -> "123"
        BarcodeFormat.LOGMARS -> "LOGMARS-TEST"
        BarcodeFormat.NVE_18 -> "12345678901234567"
        BarcodeFormat.DPD_CODE -> "ABCDEFGHIJK1234567890123456"
        BarcodeFormat.PHARMACODE_2_TRACK -> "1234"
        BarcodeFormat.PHARMAZENTRALNUMMER -> "1234567"
        BarcodeFormat.POSTNET -> "12345"
        BarcodeFormat.ROYAL_MAIL_4_STATE -> "AB123"
        BarcodeFormat.USPS_ONE_CODE -> "12345678901234567890"
        BarcodeFormat.USPS_PACKAGE -> "[420]90210"
        BarcodeFormat.JAPAN_POST -> "123-4567"
        BarcodeFormat.KIX_CODE -> "1234AB"
        BarcodeFormat.KOREA_POST -> "12345"
        BarcodeFormat.AUSTRALIA_POST -> "12345678"
        BarcodeFormat.DATA_BAR_LIMITED -> "12345678901"
        BarcodeFormat.COMPOSITE -> "[01]12345678901231"
        BarcodeFormat.EAN_UPC_ADD_ON -> "12345"
        BarcodeFormat.UNKNOWN -> "UNKNOWN"
    }

    fun expectedRoundtripText(format: BarcodeFormat, content: String): String = when (format) {
        BarcodeFormat.RSS_14 -> content + "1"
        else -> content
    }
}

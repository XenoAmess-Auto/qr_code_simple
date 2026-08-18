package com.xenoamess.qrcodesimple.data

/** All history-list filters, which Room applies together in a single query. */
data class HistoryQuery(
    val search: String = "",
    val tag: String? = null,
    val isGenerated: Boolean? = null,
    val favoritesOnly: Boolean = false,
    val type: HistoryType? = null,
    val barcodeFormat: String? = null,
    val startTime: Long? = null,
    val newestFirst: Boolean = true
)

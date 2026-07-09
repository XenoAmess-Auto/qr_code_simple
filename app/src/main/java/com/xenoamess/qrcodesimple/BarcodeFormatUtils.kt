package com.xenoamess.qrcodesimple

import android.content.Context
import com.xenoamess.qrcodesimple.data.BarcodeFormat

fun BarcodeFormat.localizedName(context: Context): String {
    val key = "format_${name.lowercase()}"
    val resId = context.resources.getIdentifier(key, "string", context.packageName)
    return if (resId != 0) {
        context.getString(resId).ifEmpty { displayName }
    } else {
        displayName
    }
}

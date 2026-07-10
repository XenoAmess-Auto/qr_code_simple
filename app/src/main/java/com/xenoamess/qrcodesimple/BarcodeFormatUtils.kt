package com.xenoamess.qrcodesimple

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
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

fun BarcodeFormat.localizedNameWithEnglish(context: Context): CharSequence {
    val localized = localizedName(context)
    if (isEnglishLocale(context) || localized == displayName) {
        return localized
    }
    return SpannableStringBuilder().apply {
        append(localized)
        append("\n")
        val start = length
        append(displayName)
        setSpan(RelativeSizeSpan(0.75f), start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(Color.GRAY), start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun isEnglishLocale(context: Context): Boolean {
    val locale = context.resources.configuration.locales.get(0)
        ?: context.resources.configuration.locale
    return locale.language.equals("en", ignoreCase = true)
}

package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 语言/区域设置辅助类
 */
object LocaleHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_FOLLOW_SYSTEM = "follow_system"

    val SUPPORTED_LANGUAGES = listOf(
        Language("system", "跟随系统 / System default"),
        Language("en", "English"),
        Language("zh", "中文"),
        Language("ja", "日本語"),
        Language("ko", "한국어"),
        Language("de", "Deutsch")
    )

    data class Language(val code: String, val displayName: String)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANGUAGE, "system") ?: "system"
    }

    fun isFollowSystem(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FOLLOW_SYSTEM, true)
    }

    fun setLanguage(context: Context, languageCode: String) {
        getPrefs(context).edit().apply {
            putString(KEY_LANGUAGE, languageCode)
            putBoolean(KEY_FOLLOW_SYSTEM, languageCode == "system")
            apply()
        }
    }

    /**
     * 返回应用了语言设置的新 Context。
     * 推荐在 Activity/Application 的 attachBaseContext 中使用。
     */
    fun applyLanguage(context: Context): Context {
        val languageCode = getLanguage(context)
        val locale = if (languageCode == "system") {
            getSystemLocale(context)
        } else {
            Locale(languageCode)
        }
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = if (languageCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.create(locale)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }

        return context.createConfigurationContext(configuration)
    }

    private fun getSystemLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0) ?: Locale.getDefault()
        } else {
            Locale.getDefault()
        }
    }

    fun getCurrentLanguageDisplayName(context: Context): String {
        val code = getLanguage(context)
        return SUPPORTED_LANGUAGES.find { it.code == code }?.displayName ?: "System default"
    }
}

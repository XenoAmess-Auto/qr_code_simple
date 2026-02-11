package com.xenoamess.qrcodesimple

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_FOLLOW_SYSTEM = "follow_system"

    // 支持的语言列表
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

    /**
     * 获取当前设置的语言代码
     */
    fun getLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANGUAGE, "system") ?: "system"
    }

    /**
     * 是否跟随系统语言
     */
    fun isFollowSystem(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FOLLOW_SYSTEM, true)
    }

    /**
     * 设置语言
     * @param languageCode 语言代码 ("system", "en", "zh")
     */
    fun setLanguage(context: Context, languageCode: String) {
        getPrefs(context).edit().apply {
            putString(KEY_LANGUAGE, languageCode)
            putBoolean(KEY_FOLLOW_SYSTEM, languageCode == "system")
            apply()
        }
    }

    /**
     * 应用语言设置
     */
    fun applyLanguage(context: Context) {
        val languageCode = getLanguage(context)
        val locale = if (languageCode == "system") {
            getSystemLocale()
        } else {
            Locale(languageCode)
        }

        updateResources(context, locale)
    }

    /**
     * 获取应用 Context（已应用语言设置）
     */
    fun getLocalizedContext(context: Context): Context {
        val languageCode = getLanguage(context)
        val locale = if (languageCode == "system") {
            getSystemLocale()
        } else {
            Locale(languageCode)
        }
        return updateResources(context, locale)
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.getDefault().get(0) ?: Locale.getDefault()
        } else {
            Locale.getDefault()
        }
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            configuration.setLocales(LocaleList(locale))
            context.createConfigurationContext(configuration)
        } else {
            configuration.locale = locale
            resources.updateConfiguration(configuration, resources.displayMetrics)
            context
        }
    }

    /**
     * 设置应用级别的语言（用于 Android 13+ per-app language）
     */
    fun setApplicationLocale(languageCode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = if (languageCode == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageCode)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    /**
     * 获取当前语言显示名称
     */
    fun getCurrentLanguageDisplayName(context: Context): String {
        val code = getLanguage(context)
        return SUPPORTED_LANGUAGES.find { it.code == code }?.displayName ?: "System default"
    }
}

package com.almi.ai.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Singleton
class AlmiPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(readLanguage(preferences))
    val language: StateFlow<String> = _language.asStateFlow()

    private val _themeMode = MutableStateFlow(readTheme(preferences))
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _apiKey = MutableStateFlow(preferences.getString(KEY_API_KEY, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setLanguage(language: String) {
        val normalized = if (language.equals("en", ignoreCase = true)) "en" else "ar"
        preferences.edit().putString(KEY_LANGUAGE, normalized).apply()
        _language.value = normalized
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
    }

    fun setThemeMode(mode: AppThemeMode) {
        preferences.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setApiKey(value: String) {
        val normalized = value.trim()
        preferences.edit().putString(KEY_API_KEY, normalized).apply()
        _apiKey.value = normalized
    }

    fun clearApiKey() = setApiKey("")

    fun currentApiKey(): String = preferences.getString(KEY_API_KEY, "").orEmpty().trim()

    companion object {
        private const val PREFERENCES_NAME = "almi_ai_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_API_KEY = "openrouter_api_key"

        fun applyStoredLanguage(context: Context) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val language = readLanguage(preferences)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }

        private fun readLanguage(preferences: android.content.SharedPreferences): String =
            preferences.getString(KEY_LANGUAGE, "ar")
                ?.takeIf { it == "ar" || it == "en" }
                ?: "ar"

        private fun readTheme(preferences: android.content.SharedPreferences): AppThemeMode =
            runCatching {
                AppThemeMode.valueOf(preferences.getString(KEY_THEME, AppThemeMode.SYSTEM.name).orEmpty())
            }.getOrDefault(AppThemeMode.SYSTEM)
    }
}

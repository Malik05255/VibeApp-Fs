package com.vibe.app.data.preferences

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class LanguageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    private val _language =
        MutableStateFlow(
            getLanguage()
        )

    val language: StateFlow<String> =
        _language

    /**
     * Applies the stored locale to this process without forcing Activity recreation.
     * Compose already observes [language], so runtime direction/text updates can happen
     * without the visible AppCompatDelegate recreation flash.
     */
    fun applyStoredLanguage() {
        val storedLanguage = getLanguage()
        _language.value = storedLanguage
        applyLocaleToResources(storedLanguage)
    }

    fun isLanguageSelected(): Boolean {
        return preferences.getBoolean(
            KEY_LANGUAGE_SELECTED,
            false
        )
    }

    /**
     * Persists and applies the locale in-place. This intentionally avoids
     * AppCompatDelegate.setApplicationLocales(), which recreates the Activity and
     * caused the settings screen to visibly blink when switching ar/en.
     */
    fun setLanguage(
        language: String
    ) {
        val normalizedLanguage = normalizeLanguage(language)

        preferences.edit()
            .putString(KEY_LANGUAGE, normalizedLanguage)
            .putBoolean(KEY_LANGUAGE_SELECTED, true)
            .apply()

        _language.value = normalizedLanguage
        applyLocaleToResources(normalizedLanguage)
    }

    fun getCurrentLanguage(): String = _language.value

    private fun getLanguage(): String {
        return preferences
            .getString(KEY_LANGUAGE, "ar")
            ?.let(::normalizeLanguage)
            ?: "ar"
    }

    private fun normalizeLanguage(language: String): String {
        return when (language.trim().lowercase()) {
            "ar", "arabic", "العربية" -> "ar"
            "en", "english", "الإنجليزية" -> "en"
            else -> "en"
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLocaleToResources(language: String) {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = Configuration(resources.configuration)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            configuration.setLocale(locale)
        }
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    companion object {
        private const val PREFS_NAME = "language_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LANGUAGE_SELECTED = "language_selected"
    }
}

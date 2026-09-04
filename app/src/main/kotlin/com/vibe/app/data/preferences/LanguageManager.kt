package com.vibe.app.data.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class LanguageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    private val _language = MutableStateFlow(getLanguage())
    val language: StateFlow<String> = _language

    /**
     * Applies the persisted app language using AppCompat's per-app locale API.
     *
     * This is deliberately preferred over Resources.updateConfiguration():
     * AppCompat updates Activity resources, Compose stringResource(), layout
     * direction (RTL/LTR), and recreates the host Activity when needed so the
     * complete UI switches language consistently.
     */
    fun applyStoredLanguage() {
        val storedLanguage = getLanguage()
        _language.value = storedLanguage
        applyApplicationLocale(storedLanguage)
    }

    fun isLanguageSelected(): Boolean =
        preferences.getBoolean(KEY_LANGUAGE_SELECTED, false)

    fun setLanguage(language: String) {
        val normalizedLanguage = normalizeLanguage(language)

        preferences.edit()
            .putString(KEY_LANGUAGE, normalizedLanguage)
            .putBoolean(KEY_LANGUAGE_SELECTED, true)
            .apply()

        _language.value = normalizedLanguage
        applyApplicationLocale(normalizedLanguage)
    }

    fun getCurrentLanguage(): String = _language.value

    private fun getLanguage(): String =
        preferences
            .getString(KEY_LANGUAGE, "ar")
            ?.let(::normalizeLanguage)
            ?: "ar"

    private fun normalizeLanguage(language: String): String =
        when (language.trim().lowercase()) {
            "ar", "arabic", "العربية" -> "ar"
            "en", "english", "الإنجليزية" -> "en"
            else -> "en"
        }

    private fun applyApplicationLocale(language: String) {
        val desired = LocaleListCompat.forLanguageTags(language)
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != desired.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }

    companion object {
        private const val PREFS_NAME = "language_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LANGUAGE_SELECTED = "language_selected"
    }
}

package com.malik.lmai.data.preferences

import android.content.Context
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

    fun applyStoredLanguage() {
        val storedLanguage = getLanguage()
        _language.value = storedLanguage
        AppText.setLanguage(storedLanguage)
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
        AppText.setLanguage(normalizedLanguage)
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

    companion object {
        private const val PREFS_NAME = "language_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LANGUAGE_SELECTED = "language_selected"
    }
}

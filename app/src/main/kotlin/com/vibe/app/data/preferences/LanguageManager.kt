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
     * Applies the language saved in app preferences.
     *
     * Called during application startup so Android resources use the selected
     * app language before MainActivity is displayed.
     */
    fun applyStoredLanguage() {
        val storedLanguage = getLanguage()
        _language.value = storedLanguage
        applyApplicationLocale(storedLanguage)
    }

    /**
     * Returns true when the user has explicitly selected a language before.
     */
    fun isLanguageSelected(): Boolean {
        return preferences.getBoolean(
            KEY_LANGUAGE_SELECTED,
            false
        )
    }

    /**
     * Saves and applies the selected application language.
     *
     * Supported languages:
     * - ar = Arabic / RTL
     * - en = English / LTR
     */
    fun setLanguage(
        language: String
    ) {
        val normalizedLanguage =
            normalizeLanguage(language)

        preferences.edit()
            .putString(
                KEY_LANGUAGE,
                normalizedLanguage
            )
            .putBoolean(
                KEY_LANGUAGE_SELECTED,
                true
            )
            .apply()

        _language.value =
            normalizedLanguage

        applyApplicationLocale(
            normalizedLanguage
        )
    }

    /**
     * Returns the currently active language.
     */
    fun getCurrentLanguage(): String {
        return _language.value
    }

    /**
     * Returns the stored language.
     *
     * Arabic remains the default language until the user makes the first
     * explicit selection.
     */
    private fun getLanguage(): String {
        return preferences
            .getString(
                KEY_LANGUAGE,
                "ar"
            )
            ?.let(::normalizeLanguage)
            ?: "ar"
    }

    private fun normalizeLanguage(
        language: String
    ): String {
        return when (
            language.trim().lowercase()
        ) {
            "ar",
            "arabic",
            "العربية" -> "ar"

            "en",
            "english",
            "الإنجليزية" -> "en"

            else -> "en"
        }
    }

    private fun applyApplicationLocale(
        language: String
    ) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(
                language
            )
        )
    }

    companion object {
        private const val PREFS_NAME =
            "language_settings"

        private const val KEY_LANGUAGE =
            "language"

        private const val KEY_LANGUAGE_SELECTED =
            "language_selected"
    }
}

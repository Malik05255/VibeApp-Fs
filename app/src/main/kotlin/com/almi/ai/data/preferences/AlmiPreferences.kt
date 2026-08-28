package com.almi.ai.data.preferences

import android.content.Context
import android.content.SharedPreferences
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

enum class AiMode {
    CUSTOM,
    FREE_AUTO,
}

data class CustomAiConfig(
    val providerName: String = "OpenRouter",
    val baseUrl: String = DEFAULT_OPENROUTER_BASE_URL,
    val apiKey: String = "",
    val imageEndpoint: String = "/images",
    val imageModel: String = DEFAULT_IMAGE_MODEL,
    val videoEndpoint: String = "/videos",
    val videoModel: String = DEFAULT_VIDEO_MODEL,
) {
    val isUsable: Boolean
        get() = baseUrl.isNotBlank() &&
            apiKey.isNotBlank() &&
            imageEndpoint.isNotBlank() &&
            imageModel.isNotBlank() &&
            videoEndpoint.isNotBlank() &&
            videoModel.isNotBlank()

    companion object {
        const val DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
        const val DEFAULT_IMAGE_MODEL = "openai/gpt-image-1"
        const val DEFAULT_VIDEO_MODEL = "bytedance/seedance-2.0-fast"
    }
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

    private val _aiMode = MutableStateFlow(readAiMode(preferences))
    val aiMode: StateFlow<AiMode> = _aiMode.asStateFlow()

    private val _customAiConfig = MutableStateFlow(readCustomConfig(preferences))
    val customAiConfig: StateFlow<CustomAiConfig> = _customAiConfig.asStateFlow()

    private val _freeOpenRouterApiKey = MutableStateFlow(readFreeOpenRouterKey(preferences))
    val freeOpenRouterApiKey: StateFlow<String> = _freeOpenRouterApiKey.asStateFlow()

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

    fun setAiMode(mode: AiMode) {
        preferences.edit().putString(KEY_AI_MODE, mode.name).apply()
        _aiMode.value = mode
    }

    fun setCustomAiConfig(config: CustomAiConfig) {
        val normalized = config.copy(
            providerName = config.providerName.trim(),
            baseUrl = config.baseUrl.trim().trimEnd('/'),
            apiKey = config.apiKey.trim(),
            imageEndpoint = normalizeEndpoint(config.imageEndpoint),
            imageModel = config.imageModel.trim(),
            videoEndpoint = normalizeEndpoint(config.videoEndpoint),
            videoModel = config.videoModel.trim(),
        )
        preferences.edit()
            .putString(KEY_CUSTOM_PROVIDER_NAME, normalized.providerName)
            .putString(KEY_CUSTOM_BASE_URL, normalized.baseUrl)
            .putString(KEY_CUSTOM_API_KEY, normalized.apiKey)
            .putString(KEY_CUSTOM_IMAGE_ENDPOINT, normalized.imageEndpoint)
            .putString(KEY_CUSTOM_IMAGE_MODEL, normalized.imageModel)
            .putString(KEY_CUSTOM_VIDEO_ENDPOINT, normalized.videoEndpoint)
            .putString(KEY_CUSTOM_VIDEO_MODEL, normalized.videoModel)
            .apply()
        _customAiConfig.value = normalized
    }

    fun setFreeOpenRouterApiKey(value: String) {
        val normalized = value.trim()
        preferences.edit().putString(KEY_FREE_OPENROUTER_API_KEY, normalized).apply()
        _freeOpenRouterApiKey.value = normalized
    }

    fun currentAiMode(): AiMode = readAiMode(preferences)
    fun currentCustomAiConfig(): CustomAiConfig = readCustomConfig(preferences)
    fun currentFreeOpenRouterApiKey(): String = readFreeOpenRouterKey(preferences)

    companion object {
        private const val PREFERENCES_NAME = "almi_ai_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_THEME = "theme"
        private const val KEY_AI_MODE = "ai_mode"

        private const val KEY_CUSTOM_PROVIDER_NAME = "custom_provider_name"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_CUSTOM_IMAGE_ENDPOINT = "custom_image_endpoint"
        private const val KEY_CUSTOM_IMAGE_MODEL = "custom_image_model"
        private const val KEY_CUSTOM_VIDEO_ENDPOINT = "custom_video_endpoint"
        private const val KEY_CUSTOM_VIDEO_MODEL = "custom_video_model"
        private const val KEY_FREE_OPENROUTER_API_KEY = "free_openrouter_api_key"

        // Previous ALMI_AI builds stored the OpenRouter key here. Keep it as a read-only migration source.
        private const val LEGACY_KEY_API_KEY = "openrouter_api_key"

        fun applyStoredLanguage(context: Context) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val language = readLanguage(preferences)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }

        private fun readLanguage(preferences: SharedPreferences): String =
            preferences.getString(KEY_LANGUAGE, "ar")
                ?.takeIf { it == "ar" || it == "en" }
                ?: "ar"

        private fun readTheme(preferences: SharedPreferences): AppThemeMode =
            runCatching {
                AppThemeMode.valueOf(preferences.getString(KEY_THEME, AppThemeMode.SYSTEM.name).orEmpty())
            }.getOrDefault(AppThemeMode.SYSTEM)

        private fun readAiMode(preferences: SharedPreferences): AiMode =
            runCatching {
                AiMode.valueOf(preferences.getString(KEY_AI_MODE, AiMode.CUSTOM.name).orEmpty())
            }.getOrDefault(AiMode.CUSTOM)

        private fun readCustomConfig(preferences: SharedPreferences): CustomAiConfig {
            val migratedKey = preferences.getString(KEY_CUSTOM_API_KEY, null)
                ?: preferences.getString(LEGACY_KEY_API_KEY, "")
                .orEmpty()
            return CustomAiConfig(
                providerName = preferences.getString(KEY_CUSTOM_PROVIDER_NAME, "OpenRouter").orEmpty(),
                baseUrl = preferences.getString(
                    KEY_CUSTOM_BASE_URL,
                    CustomAiConfig.DEFAULT_OPENROUTER_BASE_URL,
                ).orEmpty(),
                apiKey = migratedKey,
                imageEndpoint = preferences.getString(KEY_CUSTOM_IMAGE_ENDPOINT, "/images").orEmpty(),
                imageModel = preferences.getString(
                    KEY_CUSTOM_IMAGE_MODEL,
                    CustomAiConfig.DEFAULT_IMAGE_MODEL,
                ).orEmpty(),
                videoEndpoint = preferences.getString(KEY_CUSTOM_VIDEO_ENDPOINT, "/videos").orEmpty(),
                videoModel = preferences.getString(
                    KEY_CUSTOM_VIDEO_MODEL,
                    CustomAiConfig.DEFAULT_VIDEO_MODEL,
                ).orEmpty(),
            )
        }

        private fun readFreeOpenRouterKey(preferences: SharedPreferences): String =
            (preferences.getString(KEY_FREE_OPENROUTER_API_KEY, null)
                ?: preferences.getString(LEGACY_KEY_API_KEY, ""))
                .orEmpty()
                .trim()

        private fun normalizeEndpoint(value: String): String {
            val trimmed = value.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
            return if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        }
    }
}

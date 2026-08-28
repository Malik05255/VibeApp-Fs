package com.almi.ai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AlmiPreferences
import com.almi.ai.data.preferences.ApiKeyRecord
import com.almi.ai.data.preferences.ApiKeyVault
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.data.preferences.CustomAiConfig
import com.almi.ai.data.repository.FreeAiCandidate
import com.almi.ai.data.repository.FreeAiCatalogRepository
import com.almi.ai.data.repository.OpenRouterOAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AlmiPreferences,
    private val freeCatalogRepository: FreeAiCatalogRepository,
    private val apiKeyVault: ApiKeyVault,
    private val openRouterOAuthRepository: OpenRouterOAuthRepository,
) : ViewModel() {
    val language: StateFlow<String> = preferences.language
    val themeMode: StateFlow<AppThemeMode> = preferences.themeMode
    val aiMode: StateFlow<AiMode> = preferences.aiMode
    val customAiConfig: StateFlow<CustomAiConfig> = preferences.customAiConfig
    val apiKeys: StateFlow<List<ApiKeyRecord>> = apiKeyVault.keys

    private val _freeAiStatus = MutableStateFlow(FreeAiStatus())
    val freeAiStatus: StateFlow<FreeAiStatus> = _freeAiStatus.asStateFlow()

    private val _oauthState = MutableStateFlow(OAuthConnectionState())
    val oauthState: StateFlow<OAuthConnectionState> = _oauthState.asStateFlow()

    init {
        if (preferences.currentAiMode() == AiMode.FREE_AUTO) refreshFreeCatalog()
    }

    fun setLanguage(language: String) = preferences.setLanguage(language)
    fun setThemeMode(mode: AppThemeMode) = preferences.setThemeMode(mode)

    fun saveAndActivateCustom(config: CustomAiConfig) {
        preferences.setCustomAiConfig(config)
        preferences.setAiMode(AiMode.CUSTOM)
    }

    fun setFreeMode(enabled: Boolean) {
        preferences.setAiMode(if (enabled) AiMode.FREE_AUTO else AiMode.CUSTOM)
        if (enabled) refreshFreeCatalog()
    }

    fun connectOpenRouterAutomatically() {
        if (_oauthState.value.isConnecting) return
        viewModelScope.launch {
            _oauthState.value = OAuthConnectionState(isConnecting = true)
            openRouterOAuthRepository.connect()
                .onSuccess { result ->
                    apiKeyVault.addOpenRouterKey(
                        secret = result.apiKey,
                        label = result.userId?.let { "OpenRouter ${it.takeLast(6)}" } ?: "OpenRouter OAuth",
                    )
                    _oauthState.value = OAuthConnectionState(connected = true)
                    refreshFreeCatalog()
                }
                .onFailure { error ->
                    _oauthState.value = OAuthConnectionState(
                        error = error.message ?: "oauth_failed",
                    )
                }
        }
    }

    fun addManualOpenRouterKey(value: String) {
        if (value.isBlank()) return
        apiKeyVault.addOpenRouterKey(value, "OpenRouter manual")
        refreshFreeCatalog()
    }

    fun removeApiKey(id: String) {
        apiKeyVault.remove(id)
        refreshFreeCatalog()
    }

    fun setApiKeyEnabled(id: String, enabled: Boolean) {
        apiKeyVault.setEnabled(id, enabled)
        refreshFreeCatalog()
    }

    fun clearOAuthMessage() {
        _oauthState.value = OAuthConnectionState()
    }

    fun refreshFreeCatalog() {
        viewModelScope.launch {
            _freeAiStatus.value = _freeAiStatus.value.copy(isChecking = true, error = null)
            val apiKey = apiKeyVault.activeOpenRouterKeys().firstOrNull()?.secret
            freeCatalogRepository.discover(apiKey)
                .onSuccess { catalog ->
                    _freeAiStatus.value = FreeAiStatus(
                        isChecking = false,
                        imageModels = catalog.imageModels,
                        videoModels = catalog.videoModels,
                    )
                }
                .onFailure { error ->
                    _freeAiStatus.value = FreeAiStatus(
                        isChecking = false,
                        error = error.message ?: "free_catalog_failed",
                    )
                }
        }
    }
}

data class FreeAiStatus(
    val isChecking: Boolean = false,
    val imageModels: List<FreeAiCandidate> = emptyList(),
    val videoModels: List<FreeAiCandidate> = emptyList(),
    val error: String? = null,
)

data class OAuthConnectionState(
    val isConnecting: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
)

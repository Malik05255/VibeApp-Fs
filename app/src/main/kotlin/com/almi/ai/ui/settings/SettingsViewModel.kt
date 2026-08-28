package com.almi.ai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AlmiPreferences
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.data.preferences.CustomAiConfig
import com.almi.ai.data.repository.FreeAiCandidate
import com.almi.ai.data.repository.FreeAiCatalogRepository
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
) : ViewModel() {
    val language: StateFlow<String> = preferences.language
    val themeMode: StateFlow<AppThemeMode> = preferences.themeMode
    val aiMode: StateFlow<AiMode> = preferences.aiMode
    val customAiConfig: StateFlow<CustomAiConfig> = preferences.customAiConfig
    val freeOpenRouterApiKey: StateFlow<String> = preferences.freeOpenRouterApiKey

    private val _freeAiStatus = MutableStateFlow(FreeAiStatus())
    val freeAiStatus: StateFlow<FreeAiStatus> = _freeAiStatus.asStateFlow()

    init {
        if (preferences.currentAiMode() == AiMode.FREE_AUTO) {
            refreshFreeCatalog()
        }
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

    fun saveFreeOpenRouterApiKey(value: String) {
        preferences.setFreeOpenRouterApiKey(value)
    }

    fun refreshFreeCatalog() {
        viewModelScope.launch {
            _freeAiStatus.value = _freeAiStatus.value.copy(isChecking = true, error = null)
            freeCatalogRepository.discover()
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

package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.repository.SettingRepository
import com.vibe.app.feature.ai.AiExecutionMode
import com.vibe.app.feature.ai.FreeAiRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FreeAiSettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
) : ViewModel() {

    data class UiState(
        val freeAiEnabled: Boolean = true,
        val executionMode: AiExecutionMode = AiExecutionMode.AUTOMATIC,
        val configuredFreeProviders: Int = 0,
        val totalFreeSources: Int = 6,
        val customProviderActive: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            var platforms = runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(emptyList())
            val configuredFree = freeAiRouter.orderedCandidates(platforms)
                .map { it.provider }
                .distinct()
                .size
            val customActive = platforms.any { platform ->
                platform.enabled && !freeAiRouter.isFreeCandidate(platform)
            }

            var freeEnabled = runCatching { settingRepository.getFreeAiEnabled() }.getOrDefault(true)
            if (customActive && freeEnabled) {
                freeEnabled = false
                runCatching { settingRepository.updateFreeAiEnabled(false) }
            } else if (freeEnabled && !customActive) {
                activateBestFreePlatform(platforms)
                platforms = runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(platforms)
            }

            val mode = AiExecutionMode.fromStoredValue(
                runCatching { settingRepository.getAiExecutionMode() }.getOrNull()
            )

            _uiState.value = UiState(
                freeAiEnabled = freeEnabled,
                executionMode = mode,
                configuredFreeProviders = configuredFree,
                customProviderActive = customActive,
            )
        }
    }

    fun setFreeAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val platforms = runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(emptyList())

            if (enabled) {
                activateBestFreePlatform(platforms)
            } else {
                platforms
                    .filter { it.enabled && freeAiRouter.isFreeCandidate(it) }
                    .forEach { platform ->
                        runCatching {
                            settingRepository.updatePlatformV2(platform.copy(enabled = false))
                        }
                    }
            }

            runCatching { settingRepository.updateFreeAiEnabled(enabled) }
            refresh()
        }
    }

    fun setExecutionMode(mode: AiExecutionMode) {
        viewModelScope.launch {
            runCatching { settingRepository.updateAiExecutionMode(mode.name) }
            _uiState.value = _uiState.value.copy(executionMode = mode)
        }
    }

    private suspend fun activateBestFreePlatform(platforms: List<PlatformV2>) {
        val best = freeAiRouter.selectBest(platforms) ?: return

        platforms.forEach { platform ->
            val shouldEnable = platform.uid == best.uid
            if (platform.enabled != shouldEnable) {
                runCatching {
                    settingRepository.updatePlatformV2(platform.copy(enabled = shouldEnable))
                }
            }
        }
    }
}

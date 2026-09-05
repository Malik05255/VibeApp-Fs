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
            var platforms = runCatching {
                settingRepository.fetchPlatformV2s()
            }.getOrDefault(emptyList())

            val configuredFree = freeAiRouter.orderedCandidates(platforms)
                .map { it.provider }
                .distinct()
                .size

            val customActive = platforms.any { platform ->
                platform.enabled && !freeAiRouter.isFreeCandidate(platform)
            }

            val storedFreeEnabled = runCatching {
                settingRepository.getFreeAiEnabled()
            }.getOrDefault(true)

            val freeEnabled = !customActive

            if (customActive) {
                // External API was explicitly enabled by the user. Free AI
                // enters standby automatically and must not compete with it.
                if (storedFreeEnabled) {
                    runCatching { settingRepository.updateFreeAiEnabled(false) }
                }
                deactivateFreePlatforms(platforms)
            } else {
                // No external provider is active. Free AI is mandatory fallback
                // and is restored automatically, including after a manual API off.
                if (!storedFreeEnabled) {
                    runCatching { settingRepository.updateFreeAiEnabled(true) }
                }
                activateBestFreePlatform(platforms)
                platforms = runCatching {
                    settingRepository.fetchPlatformV2s()
                }.getOrDefault(platforms)
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

    /**
     * Kept for compatibility with older UI code. Free AI is no longer a user
     * toggle; its state is derived automatically from external-provider state.
     */
    fun setFreeAiEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        refresh()
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

    private suspend fun deactivateFreePlatforms(platforms: List<PlatformV2>) {
        platforms
            .filter { platform ->
                platform.enabled && freeAiRouter.isFreeCandidate(platform)
            }
            .forEach { platform ->
                runCatching {
                    settingRepository.updatePlatformV2(platform.copy(enabled = false))
                }
            }
    }
}

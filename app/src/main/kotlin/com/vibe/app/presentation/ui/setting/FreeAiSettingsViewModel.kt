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
        val executionMode: AiExecutionMode = AiExecutionMode.MANUAL,
        val configuredFreeProviders: Int = 0,
        val totalFreeSources: Int = 6,
        val customProviderActive: Boolean = false,
        val hiddenInternalProviderUids: Set<String> = emptySet(),
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
                platform.enabled && freeAiRouter.isExternal(platform)
            }

            val storedFreeEnabled = runCatching {
                settingRepository.getFreeAiEnabled()
            }.getOrDefault(true)

            val freeEnabled = !customActive

            if (customActive) {
                // A user-managed external API has priority only because the user
                // explicitly enabled it. Hidden Free AI stays in standby.
                if (storedFreeEnabled) {
                    runCatching { settingRepository.updateFreeAiEnabled(false) }
                }
                deactivateInternalPlatforms(platforms)
            } else {
                // No external API is active. Free AI is mandatory automatic
                // fallback, including after the user manually turns an API off.
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
                hiddenInternalProviderUids = platforms
                    .filter(freeAiRouter::isInternalFree)
                    .mapTo(linkedSetOf()) { it.uid },
            )
        }
    }

    /**
     * Compatibility shim for old callers. Free AI no longer has a user toggle;
     * it is active whenever no external API is enabled.
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

    private suspend fun deactivateInternalPlatforms(platforms: List<PlatformV2>) {
        platforms
            .filter { platform ->
                platform.enabled && freeAiRouter.isInternalFree(platform)
            }
            .forEach { platform ->
                runCatching {
                    settingRepository.updatePlatformV2(platform.copy(enabled = false))
                }
            }
    }
}

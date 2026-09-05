package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.BuildConfig
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.repository.SettingRepository
import com.vibe.app.feature.ai.AiExecutionMode
import com.vibe.app.feature.ai.FreeAiBootstrapper
import com.vibe.app.feature.ai.FreeAiRouter
import com.vibe.app.feature.ai.openrouter.OpenRouterOAuthCallbackBus
import com.vibe.app.feature.ai.openrouter.OpenRouterOAuthCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FreeAiSettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
    private val freeAiBootstrapper: FreeAiBootstrapper,
    private val openRouterOAuthCoordinator: OpenRouterOAuthCoordinator,
) : ViewModel() {

    data class UiState(
        val freeAiEnabled: Boolean = true,
        val executionMode: AiExecutionMode = AiExecutionMode.MANUAL,
        val configuredFreeProviders: Int = 0,
        val customProviderActive: Boolean = false,
        val hiddenInternalProviderUids: Set<String> = emptySet(),
        val openRouterConnected: Boolean = false,
        val openRouterConnecting: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _openBrowser = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openBrowser: SharedFlow<String> = _openBrowser.asSharedFlow()

    init {
        refresh()
        viewModelScope.launch {
            OpenRouterOAuthCallbackBus.callback.collect { uri ->
                if (uri == null) return@collect
                _uiState.value = _uiState.value.copy(openRouterConnecting = true)
                openRouterOAuthCoordinator.complete(uri)
                OpenRouterOAuthCallbackBus.consume(uri)
                _uiState.value = _uiState.value.copy(openRouterConnecting = false)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            var platforms = runCatching { freeAiBootstrapper.ensureReady() }
                .getOrElse {
                    runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(emptyList())
                }

            val configuredFree = freeAiRouter.orderedCandidates(platforms)
                .map { it.provider }
                .distinct()
                .size

            val customActive = platforms.any { platform ->
                platform.enabled && freeAiRouter.isExternal(platform)
            }

            val storedFreeEnabled = runCatching { settingRepository.getFreeAiEnabled() }
                .getOrDefault(true)

            val freeEnabled = !customActive

            if (customActive) {
                if (storedFreeEnabled) runCatching { settingRepository.updateFreeAiEnabled(false) }
                deactivateInternalPlatforms(platforms)
            } else {
                if (!storedFreeEnabled) runCatching { settingRepository.updateFreeAiEnabled(true) }
                activateBestFreePlatform(platforms)
                platforms = runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(platforms)
            }

            val mode = AiExecutionMode.fromStoredValue(
                runCatching { settingRepository.getAiExecutionMode() }.getOrNull()
            )

            _uiState.value = _uiState.value.copy(
                freeAiEnabled = freeEnabled,
                executionMode = mode,
                configuredFreeProviders = configuredFree,
                customProviderActive = customActive,
                hiddenInternalProviderUids = platforms
                    .filter(freeAiRouter::isInternalFree)
                    .mapTo(linkedSetOf()) { it.uid },
                openRouterConnected = openRouterOAuthCoordinator.isConnected(),
            )
        }
    }

    fun connectOpenRouter() {
        val url = runCatching {
            openRouterOAuthCoordinator.begin(BuildConfig.OPENROUTER_OAUTH_CALLBACK_URL)
        }.getOrNull() ?: return
        _uiState.value = _uiState.value.copy(openRouterConnecting = true)
        _openBrowser.tryEmit(url)
    }

    fun disconnectOpenRouter() {
        viewModelScope.launch {
            openRouterOAuthCoordinator.disconnect()
            _uiState.value = _uiState.value.copy(
                openRouterConnected = false,
                openRouterConnecting = false,
            )
            refresh()
        }
    }

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
                runCatching { settingRepository.updatePlatformV2(platform.copy(enabled = shouldEnable)) }
            }
        }
    }

    private suspend fun deactivateInternalPlatforms(platforms: List<PlatformV2>) {
        platforms
            .filter { platform -> platform.enabled && freeAiRouter.isInternalFree(platform) }
            .forEach { platform ->
                runCatching { settingRepository.updatePlatformV2(platform.copy(enabled = false)) }
            }
    }
}

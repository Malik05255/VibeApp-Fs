package com.malik.lmai.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.BuildConfig
import com.malik.lmai.data.repository.SettingRepository
import com.malik.lmai.feature.ai.AiExecutionMode
import com.malik.lmai.feature.ai.FreeAiBootstrapper
import com.malik.lmai.feature.ai.FreeAiRouter
import com.malik.lmai.feature.ai.FreeAiRuntimeAvailability
import com.malik.lmai.feature.ai.openrouter.OpenRouterOAuthCallbackBus
import com.malik.lmai.feature.ai.openrouter.OpenRouterOAuthCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Internal H capacity settings.
 *
 * Kept under the legacy class name to avoid needless churn, but no provider name or free
 * route is exposed in the normal provider settings screen. This ViewModel powers only
 * generic H capacity controls.
 */
@HiltViewModel
class FreeAiSettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
    private val freeAiBootstrapper: FreeAiBootstrapper,
    private val runtimeAvailability: FreeAiRuntimeAvailability,
    private val openRouterOAuthCoordinator: OpenRouterOAuthCoordinator,
) : ViewModel() {

    data class UiState(
        val freeAiEnabled: Boolean = true,
        val automaticCloudRoutesEnabled: Boolean = true,
        val executionMode: AiExecutionMode = AiExecutionMode.MANUAL,
        val configuredFreeProviders: Int = 0,
        val customProviderActive: Boolean = false,
        val hiddenInternalProviderUids: Set<String> = emptySet(),
        val networkAvailable: Boolean? = null,
        val openRouterConnected: Boolean = false,
        val openRouterConnecting: Boolean = false,
        val openRouterError: String? = null,
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

                _uiState.value = _uiState.value.copy(
                    openRouterConnecting = true,
                    openRouterError = null,
                )

                val result = openRouterOAuthCoordinator.complete(uri)
                OpenRouterOAuthCallbackBus.consume(uri)

                _uiState.value = _uiState.value.copy(
                    openRouterConnecting = false,
                    openRouterError = result.exceptionOrNull()?.message?.take(MAX_ERROR_CHARS),
                )
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val platforms = runCatching { freeAiBootstrapper.ensureReady() }
                .getOrElse {
                    runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(emptyList())
                }

            val availability = try {
                runtimeAvailability.evaluate(platforms)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val usablePlatforms = availability?.usablePlatforms ?: platforms

            val configuredFree = freeAiRouter.orderedCandidates(usablePlatforms)
                .map { it.provider }
                .distinct()
                .size

            val customActive = platforms.any { platform ->
                platform.enabled && freeAiRouter.isExternal(platform)
            }

            val laneEnabled = runCatching { settingRepository.getFreeAiEnabled() }
                .getOrDefault(!customActive)
            val automaticCloudRoutes = runCatching {
                settingRepository.getHAutoCloudRoutesEnabled()
            }.getOrDefault(true)

            val mode = AiExecutionMode.fromStoredValue(
                runCatching { settingRepository.getAiExecutionMode() }.getOrNull()
            )

            _uiState.value = _uiState.value.copy(
                freeAiEnabled = laneEnabled,
                automaticCloudRoutesEnabled = automaticCloudRoutes,
                executionMode = mode,
                configuredFreeProviders = configuredFree,
                customProviderActive = customActive,
                hiddenInternalProviderUids = platforms
                    .filter(freeAiRouter::isInternalFree)
                    .mapTo(linkedSetOf()) { it.uid },
                networkAvailable = availability?.networkAvailable,
                openRouterConnected = openRouterOAuthCoordinator.isConnected(),
            )
        }
    }

    fun connectOpenRouter() {
        viewModelScope.launch {
            runCatching { settingRepository.updateHAutoCloudRoutesEnabled(true) }
            runCatching { freeAiBootstrapper.ensureReady() }

            val url = try {
                openRouterOAuthCoordinator.begin(BuildConfig.OPENROUTER_OAUTH_CALLBACK_URL)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    openRouterConnecting = false,
                    openRouterError = e.message?.take(MAX_ERROR_CHARS)
                        ?: "Unable to start cloud authorization.",
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                automaticCloudRoutesEnabled = true,
                openRouterConnecting = true,
                openRouterError = null,
            )

            if (!_openBrowser.tryEmit(url)) {
                _uiState.value = _uiState.value.copy(
                    openRouterConnecting = false,
                    openRouterError = "Unable to open the cloud authorization page.",
                )
            }
        }
    }

    fun reportOpenRouterLaunchFailure(error: Throwable) {
        _uiState.value = _uiState.value.copy(
            openRouterConnecting = false,
            openRouterError = error.message?.take(MAX_ERROR_CHARS)
                ?: "Unable to open the cloud authorization page.",
        )
    }

    fun disconnectOpenRouter() {
        viewModelScope.launch {
            try {
                openRouterOAuthCoordinator.disconnect()
                _uiState.value = _uiState.value.copy(
                    openRouterConnected = false,
                    openRouterConnecting = false,
                    openRouterError = null,
                )
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    openRouterConnecting = false,
                    openRouterError = e.message?.take(MAX_ERROR_CHARS)
                        ?: "Unable to disconnect cloud capacity.",
                )
            }
        }
    }

    fun setAutomaticCloudRoutesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingRepository.updateHAutoCloudRoutesEnabled(enabled) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        openRouterError = it.message?.take(MAX_ERROR_CHARS),
                    )
                    return@launch
                }

            if (!enabled) {
                // A disabled hidden pool should not retain a provider credential silently.
                runCatching { openRouterOAuthCoordinator.disconnect() }
            }
            runCatching { freeAiBootstrapper.ensureReady() }
            refresh()
        }
    }

    /** Legacy compatibility hook. Normal UI no longer exposes this lane toggle. */
    fun setFreeAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingRepository.updateFreeAiEnabled(enabled) }
            refresh()
        }
    }

    fun setExecutionMode(mode: AiExecutionMode) {
        viewModelScope.launch {
            val updated = runCatching { settingRepository.updateAiExecutionMode(mode.name) }
                .isSuccess
            if (updated) {
                _uiState.value = _uiState.value.copy(executionMode = mode)
            }
        }
    }

    companion object {
        private const val MAX_ERROR_CHARS = 400
    }
}

package com.malik.lmai.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.dto.OpenRouterModel
import com.malik.lmai.data.network.OpenRouterModelsAPI
import com.malik.lmai.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
    private val openRouterModelsAPI: OpenRouterModelsAPI
) : ViewModel() {

    private val _platformState =
        MutableStateFlow<List<PlatformV2>>(emptyList())

    val platformState: StateFlow<List<PlatformV2>> =
        _platformState.asStateFlow()

    private val _dialogState =
        MutableStateFlow(DialogState())

    val dialogState: StateFlow<DialogState> =
        _dialogState.asStateFlow()

    private val _switchedPlatformEvent =
        MutableSharedFlow<String>()

    val switchedPlatformEvent: SharedFlow<String> =
        _switchedPlatformEvent.asSharedFlow()

    private val _debugMode =
        MutableStateFlow(false)

    val debugMode: StateFlow<Boolean> =
        _debugMode.asStateFlow()

    private val _apiProvider =
        MutableStateFlow("OPEN_ROUTER")

    val apiProvider: StateFlow<String> =
        _apiProvider.asStateFlow()

    private val _apiKey =
        MutableStateFlow("")

    val apiKey: StateFlow<String> =
        _apiKey.asStateFlow()

    private val _customApiUrl =
        MutableStateFlow("")

    val customApiUrl: StateFlow<String> =
        _customApiUrl.asStateFlow()

    /*
     * OpenRouter models
     */
    private val _models =
        MutableStateFlow<List<OpenRouterModel>>(emptyList())

    val models: StateFlow<List<OpenRouterModel>> =
        _models.asStateFlow()

    private val _isLoading =
        MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> =
        _isLoading.asStateFlow()

    private val _isFreeFilter =
        MutableStateFlow(true)

    val isFreeFilter: StateFlow<Boolean> =
        _isFreeFilter.asStateFlow()

    init {
        fetchPlatforms()
        fetchDebugMode()
    }

    /*
     * API provider
     */
    fun setApiProvider(
        provider: String
    ) {
        _apiProvider.value = provider
    }

    /*
     * API key
     */
    fun setApiKey(
        key: String
    ) {
        _apiKey.value = key
    }

    /*
     * Custom API URL
     */
    fun setCustomApiUrl(
        url: String
    ) {
        _customApiUrl.value = url
    }

    /*
     * Load OpenRouter models.
     */
    fun fetchModels(
        apiKey: String,
        isFreeOnly: Boolean
    ) {
        viewModelScope.launch {

            _isLoading.value = true
            _isFreeFilter.value = isFreeOnly

            try {

                val cleanApiKey =
                    apiKey.trim()

                if (cleanApiKey.isEmpty()) {
                    _models.value = emptyList()
                    return@launch
                }

                val result =
                    openRouterModelsAPI
                        .fetchOpenRouterModels(
                            apiKey = cleanApiKey,
                            isFreeOnly = isFreeOnly
                        )

                _models.value = result

            } catch (_: Exception) {

                _models.value =
                    emptyList()

            } finally {

                _isLoading.value = false
            }
        }
    }

    /*
     * Save API settings.
     */
    fun saveApiSettings() {
        viewModelScope.launch {

            try {

                settingRepository.saveApiSettings(
                    provider = _apiProvider.value,
                    apiKey = _apiKey.value.trim(),
                    customUrl = _customApiUrl.value.trim()
                )

            } catch (_: Exception) {
                // Ignore persistence errors here.
            }
        }
    }

    /*
     * Fetch platforms.
     */
    fun fetchPlatforms() {
        viewModelScope.launch {

            try {

                val platforms =
                    settingRepository.fetchPlatformV2s()

                _platformState.update {
                    platforms
                }

            } catch (_: Exception) {

                _platformState.update {
                    emptyList()
                }
            }
        }
    }

    /*
     * Add platform.
     *
     * Only one platform can be enabled at a time.
     */
    fun addPlatform(
        platform: PlatformV2
    ) {
        viewModelScope.launch {

            try {

                if (platform.enabled) {

                    val allPlatforms =
                        settingRepository
                            .fetchPlatformV2s()

                    val othersEnabled =
                        allPlatforms.filter {
                            it.enabled &&
                                it.id != platform.id
                        }

                    othersEnabled.forEach {
                        settingRepository
                            .updatePlatformV2(
                                it.copy(
                                    enabled = false
                                )
                            )
                    }

                    if (othersEnabled.isNotEmpty()) {

                        _switchedPlatformEvent.emit(
                            platform.name
                        )
                    }
                }

                settingRepository.addPlatformV2(
                    platform
                )

                fetchPlatforms()

            } catch (_: Exception) {
                // Keep current UI state.
            }
        }
    }

    /*
     * Update platform.
     */
    fun updatePlatform(
        platform: PlatformV2
    ) {
        viewModelScope.launch {

            try {

                settingRepository.updatePlatformV2(
                    platform
                )

                fetchPlatforms()

            } catch (_: Exception) {
                // Keep current UI state.
            }
        }
    }

    /*
     * Delete platform.
     */
    fun deletePlatform(
        platform: PlatformV2
    ) {
        viewModelScope.launch {

            try {

                settingRepository.deletePlatformV2(
                    platform
                )

                fetchPlatforms()

            } catch (_: Exception) {
                // Keep current UI state.
            }
        }
    }

    /*
     * Enable / disable platform.
     */
    fun togglePlatformEnabled(
        platformId: Int
    ) {
        val platform =
            _platformState.value.find {
                it.id == platformId
            } ?: return

        val enable =
            !platform.enabled

        if (!enable) {

            updatePlatform(
                platform.copy(
                    enabled = false
                )
            )

            return
        }

        viewModelScope.launch {

            try {

                val others =
                    _platformState.value.filter {
                        it.enabled &&
                            it.id != platformId
                    }

                others.forEach {
                    settingRepository
                        .updatePlatformV2(
                            it.copy(
                                enabled = false
                            )
                        )
                }

                settingRepository
                    .updatePlatformV2(
                        platform.copy(
                            enabled = true
                        )
                    )

                if (others.isNotEmpty()) {

                    _switchedPlatformEvent.emit(
                        platform.name
                    )
                }

                fetchPlatforms()

            } catch (_: Exception) {
                // Keep current UI state.
            }
        }
    }

    /*
     * Theme dialog.
     */
    fun openThemeDialog() {
        _dialogState.update {
            it.copy(
                isThemeDialogOpen = true
            )
        }
    }

    fun closeThemeDialog() {
        _dialogState.update {
            it.copy(
                isThemeDialogOpen = false
            )
        }
    }

    /*
     * Delete dialog.
     */
    fun openDeleteDialog(
        platformId: Int
    ) {
        _dialogState.update {
            it.copy(
                isDeleteDialogOpen = true,
                platformToDelete = platformId
            )
        }
    }

    fun closeDeleteDialog() {
        _dialogState.update {
            it.copy(
                isDeleteDialogOpen = false,
                platformToDelete = null
            )
        }
    }

    /*
     * Confirm platform deletion.
     */
    fun confirmDelete() {

        val platformId =
            _dialogState.value.platformToDelete
                ?: return

        val platform =
            _platformState.value.find {
                it.id == platformId
            }

        if (platform != null) {
            deletePlatform(platform)
        }

        closeDeleteDialog()
    }

    /*
     * Debug mode.
     */
    fun toggleDebugMode() {

        val value =
            !_debugMode.value

        _debugMode.update {
            value
        }

        viewModelScope.launch {

            try {

                settingRepository.updateDebugMode(
                    value
                )

            } catch (_: Exception) {
                // Keep current UI state.
            }
        }
    }

    private fun fetchDebugMode() {
        viewModelScope.launch {

            try {

                _debugMode.update {
                    settingRepository.getDebugMode()
                }

            } catch (_: Exception) {

                _debugMode.update {
                    false
                }
            }
        }
    }

    data class DialogState(
        val isThemeDialogOpen: Boolean = false,
        val isDeleteDialogOpen: Boolean = false,
        val platformToDelete: Int? = null
    )
}

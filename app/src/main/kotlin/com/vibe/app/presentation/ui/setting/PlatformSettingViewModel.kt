package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.network.OpenRouterModelsAPI
import com.vibe.app.data.repository.SettingRepository
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
class PlatformSettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val openRouterModelsAPI: OpenRouterModelsAPI,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val platformUid: String =
        checkNotNull(
            savedStateHandle["platformUid"]
        )

    private val _platformState =
        MutableStateFlow<PlatformV2?>(null)

    val platformState: StateFlow<PlatformV2?> =
        _platformState.asStateFlow()

    private val _dialogState =
        MutableStateFlow(DialogState())

    val dialogState: StateFlow<DialogState> =
        _dialogState.asStateFlow()

    private val _isDeleted =
        MutableStateFlow(false)

    val isDeleted: StateFlow<Boolean> =
        _isDeleted.asStateFlow()

    private val _switchedPlatformEvent =
        MutableSharedFlow<String>()

    val switchedPlatformEvent: SharedFlow<String> =
        _switchedPlatformEvent.asSharedFlow()

    private val _availableModels =
        MutableStateFlow<List<OpenRouterModel>>(
            emptyList()
        )

    val availableModels: StateFlow<List<OpenRouterModel>> =
        _availableModels.asStateFlow()

    private val _isLoadingModels =
        MutableStateFlow(false)

    val isLoadingModels: StateFlow<Boolean> =
        _isLoadingModels.asStateFlow()

    private var currentIsFreeFilter =
        true

    init {
        loadPlatform()
    }

    private fun loadPlatform() {

        viewModelScope.launch {

            try {

                val platforms =
                    settingRepository
                        .fetchPlatformV2s()

                val platform =
                    platforms.firstOrNull {
                        it.uid == platformUid
                    }

                _platformState.value =
                    platform

                /*
                 * Dynamic model loading belongs only
                 * to OpenRouter.
                 *
                 * Google AI Studio, Qwen, Kimi,
                 * DeepSeek, MiniMax and Custom APIs
                 * must never load models from
                 * OpenRouter here.
                 */
                if (
                    platform?.compatibleType ==
                    ClientType.OPEN_ROUTER &&
                    !platform.token.isNullOrBlank()
                ) {

                    loadModels(
                        isFreeOnly =
                            currentIsFreeFilter
                    )

                } else {

                    _availableModels.value =
                        emptyList()
                }

            } catch (_: Exception) {

                _platformState.value =
                    null

                _availableModels.value =
                    emptyList()

                _isLoadingModels.value =
                    false
            }
        }
    }

    /*
     * Load OpenRouter models.
     *
     * This operation is intentionally restricted
     * to ClientType.OPEN_ROUTER.
     */
    fun loadModels(
        isFreeOnly: Boolean,
    ) {

        currentIsFreeFilter =
            isFreeOnly

        val platform =
            _platformState.value

        if (
            platform?.compatibleType !=
            ClientType.OPEN_ROUTER
        ) {

            _availableModels.value =
                emptyList()

            _isLoadingModels.value =
                false

            return
        }

        if (
            platform.token.isNullOrBlank()
        ) {

            _availableModels.value =
                emptyList()

            _isLoadingModels.value =
                false

            return
        }

        viewModelScope.launch {

            _isLoadingModels.value =
                true

            try {

                val models =
                    fetchOpenRouterModels(
                        isFreeOnly =
                            isFreeOnly
                    )

                _availableModels.value =
                    models

            } catch (_: Exception) {

                _availableModels.value =
                    emptyList()

            } finally {

                _isLoadingModels.value =
                    false
            }
        }
    }

    /*
     * Fetch model list from OpenRouter.
     *
     * This must never be used by Google AI Studio
     * or any other provider.
     */
    suspend fun fetchOpenRouterModels(
        isFreeOnly: Boolean,
    ): List<OpenRouterModel> {

        var platform =
            _platformState.value

        if (
            platform?.compatibleType !=
            ClientType.OPEN_ROUTER
        ) {
            return emptyList()
        }

        /*
         * Reload from database if the local state
         * does not currently contain an API key.
         */
        if (
            platform.token.isNullOrBlank()
        ) {

            val platforms =
                settingRepository
                    .fetchPlatformV2s()

            val storedPlatform =
                platforms.firstOrNull {
                    it.uid == platformUid
                }

            if (
                storedPlatform?.compatibleType !=
                ClientType.OPEN_ROUTER
            ) {
                return emptyList()
            }

            platform =
                storedPlatform
        }

        val apiKey =
            platform.token
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: return emptyList()

        return openRouterModelsAPI
            .fetchOpenRouterModels(
                apiKey =
                    apiKey,

                isFreeOnly =
                    isFreeOnly,
            )
    }

    /*
     * Enable / disable provider.
     *
     * Only one provider can be active at a time.
     */
    fun toggleEnabled() {

        val platform =
            _platformState.value
                ?: return

        val shouldEnable =
            !platform.enabled

        /*
         * Disable current provider.
         */
        if (!shouldEnable) {

            updatePlatform(
                platform.copy(
                    enabled = false
                )
            )

            return
        }

        viewModelScope.launch {

            try {

                val allPlatforms =
                    settingRepository
                        .fetchPlatformV2s()

                val otherEnabledPlatforms =
                    allPlatforms.filter {
                        it.enabled &&
                            it.id != platform.id
                    }

                /*
                 * Disable any previously active
                 * provider.
                 */
                otherEnabledPlatforms
                    .forEach { otherPlatform ->

                        settingRepository
                            .updatePlatformV2(
                                otherPlatform.copy(
                                    enabled = false
                                )
                            )
                    }

                val updatedPlatform =
                    platform.copy(
                        enabled = true
                    )

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.value =
                    updatedPlatform

                if (
                    otherEnabledPlatforms
                        .isNotEmpty()
                ) {

                    _switchedPlatformEvent.emit(
                        updatedPlatform.name
                    )
                }

            } catch (_: Exception) {
                /*
                 * Keep existing state if database
                 * update fails.
                 */
            }
        }
    }

    /*
     * Enable / disable reasoning.
     */
    fun toggleReasoning() {

        val platform =
            _platformState.value
                ?: return

        updatePlatform(
            platform.copy(
                reasoning =
                    !platform.reasoning
            )
        )
    }

    /*
     * Persist a complete PlatformV2 object.
     */
    fun updatePlatform(
        platform: PlatformV2,
    ) {

        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        platform
                    )

                _platformState.value =
                    platform

            } catch (_: Exception) {
                /*
                 * Do not replace the current UI
                 * state if persistence fails.
                 */
            }
        }
    }

    /*
     * Update API key.
     *
     * Important:
     * OpenRouter model loading happens only AFTER
     * the new token has been persisted and the
     * current platform state has been updated.
     *
     * This prevents loadModels() from using the
     * previous API key.
     */
    fun updateApiToken(
        token: String,
    ) {

        val platform =
            _platformState.value
                ?: return

        val newToken =
            token
                .trim()
                .removePrefix("Bearer ")
                .removePrefix("bearer ")
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }

        val updatedPlatform =
            platform.copy(
                token = newToken
            )

        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.value =
                    updatedPlatform

                closeApiTokenDialog()

                /*
                 * Only OpenRouter loads its
                 * model catalog dynamically.
                 */
                if (
                    updatedPlatform.compatibleType ==
                    ClientType.OPEN_ROUTER &&
                    !newToken.isNullOrBlank()
                ) {

                    loadModels(
                        isFreeOnly =
                            currentIsFreeFilter
                    )

                } else {

                    _availableModels.value =
                        emptyList()

                    _isLoadingModels.value =
                        false
                }

            } catch (_: Exception) {
                /*
                 * Keep previous state when saving
                 * the API key fails.
                 */
            }
        }
    }

    /*
     * Update selected model.
     *
     * request.platform.model later receives this
     * value directly, therefore we must never
     * replace it with an OpenRouter fallback.
     */
    fun updateApiModel(
        model: String,
    ) {

        val platform =
            _platformState.value
                ?: return

        val cleanedModel =
            model.trim()

        if (
            cleanedModel.isEmpty()
        ) {
            return
        }

        val updatedPlatform =
            platform.copy(
                model = cleanedModel
            )

        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.value =
                    updatedPlatform

                closeApiModelDialog()

            } catch (_: Exception) {
                /*
                 * Keep existing model if save fails.
                 */
            }
        }
    }

    /*
     * Update provider API URL.
     */
    fun updateApiUrl(
        url: String,
    ) {

        val platform =
            _platformState.value
                ?: return

        val cleanedUrl =
            url
                .trim()
                .trimEnd('/')

        if (
            cleanedUrl.isEmpty()
        ) {
            return
        }

        val updatedPlatform =
            platform.copy(
                apiUrl = cleanedUrl
            )

        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.value =
                    updatedPlatform

                closeApiUrlDialog()

            } catch (_: Exception) {
                /*
                 * Keep previous URL if save fails.
                 */
            }
        }
    }

    /*
     * Update platform display name.
     */
    fun updatePlatformName(
        name: String,
    ) {

        val platform =
            _platformState.value
                ?: return

        val cleanedName =
            name.trim()

        if (
            cleanedName.isEmpty()
        ) {
            return
        }

        val updatedPlatform =
            platform.copy(
                name = cleanedName
            )

        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.value =
                    updatedPlatform

                closePlatformNameDialog()

            } catch (_: Exception) {
                /*
                 * Keep previous platform name if
                 * persistence fails.
                 */
            }
        }
    }

    /*
     * Temperature.
     *
     * Valid range:
     * 0.0 .. 2.0
     */
    fun updateTemperature(
        temperature: Float?,
    ) {

        val platform =
            _platformState.value
                ?: return

        val normalizedTemperature =
            temperature
                ?.coerceIn(
                    0f,
                    2f
                )

        val updatedPlatform =
            platform.copy(
                temperature =
                    normalizedTemperature
            )

        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.value =
                    updatedPlatform

                closeTemperatureDialog()

            } catch (_: Exception) {
                /*
                 * Keep previous value.
                 */
            }
        }
    }

    /*
     * Top P.
     *
     * Valid range:
     * 0.1 .. 1.0
     */
    fun updateTopP(
        topP: Float?,
    ) {

        val platform =
            _platformState.value
                ?: return

        val normalizedTopP =
            topP
                ?.coerceIn(
                    0.1f,
                    1f
                )

        val updatedPlatform =
            platform.copy(
                topP =
                    normalizedTopP
            )

        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.value =
                    updatedPlatform

                closeTopPDialog()

            } catch (_: Exception) {
                /*
                 * Keep previous value.
                 */
            }
        }
    }

    /*
     * System prompt.
     */
    fun updateSystemPrompt(
        prompt: String,
    ) {

        val platform =
            _platformState.value
                ?: return

        val updatedPlatform =
            platform.copy(
                systemPrompt =
                    prompt.trim()
            )

        viewModelScope.launch {

            try {

                settingRepository
                    .updatePlatformV2(
                        updatedPlatform
                    )

                _platformState.value =
                    updatedPlatform

                closeSystemPromptDialog()

            } catch (_: Exception) {
                /*
                 * Keep previous value.
                 */
            }
        }
    }

    /*
     * Platform name dialog.
     */
    fun openPlatformNameDialog() {

        _dialogState.update {
            it.copy(
                isPlatformNameDialogOpen =
                    true
            )
        }
    }

    fun closePlatformNameDialog() {

        _dialogState.update {
            it.copy(
                isPlatformNameDialogOpen =
                    false
            )
        }
    }

    /*
     * API URL dialog.
     */
    fun openApiUrlDialog() {

        _dialogState.update {
            it.copy(
                isApiUrlDialogOpen =
                    true
            )
        }
    }

    fun closeApiUrlDialog() {

        _dialogState.update {
            it.copy(
                isApiUrlDialogOpen =
                    false
            )
        }
    }

    /*
     * API key dialog.
     */
    fun openApiTokenDialog() {

        _dialogState.update {
            it.copy(
                isApiTokenDialogOpen =
                    true
            )
        }
    }

    fun closeApiTokenDialog() {

        _dialogState.update {
            it.copy(
                isApiTokenDialogOpen =
                    false
            )
        }
    }

    /*
     * Model dialog.
     */
    fun openApiModelDialog() {

        _dialogState.update {
            it.copy(
                isApiModelDialogOpen =
                    true
            )
        }
    }

    fun closeApiModelDialog() {

        _dialogState.update {
            it.copy(
                isApiModelDialogOpen =
                    false
            )
        }
    }

    /*
     * Temperature dialog.
     */
    fun openTemperatureDialog() {

        _dialogState.update {
            it.copy(
                isTemperatureDialogOpen =
                    true
            )
        }
    }

    fun closeTemperatureDialog() {

        _dialogState.update {
            it.copy(
                isTemperatureDialogOpen =
                    false
            )
        }
    }

    /*
     * Top P dialog.
     */
    fun openTopPDialog() {

        _dialogState.update {
            it.copy(
                isTopPDialogOpen =
                    true
            )
        }
    }

    fun closeTopPDialog() {

        _dialogState.update {
            it.copy(
                isTopPDialogOpen =
                    false
            )
        }
    }

    /*
     * System prompt dialog.
     */
    fun openSystemPromptDialog() {

        _dialogState.update {
            it.copy(
                isSystemPromptDialogOpen =
                    true
            )
        }
    }

    fun closeSystemPromptDialog() {

        _dialogState.update {
            it.copy(
                isSystemPromptDialogOpen =
                    false
            )
        }
    }

    /*
     * Delete dialog.
     */
    fun openDeleteDialog() {

        _dialogState.update {
            it.copy(
                isDeleteDialogOpen =
                    true
            )
        }
    }

    fun closeDeleteDialog() {

        _dialogState.update {
            it.copy(
                isDeleteDialogOpen =
                    false
            )
        }
    }

    /*
     * Delete platform.
     */
    fun deletePlatform() {

        val platform =
            _platformState.value
                ?: return

        viewModelScope.launch {

            try {

                settingRepository
                    .deletePlatformV2(
                        platform
                    )

                closeDeleteDialog()

                _isDeleted.value =
                    true

            } catch (_: Exception) {
                /*
                 * Do not navigate away if delete
                 * operation fails.
                 */
            }
        }
    }

    data class DialogState(

        val isPlatformNameDialogOpen:
            Boolean = false,

        val isApiUrlDialogOpen:
            Boolean = false,

        val isApiTokenDialogOpen:
            Boolean = false,

        val isApiModelDialogOpen:
            Boolean = false,

        val isTemperatureDialogOpen:
            Boolean = false,

        val isTopPDialogOpen:
            Boolean = false,

        val isSystemPromptDialogOpen:
            Boolean = false,

        val isDeleteDialogOpen:
            Boolean = false,
    )
}

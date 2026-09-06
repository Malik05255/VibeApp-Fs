package com.malik.lmai.presentation.ui.setup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.data.ModelConstants
import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.dto.OpenRouterModel
import com.malik.lmai.data.dto.qwen.request.QwenChatCompletionRequest
import com.malik.lmai.data.dto.qwen.request.QwenChatMessage
import com.malik.lmai.data.dto.qwen.request.qwenTextContent
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.data.model.GoogleAiStudioModelCatalog
import com.malik.lmai.data.network.OpenAIAPI
import com.malik.lmai.data.repository.SettingRepository
import com.malik.lmai.feature.agent.service.AgentErrorMessageFormatter
import com.malik.lmai.feature.ai.FreeAiProviderPreset
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

sealed class SaveStatus {
    data object Idle : SaveStatus()
    data object Saving : SaveStatus()
    data object Success : SaveStatus()
    data class Error(val message: String) : SaveStatus()
}

sealed class ModelsFetchStatus {
    data object Idle : ModelsFetchStatus()
    data object Loading : ModelsFetchStatus()
    data class Success(val models: List<OpenRouterModel>) : ModelsFetchStatus()
    data class Error(val message: String) : ModelsFetchStatus()
}

@HiltViewModel
class SetupViewModelV2 @Inject constructor(
    private val settingRepository: SettingRepository,
    private val openAIAPI: OpenAIAPI,
) : ViewModel() {

    private val _platforms = MutableStateFlow<List<PlatformV2>>(emptyList())
    val platforms: StateFlow<List<PlatformV2>> = _platforms.asStateFlow()

    private val _wizardStep = MutableStateFlow(WIZARD_STEP_BASICS)
    val wizardStep: StateFlow<Int> = _wizardStep.asStateFlow()

    private val _selectedClientType = MutableStateFlow<ClientType?>(null)
    val selectedClientType: StateFlow<ClientType?> = _selectedClientType.asStateFlow()

    private val _providerPreset = MutableStateFlow<FreeAiProviderPreset?>(null)
    val providerPreset: StateFlow<FreeAiProviderPreset?> = _providerPreset.asStateFlow()

    private val _platformName = MutableStateFlow("")
    val platformName: StateFlow<String> = _platformName.asStateFlow()

    private val _apiUrl = MutableStateFlow("")
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model.asStateFlow()

    private val _isFreePlan = MutableStateFlow(true)
    val isFreePlan: StateFlow<Boolean> = _isFreePlan.asStateFlow()

    private val _modelsFetchStatus =
        MutableStateFlow<ModelsFetchStatus>(ModelsFetchStatus.Idle)
    val modelsFetchStatus: StateFlow<ModelsFetchStatus> =
        _modelsFetchStatus.asStateFlow()

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private val _switchedPlatformEvent = MutableSharedFlow<String>()
    val switchedPlatformEvent: SharedFlow<String> =
        _switchedPlatformEvent.asSharedFlow()

    init {
        loadPlatforms()
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            try {
                _platforms.value = settingRepository.fetchPlatformV2s()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load platforms", e)
            }
        }
    }

    fun selectClientType(clientType: ClientType) {
        _selectedClientType.value = clientType
        _providerPreset.value = null
        _platformName.value = getDefaultPlatformName(clientType)
        _apiUrl.value = getDefaultApiUrl(clientType)
        _apiKey.value = ""
        _model.value = getDefaultModel(clientType)
        _isFreePlan.value =
            clientType == ClientType.OPEN_ROUTER ||
                clientType == ClientType.GOOGLE_AI_STUDIO
        resetTransientWizardState()
    }

    fun selectProviderPreset(preset: FreeAiProviderPreset) {
        // Presets use the existing OpenAI-compatible CUSTOM transport. The
        // explicit provider field is persisted separately for deterministic
        // routing and failover classification.
        _selectedClientType.value = ClientType.CUSTOM
        _providerPreset.value = preset
        _platformName.value = preset.displayName
        _apiUrl.value = preset.apiUrl
        _apiKey.value = ""
        _model.value = ""
        _isFreePlan.value = true
        resetTransientWizardState()
    }

    private fun resetTransientWizardState() {
        _modelsFetchStatus.value = ModelsFetchStatus.Idle
        _saveStatus.value = SaveStatus.Idle
        _wizardStep.value = WIZARD_STEP_BASICS
    }

    fun updatePlatformName(name: String) {
        _platformName.value = name
    }

    fun updateApiUrl(url: String) {
        _apiUrl.value = url
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun updateModel(modelName: String) {
        _model.value = modelName
    }

    fun updatePlanType(isFree: Boolean) {
        val provider = _selectedClientType.value
        if (
            provider != ClientType.OPEN_ROUTER &&
            provider != ClientType.GOOGLE_AI_STUDIO
        ) {
            return
        }

        _isFreePlan.value = isFree
        fetchModels()
    }

    /**
     * OpenRouter models are fetched live from OpenRouter.
     * Google AI Studio uses a curated current catalog because Google's model-list
     * endpoint does not include free/paid pricing metadata.
     */
    fun fetchModels() {
        when (_selectedClientType.value) {
            ClientType.OPEN_ROUTER -> fetchOpenRouterModels()
            ClientType.GOOGLE_AI_STUDIO -> loadGoogleModels()
            else -> _modelsFetchStatus.value = ModelsFetchStatus.Idle
        }
    }

    private fun fetchOpenRouterModels() {
        val currentApiKey = normalizeApiKey(_apiKey.value)
        if (currentApiKey.isNullOrBlank()) {
            _modelsFetchStatus.value = ModelsFetchStatus.Error(
                "OpenRouter API key is required"
            )
            return
        }

        viewModelScope.launch {
            _modelsFetchStatus.value = ModelsFetchStatus.Loading
            try {
                val fetchedModels = settingRepository.fetchOpenRouterModels(
                    apiKey = currentApiKey,
                    isFreeOnly = _isFreePlan.value,
                )
                applyFetchedModels(fetchedModels)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch OpenRouter models", e)
                _modelsFetchStatus.value = ModelsFetchStatus.Error(
                    e.message ?: "Failed to fetch model list"
                )
            }
        }
    }

    private fun loadGoogleModels() {
        val models = GoogleAiStudioModelCatalog.models(
            isFreeOnly = _isFreePlan.value
        )
        applyFetchedModels(models)
    }

    private fun applyFetchedModels(models: List<OpenRouterModel>) {
        _modelsFetchStatus.value = ModelsFetchStatus.Success(models)

        if (models.isEmpty()) {
            _model.value = ""
            return
        }

        val currentModel = _model.value.trim()
        val currentModelExists = models.any { it.id == currentModel }
        if (!currentModelExists) {
            _model.value = models.first().id
        }
    }

    fun nextWizardStep() {
        if (!canProceedFromStep(_wizardStep.value)) return

        if (_wizardStep.value == WIZARD_STEP_API_KEY) {
            when (_selectedClientType.value) {
                ClientType.OPEN_ROUTER,
                ClientType.GOOGLE_AI_STUDIO -> fetchModels()

                else -> Unit
            }
        }

        _wizardStep.update {
            minOf(WIZARD_TOTAL_STEPS - 1, it + 1)
        }
    }

    fun previousWizardStep() {
        _wizardStep.update {
            maxOf(WIZARD_STEP_BASICS, it - 1)
        }
    }

    fun resetWizard() {
        _wizardStep.value = WIZARD_STEP_BASICS
        _selectedClientType.value = null
        _providerPreset.value = null
        _platformName.value = ""
        _apiUrl.value = ""
        _apiKey.value = ""
        _model.value = ""
        _isFreePlan.value = true
        _modelsFetchStatus.value = ModelsFetchStatus.Idle
    }

    fun savePlatform() {
        if (_saveStatus.value == SaveStatus.Saving) return

        val clientType = _selectedClientType.value ?: return
        val preset = _providerPreset.value
        val cleanName = _platformName.value.trim()
        val cleanApiUrl = _apiUrl.value.trim().trimEnd('/')
        val cleanModel = _model.value.trim()
        val cleanApiKey = normalizeApiKey(_apiKey.value)

        if (cleanName.isBlank()) {
            _saveStatus.value = SaveStatus.Error("Platform name is required")
            return
        }
        if (cleanApiUrl.isBlank()) {
            _saveStatus.value = SaveStatus.Error("API URL is required")
            return
        }
        if (
            preset == FreeAiProviderPreset.CLOUDFLARE &&
            cleanApiUrl.contains("YOUR_ACCOUNT_ID", ignoreCase = true)
        ) {
            _saveStatus.value = SaveStatus.Error(
                "Replace YOUR_ACCOUNT_ID in the Cloudflare API URL before saving"
            )
            return
        }
        if (cleanModel.isBlank()) {
            _saveStatus.value = SaveStatus.Error("Model ID is required")
            return
        }
        if (isApiKeyRequired() && cleanApiKey.isNullOrBlank()) {
            _saveStatus.value = SaveStatus.Error("API key is required")
            return
        }

        viewModelScope.launch {
            _saveStatus.value = SaveStatus.Saving

            try {
                val connectionError = validateProviderConnection(
                    clientType = clientType,
                    apiUrl = cleanApiUrl,
                    apiKey = cleanApiKey,
                    model = cleanModel,
                )

                if (connectionError != null) {
                    _saveStatus.value = SaveStatus.Error(connectionError)
                    return@launch
                }

                val catalogProvider =
                    clientType == ClientType.OPEN_ROUTER ||
                        clientType == ClientType.GOOGLE_AI_STUDIO

                val providerCode = explicitProviderCode(
                    clientType = clientType,
                    preset = preset,
                )

                val platform = PlatformV2(
                    name = cleanName,
                    compatibleType = clientType,
                    enabled = true,
                    apiUrl = cleanApiUrl,
                    token = cleanApiKey,
                    model = cleanModel,
                    provider = providerCode,
                    isFree = when {
                        preset != null -> true
                        catalogProvider -> _isFreePlan.value
                        else -> null
                    },
                    temperature = 1.0f,
                    topP = 1.0f,
                    systemPrompt = null,
                    stream = true,
                    reasoning = false,
                    timeout = 30,
                )

                val allPlatforms = settingRepository.fetchPlatformV2s()
                val othersEnabled = allPlatforms.filter { it.enabled }

                othersEnabled.forEach { existingPlatform ->
                    settingRepository.updatePlatformV2(
                        existingPlatform.copy(enabled = false)
                    )
                }

                settingRepository.addPlatformV2(platform)

                if (othersEnabled.isNotEmpty()) {
                    _switchedPlatformEvent.emit(platform.name)
                }

                _platforms.value = settingRepository.fetchPlatformV2s()
                _saveStatus.value = SaveStatus.Success
                resetWizard()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save platform", e)
                _saveStatus.value = SaveStatus.Error(
                    AgentErrorMessageFormatter.format(
                        e.message ?: "Unknown error"
                    )
                )
            }
        }
    }

    private fun explicitProviderCode(
        clientType: ClientType,
        preset: FreeAiProviderPreset?,
    ): String? = when {
        preset != null -> preset.code
        clientType == ClientType.OPEN_ROUTER -> "openrouter"
        clientType == ClientType.GOOGLE_AI_STUDIO -> "gemini"
        else -> null
    }

    private suspend fun validateProviderConnection(
        clientType: ClientType,
        apiUrl: String,
        apiKey: String?,
        model: String,
    ): String? {
        return try {
            openAIAPI.setToken(apiKey)
            openAIAPI.setAPIUrl(apiUrl)
            openAIAPI.setProvider(
                type = clientType.name,
                customUrl = apiUrl,
            )

            val response = openAIAPI.completeQwenChatCompletion(
                request = QwenChatCompletionRequest(
                    model = model,
                    messages = listOf(
                        QwenChatMessage(
                            role = "user",
                            content = qwenTextContent("Reply with OK."),
                        )
                    ),
                    stream = false,
                )
            )

            val error = response.error
            if (error == null) {
                null
            } else {
                val rawError = buildString {
                    val code = error.code ?: error.type ?: "error"
                    if (code.all { it.isDigit() }) {
                        append("HTTP ")
                    }
                    append(code)
                    append(": ")
                    append(error.message)
                }
                AgentErrorMessageFormatter.format(rawError)
            }
        } catch (e: Exception) {
            AgentErrorMessageFormatter.format(
                e.message ?: "Unknown network error"
            )
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = SaveStatus.Idle
    }

    fun deletePlatform(platform: PlatformV2) {
        viewModelScope.launch {
            try {
                settingRepository.deletePlatformV2(platform)
                _platforms.value = settingRepository.fetchPlatformV2s()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete platform", e)
            }
        }
    }

    fun isApiKeyRequired(): Boolean =
        _selectedClientType.value != ClientType.CUSTOM ||
            _providerPreset.value != null

    fun canProceedFromStep(step: Int): Boolean =
        when (step) {
            WIZARD_STEP_BASICS ->
                _platformName.value.isNotBlank() &&
                    _apiUrl.value.isNotBlank()

            WIZARD_STEP_API_KEY ->
                !isApiKeyRequired() ||
                    _apiKey.value.isNotBlank()

            WIZARD_STEP_MODEL ->
                _model.value.isNotBlank()

            else -> false
        }

    fun isSetupComplete(): Boolean = _platforms.value.isNotEmpty()

    private fun getDefaultPlatformName(clientType: ClientType): String =
        when (clientType) {
            ClientType.OPEN_ROUTER -> "OpenRouter"
            ClientType.GOOGLE_AI_STUDIO -> "Google AI Studio"
            ClientType.CUSTOM -> "Custom API"
            ClientType.OPENAI -> "OpenAI"
            ClientType.ANTHROPIC -> "Anthropic"
            ClientType.QWEN -> "Qwen"
            ClientType.KIMI -> "Kimi"
            ClientType.MINIMAX -> "MiniMax"
            ClientType.DEEPSEEK -> "DeepSeek"
        }

    private fun getDefaultApiUrl(clientType: ClientType): String =
        when (clientType) {
            ClientType.OPEN_ROUTER -> ModelConstants.OPENROUTER_API_URL
            ClientType.GOOGLE_AI_STUDIO -> ModelConstants.GOOGLE_AI_STUDIO_API_URL
            ClientType.CUSTOM -> ModelConstants.CUSTOM_API_URL
            else -> ""
        }

    private fun getDefaultModel(clientType: ClientType): String =
        when (clientType) {
            ClientType.OPEN_ROUTER -> ""
            ClientType.GOOGLE_AI_STUDIO -> "gemini-3.7-flash"
            ClientType.CUSTOM -> ""
            ClientType.OPENAI -> "gpt-4o"
            ClientType.ANTHROPIC -> "claude-3-5-sonnet"
            ClientType.QWEN -> "qwen-max"
            ClientType.KIMI -> "moonshot-v1-8k"
            ClientType.MINIMAX -> "abab6.5s-chat"
            ClientType.DEEPSEEK -> "deepseek-chat"
        }

    private fun normalizeApiKey(rawApiKey: String): String? {
        val trimmed = rawApiKey.trim()
        if (trimmed.isBlank()) return null

        val normalized = if (
            trimmed.startsWith(
                prefix = "Bearer ",
                ignoreCase = true,
            )
        ) {
            trimmed.substring("Bearer ".length).trim()
        } else {
            trimmed
        }

        return normalized.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "SetupViewModelV2"

        const val WIZARD_STEP_BASICS = 0
        const val WIZARD_STEP_API_KEY = 1
        const val WIZARD_STEP_MODEL = 2
        const val WIZARD_TOTAL_STEPS = 3
    }
}

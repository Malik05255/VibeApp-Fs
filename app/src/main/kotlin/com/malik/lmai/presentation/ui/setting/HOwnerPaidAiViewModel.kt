package com.malik.lmai.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.feature.assistant.HCloudLinkResponse
import com.malik.lmai.feature.assistant.HOwnerPaidAiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

@HiltViewModel
class HOwnerPaidAiViewModel @Inject constructor(
    private val client: HOwnerPaidAiClient,
) : ViewModel() {
    private val _state = MutableStateFlow(HOwnerPaidAiUiState())
    val state: StateFlow<HOwnerPaidAiUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            applyStatus(client.status())
        }
    }

    fun createSetupLink(
        selectedModel: String,
        dailyCallLimit: Int,
        hardTasksOnly: Boolean,
        allowFreeFallback: Boolean,
    ) {
        val model = selectedModel.trim()
        if (model.isEmpty() || model.length > 200 || dailyCallLimit !in 1..100) {
            _state.update { it.copy(error = "invalid_owner_paid_setup") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, pendingSetupUrl = null) }
            val response = client.createSetupLink(
                selectedModel = model,
                dailyCallLimit = dailyCallLimit,
                hardTasksOnly = hardTasksOnly,
                allowFreeFallback = allowFreeFallback,
            )
            if (!response.ok) {
                _state.update {
                    it.copy(loading = false, error = response.errorCode())
                }
                return@launch
            }
            val connectUrl = response.string("connectUrl")
            if (connectUrl.isNullOrBlank()) {
                _state.update { it.copy(loading = false, error = "missing_setup_url") }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    error = null,
                    pendingSetupUrl = connectUrl,
                )
            }
        }
    }

    fun disable() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val response = client.disable()
            if (!response.ok) {
                _state.update { it.copy(loading = false, error = response.errorCode()) }
            } else {
                applyStatus(client.status())
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, pendingSetupUrl = null) }
            val response = client.disconnect()
            if (!response.ok) {
                _state.update { it.copy(loading = false, error = response.errorCode()) }
            } else {
                applyStatus(client.status())
            }
        }
    }

    fun consumeSetupUrl() {
        _state.update { it.copy(pendingSetupUrl = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun applyStatus(response: HCloudLinkResponse) {
        if (!response.ok) {
            _state.update {
                it.copy(
                    loading = false,
                    error = response.errorCode(),
                    linked = response.bool("linked") ?: it.linked,
                )
            }
            return
        }

        _state.update { current ->
            current.copy(
                loading = false,
                linked = response.bool("linked") ?: true,
                connected = response.bool("connected") ?: false,
                enabled = response.bool("enabled") ?: false,
                selectedModel = response.string("selectedModel"),
                hardTasksOnly = response.bool("hardTasksOnly") ?: true,
                allowFreeFallback = response.bool("allowFreeFallback") ?: false,
                dailyCallLimit = response.int("dailyCallLimit") ?: 0,
                callsUsedToday = response.int("callsUsedToday") ?: 0,
                costUsdToday = response.double("costUsdToday") ?: 0.0,
                priceGuard = response.bool("priceGuard") ?: false,
                explicitPriceReview = response.bool("explicitPriceReview") ?: false,
                error = null,
            )
        }
    }
}

data class HOwnerPaidAiUiState(
    val loading: Boolean = false,
    val linked: Boolean = true,
    val connected: Boolean = false,
    val enabled: Boolean = false,
    val selectedModel: String? = null,
    val hardTasksOnly: Boolean = true,
    val allowFreeFallback: Boolean = false,
    val dailyCallLimit: Int = 0,
    val callsUsedToday: Int = 0,
    val costUsdToday: Double = 0.0,
    val priceGuard: Boolean = false,
    val explicitPriceReview: Boolean = false,
    val pendingSetupUrl: String? = null,
    val error: String? = null,
)

private fun HCloudLinkResponse.string(key: String): String? =
    (body[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun HCloudLinkResponse.bool(key: String): Boolean? =
    (body[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun HCloudLinkResponse.int(key: String): Int? =
    (body[key] as? JsonPrimitive)?.content?.toIntOrNull()

private fun HCloudLinkResponse.double(key: String): Double? =
    (body[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

private fun HCloudLinkResponse.errorCode(): String = string("error") ?: "provider_app_failed"

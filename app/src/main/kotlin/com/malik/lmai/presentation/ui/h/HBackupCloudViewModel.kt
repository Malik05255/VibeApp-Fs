package com.malik.lmai.presentation.ui.h

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.feature.assistant.HCloudLinkResponse
import com.malik.lmai.feature.assistant.HCloudManagerClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@HiltViewModel
class HBackupCloudViewModel @Inject constructor(
    private val client: HCloudManagerClient,
) : ViewModel() {
    private val _state = MutableStateFlow(HBackupCloudUiState())
    val state: StateFlow<HBackupCloudUiState> = _state.asStateFlow()

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

    fun createBackupSetupLink() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, pendingSetupUrl = null) }
            val response = client.createBackupSetupLink()
            if (!response.ok) {
                _state.update { it.copy(loading = false, error = response.errorCode()) }
                return@launch
            }
            val url = response.string("connectUrl")
            if (url.isNullOrBlank()) {
                _state.update { it.copy(loading = false, error = "missing_backup_setup_url") }
            } else {
                _state.update { it.copy(loading = false, pendingSetupUrl = url) }
            }
        }
    }

    fun disconnectBackup() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, pendingSetupUrl = null) }
            val response = client.disconnectBackup()
            if (!response.ok) {
                _state.update { it.copy(loading = false, error = response.errorCode()) }
            } else {
                applyStatus(response)
            }
        }
    }

    fun consumeSetupUrl() {
        _state.update { it.copy(pendingSetupUrl = null) }
    }

    private fun applyStatus(response: HCloudLinkResponse) {
        if (!response.ok) {
            _state.update {
                it.copy(
                    loading = false,
                    linked = response.bool("linked") ?: it.linked,
                    error = response.errorCode(),
                )
            }
            return
        }

        val backup = response.objectValue("backup")
        _state.update {
            it.copy(
                loading = false,
                linked = response.bool("linked") ?: true,
                primaryHealthy = response.bool("primaryHealthy") ?: false,
                backupConfigured = response.bool("backupConfigured") ?: false,
                backupReady = response.bool("backupReady") ?: false,
                automaticFailoverReady = response.bool("automaticFailoverReady") ?: false,
                storageBackupReady = backup?.bool("storageBackupReady") ?: false,
                backupHealthy = backup?.bool("healthy") ?: false,
                capacityState = response.string("capacityState") ?: "unknown",
                error = null,
            )
        }
    }
}

data class HBackupCloudUiState(
    val loading: Boolean = false,
    val linked: Boolean = true,
    val primaryHealthy: Boolean = false,
    val backupConfigured: Boolean = false,
    val backupReady: Boolean = false,
    val storageBackupReady: Boolean = false,
    val backupHealthy: Boolean = false,
    val automaticFailoverReady: Boolean = false,
    val capacityState: String = "unknown",
    val pendingSetupUrl: String? = null,
    val error: String? = null,
)

private fun HCloudLinkResponse.objectValue(key: String): JsonObject? = body[key] as? JsonObject
private fun HCloudLinkResponse.string(key: String): String? =
    (body[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
private fun HCloudLinkResponse.bool(key: String): Boolean? =
    (body[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
private fun HCloudLinkResponse.errorCode(): String = string("error") ?: "cloud_manager_failed"
private fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

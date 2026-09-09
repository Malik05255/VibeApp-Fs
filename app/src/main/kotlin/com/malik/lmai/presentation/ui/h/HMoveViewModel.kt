package com.malik.lmai.presentation.ui.h

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.feature.assistant.HCloudLinkClient
import com.malik.lmai.feature.assistant.HCloudLinkResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@HiltViewModel
class HMoveViewModel @Inject constructor(
    private val cloudLinkClient: HCloudLinkClient,
) : ViewModel() {

    enum class Operation {
        NONE,
        EXPORT,
        VALIDATE,
        RESTORE,
    }

    enum class Notice {
        EXPORT_SAVED,
        EXPORT_FAILED,
        RESTORE_COMPLETE,
    }

    data class ValidationSummary(
        val memories: Int,
        val tasks: Int,
        val reminders: Int,
        val learningState: Int,
    )

    data class UiState(
        val operation: Operation = Operation.NONE,
        val validation: ValidationSummary? = null,
        val notice: Notice? = null,
        val error: String? = null,
    ) {
        val busy: Boolean
            get() = operation != Operation.NONE
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    private val mutableExportReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportReady: SharedFlow<String> = mutableExportReady.asSharedFlow()

    private var validatedSnapshot: JsonObject? = null

    fun prepareExport() {
        if (mutableState.value.busy) return
        validatedSnapshot = null
        viewModelScope.launch {
            mutableState.value = UiState(operation = Operation.EXPORT)
            val response = cloudLinkClient.portableSnapshot()
            val snapshot = response.body["snapshot"] as? JsonObject
            if (!response.ok || snapshot == null) {
                mutableState.value = UiState(error = response.errorCode("portable_snapshot_failed"))
                return@launch
            }

            mutableState.value = UiState()
            mutableExportReady.emit(snapshot.toString())
        }
    }

    fun beginImportSelection() {
        if (mutableState.value.busy) return
        validatedSnapshot = null
        mutableState.value = UiState()
    }

    fun reportLocalImportError(code: String) {
        validatedSnapshot = null
        mutableState.value = UiState(error = code)
    }

    fun validateImport(rawText: String) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.value = UiState(operation = Operation.VALIDATE)
            validatedSnapshot = null

            val root = runCatching { json.parseToJsonElement(rawText) }.getOrNull() as? JsonObject
            val snapshot = when {
                root == null -> null
                root["snapshot"] is JsonObject -> root["snapshot"] as JsonObject
                else -> root
            }
            if (snapshot == null) {
                mutableState.value = UiState(error = "portable_snapshot_invalid_json")
                return@launch
            }

            val response = cloudLinkClient.validatePortableRestore(snapshot)
            if (!response.ok) {
                mutableState.value = UiState(error = response.errorCode("portable_restore_validation_failed"))
                return@launch
            }

            val counts = response.body["counts"] as? JsonObject
            validatedSnapshot = snapshot
            mutableState.value = UiState(
                validation = ValidationSummary(
                    memories = counts.intValue("memories"),
                    tasks = counts.intValue("tasks"),
                    reminders = counts.intValue("reminders"),
                    learningState = counts.intValue("learningState"),
                ),
            )
        }
    }

    fun restoreValidatedSnapshot() {
        if (mutableState.value.busy) return
        val snapshot = validatedSnapshot ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                operation = Operation.RESTORE,
                notice = null,
                error = null,
            )
            val response = cloudLinkClient.restorePortableSnapshot(
                snapshot = snapshot,
                confirmation = HCloudLinkClient.PORTABLE_RESTORE_CONFIRMATION,
            )
            if (!response.ok) {
                mutableState.value = mutableState.value.copy(
                    operation = Operation.NONE,
                    error = response.errorCode("portable_restore_failed"),
                )
                return@launch
            }

            validatedSnapshot = null
            mutableState.value = UiState(notice = Notice.RESTORE_COMPLETE)
        }
    }

    fun reportExportResult(success: Boolean) {
        mutableState.value = mutableState.value.copy(
            notice = if (success) Notice.EXPORT_SAVED else Notice.EXPORT_FAILED,
            error = null,
        )
    }

    private fun HCloudLinkResponse.errorCode(fallback: String): String =
        (body["error"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: fallback

    private fun JsonObject?.intValue(key: String): Int =
        ((this?.get(key) as? JsonPrimitive)?.content?.toIntOrNull() ?: 0).coerceAtLeast(0)
}

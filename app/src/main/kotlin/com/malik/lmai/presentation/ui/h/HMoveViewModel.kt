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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@HiltViewModel
class HMoveViewModel @Inject constructor(
    private val cloudLinkClient: HCloudLinkClient,
) : ViewModel() {

    enum class Operation { NONE, EXPORT, VALIDATE, RESTORE }
    enum class Notice { EXPORT_SAVED, EXPORT_FAILED, RESTORE_COMPLETE }

    data class ValidationSummary(
        val memories: Int,
        val tasks: Int,
        val reminders: Int,
        val contacts: Int,
        val learningState: Int,
    )

    data class UiState(
        val operation: Operation = Operation.NONE,
        val validation: ValidationSummary? = null,
        val notice: Notice? = null,
        val error: String? = null,
    ) {
        val busy: Boolean get() = operation != Operation.NONE
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    private val mutableExportReady = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportReady: SharedFlow<String> = mutableExportReady.asSharedFlow()

    private var validatedSnapshot: JsonObject? = null
    private var validatedV3ImportSessionId: String? = null

    fun prepareExport() {
        if (mutableState.value.busy) return
        clearValidatedImport()
        viewModelScope.launch {
            mutableState.value = UiState(operation = Operation.EXPORT)
            val legacy = cloudLinkClient.portableSnapshot()
            val snapshot = legacy.body["snapshot"] as? JsonObject
            if (legacy.ok && snapshot != null) {
                mutableState.value = UiState()
                mutableExportReady.emit(snapshot.toString())
                return@launch
            }

            val requiresV3 = legacy.statusCode == 409 &&
                legacy.errorCode("") == "portable_snapshot_requires_pagination"
            if (!requiresV3) {
                mutableState.value = UiState(error = legacy.errorCode("portable_snapshot_failed"))
                return@launch
            }

            val v3 = exportPortableV3()
            if (v3 == null) {
                if (mutableState.value.error == null) {
                    mutableState.value = UiState(error = "portable_v3_export_failed")
                }
                return@launch
            }
            mutableState.value = UiState()
            mutableExportReady.emit(v3)
        }
    }

    private suspend fun exportPortableV3(): String? {
        val begin = cloudLinkClient.beginPortableV3Export(V3_PAGE_SIZE)
        val session = begin.body["session"] as? JsonObject
        if (!begin.ok || session == null) {
            mutableState.value = UiState(error = begin.errorCode("portable_v3_begin_failed"))
            return null
        }
        val sessionId = session.stringValue("sessionId") ?: run {
            mutableState.value = UiState(error = "portable_v3_session_invalid")
            return null
        }
        val pageSize = session.intValue("pageSize").takeIf { it in 1..500 } ?: V3_PAGE_SIZE
        val counts = session["counts"] as? JsonObject ?: run {
            mutableState.value = UiState(error = "portable_v3_counts_invalid")
            return null
        }

        val pages = mutableListOf<JsonObject>()
        for ((section, countKey) in V3_SECTIONS) {
            val count = counts.intValue(countKey)
            val pageCount = if (count == 0) 0 else (count + pageSize - 1) / pageSize
            for (pageIndex in 0 until pageCount) {
                val response = cloudLinkClient.portableV3Page(sessionId, section, pageIndex)
                val page = response.body["page"] as? JsonObject
                if (!response.ok || page == null) {
                    mutableState.value = UiState(error = response.errorCode("portable_v3_page_failed"))
                    return null
                }
                pages += page
            }
        }

        val manifestResponse = cloudLinkClient.portableV3Manifest(sessionId)
        val manifest = manifestResponse.body["manifest"] as? JsonObject
        if (!manifestResponse.ok || manifest == null) {
            mutableState.value = UiState(error = manifestResponse.errorCode("portable_v3_manifest_failed"))
            return null
        }

        return buildJsonObject {
            put("format", "h-portable-bundle")
            put("schemaVersion", 3)
            put("manifest", manifest)
            put("pages", JsonArray(pages))
        }.toString()
    }

    fun beginImportSelection() {
        if (mutableState.value.busy) return
        clearValidatedImport()
        mutableState.value = UiState()
    }

    fun reportLocalImportError(code: String) {
        clearValidatedImport()
        mutableState.value = UiState(error = code)
    }

    fun validateImport(rawText: String) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.value = UiState(operation = Operation.VALIDATE)
            clearValidatedImport()

            val root = runCatching { json.parseToJsonElement(rawText) }.getOrNull() as? JsonObject
            if (root == null) {
                mutableState.value = UiState(error = "portable_snapshot_invalid_json")
                return@launch
            }

            if (root.stringValue("format") == "h-portable-bundle" && root.intValue("schemaVersion") == 3) {
                validatePortableV3Bundle(root)
                return@launch
            }

            val snapshot = when {
                root["snapshot"] is JsonObject -> root["snapshot"] as JsonObject
                else -> root
            }
            val response = cloudLinkClient.validatePortableRestore(snapshot)
            if (!response.ok) {
                mutableState.value = UiState(error = response.errorCode("portable_restore_validation_failed"))
                return@launch
            }

            val counts = response.body["counts"] as? JsonObject
            validatedSnapshot = snapshot
            mutableState.value = UiState(validation = counts.toSummary())
        }
    }

    private suspend fun validatePortableV3Bundle(bundle: JsonObject) {
        val manifest = bundle["manifest"] as? JsonObject
        val pages = bundle["pages"] as? JsonArray
        if (manifest == null || pages == null || pages.any { it !is JsonObject }) {
            mutableState.value = UiState(error = "portable_v3_bundle_invalid")
            return
        }

        val begin = cloudLinkClient.beginPortableV3Restore(manifest)
        val importSession = begin.body["importSession"] as? JsonObject
        val importSessionId = importSession?.stringValue("importSessionId")
        if (!begin.ok || importSessionId == null) {
            mutableState.value = UiState(error = begin.errorCode("portable_v3_restore_begin_failed"))
            return
        }

        for (element in pages) {
            val page = element as JsonObject
            val staged = cloudLinkClient.stagePortableV3RestorePage(importSessionId, page)
            if (!staged.ok) {
                mutableState.value = UiState(error = staged.errorCode("portable_v3_restore_page_failed"))
                return
            }
        }

        val counts = begin.body["counts"] as? JsonObject
        validatedV3ImportSessionId = importSessionId
        mutableState.value = UiState(validation = counts.toSummary())
    }

    fun restoreValidatedSnapshot() {
        if (mutableState.value.busy) return
        val legacySnapshot = validatedSnapshot
        val v3Session = validatedV3ImportSessionId
        if (legacySnapshot == null && v3Session == null) return

        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                operation = Operation.RESTORE,
                notice = null,
                error = null,
            )
            val response = if (v3Session != null) {
                cloudLinkClient.restorePortableV3(
                    importSessionId = v3Session,
                    confirmation = HCloudLinkClient.PORTABLE_RESTORE_V3_CONFIRMATION,
                )
            } else {
                cloudLinkClient.restorePortableSnapshot(
                    snapshot = checkNotNull(legacySnapshot),
                    confirmation = HCloudLinkClient.PORTABLE_RESTORE_CONFIRMATION,
                )
            }
            if (!response.ok) {
                mutableState.value = mutableState.value.copy(
                    operation = Operation.NONE,
                    error = response.errorCode("portable_restore_failed"),
                )
                return@launch
            }

            clearValidatedImport()
            mutableState.value = UiState(notice = Notice.RESTORE_COMPLETE)
        }
    }

    fun reportExportResult(success: Boolean) {
        mutableState.value = mutableState.value.copy(
            notice = if (success) Notice.EXPORT_SAVED else Notice.EXPORT_FAILED,
            error = null,
        )
    }

    private fun clearValidatedImport() {
        validatedSnapshot = null
        validatedV3ImportSessionId = null
    }

    private fun JsonObject?.toSummary(): ValidationSummary = ValidationSummary(
        memories = this.intValue("memories"),
        tasks = this.intValue("tasks"),
        reminders = this.intValue("reminders"),
        contacts = this.intValue("contacts"),
        learningState = this.intValue("learningState"),
    )

    private fun HCloudLinkResponse.errorCode(fallback: String): String =
        (body["error"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: fallback

    private fun JsonObject?.intValue(key: String): Int =
        ((this?.get(key) as? JsonPrimitive)?.content?.toIntOrNull() ?: 0).coerceAtLeast(0)

    private fun JsonObject?.stringValue(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        private const val V3_PAGE_SIZE = 200
        private val V3_SECTIONS = listOf(
            "memories" to "memories",
            "tasks" to "tasks",
            "reminders" to "reminders",
            "contacts" to "contacts",
            "learning" to "learningState",
        )
    }
}

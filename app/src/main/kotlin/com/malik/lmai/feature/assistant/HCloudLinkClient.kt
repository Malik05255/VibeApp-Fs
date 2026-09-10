package com.malik.lmai.feature.assistant

import com.malik.lmai.presentation.ui.auth.GoogleIdTokenProvider
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Owner-only bridge between the Android H identity and the shared H cloud runtime.
 *
 * Authentication uses a current Google ID token supplied by GoogleIdTokenProvider.
 * H_RUNTIME_SECRET and Supabase service credentials never enter the APK. Linked runtime
 * operations are routed only after an owner-authenticated preflight. Once an operation is
 * dispatched to a selected runtime it is never replayed automatically on the alternate
 * cloud after a transport failure, preventing ambiguous duplicate writes.
 */
@Singleton
class HCloudLinkClient @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val runtimeRouteSelector: HRuntimeRouteSelector,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startLink(): HCloudLinkResponse = post(
        action = "create_pairing",
        routeBeforeDispatch = false,
    )

    suspend fun finishLink(pairingCode: String): HCloudLinkResponse = post(
        action = "finalize_pairing",
        extra = buildJsonObject {
            put("pairing_code", pairingCode.trim())
        },
        routeBeforeDispatch = false,
    )

    // Link status must remain available before the Google identity has completed owner
    // pairing, so it intentionally talks to Primary directly.
    suspend fun status(): HCloudLinkResponse = post(
        action = "status",
        routeBeforeDispatch = false,
    )

    suspend fun snapshot(): HCloudLinkResponse = post("snapshot")

    /**
     * Low-latency snapshot used only while preparing an interactive H model turn.
     * HttpURLConnection is blocking, so coroutine timeout alone is insufficient; the
     * socket connection/read deadlines are intentionally short here as well.
     */
    suspend fun snapshotForInteractiveContext(): HCloudLinkResponse = post(
        action = "snapshot",
        connectTimeoutMs = INTERACTIVE_CONNECT_TIMEOUT_MS,
        readTimeoutMs = INTERACTIVE_READ_TIMEOUT_MS,
    )

    /**
     * Uploads only H's bounded aggregate learning profile. The payload type cannot carry
     * raw prompts, model replies, attachment contents, credentials, or provider history.
     */
    suspend fun syncLearningState(state: HCloudLearningState): HCloudLinkResponse = post(
        action = "learning_seed",
        extra = buildJsonObject {
            put("baseline", state.toBaselineJson())
        },
        connectTimeoutMs = LEARNING_SYNC_CONNECT_TIMEOUT_MS,
        readTimeoutMs = LEARNING_SYNC_READ_TIMEOUT_MS,
    )

    /**
     * Sends one bounded attachment to H's transient media endpoint. Route selection occurs
     * before upload; an ambiguous upload failure is never replayed on the other cloud.
     */
    suspend fun analyzeEphemeralMedia(
        kind: String,
        mimeType: String,
        fileName: String?,
        caption: String?,
        base64: String,
        durationMs: Long? = null,
    ): HCloudLinkResponse = post(
        action = "analyze_ephemeral_media",
        extra = buildJsonObject {
            put("kind", kind.trim().lowercase())
            put("mime_type", mimeType.trim().lowercase())
            fileName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("file_name", it) }
            caption?.trim()?.takeIf { it.isNotEmpty() }?.let { put("caption", it) }
            put("base64", base64)
            durationMs?.takeIf { it > 0L }?.let { put("duration_ms", it) }
        },
        connectTimeoutMs = MEDIA_SYNC_CONNECT_TIMEOUT_MS,
        readTimeoutMs = MEDIA_SYNC_READ_TIMEOUT_MS,
        endpoint = MEDIA_SYNC_URL,
    )

    suspend fun remember(
        text: String,
        category: String = "general",
        originalText: String? = null,
    ): HCloudLinkResponse = post(
        action = "remember",
        extra = buildJsonObject {
            put("text", text.trim())
            put("category", category.trim().lowercase())
            originalText?.trim()?.takeIf { it.isNotEmpty() }?.let {
                put("original_text", it)
            }
        },
    )

    /**
     * Shared reminder transport. Reminder ownership and app/WhatsApp delivery separation
     * are enforced by h-reminder-sync in the H Cloud Core, not by the UI.
     */
    suspend fun reminderSync(
        action: String,
        extra: JsonObject = buildJsonObject {},
    ): HCloudLinkResponse = post(
        action = action,
        extra = extra,
        endpoint = REMINDER_SYNC_URL,
    )

    /**
     * Exports H-owned portable core state directly to the authenticated app. The endpoint
     * never routes the snapshot through a provider/model and excludes credentials, routing
     * identity, transcripts, raw media, and transient media derivatives by schema.
     */
    suspend fun portableSnapshot(): HCloudLinkResponse = post(
        action = null,
        endpoint = PORTABLE_SNAPSHOT_URL,
        connectTimeoutMs = PORTABLE_CONNECT_TIMEOUT_MS,
        readTimeoutMs = PORTABLE_READ_TIMEOUT_MS,
    )

    /** Validate integrity/schema on the selected H runtime without writing any H state. */
    suspend fun validatePortableRestore(snapshot: JsonObject): HCloudLinkResponse = post(
        action = null,
        extra = buildJsonObject {
            put("mode", "validate")
            put("snapshot", snapshot)
        },
        endpoint = PORTABLE_RESTORE_URL,
        connectTimeoutMs = PORTABLE_CONNECT_TIMEOUT_MS,
        readTimeoutMs = PORTABLE_READ_TIMEOUT_MS,
    )

    /**
     * Executes the merge-only atomic restore after explicit owner confirmation. Selection
     * happens before dispatch and this mutation is never replayed on the alternate cloud.
     */
    suspend fun restorePortableSnapshot(
        snapshot: JsonObject,
        confirmation: String,
    ): HCloudLinkResponse = post(
        action = null,
        extra = buildJsonObject {
            put("mode", "restore")
            put("snapshot", snapshot)
            put("confirmation", confirmation)
        },
        endpoint = PORTABLE_RESTORE_URL,
        connectTimeoutMs = PORTABLE_CONNECT_TIMEOUT_MS,
        readTimeoutMs = PORTABLE_RESTORE_READ_TIMEOUT_MS,
    )

    private suspend fun post(
        action: String?,
        extra: JsonObject = buildJsonObject {},
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        endpoint: String = SYNC_URL,
        routeBeforeDispatch: Boolean = true,
    ): HCloudLinkResponse = withContext(Dispatchers.IO) {
        val token = googleIdTokenProvider.getToken()
            ?: return@withContext HCloudLinkResponse.localError(
                if (googleIdTokenProvider.hasSignedInSession()) {
                    "google_token_refresh_failed"
                } else {
                    "google_sign_in_required"
                }
            )

        val payload = buildJsonObject {
            action?.trim()?.takeIf { it.isNotEmpty() }?.let {
                put("action", JsonPrimitive(it))
            }
            extra.forEach { (key, value) -> put(key, value) }
        }

        val first = executeSelectedPost(
            primaryEndpoint = endpoint,
            token = token,
            payload = payload,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            routeBeforeDispatch = routeBeforeDispatch,
        )
        if (first.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return@withContext first
        }

        // 401 is rejected before owner-scoped execution. Refresh authentication exactly
        // once; transport/5xx failures after dispatch are never retried on another cloud.
        val refreshed = googleIdTokenProvider.getToken(forceRefresh = true)
            ?: return@withContext HCloudLinkResponse.localError("google_token_refresh_failed")
        if (refreshed == token) return@withContext first

        executeSelectedPost(
            primaryEndpoint = endpoint,
            token = refreshed,
            payload = payload,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            routeBeforeDispatch = routeBeforeDispatch,
        )
    }

    private fun executeSelectedPost(
        primaryEndpoint: String,
        token: String,
        payload: JsonObject,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        routeBeforeDispatch: Boolean,
    ): HCloudLinkResponse {
        val selectedEndpoint = if (routeBeforeDispatch) {
            when (val selection = runtimeRouteSelector.selectEndpoint(token, primaryEndpoint)) {
                is HRuntimeRouteSelection.Selected -> selection.endpoint
                is HRuntimeRouteSelection.Rejected -> return selection.response
            }
        } else {
            primaryEndpoint
        }

        // IMPORTANT: exactly one operational dispatch. Do not add fallback/replay here.
        return executePost(
            endpoint = selectedEndpoint,
            token = token,
            payload = payload,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
        )
    }

    private fun executePost(
        endpoint: String,
        token: String,
        payload: JsonObject,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HCloudLinkResponse {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse {
                    buildJsonObject {
                        put("ok", false)
                        put("error", "invalid_cloud_response")
                    }
                }
            HCloudLinkResponse(status, body)
        } catch (error: Exception) {
            HCloudLinkResponse.localError(error.message ?: "cloud_link_failed")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        // Supabase project/function URLs are public routing metadata, not credentials.
        // Authentication still requires a verified Google token + owner WhatsApp pairing.
        private const val PRIMARY_BASE_URL = HRuntimeRouteSelector.PRIMARY_BASE_URL
        private const val SYNC_URL = "$PRIMARY_BASE_URL/functions/v1/h-app-sync"
        private const val MEDIA_SYNC_URL = "$PRIMARY_BASE_URL/functions/v1/h-app-media"
        private const val REMINDER_SYNC_URL = "$PRIMARY_BASE_URL/functions/v1/h-reminder-sync"
        private const val PORTABLE_SNAPSHOT_URL = "$PRIMARY_BASE_URL/functions/v1/h-portable-snapshot"
        private const val PORTABLE_RESTORE_URL = "$PRIMARY_BASE_URL/functions/v1/h-portable-restore"

        const val PORTABLE_RESTORE_CONFIRMATION = "RESTORE_H_PORTABLE_V1"

        private const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
        private const val DEFAULT_READ_TIMEOUT_MS = 15_000
        private const val INTERACTIVE_CONNECT_TIMEOUT_MS = 800
        private const val INTERACTIVE_READ_TIMEOUT_MS = 800
        private const val LEARNING_SYNC_CONNECT_TIMEOUT_MS = 1_500
        private const val LEARNING_SYNC_READ_TIMEOUT_MS = 2_000
        private const val MEDIA_SYNC_CONNECT_TIMEOUT_MS = 10_000
        private const val MEDIA_SYNC_READ_TIMEOUT_MS = 60_000
        private const val PORTABLE_CONNECT_TIMEOUT_MS = 10_000
        private const val PORTABLE_READ_TIMEOUT_MS = 30_000
        private const val PORTABLE_RESTORE_READ_TIMEOUT_MS = 60_000
    }
}

data class HCloudLinkResponse(
    val statusCode: Int,
    val body: JsonObject,
) {
    val ok: Boolean
        get() = (body["ok"] as? JsonPrimitive)?.content == "true"

    companion object {
        fun localError(message: String) = HCloudLinkResponse(
            statusCode = 0,
            body = buildJsonObject {
                put("ok", false)
                put("error", message)
            },
        )
    }
}

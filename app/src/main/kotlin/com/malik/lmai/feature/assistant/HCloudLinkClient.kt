package com.malik.lmai.feature.assistant

import com.malik.lmai.presentation.ui.auth.GoogleIdTokenProvider
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
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
 * Runtime/service credentials never enter the APK. When a validated standby exists,
 * routeable H operations use preflight-before-execution: Android never retries a request
 * on another cloud after execution may already have started.
 */
@Singleton
class HCloudLinkClient @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val runtimeRouteStore: HRuntimeRouteStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startLink(): HCloudLinkResponse = post("create_pairing")

    suspend fun finishLink(pairingCode: String): HCloudLinkResponse = post(
        action = "finalize_pairing",
        extra = buildJsonObject { put("pairing_code", pairingCode.trim()) },
    )

    suspend fun status(): HCloudLinkResponse = post("status")

    suspend fun snapshot(): HCloudLinkResponse = post("snapshot")

    suspend fun snapshotForInteractiveContext(): HCloudLinkResponse = post(
        action = "snapshot",
        connectTimeoutMs = INTERACTIVE_CONNECT_TIMEOUT_MS,
        readTimeoutMs = INTERACTIVE_READ_TIMEOUT_MS,
    )

    suspend fun syncLearningState(state: HCloudLearningState): HCloudLinkResponse = post(
        action = "learning_seed",
        extra = buildJsonObject { put("baseline", state.toBaselineJson()) },
        connectTimeoutMs = LEARNING_SYNC_CONNECT_TIMEOUT_MS,
        readTimeoutMs = LEARNING_SYNC_READ_TIMEOUT_MS,
    )

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
            originalText?.trim()?.takeIf { it.isNotEmpty() }?.let { put("original_text", it) }
        },
    )

    suspend fun reminderSync(
        action: String,
        extra: JsonObject = buildJsonObject {},
    ): HCloudLinkResponse = post(
        action = action,
        extra = extra,
        endpoint = REMINDER_SYNC_URL,
    )

    /** Administrative portable operations intentionally remain primary-only during failover. */
    suspend fun portableSnapshot(): HCloudLinkResponse = post(
        action = null,
        endpoint = PORTABLE_SNAPSHOT_URL,
        connectTimeoutMs = PORTABLE_CONNECT_TIMEOUT_MS,
        readTimeoutMs = PORTABLE_READ_TIMEOUT_MS,
    )

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
            action?.trim()?.takeIf { it.isNotEmpty() }?.let { put("action", JsonPrimitive(it)) }
            extra.forEach { (key, value) -> put(key, value) }
        }

        val first = executeRoutedPost(endpoint, token, payload, connectTimeoutMs, readTimeoutMs)
        if (first.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED) return@withContext first

        val refreshed = googleIdTokenProvider.getToken(forceRefresh = true)
            ?: return@withContext HCloudLinkResponse.localError("google_token_refresh_failed")
        if (refreshed == token) return@withContext first

        executeRoutedPost(endpoint, refreshed, payload, connectTimeoutMs, readTimeoutMs)
    }

    private suspend fun executeRoutedPost(
        primaryEndpoint: String,
        token: String,
        payload: JsonObject,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HCloudLinkResponse {
        if (!isRuntimeFailoverRouteable(primaryEndpoint)) {
            return executePost(primaryEndpoint, token, payload, connectTimeoutMs, readTimeoutMs)
        }

        val standbyEndpoint = runtimeRouteStore.standbyFunctionUrl(primaryEndpoint)
            ?: return executePost(primaryEndpoint, token, payload, connectTimeoutMs, readTimeoutMs)

        if (runtimeRouteStore.isStandbyStickyActive()) {
            return executePost(standbyEndpoint, token, payload, connectTimeoutMs, readTimeoutMs)
        }

        // Preflight uses a read-only status call. We never send the real request to primary
        // and then retry it on standby because the first execution might have committed.
        val primaryPreflight = executePost(
            endpoint = SYNC_URL,
            token = token,
            payload = buildJsonObject { put("action", "status") },
            connectTimeoutMs = ROUTE_PREFLIGHT_CONNECT_TIMEOUT_MS,
            readTimeoutMs = ROUTE_PREFLIGHT_READ_TIMEOUT_MS,
        )
        if (primaryPreflight.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) return primaryPreflight
        if (primaryPreflight.statusCode != 0 && primaryPreflight.statusCode < 500) {
            return executePost(primaryEndpoint, token, payload, connectTimeoutMs, readTimeoutMs)
        }

        val routeUrl = runtimeRouteStore.standbyRouteUrl()
            ?: return HCloudLinkResponse.localError("no_validated_h_runtime_available")
        val routeStatus = executePost(
            endpoint = routeUrl,
            token = token,
            payload = buildJsonObject { put("action", "status") },
            connectTimeoutMs = ROUTE_PREFLIGHT_CONNECT_TIMEOUT_MS,
            readTimeoutMs = ROUTE_PREFLIGHT_READ_TIMEOUT_MS,
        )
        if (routeStatus.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) return routeStatus
        if (routeStatus.ok && routeStatus.boolean("activeReady")) {
            runtimeRouteStore.markStandbyActive()
            return executePost(standbyEndpoint, token, payload, connectTimeoutMs, readTimeoutMs)
        }
        if (!routeStatus.ok || !routeStatus.boolean("preflightReady")) {
            return HCloudLinkResponse.localError("no_validated_h_runtime_available")
        }

        val requestId = UUID.randomUUID().toString()
        val promotion = executePost(
            endpoint = routeUrl,
            token = token,
            payload = buildJsonObject {
                put("action", "promote_request_only")
                put("request_id", requestId)
            },
            connectTimeoutMs = ROUTE_PROMOTION_CONNECT_TIMEOUT_MS,
            readTimeoutMs = ROUTE_PROMOTION_READ_TIMEOUT_MS,
        )
        if (promotion.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) return promotion
        if (
            !promotion.ok ||
            !promotion.boolean("activeReady") ||
            !promotion.boolean("promoted") ||
            promotion.string("mode") != "request_only" ||
            promotion.string("requestId") != requestId ||
            promotion.boolean("schedulerActive") ||
            promotion.boolean("autonomousOutboundActive")
        ) {
            return HCloudLinkResponse.localError("standby_promotion_not_confirmed")
        }

        runtimeRouteStore.markStandbyActive()
        return executePost(standbyEndpoint, token, payload, connectTimeoutMs, readTimeoutMs)
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
            connection.outputStream.use { output -> output.write(payload.toString().toByteArray(Charsets.UTF_8)) }
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

    private fun isRuntimeFailoverRouteable(endpoint: String): Boolean =
        endpoint == SYNC_URL || endpoint == MEDIA_SYNC_URL || endpoint == REMINDER_SYNC_URL

    private fun HCloudLinkResponse.boolean(key: String): Boolean =
        (body[key] as? JsonPrimitive)?.content == "true"

    private fun HCloudLinkResponse.string(key: String): String? =
        (body[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        private const val SYNC_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-app-sync"
        private const val MEDIA_SYNC_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-app-media"
        private const val REMINDER_SYNC_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-reminder-sync"
        private const val PORTABLE_SNAPSHOT_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-portable-snapshot"
        private const val PORTABLE_RESTORE_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-portable-restore"

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
        private const val ROUTE_PREFLIGHT_CONNECT_TIMEOUT_MS = 900
        private const val ROUTE_PREFLIGHT_READ_TIMEOUT_MS = 900
        private const val ROUTE_PROMOTION_CONNECT_TIMEOUT_MS = 2_500
        private const val ROUTE_PROMOTION_READ_TIMEOUT_MS = 4_000
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

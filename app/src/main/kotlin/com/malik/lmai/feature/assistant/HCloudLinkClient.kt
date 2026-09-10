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
 * H_RUNTIME_SECRET and Supabase service credentials never enter the APK. The server
 * requires a one-time owner WhatsApp pairing before this Google identity can read or
 * explicitly save shared H state or use owner-scoped transient cloud media capacity.
 *
 * When a validated standby has previously been configured, Android performs a side-effect
 * free primary preflight before an H operation. Only an availability failure before the
 * real request may trigger request-only standby promotion. Once promoted, the public
 * standby route is pinned locally and there is no automatic failback. A real request is
 * never retried on another cloud after its execution may have started.
 */
@Singleton
class HCloudLinkClient @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val routeStore: HCloudRouteStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startLink(): HCloudLinkResponse = post(
        action = "create_pairing",
        allowFailover = false,
    )

    suspend fun finishLink(pairingCode: String): HCloudLinkResponse = post(
        action = "finalize_pairing",
        extra = buildJsonObject {
            put("pairing_code", pairingCode.trim())
        },
        allowFailover = false,
    )

    suspend fun status(): HCloudLinkResponse = post("status")

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
     * Sends one bounded attachment to H's transient media endpoint. The server never
     * persists the raw payload and refuses paid fallback. Android is responsible for
     * local reduction/compression and the three-minute audio/video guard before calling.
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

    /** Validate integrity/schema on the target H cloud without writing any H state. */
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
     * Executes the merge-only atomic restore after the caller has obtained explicit owner
     * confirmation. The server independently rejects any other confirmation value.
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
        allowFailover: Boolean = true,
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

        val first = executeWithToken(
            primaryEndpoint = endpoint,
            token = token,
            payload = payload,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            allowFailover = allowFailover,
        )
        if (first.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return@withContext first
        }

        // HTTP 401 is an explicit non-execution response, so refreshing the Google token
        // and retrying once is safe. Transport/5xx failures from the real operation are
        // never retried on a different cloud because execution may already have started.
        val refreshed = googleIdTokenProvider.getToken(forceRefresh = true)
            ?: return@withContext HCloudLinkResponse.localError("google_token_refresh_failed")
        if (refreshed == token) return@withContext first

        executeWithToken(
            primaryEndpoint = endpoint,
            token = refreshed,
            payload = payload,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            allowFailover = allowFailover,
        )
    }

    private fun executeWithToken(
        primaryEndpoint: String,
        token: String,
        payload: JsonObject,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        allowFailover: Boolean,
    ): HCloudLinkResponse {
        val resolution = resolveEndpointBeforeExecution(
            primaryEndpoint = primaryEndpoint,
            token = token,
            allowFailover = allowFailover,
        )
        resolution.failure?.let { return it }
        val resolvedEndpoint = resolution.endpoint ?: return HCloudLinkResponse.localError("h_route_unavailable")

        return executePost(
            endpoint = resolvedEndpoint,
            token = token,
            payload = payload,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs,
        )
    }

    private fun resolveEndpointBeforeExecution(
        primaryEndpoint: String,
        token: String,
        allowFailover: Boolean,
    ): RouteResolution {
        if (!allowFailover) return RouteResolution(endpoint = primaryEndpoint)

        routeStore.pinnedStandbyFunctionBase()?.let { standbyBase ->
            return RouteResolution(endpoint = endpointForFunction(standbyBase, primaryEndpoint))
        }

        val standbyBase = routeStore.standbyFunctionBase()
            ?: return RouteResolution(endpoint = primaryEndpoint)

        val primaryProbe = executePost(
            endpoint = SYNC_URL,
            token = token,
            payload = STATUS_PAYLOAD,
            connectTimeoutMs = PREFLIGHT_CONNECT_TIMEOUT_MS,
            readTimeoutMs = PREFLIGHT_READ_TIMEOUT_MS,
        )
        if (!isAvailabilityFailure(primaryProbe.statusCode)) {
            return RouteResolution(endpoint = primaryEndpoint)
        }

        val standbyProbe = executePost(
            endpoint = "$standbyBase/h-app-sync",
            token = token,
            payload = STATUS_PAYLOAD,
            connectTimeoutMs = PREFLIGHT_CONNECT_TIMEOUT_MS,
            readTimeoutMs = PREFLIGHT_READ_TIMEOUT_MS,
        )
        if (standbyProbe.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return RouteResolution(failure = standbyProbe)
        }
        if (!standbyProbe.ok || !jsonBoolean(standbyProbe.body, "linked")) {
            return RouteResolution(
                failure = if (standbyProbe.statusCode in 400..499) {
                    standbyProbe
                } else {
                    HCloudLinkResponse.localError("standby_identity_not_ready")
                },
            )
        }

        val requestId = UUID.randomUUID().toString().replace("-", "")
        val promotion = executePost(
            endpoint = "$standbyBase/h-standby-promote",
            token = token,
            payload = buildJsonObject {
                put("mode", "request_only")
                put("request_id", requestId)
            },
            connectTimeoutMs = PROMOTION_CONNECT_TIMEOUT_MS,
            readTimeoutMs = PROMOTION_READ_TIMEOUT_MS,
        )
        if (promotion.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return RouteResolution(failure = promotion)
        }
        if (!validPromotionResponse(promotion, requestId)) {
            return RouteResolution(
                failure = if (promotion.statusCode != 0) {
                    promotion
                } else {
                    HCloudLinkResponse.localError("standby_promotion_unavailable")
                },
            )
        }

        val projectEndpoint = projectEndpointFromFunctionBase(standbyBase)
            ?: return RouteResolution(failure = HCloudLinkResponse.localError("invalid_standby_route"))
        if (!routeStore.pinStandby(projectEndpoint)) {
            return RouteResolution(failure = HCloudLinkResponse.localError("invalid_standby_route"))
        }

        return RouteResolution(endpoint = endpointForFunction(standbyBase, primaryEndpoint))
    }

    private fun validPromotionResponse(response: HCloudLinkResponse, requestId: String): Boolean {
        return response.ok &&
            jsonBoolean(response.body, "promoted") &&
            jsonBoolean(response.body, "active") &&
            jsonString(response.body, "mode") == "request_only" &&
            jsonString(response.body, "requestId") == requestId &&
            jsonBoolean(response.body, "replicaWritesFenced") &&
            !jsonBoolean(response.body, "schedulerActive") &&
            !jsonBoolean(response.body, "autonomousOutboundActive")
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

    private data class RouteResolution(
        val endpoint: String? = null,
        val failure: HCloudLinkResponse? = null,
    )

    companion object {
        // Supabase project/function URLs are public routing metadata, not credentials.
        // Authentication still requires a verified Google token + owner WhatsApp pairing.
        private const val PRIMARY_FUNCTION_BASE =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1"
        private const val SYNC_URL = "$PRIMARY_FUNCTION_BASE/h-app-sync"
        private const val MEDIA_SYNC_URL = "$PRIMARY_FUNCTION_BASE/h-app-media"
        private const val REMINDER_SYNC_URL = "$PRIMARY_FUNCTION_BASE/h-reminder-sync"
        private const val PORTABLE_SNAPSHOT_URL = "$PRIMARY_FUNCTION_BASE/h-portable-snapshot"
        private const val PORTABLE_RESTORE_URL = "$PRIMARY_FUNCTION_BASE/h-portable-restore"

        private val STATUS_PAYLOAD = buildJsonObject { put("action", "status") }

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
        private const val PREFLIGHT_CONNECT_TIMEOUT_MS = 1_000
        private const val PREFLIGHT_READ_TIMEOUT_MS = 1_500
        private const val PROMOTION_CONNECT_TIMEOUT_MS = 2_000
        private const val PROMOTION_READ_TIMEOUT_MS = 5_000

        internal fun isAvailabilityFailure(statusCode: Int): Boolean =
            statusCode == 0 || statusCode in setOf(404, 408, 500, 502, 503, 504)

        internal fun endpointForFunction(functionBase: String, primaryEndpoint: String): String {
            val functionName = primaryEndpoint.substringAfterLast('/').trim()
            return "${functionBase.trimEnd('/')}/$functionName"
        }

        internal fun projectEndpointFromFunctionBase(functionBase: String): String? {
            val suffix = "/functions/v1"
            val base = functionBase.trimEnd('/')
            if (!base.endsWith(suffix)) return null
            return HCloudRouteStore.normalizeProjectEndpoint(base.removeSuffix(suffix))
        }

        internal fun jsonBoolean(body: JsonObject, key: String): Boolean =
            (body[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true

        internal fun jsonString(body: JsonObject, key: String): String? =
            (body[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
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
package com.malik.lmai.feature.assistant

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Chooses the H cloud before the real operation starts.
 *
 * Cross-cloud retry after transmission begins is forbidden because a transport failure can
 * be ambiguous: the server may already have committed the operation. The standby endpoint
 * stored on-device is public routing metadata only and is always re-attested live before use.
 */
@Singleton
class HAppStandbyRouter @Inject constructor(
    private val routeStore: HStandbyRouteStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun selectEndpoint(primaryEndpoint: String, token: String, allowStandbyFallback: Boolean): String {
        if (!allowStandbyFallback || !isSupportedPrimaryEndpoint(primaryEndpoint)) return primaryEndpoint
        val standbyBase = routeStore.endpoint() ?: return primaryEndpoint

        val primaryProbe = postJson(
            endpoint = PRIMARY_SYNC_URL,
            token = token,
            payload = buildJsonObject { put("action", JsonPrimitive("status")) },
        )
        if (primaryProbe.primaryAvailable) return primaryEndpoint

        val standbyProbe = postJson(
            endpoint = "$standbyBase/functions/v1/$STANDBY_ROUTE_STATUS_FUNCTION",
            token = token,
            payload = buildJsonObject {},
        )
        if (!standbyProbe.httpSuccess) return primaryEndpoint
        if (standbyProbe.body.bool("activeReady") != true) return primaryEndpoint
        if (standbyProbe.body.string("mode") != "request_only") return primaryEndpoint
        if (standbyProbe.body.bool("promotionAttested") != true) return primaryEndpoint
        if (standbyProbe.body.bool("replicaWritesEnabled") != false) return primaryEndpoint
        if (standbyProbe.body.bool("schedulerActive") == true) return primaryEndpoint
        if (standbyProbe.body.bool("autonomousOutboundActive") == true) return primaryEndpoint
        if (standbyProbe.body.bool("restoreVerified") != true) return primaryEndpoint

        val slug = URL(primaryEndpoint).path.substringAfterLast('/').trim()
        return "$standbyBase/functions/v1/$slug"
    }

    private fun isSupportedPrimaryEndpoint(endpoint: String): Boolean = runCatching {
        val url = URL(endpoint)
        url.protocol == "https" &&
            url.host == PRIMARY_HOST &&
            url.path.substringAfterLast('/') in FAILOVER_CAPABLE_FUNCTIONS
    }.getOrDefault(false)

    private fun postJson(endpoint: String, token: String, payload: JsonObject): ProbeResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = PREFLIGHT_TIMEOUT_MS
            readTimeout = PREFLIGHT_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { buildJsonObject {} }
            ProbeResult(
                statusCode = status,
                body = body,
            )
        } catch (_: Exception) {
            ProbeResult(statusCode = 0, body = buildJsonObject {})
        } finally {
            connection.disconnect()
        }
    }

    private data class ProbeResult(
        val statusCode: Int,
        val body: JsonObject,
    ) {
        val httpSuccess: Boolean get() = statusCode in 200..299

        // Authentication/business responses prove the primary runtime is reachable and must
        // not be used as an excuse to fail over. Network/5xx/timeout and a missing endpoint
        // are availability-class preflight failures and occur before the real operation.
        val primaryAvailable: Boolean
            get() = statusCode in 200..499 && statusCode !in AVAILABILITY_HTTP_FAILURES
    }

    companion object {
        private const val PRIMARY_HOST = "abavsspydbpkudhswmzp.supabase.co"
        private const val PRIMARY_SYNC_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-app-sync"
        private const val STANDBY_ROUTE_STATUS_FUNCTION = "h-standby-route-status"
        private const val PREFLIGHT_TIMEOUT_MS = 1_200

        private val AVAILABILITY_HTTP_FAILURES = setOf(404, 408)
        private val FAILOVER_CAPABLE_FUNCTIONS = setOf(
            "h-app-sync",
            "h-app-media",
            "h-reminder-sync",
            "h-portable-snapshot",
            "h-portable-restore",
        )
    }
}

private fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

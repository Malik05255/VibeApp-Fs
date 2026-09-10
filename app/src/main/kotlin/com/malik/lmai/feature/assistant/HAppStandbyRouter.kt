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

/**
 * Chooses the H cloud before the real operation starts.
 *
 * A request-active standby is sticky: once promotion is attested, Android keeps using it
 * even if the former primary later becomes reachable. Cross-cloud retry after transmission
 * begins is forbidden because the remote operation may already have committed.
 */
@Singleton
class HAppStandbyRouter @Inject constructor(
    private val routeStore: HStandbyRouteStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun selectEndpoint(primaryEndpoint: String, token: String, allowStandbyFallback: Boolean): String {
        if (!allowStandbyFallback || !isSupportedPrimaryEndpoint(primaryEndpoint)) return primaryEndpoint
        val standbyBase = routeStore.endpoint() ?: return primaryEndpoint
        val slug = URL(primaryEndpoint).path.substringAfterLast('/').trim()
        val standbyEndpoint = "$standbyBase/functions/v1/$slug"

        // Live attestation establishes the sticky request-active latch. After that latch is
        // established, a temporary standby probe failure must never silently fail back to the
        // former primary; the real request remains on the same promoted standby and therefore
        // fails closed if that standby is unavailable or its server-side execution fence closes.
        val standbyProbe = postJson(
            endpoint = "$standbyBase/functions/v1/$STANDBY_ROUTE_STATUS_FUNCTION",
            token = token,
            payload = buildJsonObject {},
        )
        if (standbyProbe.isAttestedActiveStandby()) {
            routeStore.markRequestActive()
            return standbyEndpoint
        }
        if (routeStore.requestActiveLatched()) return standbyEndpoint

        // Android never promotes a passive standby. Until the external control plane attests
        // request-only promotion, ordinary app requests remain on the primary.
        return primaryEndpoint
    }

    private fun ProbeResult.isAttestedActiveStandby(): Boolean =
        httpSuccess &&
            body.bool("activeReady") == true &&
            body.string("mode") == "request_only" &&
            body.bool("promotionAttested") == true &&
            body.bool("replicaWritesEnabled") == false &&
            body.bool("schedulerActive") != true &&
            body.bool("autonomousOutboundActive") != true &&
            body.bool("restoreVerified") == true &&
            body.string("replicationProtocol") == "exact_mirror_v2"

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
            ProbeResult(statusCode = status, body = body)
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
    }

    companion object {
        private const val PRIMARY_HOST = "abavsspydbpkudhswmzp.supabase.co"
        private const val STANDBY_ROUTE_STATUS_FUNCTION = "h-standby-route-status"
        private const val PREFLIGHT_TIMEOUT_MS = 1_200

        private val FAILOVER_CAPABLE_FUNCTIONS = setOf(
            "h-app-sync",
            "h-app-media",
            "h-reminder-sync",
        )
    }
}

private fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

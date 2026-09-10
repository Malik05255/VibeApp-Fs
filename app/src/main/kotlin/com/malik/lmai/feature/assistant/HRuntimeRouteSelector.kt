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

sealed interface HRuntimeRouteSelection {
    data class Selected(val endpoint: String, val role: String) : HRuntimeRouteSelection
    data class Rejected(val response: HCloudLinkResponse) : HRuntimeRouteSelection
}

/**
 * Selects one H runtime before an app operation is dispatched.
 *
 * Selection may probe Primary then an already-trusted cached Standby. It never retries the
 * actual operation on the alternate cloud after dispatch starts, preventing ambiguous
 * duplicate writes after a timeout or connection loss.
 */
@Singleton
class HRuntimeRouteSelector @Inject constructor(
    private val routeStore: HRuntimeRouteStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun selectEndpoint(googleIdToken: String, primaryEndpoint: String): HRuntimeRouteSelection {
        val functionName = primaryFunctionName(primaryEndpoint)
            ?: return HRuntimeRouteSelection.Rejected(HCloudLinkResponse.localError("invalid_primary_runtime_endpoint"))

        val primaryPreflight = probe(
            baseUrl = PRIMARY_BASE_URL,
            googleIdToken = googleIdToken,
        )
        if (primaryPreflight.statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return HRuntimeRouteSelection.Rejected(primaryPreflight)
        }

        if (primaryPreflight.ok) {
            val role = primaryPreflight.string("runtimeRole")
            val requestReady = primaryPreflight.bool("requestReady") == true
            if (role != "primary" || !requestReady) {
                return HRuntimeRouteSelection.Rejected(HCloudLinkResponse.localError("primary_runtime_preflight_rejected"))
            }
            updateCachedBackup(primaryPreflight)
            return HRuntimeRouteSelection.Selected(primaryEndpoint, "primary")
        }

        if (!HRuntimeRoutePolicy.primaryPreflightCanFallBack(primaryPreflight.statusCode)) {
            return HRuntimeRouteSelection.Rejected(primaryPreflight)
        }

        val standbyBase = routeStore.backupEndpoint()
            ?: return HRuntimeRouteSelection.Rejected(HCloudLinkResponse.localError("standby_route_not_cached"))
        if (standbyBase == PRIMARY_BASE_URL) {
            routeStore.clearBackupEndpoint()
            return HRuntimeRouteSelection.Rejected(HCloudLinkResponse.localError("standby_route_matches_primary"))
        }

        val standbyPreflight = probe(standbyBase, googleIdToken)
        if (!standbyPreflight.ok) return HRuntimeRouteSelection.Rejected(standbyPreflight)
        val standbyAccepted =
            standbyPreflight.string("runtimeRole") == "standby" &&
            standbyPreflight.bool("requestReady") == true &&
            standbyPreflight.bool("requestOnlyActive") == true &&
            standbyPreflight.bool("promoted") == true &&
            standbyPreflight.bool("replicaWritesFenced") == true &&
            standbyPreflight.bool("promotionAttested") == true
        if (!standbyAccepted) {
            return HRuntimeRouteSelection.Rejected(HCloudLinkResponse.localError("standby_runtime_not_request_active"))
        }

        val endpoint = HRuntimeRoutePolicy.functionUrl(standbyBase, functionName)
            ?: return HRuntimeRouteSelection.Rejected(HCloudLinkResponse.localError("invalid_standby_runtime_endpoint"))
        return HRuntimeRouteSelection.Selected(endpoint, "standby")
    }

    private fun updateCachedBackup(preflight: HCloudLinkResponse) {
        if (preflight.bool("backupConfigured") != true) {
            routeStore.clearBackupEndpoint()
            return
        }
        val endpoint = preflight.string("backupEndpoint")
        val normalized = HRuntimeRoutePolicy.normalizeSupabaseBaseUrl(endpoint)
        if (normalized == null || normalized == PRIMARY_BASE_URL) {
            routeStore.clearBackupEndpoint()
            return
        }
        routeStore.rememberBackupEndpoint(normalized)
    }

    private fun probe(baseUrl: String, googleIdToken: String): HCloudLinkResponse {
        val endpoint = HRuntimeRoutePolicy.functionUrl(baseUrl, ROUTE_FUNCTION)
            ?: return HCloudLinkResponse.localError("invalid_runtime_route_probe_endpoint")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = PREFLIGHT_CONNECT_TIMEOUT_MS
            readTimeout = PREFLIGHT_READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $googleIdToken")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
        }
        return try {
            connection.outputStream.use { output ->
                output.write("{}".toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse {
                    buildJsonObject {
                        put("ok", JsonPrimitive(false))
                        put("error", JsonPrimitive("invalid_runtime_route_response"))
                    }
                }
            HCloudLinkResponse(status, body)
        } catch (error: Exception) {
            HCloudLinkResponse.localError(error.message ?: "runtime_route_probe_failed")
        } finally {
            connection.disconnect()
        }
    }

    private fun primaryFunctionName(endpoint: String): String? {
        val prefix = "$PRIMARY_BASE_URL/functions/v1/"
        if (!endpoint.startsWith(prefix)) return null
        val name = endpoint.removePrefix(prefix)
        return name.takeIf { it.matches(Regex("^[a-z0-9][a-z0-9-]{1,80}$")) }
    }

    private fun HCloudLinkResponse.string(key: String): String? =
        (body[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun HCloudLinkResponse.bool(key: String): Boolean? =
        (body[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    companion object {
        const val PRIMARY_BASE_URL = "https://abavsspydbpkudhswmzp.supabase.co"
        private const val ROUTE_FUNCTION = "h-app-runtime-route"
        private const val PREFLIGHT_CONNECT_TIMEOUT_MS = 1_200
        private const val PREFLIGHT_READ_TIMEOUT_MS = 1_800
    }
}

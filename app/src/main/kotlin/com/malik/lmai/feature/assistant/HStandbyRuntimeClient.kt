package com.malik.lmai.feature.assistant

import com.malik.lmai.presentation.ui.auth.GoogleIdTokenProvider
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** Owner-authenticated bridge that only creates a short-lived Standby setup link. */
@Singleton
class HStandbyRuntimeClient @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val routeStore: HCloudRouteStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createSetupLink(): HCloudLinkResponse = withContext(Dispatchers.IO) {
        val token = googleIdTokenProvider.getToken()
            ?: return@withContext HCloudLinkResponse.localError(
                if (googleIdTokenProvider.hasSignedInSession()) {
                    "google_token_refresh_failed"
                } else {
                    "google_sign_in_required"
                },
            )
        val payload = buildJsonObject { put("action", JsonPrimitive("create_setup_link")) }.toString()
        val first = executePost(token, payload).also(::rememberStandbyEndpoint)
        if (first.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED) return@withContext first

        val refreshed = googleIdTokenProvider.getToken(forceRefresh = true)
            ?: return@withContext HCloudLinkResponse.localError("google_token_refresh_failed")
        if (refreshed == token) return@withContext first
        executePost(refreshed, payload).also(::rememberStandbyEndpoint)
    }

    private fun rememberStandbyEndpoint(response: HCloudLinkResponse) {
        if (!response.ok) return
        val endpoint = (response.body["targetEndpoint"] as? JsonPrimitive)?.content
            ?: (response.body["target_endpoint"] as? JsonPrimitive)?.content
        routeStore.rememberStandbyProjectEndpoint(endpoint)
    }

    private fun executePost(token: String, payload: String): HCloudLinkResponse {
        val connection = (URL(STANDBY_RUNTIME_APP_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse {
                    buildJsonObject {
                        put("ok", JsonPrimitive(false))
                        put("error", JsonPrimitive("invalid_standby_runtime_response"))
                    }
                }
            HCloudLinkResponse(status, body)
        } catch (error: Exception) {
            HCloudLinkResponse.localError(error.message ?: "standby_runtime_app_failed")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val STANDBY_RUNTIME_APP_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-standby-runtime-app"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}
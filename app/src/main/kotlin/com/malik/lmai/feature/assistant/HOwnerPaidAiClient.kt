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
 * Owner-only Android bridge for H's optional BYOK/paid provider.
 *
 * The APK carries only the public Edge Function URL. Authentication is a current Google
 * ID token for the already-paired H owner. Runtime secrets, Supabase service credentials,
 * provider API keys, and encrypted provider credentials never enter Android storage.
 *
 * Once enabled, the selected paid/BYOK provider is H's exclusive AI route for every turn.
 */
@Singleton
class HOwnerPaidAiClient @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun status(): HCloudLinkResponse = post("status")

    suspend fun createSetupLink(
        selectedModel: String,
        dailyCallLimit: Int,
    ): HCloudLinkResponse = post(
        action = "setup_link",
        extra = buildJsonObject {
            put("provider", "openrouter")
            put("selectedModel", selectedModel.trim())
            put("dailyCallLimit", dailyCallLimit)
            put("hardTasksOnly", false)
            put("allowFreeFallback", false)
        },
    )

    suspend fun disable(): HCloudLinkResponse = post("disable")

    suspend fun disconnect(): HCloudLinkResponse = post("disconnect")

    private suspend fun post(
        action: String,
        extra: JsonObject = buildJsonObject {},
    ): HCloudLinkResponse = withContext(Dispatchers.IO) {
        val token = googleIdTokenProvider.getToken()
            ?: return@withContext HCloudLinkResponse.localError(
                if (googleIdTokenProvider.hasSignedInSession()) {
                    "google_token_refresh_failed"
                } else {
                    "google_sign_in_required"
                },
            )

        val payload = buildJsonObject {
            put("action", JsonPrimitive(action))
            extra.forEach { (key, value) -> put(key, value) }
        }

        val first = executePost(token, payload)
        if (first.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return@withContext first
        }

        val refreshed = googleIdTokenProvider.getToken(forceRefresh = true)
            ?: return@withContext HCloudLinkResponse.localError("google_token_refresh_failed")
        if (refreshed == token) return@withContext first
        executePost(refreshed, payload)
    }

    private fun executePost(token: String, payload: JsonObject): HCloudLinkResponse {
        val connection = (URL(PROVIDER_APP_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
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
                        put("error", "invalid_provider_app_response")
                    }
                }
            HCloudLinkResponse(status, body)
        } catch (error: Exception) {
            HCloudLinkResponse.localError(error.message ?: "provider_app_failed")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val PROVIDER_APP_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-ai-provider-app"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}

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

/** Owner-authenticated bridge for H multi-cloud status and backup-cloud onboarding. */
@Singleton
class HCloudManagerClient @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
    private val runtimeRouteStore: HRuntimeRouteStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun status(): HCloudLinkResponse = post("status")

    suspend fun createBackupSetupLink(): HCloudLinkResponse = post("create_backup_setup_link")

    suspend fun disconnectBackup(): HCloudLinkResponse = post("disconnect_backup")

    private suspend fun post(action: String): HCloudLinkResponse = withContext(Dispatchers.IO) {
        val token = googleIdTokenProvider.getToken()
            ?: return@withContext HCloudLinkResponse.localError(
                if (googleIdTokenProvider.hasSignedInSession()) {
                    "google_token_refresh_failed"
                } else {
                    "google_sign_in_required"
                },
            )
        val payload = buildJsonObject { put("action", JsonPrimitive(action)) }
        val first = executePost(token, payload.toString())
        if (first.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED) {
            applyRoutingSideEffects(action, first)
            return@withContext first
        }

        val refreshed = googleIdTokenProvider.getToken(forceRefresh = true)
            ?: return@withContext HCloudLinkResponse.localError("google_token_refresh_failed")
        if (refreshed == token) return@withContext first
        executePost(refreshed, payload.toString()).also { applyRoutingSideEffects(action, it) }
    }

    private fun applyRoutingSideEffects(action: String, response: HCloudLinkResponse) {
        if (!response.ok) return
        if (action == "disconnect_backup") {
            runtimeRouteStore.clearStandby()
            return
        }
        if (action == "status") {
            val backupConfigured = (response.body["backupConfigured"] as? JsonPrimitive)
                ?.content
                ?.toBooleanStrictOrNull()
            if (backupConfigured == false) runtimeRouteStore.clearStandby()
        }
    }

    private fun executePost(token: String, payload: String): HCloudLinkResponse {
        val connection = (URL(CLOUD_MANAGER_URL).openConnection() as HttpURLConnection).apply {
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
                        put("error", JsonPrimitive("invalid_cloud_manager_response"))
                    }
                }
            HCloudLinkResponse(status, body)
        } catch (error: Exception) {
            HCloudLinkResponse.localError(error.message ?: "cloud_manager_failed")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CLOUD_MANAGER_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-cloud-manager"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 20_000
    }
}

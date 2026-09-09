package com.malik.lmai.feature.assistant

import android.content.Context
import com.malik.lmai.presentation.ui.auth.GoogleAccountSession
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Authentication is the current Google ID token. H_RUNTIME_SECRET and Supabase service
 * credentials never enter the APK. The server requires a one-time owner WhatsApp pairing
 * before this Google identity can read or explicitly save shared H state.
 */
@Singleton
class HCloudLinkClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startLink(): HCloudLinkResponse = post("create_pairing")

    suspend fun finishLink(pairingCode: String, waId: String): HCloudLinkResponse = post(
        action = "finalize_pairing",
        extra = buildJsonObject {
            put("pairing_code", pairingCode.trim())
            put("wa_id", waId.trim())
        },
    )

    suspend fun status(): HCloudLinkResponse = post("status")

    suspend fun snapshot(): HCloudLinkResponse = post("snapshot")

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

    private suspend fun post(
        action: String,
        extra: JsonObject = buildJsonObject {},
    ): HCloudLinkResponse = withContext(Dispatchers.IO) {
        val token = GoogleAccountSession.get(context)?.idToken?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@withContext HCloudLinkResponse.localError("google_sign_in_required")

        val payload = buildJsonObject {
            put("action", JsonPrimitive(action))
            extra.forEach { (key, value) -> put(key, value) }
        }

        val connection = (URL(SYNC_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
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
        // Supabase project/function URL is public routing metadata, not a credential.
        // Authentication still requires a verified Google token + owner WhatsApp pairing.
        private const val SYNC_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-app-sync"
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

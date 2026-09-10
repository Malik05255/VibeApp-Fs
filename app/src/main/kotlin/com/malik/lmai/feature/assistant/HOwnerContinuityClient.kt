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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Read-only proof that the signed-in Android session resolves to an already-linked H owner.
 *
 * The server returns only an opaque HMAC continuity handle. The runtime user key, WhatsApp
 * owner identifier, Google subject, provider credentials and raw media never enter this API.
 */
@Singleton
class HOwnerContinuityClient @Inject constructor(
    private val googleIdTokenProvider: GoogleIdTokenProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun status(): HOwnerContinuityStatus = withContext(Dispatchers.IO) {
        val token = googleIdTokenProvider.getToken()
            ?: return@withContext HOwnerContinuityStatus.error(
                if (googleIdTokenProvider.hasSignedInSession()) {
                    "google_token_refresh_failed"
                } else {
                    "google_sign_in_required"
                }
            )

        val first = execute(token)
        if (first.statusCode != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return@withContext parse(first)
        }

        val refreshed = googleIdTokenProvider.getToken(forceRefresh = true)
            ?: return@withContext HOwnerContinuityStatus.error("google_token_refresh_failed")
        if (refreshed == token) return@withContext parse(first)
        parse(execute(refreshed))
    }

    private fun execute(token: String): RawContinuityResponse {
        val connection = (URL(CONTINUITY_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            RawContinuityResponse(status, body)
        } catch (_: Exception) {
            RawContinuityResponse(0, null)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(response: RawContinuityResponse): HOwnerContinuityStatus {
        val body = response.body ?: return HOwnerContinuityStatus.error("invalid_cloud_response")
        if (response.statusCode !in 200..299 || body.bool("ok") != true) {
            return HOwnerContinuityStatus.error(body.text("error") ?: "owner_continuity_failed")
        }
        if (body.text("service") != "h-owner-continuity") {
            return HOwnerContinuityStatus.error("invalid_continuity_service")
        }

        val linked = body.bool("linked") == true
        val resume = body.bool("resumeExistingH") == true
        val pairingRequired = body.bool("pairingRequired") == true
        val sameRuntime = body.bool("sameRuntimeAsWhatsApp") == true
        val portable = body.bool("portableSnapshotAvailable") == true
        val handle = body.text("continuityHandle")

        if (!linked) {
            return if (!resume && pairingRequired && handle == null) {
                HOwnerContinuityStatus(
                    ok = true,
                    linked = false,
                    resumeExistingH = false,
                    continuityHandle = null,
                    pairingRequired = true,
                    sameRuntimeAsWhatsApp = false,
                    portableSnapshotAvailable = false,
                    error = null,
                )
            } else {
                HOwnerContinuityStatus.error("invalid_unlinked_continuity_contract")
            }
        }

        if (!resume || pairingRequired || !sameRuntime || handle == null || !HANDLE_PATTERN.matches(handle)) {
            return HOwnerContinuityStatus.error("invalid_linked_continuity_contract")
        }
        return HOwnerContinuityStatus(
            ok = true,
            linked = true,
            resumeExistingH = true,
            continuityHandle = handle,
            pairingRequired = false,
            sameRuntimeAsWhatsApp = true,
            portableSnapshotAvailable = portable,
            error = null,
        )
    }

    private fun JsonObject.bool(name: String): Boolean? =
        (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.text(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private data class RawContinuityResponse(
        val statusCode: Int,
        val body: JsonObject?,
    )

    companion object {
        private const val CONTINUITY_URL =
            "https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-owner-continuity"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private val HANDLE_PATTERN = Regex("^h1_[0-9a-f]{64}$")
    }
}

data class HOwnerContinuityStatus(
    val ok: Boolean,
    val linked: Boolean,
    val resumeExistingH: Boolean,
    val continuityHandle: String?,
    val pairingRequired: Boolean,
    val sameRuntimeAsWhatsApp: Boolean,
    val portableSnapshotAvailable: Boolean,
    val error: String?,
) {
    companion object {
        fun error(message: String) = HOwnerContinuityStatus(
            ok = false,
            linked = false,
            resumeExistingH = false,
            continuityHandle = null,
            pairingRequired = false,
            sameRuntimeAsWhatsApp = false,
            portableSnapshotAvailable = false,
            error = message,
        )
    }
}

package com.malik.lmai.feature.mcp

import android.net.Uri
import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class PeachMcpOAuthCoordinator @Inject constructor(
    private val httpClient: HttpClient,
    private val store: PeachMcpSecureStore,
    private val loopbackServer: PeachMcpLoopbackServer,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun begin(): Result<String> = safeResult {
        val redirectUri = loopbackServer.start()
        try {
            val metadata = discoverOAuthMetadata()
            store.saveOAuthMetadata(metadata)

            // Peach validates redirect_uris during dynamic registration. Register against the
            // exact localhost listener created for this authorization attempt.
            val clientId = registerClient(metadata, redirectUri).also(store::saveClientId)

            val verifier = randomUrlSafe(64)
            val state = randomUrlSafe(32)
            store.savePendingOAuth(
                PeachMcpSecureStore.PendingOAuth(
                    state = state,
                    verifier = verifier,
                    redirectUri = redirectUri,
                    createdAtMillis = System.currentTimeMillis(),
                )
            )

            Uri.parse(metadata.authorizationEndpoint)
                .buildUpon()
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("code_challenge", codeChallenge(verifier))
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .apply {
                    if (metadata.scope.isNotBlank()) appendQueryParameter("scope", metadata.scope)
                    appendQueryParameter("resource", SERVER_URL)
                }
                .build()
                .toString()
        } catch (e: Exception) {
            loopbackServer.stop()
            throw e
        }
    }

    suspend fun complete(uri: Uri): Result<Unit> = safeResult {
        val pending = store.pendingOAuth()
            ?: error("No Peach authorization session is pending")
        try {
            check(System.currentTimeMillis() - pending.createdAtMillis <= SESSION_TTL_MILLIS) {
                "Peach authorization session expired"
            }
            check(matchesPendingRedirect(uri, pending.redirectUri)) {
                "Unexpected Peach authorization callback"
            }
            val returnedState = uri.getQueryParameter("state")?.takeIf(String::isNotBlank)
                ?: error("Peach authorization state is missing")
            check(constantTimeEquals(pending.state, returnedState)) {
                "Peach authorization state does not match"
            }
            uri.getQueryParameter("error")?.takeIf(String::isNotBlank)?.let { errorCode ->
                val description = uri.getQueryParameter("error_description")
                error("Peach authorization was rejected: $errorCode${description?.let { " ($it)" }.orEmpty()}")
            }
            val code = uri.getQueryParameter("code")?.takeIf(String::isNotBlank)
                ?: error("Peach authorization code is missing")
            val metadata = store.oauthMetadata() ?: discoverOAuthMetadata().also(store::saveOAuthMetadata)
            val clientId = store.clientId() ?: error("Peach MCP client registration is missing")
            val response = httpClient.post(metadata.tokenEndpoint) {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", "authorization_code")
                            append("code", code)
                            append("redirect_uri", pending.redirectUri)
                            append("client_id", clientId)
                            append("code_verifier", pending.verifier)
                            append("resource", SERVER_URL)
                        }
                    )
                )
            }
            check(response.status.value in 200..299) {
                "Peach token exchange failed (${response.status.value}): ${response.body<String>().take(300)}"
            }
            saveTokenResponse(parseObject(response.body()))
        } finally {
            store.clearPendingOAuth()
            loopbackServer.stop()
        }
    }

    suspend fun validAccessToken(): String? {
        val current = store.tokens() ?: return null
        if (current.expiresAtMillis - System.currentTimeMillis() > TOKEN_REFRESH_SKEW_MILLIS) {
            return current.accessToken
        }
        val refresh = current.refreshToken?.takeIf { it.isNotBlank() } ?: return current.accessToken
        val metadata = store.oauthMetadata() ?: return current.accessToken
        val clientId = store.clientId() ?: return current.accessToken
        return try {
            val response = httpClient.post(metadata.tokenEndpoint) {
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", "refresh_token")
                            append("refresh_token", refresh)
                            append("client_id", clientId)
                            append("resource", SERVER_URL)
                        }
                    )
                )
            }
            if (response.status.value !in 200..299) return current.accessToken
            val obj = parseObject(response.body<String>())
            saveTokenResponse(obj, fallbackRefreshToken = refresh)
            store.tokens()?.accessToken
        } catch (_: Exception) {
            current.accessToken
        }
    }

    fun isConnected(): Boolean = store.tokens()?.accessToken?.isNotBlank() == true

    fun disconnect() {
        loopbackServer.stop()
        store.clearConnection()
    }

    private fun matchesPendingRedirect(uri: Uri, redirectUri: String): Boolean {
        val expected = Uri.parse(redirectUri)
        return uri.scheme == expected.scheme &&
            uri.host == expected.host &&
            uri.port == expected.port &&
            uri.path == expected.path
    }

    private suspend fun discoverOAuthMetadata(): PeachMcpSecureStore.OAuthMetadata {
        store.oauthMetadata()?.let { return it }

        val resourceMetadata = RESOURCE_METADATA_CANDIDATES.firstNotNullOfOrNull { candidate ->
            runCatching { getJsonObject(candidate) }.getOrNull()
        } ?: discoverResourceMetadataFromChallenge()
            ?: error("Peach MCP OAuth metadata could not be discovered")

        val authorizationServer = resourceMetadata.stringArray("authorization_servers").firstOrNull()
            ?: resourceMetadata.string("authorization_server")
            ?: error("Peach did not advertise an OAuth authorization server")

        val authMetadata = authorizationMetadataCandidates(authorizationServer)
            .firstNotNullOfOrNull { candidate -> runCatching { getJsonObject(candidate) }.getOrNull() }
            ?: error("Peach OAuth authorization-server metadata could not be loaded")

        val authorizationEndpoint = authMetadata.string("authorization_endpoint")
            ?: error("Peach OAuth authorization endpoint is missing")
        val tokenEndpoint = authMetadata.string("token_endpoint")
            ?: error("Peach OAuth token endpoint is missing")
        val registrationEndpoint = authMetadata.string("registration_endpoint")
            ?: error("Peach OAuth dynamic registration endpoint is missing")
        val issuer = authMetadata.string("issuer") ?: authorizationServer

        val resourceScopes = resourceMetadata.stringArray("scopes_supported")
        val authScopes = authMetadata.stringArray("scopes_supported")
        val scope = (resourceScopes.ifEmpty { authScopes })
            .filterNot { it.equals("offline_access", ignoreCase = true) }
            .joinToString(" ")

        return PeachMcpSecureStore.OAuthMetadata(
            authorizationEndpoint = authorizationEndpoint,
            tokenEndpoint = tokenEndpoint,
            registrationEndpoint = registrationEndpoint,
            issuer = issuer,
            scope = scope,
        )
    }

    private suspend fun discoverResourceMetadataFromChallenge(): JsonObject? {
        val response = runCatching {
            httpClient.post(SERVER_URL) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json, text/event-stream")
                setBody("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
            }
        }.getOrNull() ?: return null

        val challenge = response.headers[HttpHeaders.WWWAuthenticate].orEmpty()
        val metadataUrl = Regex("resource_metadata=\"([^\"]+)\"")
            .find(challenge)?.groupValues?.getOrNull(1)
            ?: return null
        return runCatching { getJsonObject(metadataUrl) }.getOrNull()
    }

    private suspend fun registerClient(
        metadata: PeachMcpSecureStore.OAuthMetadata,
        redirectUri: String,
    ): String {
        val registrationBody = buildJsonObject {
            put("client_name", "lm_AI H")
            put("application_type", "native")
            put("token_endpoint_auth_method", "none")
            put("redirect_uris", buildJsonArray { add(JsonPrimitive(redirectUri)) })
            put("grant_types", buildJsonArray {
                add(JsonPrimitive("authorization_code"))
                add(JsonPrimitive("refresh_token"))
            })
            put("response_types", buildJsonArray { add(JsonPrimitive("code")) })
        }
        val response = httpClient.post(metadata.registrationEndpoint) {
            contentType(ContentType.Application.Json)
            setBody(registrationBody.toString())
        }
        check(response.status.value in 200..299) {
            "Peach MCP client registration failed (${response.status.value}): ${response.body<String>().take(300)}"
        }
        return parseObject(response.body<String>()).string("client_id")
            ?: error("Peach MCP registration did not return a client_id")
    }

    private fun saveTokenResponse(obj: JsonObject, fallbackRefreshToken: String? = null) {
        val accessToken = obj.string("access_token") ?: error("Peach token response has no access_token")
        val refreshToken = obj.string("refresh_token") ?: fallbackRefreshToken
        val tokenType = obj.string("token_type") ?: "Bearer"
        val expiresInSeconds = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull()
        val expiresAt = expiresInSeconds?.let { System.currentTimeMillis() + it * 1000L }
            ?: Long.MAX_VALUE
        store.saveTokens(
            PeachMcpSecureStore.TokenSet(
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenType = tokenType,
                scope = obj.string("scope"),
                expiresAtMillis = expiresAt,
            )
        )
    }

    private suspend fun getJsonObject(url: String): JsonObject {
        val response = httpClient.get(url) { header(HttpHeaders.Accept, "application/json") }
        check(response.status.value in 200..299) { "HTTP ${response.status.value}" }
        return parseObject(response.body<String>())
    }

    private fun parseObject(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private fun authorizationMetadataCandidates(issuer: String): List<String> {
        val normalized = issuer.trimEnd('/')
        val parsed = Uri.parse(normalized)
        val origin = "${parsed.scheme}://${parsed.authority}"
        val path = parsed.path.orEmpty().trimEnd('/')
        return listOf(
            "$normalized/.well-known/oauth-authorization-server",
            "$origin/.well-known/oauth-authorization-server$path",
            "$normalized/.well-known/openid-configuration",
            "$origin/.well-known/openid-configuration$path",
        ).distinct()
    }

    private fun JsonObject.string(key: String): String? = this[key]
        ?.let { it as? JsonPrimitive }
        ?.content
        ?.takeIf { it.isNotBlank() }

    private fun JsonObject.stringArray(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { element ->
            (element as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(
        a.toByteArray(Charsets.UTF_8),
        b.toByteArray(Charsets.UTF_8),
    )

    private suspend fun <T> safeResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val SERVER_URL = "https://app.trypeach.ai/api/mcp"
        private const val SESSION_TTL_MILLIS = 10 * 60 * 1000L
        private const val TOKEN_REFRESH_SKEW_MILLIS = 60 * 1000L
        private val RESOURCE_METADATA_CANDIDATES = listOf(
            "https://app.trypeach.ai/.well-known/oauth-protected-resource/api/mcp",
            "https://app.trypeach.ai/.well-known/oauth-protected-resource",
        )
    }
}

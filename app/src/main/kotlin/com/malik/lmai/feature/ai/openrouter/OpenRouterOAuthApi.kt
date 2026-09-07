package com.malik.lmai.feature.ai.openrouter

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Singleton
class OpenRouterOAuthApi @Inject constructor(
    private val client: HttpClient,
) {
    fun buildAuthorizationUrl(callbackUrl: String, codeChallenge: String): String {
        val query = listOf(
            "callback_url" to callbackUrl,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
        ).joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$AUTH_URL?$query"
    }

    suspend fun exchangeCode(code: String, verifier: String): String {
        val response = client.post(KEY_EXCHANGE_URL) {
            contentType(ContentType.Application.Json)
            setBody(
                KeyExchangeRequest(
                    code = code,
                    codeVerifier = verifier,
                    codeChallengeMethod = "S256",
                )
            )
        }
        if (response.status.value !in 200..299) {
            throw OpenRouterOAuthException(response.status.value, response.bodyAsText().take(500))
        }
        return response.body<KeyExchangeResponse>().key.trim().also {
            require(it.isNotBlank()) { "OpenRouter returned an empty API key" }
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    @Serializable
    private data class KeyExchangeRequest(
        val code: String,
        @SerialName("code_verifier") val codeVerifier: String,
        @SerialName("code_challenge_method") val codeChallengeMethod: String,
    )

    @Serializable
    private data class KeyExchangeResponse(val key: String)

    companion object {
        private const val AUTH_URL = "https://openrouter.ai/auth"
        private const val KEY_EXCHANGE_URL = "https://openrouter.ai/api/v1/auth/keys"
    }
}

class OpenRouterOAuthException(
    val statusCode: Int,
    message: String,
) : IllegalStateException("OpenRouter OAuth failed (HTTP $statusCode): $message")

package com.vibe.app.feature.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun startDeviceAuthorization(clientId: String): GitHubDeviceCodeResponse {
        require(clientId.isNotBlank()) { "GitHub OAuth Client ID is not configured" }
        val response = client.post("$LOGIN_ROOT/device/code") {
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", clientId.trim())
                        append("scope", "repo read:user workflow")
                    },
                ),
            )
        }
        if (response.status.value !in 200..299) {
            val details = parseOAuthError(response.bodyAsText())
            throw GitHubApiException(
                response.status.value,
                when (details.error) {
                    "device_flow_disabled" -> "فعّل Enable Device Flow في إعدادات GitHub OAuth App ثم حاول مرة أخرى."
                    "incorrect_client_credentials", "invalid_client" -> "GitHub رفض Client ID. تأكد أنك نسخت Client ID من OAuth App نفسه وليس Client Secret."
                    else -> details.errorDescription
                        ?: "GitHub sign-in failed (HTTP ${response.status.value})."
                },
            )
        }
        return response.body()
    }

    suspend fun pollDeviceAuthorization(
        clientId: String,
        deviceCode: String,
    ): GitHubDeviceTokenResponse {
        val response = client.post("$LOGIN_ROOT/oauth/access_token") {
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", clientId.trim())
                        append("device_code", deviceCode)
                        append("grant_type", DEVICE_GRANT_TYPE)
                    },
                ),
            )
        }
        if (response.status.value !in 200..299) {
            val details = parseOAuthError(response.bodyAsText())
            throw GitHubApiException(
                response.status.value,
                details.errorDescription ?: "GitHub sign-in failed (HTTP ${response.status.value}).",
            )
        }
        return response.body()
    }

    suspend fun getCurrentUser(token: String): GitHubUser {
        val response = client.get("$API_ROOT/user") {
            githubHeaders(token)
        }
        checkResponse(response.status.value)
        return response.body()
    }

    suspend fun listRepositories(token: String): List<GitHubRepository> {
        val response = client.get("$API_ROOT/user/repos") {
            githubHeaders(token)
            parameter("affiliation", "owner,collaborator,organization_member")
            parameter("sort", "updated")
            parameter("per_page", 100)
        }
        checkResponse(response.status.value)
        return response.body()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.githubHeaders(token: String) {
        val trimmed = token.trim()
        val normalized = if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed.substring("Bearer ".length).trim()
        } else {
            trimmed
        }
        require(normalized.isNotEmpty()) { "GitHub token is empty" }
        header(HttpHeaders.Authorization, "Bearer $normalized")
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    private fun checkResponse(statusCode: Int) {
        if (statusCode in 200..299) return
        val message = when (statusCode) {
            401 -> "GitHub rejected this token. Check the token and try again."
            403 -> "This token does not have permission to access GitHub repositories."
            else -> "GitHub connection failed (HTTP $statusCode)."
        }
        throw GitHubApiException(statusCode, message)
    }

    private fun parseOAuthError(raw: String): GitHubOAuthError = runCatching {
        JSON.decodeFromString<GitHubOAuthError>(raw)
    }.getOrElse { GitHubOAuthError() }

    companion object {
        private const val API_ROOT = "https://api.github.com"
        private const val LOGIN_ROOT = "https://github.com/login"
        private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class GitHubOAuthError(
    val error: String? = null,
    @kotlinx.serialization.SerialName("error_description") val errorDescription: String? = null,
)

class GitHubApiException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

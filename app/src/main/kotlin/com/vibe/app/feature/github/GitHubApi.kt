package com.vibe.app.feature.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApi @Inject constructor(
    private val client: HttpClient,
) {
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

    companion object {
        private const val API_ROOT = "https://api.github.com"
    }
}

class GitHubApiException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

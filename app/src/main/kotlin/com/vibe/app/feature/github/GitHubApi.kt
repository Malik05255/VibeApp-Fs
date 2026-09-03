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
    suspend fun getCurrentUser(token: String): GitHubUser =
        client.get("$API_ROOT/user") {
            githubHeaders(token)
        }.body()

    suspend fun listRepositories(token: String): List<GitHubRepository> =
        client.get("$API_ROOT/user/repos") {
            githubHeaders(token)
            parameter("affiliation", "owner,collaborator,organization_member")
            parameter("sort", "updated")
            parameter("per_page", 100)
        }.body()

    private fun io.ktor.client.request.HttpRequestBuilder.githubHeaders(token: String) {
        val normalized = token.trim().removePrefix("Bearer ").trim()
        require(normalized.isNotEmpty()) { "GitHub token is empty" }
        header(HttpHeaders.Authorization, "Bearer $normalized")
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    companion object {
        private const val API_ROOT = "https://api.github.com"
    }
}

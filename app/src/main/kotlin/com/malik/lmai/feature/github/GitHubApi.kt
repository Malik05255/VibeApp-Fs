package com.malik.lmai.feature.github

import com.malik.lmai.R
import com.malik.lmai.data.preferences.AppText
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApi @Inject constructor(
    private val client: HttpClient,
) {
    fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String,
    ): String {
        val query = listOf(
            "client_id" to clientId.trim(),
            "redirect_uri" to redirectUri,
            "scope" to "repo read:user workflow",
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
        ).joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        return "$LOGIN_ROOT/oauth/authorize?$query"
    }

    fun buildDeviceVerificationUrl(
        verificationUri: String,
        userCode: String,
    ): String {
        val separator = if (verificationUri.contains('?')) '&' else '?'
        return "$verificationUri${separator}user_code=${urlEncode(userCode)}"
    }

    suspend fun exchangeAuthorizationCode(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
        codeVerifier: String,
    ): GitHubDeviceTokenResponse {
        require(clientSecret.isNotBlank()) { "GitHub OAuth client secret is required for code exchange" }
        val response = client.post("$LOGIN_ROOT/oauth/access_token") {
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", clientId.trim())
                        append("client_secret", clientSecret.trim())
                        append("code", code)
                        append("redirect_uri", redirectUri)
                        append("code_verifier", codeVerifier)
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
                    "device_flow_disabled" -> AppText.get(R.string.github_device_flow_disabled_internal)
                    "incorrect_client_credentials", "invalid_client" -> AppText.get(R.string.github_client_id_rejected_internal)
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
        val response = client.get("$API_ROOT/user") { githubHeaders(token) }
        checkResponse(response.status.value)
        return response.body()
    }

    suspend fun listRepositories(token: String): List<GitHubRepository> {
        val repositories = mutableListOf<GitHubRepository>()
        var page = 1

        while (true) {
            val response = client.get("$API_ROOT/user/repos") {
                githubHeaders(token)
                parameter("affiliation", "owner,collaborator,organization_member")
                parameter("sort", "updated")
                parameter("per_page", REPOSITORIES_PAGE_SIZE)
                parameter("page", page)
            }
            checkResponse(response.status.value)
            val batch = response.body<List<GitHubRepository>>()
            repositories += batch

            if (batch.size < REPOSITORIES_PAGE_SIZE) break
            page += 1
        }

        return repositories.distinctBy { it.id }
    }

    suspend fun listProjectCandidates(
        token: String,
        repository: GitHubRepository,
    ): List<GitHubProjectCandidate> {
        val initialTree = fetchTree(
            token = token,
            repository = repository,
            treeish = repository.defaultBranch,
            recursive = true,
        )
        val tree = if (initialTree.truncated) {
            val entries = mutableListOf<GitHubTreeEntry>()
            collectCompleteTree(
                token = token,
                repository = repository,
                treeish = repository.defaultBranch,
                prefix = "",
                depth = 0,
                output = entries,
            )
            GitHubTreeResponse(tree = entries, truncated = false)
        } else {
            initialTree.copy(tree = initialTree.tree.filterNot { isIgnoredPath(it.path) })
        }
        return detectProjects(repository, tree)
    }

    private suspend fun fetchTree(
        token: String,
        repository: GitHubRepository,
        treeish: String,
        recursive: Boolean,
    ): GitHubTreeResponse {
        val response = client.get(
            "$API_ROOT/repos/${repository.fullName}/git/trees/${urlEncode(treeish)}"
        ) {
            githubHeaders(token)
            if (recursive) parameter("recursive", "1")
        }
        checkResponse(response.status.value)
        return response.body()
    }

    private suspend fun collectCompleteTree(
        token: String,
        repository: GitHubRepository,
        treeish: String,
        prefix: String,
        depth: Int,
        output: MutableList<GitHubTreeEntry>,
    ) {
        if (depth > MAX_TREE_DEPTH || output.size >= MAX_TREE_ENTRIES) return

        val recursive = fetchTree(
            token = token,
            repository = repository,
            treeish = treeish,
            recursive = true,
        )

        if (!recursive.truncated) {
            recursive.tree.forEach { entry ->
                if (output.size >= MAX_TREE_ENTRIES) return
                val fullPath = joinPath(prefix, entry.path)
                if (!isIgnoredPath(fullPath)) {
                    output += entry.copy(path = fullPath)
                }
            }
            return
        }

        val direct = fetchTree(
            token = token,
            repository = repository,
            treeish = treeish,
            recursive = false,
        )
        direct.tree.forEach { entry ->
            if (output.size >= MAX_TREE_ENTRIES) return
            val fullPath = joinPath(prefix, entry.path)
            if (isIgnoredPath(fullPath)) return@forEach

            when (entry.type) {
                "blob" -> output += entry.copy(path = fullPath)
                "tree" -> entry.sha?.let { sha ->
                    collectCompleteTree(
                        token = token,
                        repository = repository,
                        treeish = sha,
                        prefix = fullPath,
                        depth = depth + 1,
                        output = output,
                    )
                }
            }
        }
    }

    private fun detectProjects(
        repository: GitHubRepository,
        tree: GitHubTreeResponse,
    ): List<GitHubProjectCandidate> {
        val blobs = tree.tree
            .asSequence()
            .filter { it.type == "blob" }
            .map { it.path.trim('/') }
            .filter { it.isNotBlank() }
            .toList()
        val blobSet = blobs.toHashSet()

        val candidates = linkedMapOf<String, GitHubProjectCandidate>()

        fun add(markerPath: String, kind: GitHubProjectKind) {
            val normalized = markerPath.trim('/')
            val root = normalized.substringBeforeLast('/', missingDelimiterValue = "")
            val displayName = if (root.isBlank()) repository.name else root.substringAfterLast('/')
            val existing = candidates[root]
            val candidate = GitHubProjectCandidate(
                name = displayName,
                path = root,
                kind = kind,
            )
            if (existing == null || kindPriority(kind) < kindPriority(existing.kind)) {
                candidates[root] = candidate
            }
        }

        blobs.forEach { path ->
            when (path.substringAfterLast('/')) {
                "settings.gradle.kts", "settings.gradle" -> {
                    val root = path.substringBeforeLast('/', missingDelimiterValue = "")
                    val androidMarker = if (root.isBlank()) {
                        "app/src/main/AndroidManifest.xml"
                    } else {
                        "$root/app/src/main/AndroidManifest.xml"
                    }
                    add(
                        markerPath = path,
                        kind = if (androidMarker in blobSet) {
                            GitHubProjectKind.ANDROID_GRADLE
                        } else {
                            GitHubProjectKind.GRADLE
                        },
                    )
                }
                "package.json" -> add(path, GitHubProjectKind.NODE)
                "pubspec.yaml" -> add(path, GitHubProjectKind.FLUTTER)
                "pyproject.toml" -> add(path, GitHubProjectKind.PYTHON)
                "Cargo.toml" -> add(path, GitHubProjectKind.RUST)
            }
        }

        if (candidates.isEmpty()) {
            candidates[""] = GitHubProjectCandidate(
                name = repository.name,
                path = "",
                kind = GitHubProjectKind.REPOSITORY_ROOT,
            )
        }

        return candidates.values
            .sortedWith(compareBy<GitHubProjectCandidate>({ it.path.count { c -> c == '/' } }, { it.path }))
    }

    private fun kindPriority(kind: GitHubProjectKind): Int = when (kind) {
        GitHubProjectKind.ANDROID_GRADLE -> 0
        GitHubProjectKind.FLUTTER -> 1
        GitHubProjectKind.GRADLE -> 2
        GitHubProjectKind.NODE -> 3
        GitHubProjectKind.PYTHON -> 4
        GitHubProjectKind.RUST -> 5
        GitHubProjectKind.REPOSITORY_ROOT -> 6
    }

    private fun joinPath(prefix: String, child: String): String =
        if (prefix.isBlank()) child.trim('/') else "$prefix/${child.trim('/')}"

    private fun isIgnoredPath(path: String): Boolean =
        path.split('/').any { segment -> segment in IGNORED_TREE_SEGMENTS }

    private fun io.ktor.client.request.HttpRequestBuilder.githubHeaders(token: String) {
        val trimmed = token.trim()
        val normalized = if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed.substring("Bearer ".length).trim()
        } else trimmed
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
            404 -> "The selected GitHub repository or branch could not be read."
            else -> "GitHub connection failed (HTTP $statusCode)."
        }
        throw GitHubApiException(statusCode, message)
    }

    private fun parseOAuthError(raw: String): GitHubOAuthError = runCatching {
        JSON.decodeFromString<GitHubOAuthError>(raw)
    }.getOrElse { GitHubOAuthError() }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    companion object {
        private const val API_ROOT = "https://api.github.com"
        private const val LOGIN_ROOT = "https://github.com/login"
        private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val REPOSITORIES_PAGE_SIZE = 100
        private const val MAX_TREE_DEPTH = 16
        private const val MAX_TREE_ENTRIES = 100_000
        private val IGNORED_TREE_SEGMENTS = setOf(
            ".git",
            ".gradle",
            ".idea",
            ".dart_tool",
            "node_modules",
            "build",
            "dist",
            "out",
            "target",
            "vendor",
            "Pods",
        )
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

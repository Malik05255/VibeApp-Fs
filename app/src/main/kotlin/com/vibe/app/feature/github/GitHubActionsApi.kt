package com.vibe.app.feature.github

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class GitHubActionsApi @Inject constructor(
    private val client: HttpClient,
) {

    suspend fun ensureCloudBuildWorkflow(
        token: String,
        repositoryFullName: String,
        defaultBranch: String,
    ): Boolean {
        val metadataResponse = client.get(
            "$API_ROOT/repos/$repositoryFullName/contents/$CLOUD_WORKFLOW_PATH"
        ) {
            githubHeaders(token)
            parameter("ref", defaultBranch)
        }

        when (metadataResponse.status.value) {
            in 200..299 -> return false
            404 -> Unit
            else -> checkResponse(metadataResponse.status.value)
        }

        val encoded = Base64.getEncoder().encodeToString(
            CLOUD_WORKFLOW_YAML.toByteArray(Charsets.UTF_8)
        )
        val createResponse = client.put(
            "$API_ROOT/repos/$repositoryFullName/contents/$CLOUD_WORKFLOW_PATH"
        ) {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(
                GitHubContentWriteRequest(
                    message = "chore: add lm_AI cloud build workflow",
                    content = encoded,
                    branch = defaultBranch,
                )
            )
        }
        checkResponse(createResponse.status.value)
        return true
    }

    suspend fun dispatchCloudBuild(
        token: String,
        repositoryFullName: String,
        branch: String,
        projectPath: String,
        requestId: String,
    ): GitHubWorkflowDispatchResult {
        val response = client.post(
            "$API_ROOT/repos/$repositoryFullName/actions/workflows/$CLOUD_WORKFLOW_FILE/dispatches"
        ) {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(
                GitHubWorkflowDispatchRequest(
                    ref = branch,
                    inputs = mapOf(
                        "project_path" to projectPath.ifBlank { "." },
                        "request_id" to requestId,
                    ),
                )
            )
        }
        checkResponse(response.status.value)

        val raw = response.bodyAsText().trim()
        if (raw.isBlank()) return GitHubWorkflowDispatchResult()

        return runCatching {
            JSON.decodeFromString<GitHubWorkflowDispatchResult>(raw)
        }.getOrDefault(GitHubWorkflowDispatchResult())
    }

    suspend fun getWorkflowRun(
        token: String,
        repositoryFullName: String,
        runId: Long,
    ): GitHubWorkflowRun {
        val response = client.get(
            "$API_ROOT/repos/$repositoryFullName/actions/runs/$runId"
        ) {
            githubHeaders(token)
        }
        checkResponse(response.status.value)
        return response.body()
    }

    suspend fun findCloudBuildRun(
        token: String,
        repositoryFullName: String,
        branch: String,
        requestId: String? = null,
    ): GitHubWorkflowRun? {
        val response = client.get(
            "$API_ROOT/repos/$repositoryFullName/actions/workflows/$CLOUD_WORKFLOW_FILE/runs"
        ) {
            githubHeaders(token)
            parameter("branch", branch)
            parameter("event", "workflow_dispatch")
            parameter("per_page", 20)
        }
        if (response.status.value == 404) return null
        checkResponse(response.status.value)

        val runs = response.body<GitHubWorkflowRunsResponse>().workflowRuns
        return if (requestId.isNullOrBlank()) {
            runs.firstOrNull()
        } else {
            runs.firstOrNull { run ->
                run.displayTitle?.contains(requestId, ignoreCase = false) == true
            }
        }
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
            401 -> "GitHub rejected this token. Sign in again and retry."
            403 -> "GitHub denied Actions/workflow access for this repository."
            404 -> "The lm_AI cloud workflow or repository could not be found."
            409 -> "GitHub could not update the workflow on the selected branch."
            422 -> "GitHub rejected the cloud build request. Check the workflow branch and inputs."
            else -> "GitHub Actions request failed (HTTP $statusCode)."
        }
        throw GitHubApiException(statusCode, message)
    }

    companion object {
        const val CLOUD_WORKFLOW_FILE = "lmai-cloud-build.yml"
        const val CLOUD_WORKFLOW_PATH = ".github/workflows/$CLOUD_WORKFLOW_FILE"

        private const val API_ROOT = "https://api.github.com"
        private val JSON = Json { ignoreUnknownKeys = true }

        val CLOUD_WORKFLOW_YAML: String = """
            name: lm_AI Cloud Build
            run-name: lm_AI Cloud Build · ${'$'}{{ inputs.request_id }}

            on:
              workflow_dispatch:
                inputs:
                  project_path:
                    description: Project directory inside the repository
                    required: true
                    default: .
                  request_id:
                    description: lm_AI request identifier
                    required: true

            permissions:
              contents: read

            jobs:
              build-android:
                runs-on: ubuntu-latest
                timeout-minutes: 45
                defaults:
                  run:
                    working-directory: ${'$'}{{ inputs.project_path }}
                steps:
                  - name: Checkout
                    uses: actions/checkout@v4

                  - name: Set up JDK 17
                    uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'
                      cache: gradle

                  - name: Validate Android Gradle project
                    shell: bash
                    run: |
                      test -f gradlew || { echo "gradlew not found in ${'$'}{{ inputs.project_path }}"; exit 2; }
                      chmod +x gradlew

                  - name: Build Debug APK
                    shell: bash
                    run: ./gradlew --no-daemon assembleDebug

                  - name: Upload APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: lmai-cloud-apk-${'$'}{{ inputs.request_id }}
                      path: ${'$'}{{ inputs.project_path }}/**/build/outputs/apk/**/*.apk
                      if-no-files-found: error
                      retention-days: 7
        """.trimIndent()
    }
}

@Serializable
private data class GitHubContentWriteRequest(
    val message: String,
    val content: String,
    val branch: String,
)

@Serializable
data class GitHubWorkflowDispatchRequest(
    val ref: String,
    val inputs: Map<String, String> = emptyMap(),
)

@Serializable
data class GitHubWorkflowDispatchResult(
    @SerialName("workflow_run_id") val workflowRunId: Long? = null,
    @SerialName("run_url") val runUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class GitHubWorkflowRunsResponse(
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
)

@Serializable
data class GitHubWorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val status: String? = null,
    val conclusion: String? = null,
    val event: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

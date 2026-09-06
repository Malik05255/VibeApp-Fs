package com.malik.lmai.feature.github

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

    /**
     * Installs or upgrades the lm_AI managed workflow.
     *
     * Returning true means GitHub received a workflow-file write, so callers
     * should allow a short propagation window before dispatching it.
     */
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

        val currentMetadata = when (metadataResponse.status.value) {
            in 200..299 -> metadataResponse.body<GitHubContentMetadata>()
            404 -> null
            else -> {
                checkResponse(metadataResponse.status.value)
                null
            }
        }

        val existingContent = currentMetadata?.decodedContent()
        if (
            existingContent != null &&
            normalizeWorkflow(existingContent) == normalizeWorkflow(CLOUD_WORKFLOW_YAML)
        ) {
            return false
        }

        val encoded = Base64.getEncoder().encodeToString(
            CLOUD_WORKFLOW_YAML.toByteArray(Charsets.UTF_8)
        )
        val writeResponse = client.put(
            "$API_ROOT/repos/$repositoryFullName/contents/$CLOUD_WORKFLOW_PATH"
        ) {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            if (currentMetadata == null) {
                setBody(
                    GitHubContentCreateRequest(
                        message = "chore: add lm_AI cloud build workflow",
                        content = encoded,
                        branch = defaultBranch,
                    )
                )
            } else {
                setBody(
                    GitHubContentUpdateRequest(
                        message = "chore: update lm_AI cloud build workflow",
                        content = encoded,
                        branch = defaultBranch,
                        sha = currentMetadata.sha,
                    )
                )
            }
        }
        checkResponse(writeResponse.status.value)
        return true
    }

    suspend fun dispatchCloudBuild(
        token: String,
        repositoryFullName: String,
        branch: String,
        projectPath: String,
        requestId: String,
        repairMode: Boolean = false,
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
                        "repair_mode" to repairMode.toString(),
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

    private fun GitHubContentMetadata.decodedContent(): String? {
        val encoded = content
            ?.replace("\n", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return runCatching {
            Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun normalizeWorkflow(value: String): String =
        value.replace("\r\n", "\n").trim()

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
        const val CLOUD_WORKFLOW_VERSION = "4"

        private const val API_ROOT = "https://api.github.com"
        private val JSON = Json { ignoreUnknownKeys = true }

        val CLOUD_WORKFLOW_YAML: String = """
            # lm_AI-managed-workflow: v$CLOUD_WORKFLOW_VERSION
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
                  repair_mode:
                    description: Attempt a guarded GitHub Copilot repair after build failure
                    required: true
                    default: false
                    type: boolean

            permissions:
              contents: read

            jobs:
              build-android:
                runs-on: ubuntu-latest
                timeout-minutes: 45
                outputs:
                  build_exit: ${'$'}{{ steps.build.outputs.exit_code }}
                defaults:
                  run:
                    working-directory: ${'$'}{{ inputs.project_path }}
                steps:
                  - name: Checkout
                    uses: actions/checkout@v6

                  - name: Set up JDK 17
                    uses: actions/setup-java@v5
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
                    id: build
                    shell: bash
                    run: |
                      set +e
                      set -o pipefail
                      ./gradlew --no-daemon assembleDebug 2>&1 | tee "${'$'}RUNNER_TEMP/lmai-build.log"
                      EXIT_CODE=${'$'}{PIPESTATUS[0]}
                      echo "exit_code=${'$'}EXIT_CODE" >> "${'$'}GITHUB_OUTPUT"
                      exit 0

                  - name: Upload build log
                    if: ${'$'}{{ steps.build.outputs.exit_code != '0' }}
                    uses: actions/upload-artifact@v4
                    with:
                      name: lmai-build-log-${'$'}{{ inputs.request_id }}
                      path: ${'$'}{{ runner.temp }}/lmai-build.log
                      if-no-files-found: error
                      retention-days: 2

                  - name: Upload APK
                    if: ${'$'}{{ steps.build.outputs.exit_code == '0' }}
                    uses: actions/upload-artifact@v4
                    with:
                      name: lmai-cloud-apk-${'$'}{{ inputs.request_id }}
                      path: ${'$'}{{ inputs.project_path }}/**/build/outputs/apk/**/*.apk
                      if-no-files-found: error
                      retention-days: 7

                  - name: Fail build-only request
                    if: ${'$'}{{ steps.build.outputs.exit_code != '0' && inputs.repair_mode != true }}
                    shell: bash
                    run: exit 1

              repair-android:
                needs: build-android
                if: ${'$'}{{ always() && inputs.repair_mode == true && needs.build-android.outputs.build_exit != '0' }}
                runs-on: ubuntu-latest
                timeout-minutes: 45
                permissions:
                  contents: write
                  pull-requests: write
                  copilot-requests: write
                defaults:
                  run:
                    working-directory: ${'$'}{{ inputs.project_path }}
                steps:
                  - name: Checkout source branch without persisted credentials
                    uses: actions/checkout@v6
                    with:
                      ref: ${'$'}{{ github.ref_name }}
                      fetch-depth: 0
                      persist-credentials: false

                  - name: Set up JDK 17
                    uses: actions/setup-java@v5
                    with:
                      distribution: temurin
                      java-version: '17'
                      cache: gradle

                  - name: Set up Node.js
                    uses: actions/setup-node@v7
                    with:
                      node-version: '24'

                  - name: Install GitHub Copilot CLI
                    shell: bash
                    run: npm install -g @github/copilot

                  - name: Download failed build log
                    uses: actions/download-artifact@v4
                    with:
                      name: lmai-build-log-${'$'}{{ inputs.request_id }}
                      path: ${'$'}{{ runner.temp }}/lmai-build-log

                  - name: Validate repair target
                    shell: bash
                    run: |
                      test -f gradlew || { echo "gradlew not found in ${'$'}{{ inputs.project_path }}"; exit 2; }
                      chmod +x gradlew

                  - name: Repair and verify source-only changes
                    id: repair
                    shell: bash
                    env:
                      COPILOT_HOME: ${'$'}{{ runner.temp }}/copilot-home
                      PROJECT_PATH: ${'$'}{{ inputs.project_path }}
                      REQUEST_ID: ${'$'}{{ inputs.request_id }}
                    run: |
                      set -euo pipefail
                      ROOT="${'$'}GITHUB_WORKSPACE"
                      PROJECT_DIR="${'$'}PWD"
                      LOG_FILE="${'$'}RUNNER_TEMP/lmai-build-log/lmai-build.log"
                      BASE_REF="${'$'}(git rev-parse HEAD)"
                      MAX_REPAIR_ATTEMPTS=2

                      git config user.name "lm_AI Cloud Repair"
                      git config user.email "lmai-cloud-repair@users.noreply.github.com"

                      build_project() {
                        set +e
                        set -o pipefail
                        ./gradlew --no-daemon assembleDebug 2>&1 | tee "${'$'}LOG_FILE"
                        local code=${'$'}{PIPESTATUS[0]}
                        set -e
                        return "${'$'}code"
                      }

                      validate_changes() {
                        cd "${'$'}ROOT"
                        local changed="${'$'}RUNNER_TEMP/lmai-changed-files.txt"
                        {
                          git diff --name-only
                          git ls-files --others --exclude-standard
                        } | sed '/^${'$'}/d' | sort -u > "${'$'}changed"

                        if [ ! -s "${'$'}changed" ]; then
                          echo "Copilot produced no repository changes."
                          return 2
                        fi

                        local project_prefix="${'$'}{PROJECT_PATH#./}"
                        project_prefix="${'$'}{project_prefix%/}"

                        while IFS= read -r file; do
                          case "${'$'}file" in
                            .github/*|.git/*|*/gradle/*|gradle/*|*/buildSrc/*|buildSrc/*|*/build.gradle|*/build.gradle.kts|build.gradle|build.gradle.kts|*/settings.gradle|*/settings.gradle.kts|settings.gradle|settings.gradle.kts|*/gradle.properties|gradle.properties|*/local.properties|local.properties|*/gradlew|gradlew|*/gradlew.bat|gradlew.bat|*.jks|*.keystore|*.p12|*.pfx|*.env|*.env.*|*secrets.properties|*google-services.json)
                              echo "Unsafe repair path rejected: ${'$'}file"
                              return 3
                              ;;
                          esac

                          if [ "${'$'}PROJECT_PATH" != "." ] && [ -n "${'$'}project_prefix" ]; then
                            case "${'$'}file" in
                              "${'$'}project_prefix"/*) ;;
                              *)
                                echo "Repair escaped project path: ${'$'}file"
                                return 4
                                ;;
                            esac
                          fi

                          case "${'$'}file" in
                            *.kt|*.java|*.xml|*.aidl|*.pro|*.c|*.cc|*.cpp|*.h|*.hpp) ;;
                            *)
                              echo "Repair file type is not source-only: ${'$'}file"
                              return 5
                              ;;
                          esac
                        done < "${'$'}changed"

                        git diff --check
                        cd "${'$'}PROJECT_DIR"
                      }

                      repaired=0
                      for attempt in ${'$'}(seq 1 "${'$'}MAX_REPAIR_ATTEMPTS"); do
                        ERROR_CONTEXT="${'$'}(tail -n 260 "${'$'}LOG_FILE" | sed -E 's/\x1B\[[0-9;]*[mK]//g')"
                        PROMPT="${'$'}(cat <<EOF
                      Repair this Android project source so ./gradlew --no-daemon assembleDebug succeeds.

                      Constraints:
                      - Make the smallest technically correct source/resource change.
                      - Work only inside the current project directory.
                      - You may edit source/resource files only: Kotlin, Java, Android XML, AIDL, ProGuard rules, or native C/C++ headers/sources.
                      - Never edit Gradle/build scripts, wrappers, version catalogs, .github, credentials, signing files, local.properties, gradle.properties, .env files, secrets.properties, google-services.json, keystores, or certificates.
                      - Do not change application identity/package name unless the compiler error explicitly proves a source declaration is wrong.
                      - Do not add unrelated features or cosmetic changes.
                      - Do not use network/web tools or shell commands. Inspect and edit source files only.
                      - This is repair attempt ${'$'}attempt of ${'$'}MAX_REPAIR_ATTEMPTS.

                      Latest build failure:
                      ${'$'}ERROR_CONTEXT
                      EOF
                      )"

                        set +e
                        GITHUB_TOKEN="${'$'}{{ github.token }}" copilot -p "${'$'}PROMPT" \
                          --no-ask-user \
                          --available-tools='edit,view,grep,glob' \
                          --allow-tool='read,write'
                        copilot_exit=${'$'}?
                        set -e

                        if [ "${'$'}copilot_exit" -ne 0 ]; then
                          echo "Copilot CLI was unavailable or denied (exit ${'$'}copilot_exit)."
                          git -C "${'$'}ROOT" reset --hard "${'$'}BASE_REF"
                          git -C "${'$'}ROOT" clean -fd
                          exit 20
                        fi

                        if ! validate_changes; then
                          git -C "${'$'}ROOT" reset --hard "${'$'}BASE_REF"
                          git -C "${'$'}ROOT" clean -fd
                          exit 21
                        fi

                        cd "${'$'}PROJECT_DIR"
                        if build_project; then
                          repaired=1
                          break
                        fi
                      done

                      if [ "${'$'}repaired" -ne 1 ]; then
                        echo "Repair attempts exhausted without a successful build."
                        git -C "${'$'}ROOT" reset --hard "${'$'}BASE_REF"
                        git -C "${'$'}ROOT" clean -fd
                        exit 22
                      fi

                      validate_changes
                      cd "${'$'}ROOT"
                      REPAIR_BRANCH="lmai-repair-${'$'}{REQUEST_ID//[^a-zA-Z0-9._-]/-}"
                      git checkout -b "${'$'}REPAIR_BRANCH"
                      git add -A
                      git commit -m "fix: lm_AI cloud repair ${'$'}REQUEST_ID"
                      echo "repair_branch=${'$'}REPAIR_BRANCH" >> "${'$'}GITHUB_OUTPUT"

                  - name: Push verified repair branch
                    shell: bash
                    env:
                      GH_TOKEN: ${'$'}{{ github.token }}
                      REPAIR_BRANCH: ${'$'}{{ steps.repair.outputs.repair_branch }}
                    run: |
                      cd "${'$'}GITHUB_WORKSPACE"
                      gh auth setup-git
                      git push origin "HEAD:refs/heads/${'$'}REPAIR_BRANCH"

                  - name: Open repair pull request
                    id: pull_request
                    shell: bash
                    env:
                      GH_TOKEN: ${'$'}{{ github.token }}
                      REPAIR_BRANCH: ${'$'}{{ steps.repair.outputs.repair_branch }}
                      REQUEST_ID: ${'$'}{{ inputs.request_id }}
                    run: |
                      cd "${'$'}GITHUB_WORKSPACE"
                      PR_URL="${'$'}(gh pr create \
                        --base "${'$'}GITHUB_REF_NAME" \
                        --head "${'$'}REPAIR_BRANCH" \
                        --title "lm_AI cloud repair: ${'$'}REQUEST_ID" \
                        --body "Automated guarded source-only repair created after a failed lm_AI cloud build. The repaired project passed ./gradlew --no-daemon assembleDebug before this pull request was opened.")"
                      echo "url=${'$'}PR_URL" >> "${'$'}GITHUB_OUTPUT"
                      echo "### lm_AI repair ready" >> "${'$'}GITHUB_STEP_SUMMARY"
                      echo "${'$'}PR_URL" >> "${'$'}GITHUB_STEP_SUMMARY"

                  - name: Upload repaired APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: lmai-repaired-apk-${'$'}{{ inputs.request_id }}
                      path: ${'$'}{{ inputs.project_path }}/**/build/outputs/apk/**/*.apk
                      if-no-files-found: error
                      retention-days: 7
        """.trimIndent()
    }
}

@Serializable
private data class GitHubContentMetadata(
    val sha: String,
    val content: String? = null,
    val encoding: String? = null,
)

@Serializable
private data class GitHubContentCreateRequest(
    val message: String,
    val content: String,
    val branch: String,
)

@Serializable
private data class GitHubContentUpdateRequest(
    val message: String,
    val content: String,
    val branch: String,
    val sha: String,
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

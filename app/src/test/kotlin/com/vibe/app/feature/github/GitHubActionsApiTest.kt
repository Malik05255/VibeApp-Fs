package com.vibe.app.feature.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubActionsApiTest {

    @Test
    fun `cloud workflow is manually dispatchable and uploads apk`() {
        val workflow = GitHubActionsApi.CLOUD_WORKFLOW_YAML

        assertTrue("lm_AI-managed-workflow: v${GitHubActionsApi.CLOUD_WORKFLOW_VERSION}" in workflow)
        assertTrue("workflow_dispatch:" in workflow)
        assertTrue("project_path:" in workflow)
        assertTrue("request_id:" in workflow)
        assertTrue("repair_mode:" in workflow)
        assertTrue("assembleDebug" in workflow)
        assertTrue("actions/upload-artifact@v4" in workflow)
        assertTrue("retention-days: 7" in workflow)
    }

    @Test
    fun `build only path keeps repository read only`() {
        val workflow = GitHubActionsApi.CLOUD_WORKFLOW_YAML

        assertTrue("permissions:\n  contents: read" in workflow)
        assertTrue("Fail build-only request" in workflow)
        assertTrue("inputs.repair_mode != true" in workflow)
    }

    @Test
    fun `repair path is explicit bounded source only and opens a pull request`() {
        val workflow = GitHubActionsApi.CLOUD_WORKFLOW_YAML

        assertTrue("MAX_REPAIR_ATTEMPTS=2" in workflow)
        assertTrue("copilot-requests: write" in workflow)
        assertTrue("pull-requests: write" in workflow)
        assertTrue("persist-credentials: false" in workflow)
        assertTrue("--available-tools='edit,view,grep,glob'" in workflow)
        assertTrue("--allow-tool='read,write'" in workflow)
        assertTrue("Repair file type is not source-only" in workflow)
        assertTrue("*/build.gradle.kts" in workflow)
        assertTrue("Unsafe repair path rejected" in workflow)
        assertTrue("Repair escaped project path" in workflow)
        assertTrue("gh pr create" in workflow)
        assertTrue("lmai-repair-" in workflow)
        assertFalse("git push origin \"HEAD:${'$'}GITHUB_REF_NAME\"" in workflow)
    }

    @Test
    fun `copilot token is scoped to the copilot child process instead of repair step environment`() {
        val workflow = GitHubActionsApi.CLOUD_WORKFLOW_YAML

        assertTrue("GITHUB_TOKEN=\"${'$'}{{ github.token }}\" copilot" in workflow)
        assertFalse("env:\n          GITHUB_TOKEN: ${'$'}{{ github.token }}" in workflow)
    }

    @Test
    fun `cloud workflow never embeds user ai credentials`() {
        val workflow = GitHubActionsApi.CLOUD_WORKFLOW_YAML.lowercase()

        assertFalse("api_key" in workflow)
        assertFalse("copilot_github_token" in workflow)
        assertFalse("authorization:" in workflow)
        assertFalse("gemini_api_key" in workflow)
        assertFalse("openai_api_key" in workflow)
        assertFalse("anthropic_api_key" in workflow)
    }
}

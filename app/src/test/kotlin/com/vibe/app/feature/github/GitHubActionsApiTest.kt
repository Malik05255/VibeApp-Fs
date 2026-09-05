package com.vibe.app.feature.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubActionsApiTest {

    @Test
    fun `cloud workflow is manually dispatchable and uploads apk`() {
        val workflow = GitHubActionsApi.CLOUD_WORKFLOW_YAML

        assertTrue("workflow_dispatch:" in workflow)
        assertTrue("project_path:" in workflow)
        assertTrue("request_id:" in workflow)
        assertTrue("assembleDebug" in workflow)
        assertTrue("actions/upload-artifact@v4" in workflow)
        assertTrue("retention-days: 7" in workflow)
    }

    @Test
    fun `cloud workflow does not embed AI credentials or write repository contents`() {
        val workflow = GitHubActionsApi.CLOUD_WORKFLOW_YAML.lowercase()

        assertFalse("api_key" in workflow)
        assertFalse("authorization:" in workflow)
        assertTrue("contents: read" in workflow)
    }
}

package com.malik.lmai.feature.agent.tool

import com.malik.lmai.feature.agent.AgentTool
import com.malik.lmai.feature.agent.AgentToolCall
import com.malik.lmai.feature.agent.AgentToolContext
import com.malik.lmai.feature.agent.AgentToolDefinition
import com.malik.lmai.feature.agent.AgentToolResult
import com.malik.lmai.feature.project.ProjectManager
import com.malik.lmai.feature.project.LmaiProjectDirs
import com.malik.lmai.feature.project.memo.MemoLoader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Singleton
class GetProjectMemoTool @Inject constructor(
    private val projectManager: ProjectManager,
    private val memoLoader: MemoLoader,
) : AgentTool {

    override val definition = AgentToolDefinition(
        name = "get_project_memo",
        description = "Re-fetch the current project memo (intent + outline). The memo is " +
            "already in your system prompt at turn start; only call this if you suspect " +
            "context compaction has dropped it mid-conversation.",
        inputSchema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject { })
        },
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        if (context.projectId.isBlank()) {
            return call.errorResult("No project context available")
        }
        val workspace = projectManager.openWorkspace(context.projectId)
        val vibeDirs = LmaiProjectDirs.fromWorkspaceRoot(workspace.rootDir)
        val memo = memoLoader.load(vibeDirs)
        val text = if (memo == null) {
            "<project-memo>(no memo yet)</project-memo>"
        } else {
            MemoLoader.assembleForPrompt(memo)
        }
        return call.result(buildJsonObject {
            put("memo", JsonPrimitive(text))
        })
    }
}

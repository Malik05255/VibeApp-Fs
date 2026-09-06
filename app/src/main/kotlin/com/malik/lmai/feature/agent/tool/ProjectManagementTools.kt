package com.malik.lmai.feature.agent.tool

import com.malik.lmai.data.repository.ProjectRepository
import com.malik.lmai.feature.agent.AgentTool
import com.malik.lmai.feature.agent.AgentToolCall
import com.malik.lmai.feature.agent.AgentToolContext
import com.malik.lmai.feature.agent.AgentToolDefinition
import com.malik.lmai.feature.agent.AgentToolResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Singleton
class RenameProjectTool @Inject constructor(
    private val projectRepository: ProjectRepository,
) : AgentTool {

    override val definition = AgentToolDefinition(
        name = "rename_project",
        description = "Rename the current project.",
        inputSchema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "name",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put("description", JsonPrimitive("A short descriptive project name, max 30 characters."))
                            put("maxLength", JsonPrimitive(30))
                        },
                    )
                },
            )
            put("required", requiredFields("name"))
        },
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val name = call.arguments.requireString("name").take(30)
        if (context.projectId.isBlank()) {
            return call.errorResult("No project context available")
        }
        projectRepository.renameProject(context.projectId, name)
        return call.okResult()
    }
}

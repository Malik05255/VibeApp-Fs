package com.vibe.app.feature.agent.tool

import com.vibe.app.feature.agent.AgentTool
import com.vibe.app.feature.agent.AgentToolCall
import com.vibe.app.feature.agent.AgentToolContext
import com.vibe.app.feature.agent.AgentToolDefinition
import com.vibe.app.feature.agent.AgentToolResult
import com.vibe.app.feature.project.ProjectManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Selects a compact set of files that are most relevant to the current user task.
 * The model should use the returned paths as the starting point for grep/read calls.
 */
@Singleton
class SelectProjectContextTool @Inject constructor(
    private val projectManager: ProjectManager,
    private val engine: ProjectContextEngine,
) : AgentTool {

    override val definition = AgentToolDefinition(
        name = "select_project_context",
        description = "Rank the existing project files against the current task and return a compact " +
            "context map: relevant paths, scores, reasons, symbols and short matching excerpts. " +
            "For an existing project, use this near the start of the turn before broad grep/read calls. " +
            "Pass the user's actual request as `query`; then read only the returned files that are needed.",
        inputSchema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put("query", stringProp("The user's current task/request, copied as faithfully as practical."))
                    put(
                        "max_files",
                        intProp("Maximum relevant files to return. Default 10; allowed range 1-20."),
                    )
                },
            )
            put("required", requiredFields("query"))
        },
    )

    override suspend fun execute(
        call: AgentToolCall,
        context: AgentToolContext,
    ): AgentToolResult = withContext(Dispatchers.IO) {
        val query = call.arguments.requireString("query")
        val maxFiles = call.arguments.optionalInt(
            "max_files",
            ProjectContextEngine.DEFAULT_MAX_FILES,
        )
        val workspace = projectManager.openWorkspace(context.projectId)
        val selection = engine.select(
            projectRoot = workspace.rootDir,
            query = query,
            maxFiles = maxFiles,
        )

        call.result(
            buildJsonObject {
                put("query", JsonPrimitive(query))
                put("files_examined", JsonPrimitive(selection.filesExamined))
                put("truncated", JsonPrimitive(selection.truncated))
                put(
                    "query_terms",
                    buildJsonArray {
                        selection.queryTerms.forEach { add(JsonPrimitive(it)) }
                    },
                )
                put(
                    "selected_files",
                    buildJsonArray {
                        selection.selectedFiles.forEach { file ->
                            add(
                                buildJsonObject {
                                    put("path", JsonPrimitive(file.path))
                                    put("score", JsonPrimitive(file.score))
                                    put(
                                        "reasons",
                                        buildJsonArray {
                                            file.reasons.forEach { add(JsonPrimitive(it)) }
                                        },
                                    )
                                    put(
                                        "symbols",
                                        buildJsonArray {
                                            file.symbols.forEach { add(JsonPrimitive(it)) }
                                        },
                                    )
                                    put(
                                        "excerpts",
                                        buildJsonArray {
                                            file.excerpts.forEach { add(JsonPrimitive(it)) }
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
                put(
                    "next_step",
                    JsonPrimitive(
                        "Use grep_project_files for exact symbols/terms, then read_project_file only for the selected paths you need.",
                    ),
                )
            },
        )
    }
}

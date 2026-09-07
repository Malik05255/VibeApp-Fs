package com.malik.lmai.feature.agent.tool

import com.malik.lmai.feature.agent.AgentTool
import com.malik.lmai.feature.agent.AgentToolCall
import com.malik.lmai.feature.agent.AgentToolContext
import com.malik.lmai.feature.agent.AgentToolDefinition
import com.malik.lmai.feature.agent.AgentToolResult
import com.malik.lmai.feature.mcp.PeachMcpClient
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class PeachWhatsAppTool @Inject constructor(
    private val peach: PeachMcpClient,
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "peach_whatsapp",
        description = "Access the WhatsApp Business account connected to H through Peach MCP. Use action=list_tools first when you need to discover the exact Peach operation, then action=call_tool with the selected tool name and arguments. Never guess recipients or send messages without a clear user request.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("list_tools"))
                        add(JsonPrimitive("call_tool"))
                    })
                })
                put("tool_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact Peach MCP tool name returned by list_tools")
                })
                put("arguments", buildJsonObject {
                    put("type", "object")
                    put("description", "Arguments for the selected Peach MCP tool")
                    put("additionalProperties", true)
                })
            })
            put("required", buildJsonArray { add(JsonPrimitive("action")) })
            put("additionalProperties", false)
        },
    )

    override suspend fun execute(
        call: AgentToolCall,
        context: AgentToolContext,
    ): AgentToolResult {
        return try {
            val args = call.arguments.jsonObject
            when (args["action"]?.jsonPrimitive?.content) {
                "list_tools" -> {
                    val tools = peach.listTools()
                    AgentToolResult(
                        toolCallId = call.id,
                        toolName = call.name,
                        output = buildJsonObject {
                            put("connected", true)
                            put("tool_count", tools.size)
                            put("tools", buildJsonArray {
                                tools.forEach { tool ->
                                    add(buildJsonObject {
                                        put("name", tool.name)
                                        put("description", tool.description)
                                        put("input_schema", tool.inputSchema)
                                    })
                                }
                            })
                        },
                    )
                }

                "call_tool" -> {
                    val toolName = args["tool_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        ?: error("tool_name is required for call_tool")
                    val remoteArguments = args["arguments"] as? JsonObject ?: buildJsonObject {}
                    val result = peach.callTool(toolName, remoteArguments)
                    AgentToolResult(
                        toolCallId = call.id,
                        toolName = call.name,
                        output = buildJsonObject {
                            put("remote_tool", toolName)
                            put("result", result)
                        },
                    )
                }

                else -> error("Unsupported peach_whatsapp action")
            }
        } catch (e: Exception) {
            AgentToolResult(
                toolCallId = call.id,
                toolName = call.name,
                output = buildJsonObject {
                    put("error", e.message ?: "Peach WhatsApp operation failed")
                },
                isError = true,
            )
        }
    }
}

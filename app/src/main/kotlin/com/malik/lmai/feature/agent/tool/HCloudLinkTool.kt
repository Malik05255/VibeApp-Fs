package com.malik.lmai.feature.agent.tool

import com.malik.lmai.feature.agent.AgentTool
import com.malik.lmai.feature.agent.AgentToolCall
import com.malik.lmai.feature.agent.AgentToolContext
import com.malik.lmai.feature.agent.AgentToolDefinition
import com.malik.lmai.feature.agent.AgentToolResult
import com.malik.lmai.feature.assistant.HCloudLinkClient
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Owner-only tool for linking the Android H identity to the same cloud state used by WhatsApp. */
class HCloudLinkTool @Inject constructor(
    private val cloud: HCloudLinkClient,
) : AgentTool {
    override val definition = AgentToolDefinition(
        name = "h_cloud_link",
        description = "Link the signed-in Android H owner to the same private H cloud runtime used by the owner's WhatsApp. " +
            "Use START_LINK when the owner asks to connect/sync H with WhatsApp; return the exact whatsappCommand to the user and tell them to send it from their owner WhatsApp. " +
            "Use FINISH_LINK only after the user says they sent that command, and only with the exact pairing code and WhatsApp number the owner explicitly provides; never infer or guess a phone number. " +
            "Use STATUS to check whether the app is already linked. Use SNAPSHOT only when the owner explicitly needs the shared cloud state. " +
            "This tool never exposes or requests H_RUNTIME_SECRET.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("START_LINK"))
                        add(JsonPrimitive("FINISH_LINK"))
                        add(JsonPrimitive("STATUS"))
                        add(JsonPrimitive("SNAPSHOT"))
                    })
                })
                put("pairing_code", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact eight-digit code returned by START_LINK; required for FINISH_LINK")
                })
                put("wa_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Owner WhatsApp number explicitly supplied by the owner; required for FINISH_LINK and never guessed")
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
        val args = call.arguments.jsonObject
        val response = when (args["action"]?.jsonPrimitive?.content?.uppercase()) {
            "START_LINK" -> cloud.startLink()
            "STATUS" -> cloud.status()
            "SNAPSHOT" -> cloud.snapshot()
            "FINISH_LINK" -> {
                val code = args.string("pairing_code")
                    ?: return error(call, "pairing_code is required for FINISH_LINK")
                val waId = args.string("wa_id")
                    ?: return error(call, "wa_id is required for FINISH_LINK and must be supplied explicitly by the owner")
                cloud.finishLink(code, waId)
            }
            else -> return error(call, "Unsupported h_cloud_link action")
        }

        return AgentToolResult(
            toolCallId = call.id,
            toolName = call.name,
            output = buildJsonObject {
                put("http_status", response.statusCode)
                response.body.forEach { (key, value) -> put(key, value) }
            },
            isError = !response.ok,
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun error(call: AgentToolCall, message: String) = AgentToolResult(
        toolCallId = call.id,
        toolName = call.name,
        output = buildJsonObject {
            put("ok", false)
            put("error", message)
        },
        isError = true,
    )
}

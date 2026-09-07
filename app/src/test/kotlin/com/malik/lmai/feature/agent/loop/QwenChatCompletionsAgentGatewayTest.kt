package com.malik.lmai.feature.agent.loop

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.agent.AgentLoopPolicy
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolChoiceMode
import com.malik.lmai.feature.agent.AgentToolDefinition
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QwenChatCompletionsAgentGatewayTest {

    @Test
    fun `required tool choice falls back to auto for qwen`() {
        val request = createRequest(toolChoiceMode = AgentToolChoiceMode.REQUIRED, includeTools = true)

        assertEquals("auto", request.toQwenToolChoice())
    }

    @Test
    fun `none tool choice stays none when tools are present`() {
        val request = createRequest(toolChoiceMode = AgentToolChoiceMode.NONE, includeTools = true)

        assertEquals("none", request.toQwenToolChoice())
    }

    @Test
    fun `tool choice is omitted when no tools are available`() {
        val request = createRequest(toolChoiceMode = AgentToolChoiceMode.REQUIRED, includeTools = false)

        assertNull(request.toQwenToolChoice())
    }

    private fun createRequest(
        toolChoiceMode: AgentToolChoiceMode,
        includeTools: Boolean,
    ) = AgentModelRequest(
        platform = PlatformV2(
            name = "Qwen",
            compatibleType = ClientType.QWEN,
            apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            model = "qwen3-max",
        ),
        conversation = emptyList(),
        fullConversation = emptyList(),
        tools = if (includeTools) {
            listOf(
                AgentToolDefinition(
                    name = "read_file",
                    description = "Read a file",
                    inputSchema = buildJsonObject {},
                ),
            )
        } else {
            emptyList()
        },
        policy = AgentLoopPolicy(toolChoiceMode = toolChoiceMode),
    )
}

fun AgentModelRequest.toQwenToolChoice(): String? {
    if (tools.isEmpty()) {
        return null
    }

    return when (policy.toolChoiceMode) {
        AgentToolChoiceMode.AUTO ->
            "auto"

        AgentToolChoiceMode.REQUIRED ->
            "auto"

        AgentToolChoiceMode.NONE ->
            "none"
    }
}

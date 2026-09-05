package com.vibe.app.feature.agent.loop

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentConversationItem
import com.vibe.app.feature.agent.AgentLoopPolicy
import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolChoiceMode
import com.vibe.app.feature.agent.AgentToolDefinition
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnPolicyTest {

    @Test
    fun `arabic greeting stays ordinary conversation`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("\u0627\u0644\u0633\u0644\u0627\u0645 \u0639\u0644\u064a\u0643\u0645"),
        )
    }

    @Test
    fun `english greeting stays ordinary conversation`() {
        assertEquals(ChatTurnMode.CONVERSATION, ChatTurnPolicy.detect("Hello, how are you?"))
    }

    @Test
    fun `vague arabic app request enters discovery`() {
        assertEquals(
            ChatTurnMode.APP_DISCOVERY,
            ChatTurnPolicy.detect("\u0623\u0628\u064a \u062a\u0637\u0628\u064a\u0642"),
        )
    }

    @Test
    fun `vague english app request enters discovery`() {
        assertEquals(ChatTurnMode.APP_DISCOVERY, ChatTurnPolicy.detect("I want an app"))
    }

    @Test
    fun `explicit arabic build request enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("\u0623\u0646\u0634\u0626 \u062a\u0637\u0628\u064a\u0642 \u0645\u062a\u062c\u0631 \u0645\u0639 \u0633\u0644\u0629 \u0648\u062a\u0633\u062c\u064a\u0644 \u062f\u062e\u0648\u0644"),
        )
    }

    @Test
    fun `explicit english build request enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("Build me an app with login and a shopping cart"),
        )
    }

    @Test
    fun `follow up arabic modification enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("\u063a\u064a\u0631 \u0644\u0648\u0646 \u0627\u0644\u0632\u0631 \u0644\u0644\u0623\u0632\u0631\u0642"),
        )
    }

    @Test
    fun `conversation adapter disables tools and follows latest message language`() {
        val adapted = ChatTurnPolicy.adapt(
            request(
                userText = "\u0627\u0644\u0633\u0644\u0627\u0645 \u0639\u0644\u064a\u0643\u0645",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            )
        )

        assertTrue(adapted.tools.isEmpty())
        assertEquals(AgentToolChoiceMode.NONE, adapted.policy.toolChoiceMode)
        assertTrue(adapted.instructions.orEmpty().contains("latest message is Arabic"))
        assertTrue(adapted.instructions.orEmpty().contains("Return only the user-facing answer"))
    }

    @Test
    fun `execution adapter preserves project tools`() {
        val original = request(
            userText = "implement the requested app changes",
            toolChoice = AgentToolChoiceMode.REQUIRED,
        )

        val adapted = ChatTurnPolicy.adapt(original)

        assertEquals(original.tools, adapted.tools)
        assertEquals(AgentToolChoiceMode.REQUIRED, adapted.policy.toolChoiceMode)
        assertTrue(adapted.instructions.orEmpty().contains("User-facing response rules"))
    }

    private fun request(
        userText: String,
        toolChoice: AgentToolChoiceMode,
    ): AgentModelRequest {
        val tool = AgentToolDefinition(
            name = "write_project_file",
            description = "write",
            inputSchema = buildJsonObject {},
        )
        return AgentModelRequest(
            platform = PlatformV2(
                name = "Free AI",
                compatibleType = ClientType.CUSTOM,
                apiUrl = "https://example.test/v1",
                token = null,
                model = "model",
                provider = "internal:blockrun",
                isFree = true,
            ),
            conversation = listOf(
                AgentConversationItem(
                    role = AgentMessageRole.USER,
                    text = userText,
                )
            ),
            fullConversation = listOf(
                AgentConversationItem(
                    role = AgentMessageRole.USER,
                    text = userText,
                )
            ),
            instructions = "base agent prompt",
            tools = listOf(tool),
            policy = AgentLoopPolicy(toolChoiceMode = toolChoice),
        )
    }
}

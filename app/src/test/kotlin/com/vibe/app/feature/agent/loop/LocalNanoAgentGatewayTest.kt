package com.vibe.app.feature.agent.loop

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentConversationItem
import com.vibe.app.feature.agent.AgentLoopPolicy
import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.ai.LocalNanoRuntime
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNanoAgentGatewayTest {

    private val gateway = LocalNanoAgentGateway(mockk<LocalNanoRuntime>())

    @Test
    fun `large history never removes system or user instructions`() {
        val history = buildList {
            repeat(15) { index ->
                add(
                    AgentConversationItem(
                        role = if (index % 2 == 0) AgentMessageRole.USER else AgentMessageRole.ASSISTANT,
                        text = "history-$index-" + "x".repeat(2_000),
                    )
                )
            }
            add(
                AgentConversationItem(
                    role = AgentMessageRole.USER,
                    text = "LATEST_USER_REQUEST_MUST_SURVIVE " + "y".repeat(2_000),
                )
            )
        }

        val prompt = gateway.buildPrompt(
            AgentModelRequest(
                platform = platform(),
                conversation = history.takeLast(1),
                fullConversation = history,
                instructions = "KEEP_THIS_SYSTEM_INSTRUCTION",
                tools = emptyList(),
                policy = AgentLoopPolicy(),
            )
        )

        assertTrue(prompt.length <= 14_000)
        assertTrue(prompt.startsWith("You are lm_AI's on-device fallback assistant."))
        assertTrue(prompt.contains("KEEP_THIS_SYSTEM_INSTRUCTION"))
        assertTrue(prompt.contains("LATEST_USER_REQUEST_MUST_SURVIVE"))
        assertTrue(prompt.endsWith("Respond to the latest user request."))
    }

    private fun platform() = PlatformV2(
        name = "Local Gemini Nano",
        compatibleType = ClientType.CUSTOM,
        apiUrl = "local://android-aicore",
        token = null,
        model = "gemini-nano",
        provider = "internal:local",
        isFree = true,
    )
}

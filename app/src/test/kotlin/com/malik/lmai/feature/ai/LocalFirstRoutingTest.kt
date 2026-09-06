package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentLoopPolicy
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalFirstRoutingTest {

    private val healthTracker = mockk<ProviderHealthTracker>(relaxed = true)
    private val orchestrator = SmartFreeAiOrchestrator(
        FreeAiRouter(),
        AiTaskClassifier(),
        healthTracker,
    )

    @Test
    fun `ready local model wins ordinary chat over connected provider`() {
        val local = localPlatform()
        val openRouter = openRouterPlatform()
        every { healthTracker.interactiveScoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request("hello how are you"),
            platforms = listOf(openRouter, local),
        )

        assertEquals(local.uid, selected?.uid)
    }

    @Test
    fun `factual question prefers stronger connected knowledge route`() {
        val local = localPlatform()
        val gemini = geminiPlatform()
        every { healthTracker.interactiveScoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request("متى توفي أحمد زكي؟"),
            platforms = listOf(gemini, local),
        )

        assertEquals(gemini.uid, selected?.uid)
    }

    private fun request(text: String) = AgentModelRequest(
        platform = openRouterPlatform(),
        conversation = listOf(
            AgentConversationItem(
                role = AgentMessageRole.USER,
                text = text,
            )
        ),
        fullConversation = emptyList(),
        tools = emptyList(),
        policy = AgentLoopPolicy(),
    )

    private fun localPlatform() = PlatformV2(
        name = "H Local",
        compatibleType = ClientType.CUSTOM,
        apiUrl = FreeAiRouter.H_LOCAL_API_URL,
        token = null,
        model = FreeAiBootstrapper.H_LOCAL_MODEL,
        provider = "internal:local",
        isFree = true,
    )

    private fun openRouterPlatform() = PlatformV2(
        name = "H OpenRouter",
        compatibleType = ClientType.OPEN_ROUTER,
        apiUrl = "https://openrouter.ai/api/v1",
        token = "test-key",
        model = "openrouter/free",
        provider = "internal:openrouter",
        isFree = true,
    )

    private fun geminiPlatform() = PlatformV2(
        name = "H Gemini",
        compatibleType = ClientType.GOOGLE_AI_STUDIO,
        apiUrl = "https://generativelanguage.googleapis.com",
        token = "test-key",
        model = "gemini-flash",
        provider = "internal:gemini",
        isFree = true,
    )
}

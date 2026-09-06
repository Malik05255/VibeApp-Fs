package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentLoopPolicy
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolChoiceMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalFirstRoutingRegressionTest {

    private val healthTracker = mockk<ProviderHealthTracker>(relaxed = true)
    private val orchestrator = SmartFreeAiOrchestrator(
        FreeAiRouter(),
        AiTaskClassifier(),
        healthTracker,
    )

    @Test
    fun `ordinary factual conversation stays on cloud when cloud exists`() {
        val local = localPlatform()
        val cloud = openRouterPlatform()
        every { healthTracker.interactiveScoreAdjustment(any(), any()) } returns 50

        val selected = orchestrator.selectBest(
            request = request("تعرف احمد زكي متى مات"),
            platforms = listOf(cloud, local),
        )

        assertEquals(cloud.uid, selected?.uid)
    }

    @Test
    fun `personal conversation stays cloud first when a connected route exists`() {
        val local = localPlatform()
        val cloud = openRouterPlatform()
        every { healthTracker.interactiveScoreAdjustment(any(), any()) } returns 50

        val selected = orchestrator.selectBest(
            request = request("عندي مشكلة شخصية وابي افضفض لك"),
            platforms = listOf(cloud, local),
        )

        assertEquals(cloud.uid, selected?.uid)
    }

    @Test
    fun `local remains the fallback when no cloud route is usable`() {
        val local = localPlatform()
        every { healthTracker.interactiveScoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request("السلام عليكم"),
            platforms = listOf(local),
        )

        assertEquals(local.uid, selected?.uid)
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
        policy = AgentLoopPolicy(toolChoiceMode = AgentToolChoiceMode.AUTO),
    )

    private fun localPlatform() = PlatformV2(
        name = "H Local",
        compatibleType = ClientType.CUSTOM,
        apiUrl = FreeAiRouter.H_LOCAL_API_URL,
        token = null,
        model = HLocalModelManager.MODEL_ID,
        provider = "internal:local",
        isFree = true,
    )

    private fun openRouterPlatform() = PlatformV2(
        name = "OpenRouter",
        compatibleType = ClientType.OPEN_ROUTER,
        apiUrl = "https://openrouter.ai/api/v1",
        token = "internal-key",
        model = "openrouter/free",
        provider = "internal:openrouter",
        isFree = true,
    )
}

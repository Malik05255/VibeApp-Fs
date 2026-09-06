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
    fun `ready local model owns ordinary factual conversation even when cloud exists`() {
        val local = PlatformV2(
            name = "H Local",
            compatibleType = ClientType.CUSTOM,
            apiUrl = FreeAiRouter.H_LOCAL_API_URL,
            token = null,
            model = HLocalModelManager.MODEL_ID,
            provider = "internal:local",
            isFree = true,
        )
        val cloud = PlatformV2(
            name = "OpenRouter",
            compatibleType = ClientType.CUSTOM,
            apiUrl = "https://example.test/v1",
            token = "internal-key",
            model = "cloud-model",
            provider = "internal:openrouter",
            isFree = true,
        )

        every { healthTracker.interactiveScoreAdjustment(any(), any()) } returns 50

        val selected = orchestrator.selectBest(
            request = request("تعرف احمد زكي متى مات"),
            platforms = listOf(cloud, local),
        )

        assertEquals(local.uid, selected?.uid)
    }

    @Test
    fun `personal problem stays local instead of coding cloud route`() {
        val local = PlatformV2(
            name = "H Local",
            compatibleType = ClientType.CUSTOM,
            apiUrl = FreeAiRouter.H_LOCAL_API_URL,
            token = null,
            model = HLocalModelManager.MODEL_ID,
            provider = "internal:local",
            isFree = true,
        )
        val codingCloud = PlatformV2(
            name = "Free Code",
            compatibleType = ClientType.CUSTOM,
            apiUrl = FreeAiRouter.BLOCKRUN_API_BASE,
            token = null,
            model = FreeAiBootstrapper.BLOCKRUN_FAST_CODE_MODEL,
            provider = "internal:blockrun",
            isFree = true,
        )

        every { healthTracker.interactiveScoreAdjustment(any(), any()) } returns 50

        val selected = orchestrator.selectBest(
            request = request("عندي مشكلة شخصية وابي افضفض لك"),
            platforms = listOf(codingCloud, local),
        )

        assertEquals(local.uid, selected?.uid)
    }

    private fun request(text: String) = AgentModelRequest(
        platform = PlatformV2(
            name = "placeholder",
            compatibleType = ClientType.CUSTOM,
            apiUrl = "https://example.test/v1",
            token = "test-token",
            model = "test-model",
        ),
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
}

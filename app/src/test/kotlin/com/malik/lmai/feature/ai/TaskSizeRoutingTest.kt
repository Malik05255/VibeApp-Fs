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

class TaskSizeRoutingTest {

    private val healthTracker = mockk<ProviderHealthTracker>(relaxed = true)
    private val orchestrator = SmartFreeAiOrchestrator(
        FreeAiRouter(),
        AiTaskClassifier(),
        healthTracker,
    )

    @Test
    fun `single screen change uses fast coder even inside project execution`() {
        val strong = blockRun(FreeAiBootstrapper.BLOCKRUN_CODE_MODEL)
        val fast = blockRun(FreeAiBootstrapper.BLOCKRUN_FAST_CODE_MODEL)
        every { healthTracker.interactiveScoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request(
                "التطبيق كبير لكن عدل شاشة الإعدادات فقط وحرك زر الحفظ",
                AgentToolChoiceMode.REQUIRED,
            ),
            platforms = listOf(strong, fast),
        )

        assertEquals(fast.uid, selected?.uid)
    }

    @Test
    fun `whole project work uses stronger coder`() {
        val strong = blockRun(FreeAiBootstrapper.BLOCKRUN_CODE_MODEL)
        val fast = blockRun(FreeAiBootstrapper.BLOCKRUN_FAST_CODE_MODEL)
        every { healthTracker.scoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request(
                "عدل المشروع كامل وابن APK",
                AgentToolChoiceMode.REQUIRED,
            ),
            platforms = listOf(strong, fast),
        )

        assertEquals(strong.uid, selected?.uid)
    }

    private fun request(
        text: String,
        toolChoice: AgentToolChoiceMode,
    ) = AgentModelRequest(
        platform = blockRun(FreeAiBootstrapper.BLOCKRUN_CODE_MODEL),
        conversation = listOf(
            AgentConversationItem(
                role = AgentMessageRole.USER,
                text = text,
            )
        ),
        fullConversation = emptyList(),
        tools = emptyList(),
        policy = AgentLoopPolicy(toolChoiceMode = toolChoice),
    )

    private fun blockRun(model: String) = PlatformV2(
        name = model,
        compatibleType = ClientType.CUSTOM,
        apiUrl = FreeAiRouter.BLOCKRUN_API_BASE,
        token = null,
        model = model,
        provider = "internal:blockrun",
        isFree = true,
    )
}

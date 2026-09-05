package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentConversationItem
import com.vibe.app.feature.agent.AgentLoopPolicy
import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolChoiceMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartFreeAiOrchestratorTest {

    private val router = FreeAiRouter()
    private val classifier = AiTaskClassifier()
    private val healthTracker = mockk<ProviderHealthTracker>(relaxed = true)
    private val orchestrator = SmartFreeAiOrchestrator(
        router,
        classifier,
        healthTracker,
    )

    @Test
    fun `light chat prefers OpenRouter cloud route`() {
        val openRouter = platform("OpenRouter", "internal:openrouter", token = "internal-key")
        val gemini = platform("Gemini", "internal:gemini", token = "internal-key")

        every { healthTracker.scoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request("مرحبا كيف حالك"),
            platforms = listOf(gemini, openRouter),
        )

        assertEquals(openRouter.uid, selected?.uid)
    }

    @Test
    fun `complex app task remains cloud only`() {
        val legacyLocal = platform("Legacy Local", "internal:local", token = null)
        val gemini = platform("Gemini", "internal:gemini", token = "internal-key")
        val openRouter = platform("OpenRouter", "internal:openrouter", token = "internal-key")

        every { healthTracker.scoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request(
                text = "انشئ تطبيق كامل وابن APK",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            ),
            platforms = listOf(legacyLocal, openRouter, gemini),
        )

        assertEquals(openRouter.uid, selected?.uid)
    }

    @Test
    fun `learned unhealthy OpenRouter yields to healthy Gemini`() {
        val gemini = platform("Gemini", "internal:gemini", token = "internal-key")
        val openRouter = platform("OpenRouter", "internal:openrouter", token = "internal-key")

        every { healthTracker.scoreAdjustment(openRouter.uid, any()) } returns -80
        every { healthTracker.scoreAdjustment(gemini.uid, any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request(
                text = "اصلح أخطاء المشروع وابن التطبيق",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            ),
            platforms = listOf(gemini, openRouter),
        )

        assertEquals(gemini.uid, selected?.uid)
    }

    private fun request(
        text: String,
        toolChoice: AgentToolChoiceMode = AgentToolChoiceMode.AUTO,
    ) = AgentModelRequest(
        platform = platform("placeholder", "external:custom", token = "key"),
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

    private fun platform(
        name: String,
        provider: String,
        token: String?,
    ) = PlatformV2(
        name = name,
        compatibleType = ClientType.CUSTOM,
        apiUrl = if (provider == "internal:local") {
            "local://legacy"
        } else {
            "https://example.test/v1"
        },
        token = token,
        model = if (provider == "internal:local") "legacy-local" else "model",
        provider = provider,
        isFree = provider.startsWith("internal:"),
    )
}

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
    private val deviceProfiler = mockk<DeviceCapabilityProfiler>()
    private val healthTracker = mockk<ProviderHealthTracker>(relaxed = true)
    private val orchestrator = SmartFreeAiOrchestrator(
        router,
        classifier,
        deviceProfiler,
        healthTracker,
    )

    @Test
    fun `light chat prefers local on capable device`() {
        val local = platform("Local", "internal:local", token = null)
        val gemini = platform("Gemini", "internal:gemini", token = "internal-key")

        every { deviceProfiler.snapshot() } returns DeviceCapabilitySnapshot(
            profile = DeviceAiProfile.LOCAL_FULL,
            totalRamMb = 12_000,
            sdkInt = 36,
            mediaPerformanceClass = 36,
        )
        every { healthTracker.scoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request("مرحبا كيف حالك"),
            platforms = listOf(gemini, local),
        )

        assertEquals(local.uid, selected?.uid)
    }

    @Test
    fun `complex app task prefers strong cloud over local`() {
        val local = platform("Local", "internal:local", token = null)
        val gemini = platform("Gemini", "internal:gemini", token = "internal-key")
        val openRouter = platform("OpenRouter", "internal:openrouter", token = "internal-key")

        every { deviceProfiler.snapshot() } returns DeviceCapabilitySnapshot(
            profile = DeviceAiProfile.LOCAL_FULL,
            totalRamMb = 12_000,
            sdkInt = 36,
            mediaPerformanceClass = 36,
        )
        every { healthTracker.scoreAdjustment(any(), any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request(
                text = "انشئ تطبيق كامل وابن APK",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            ),
            platforms = listOf(local, openRouter, gemini),
        )

        assertEquals(gemini.uid, selected?.uid)
    }

    @Test
    fun `unhealthy top provider yields to healthy alternative`() {
        val gemini = platform("Gemini", "internal:gemini", token = "internal-key")
        val openRouter = platform("OpenRouter", "internal:openrouter", token = "internal-key")

        every { deviceProfiler.snapshot() } returns DeviceCapabilitySnapshot(
            profile = DeviceAiProfile.CLOUD_FIRST,
            totalRamMb = 6_000,
            sdkInt = 36,
            mediaPerformanceClass = 0,
        )
        every { healthTracker.scoreAdjustment(gemini.uid, any()) } returns -80
        every { healthTracker.scoreAdjustment(openRouter.uid, any()) } returns 0

        val selected = orchestrator.selectBest(
            request = request(
                text = "اصلح أخطاء المشروع وابن التطبيق",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            ),
            platforms = listOf(gemini, openRouter),
        )

        assertEquals(openRouter.uid, selected?.uid)
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
            "local://android-aicore"
        } else {
            "https://example.test/v1"
        },
        token = token,
        model = if (provider == "internal:local") "gemini-nano" else "model",
        provider = provider,
        isFree = provider.startsWith("internal:"),
    )
}

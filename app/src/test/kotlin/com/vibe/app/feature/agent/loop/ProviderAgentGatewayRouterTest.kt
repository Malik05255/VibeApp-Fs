package com.vibe.app.feature.agent.loop

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentLoopPolicy
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.ai.FreeAiFailoverCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAgentGatewayRouterTest {

    private val gateway = mockk<QwenChatCompletionsAgentGateway>()
    private val failover = mockk<FreeAiFailoverCoordinator>()
    private val router = ProviderAgentGatewayRouter(gateway, failover)

    @Test
    fun `persisted active provider is used before stale chat provider`() = runTest {
        val stale = platform("Old Gemini", "gemini")
        val active = platform("Groq", "groq")

        coEvery { failover.resolveStartPlatform(stale) } returns active
        coEvery { gateway.streamTurn(match { it.platform.uid == active.uid }) } returns
            flowOf(AgentModelEvent.Completed(finalText = "ok"))

        val events = router.streamTurn(request(stale)).toList()

        assertEquals(1, events.size)
        assertTrue(events.single() is AgentModelEvent.Completed)
        coVerify(exactly = 0) { gateway.streamTurn(match { it.platform.uid == stale.uid }) }
        coVerify(exactly = 1) { gateway.streamTurn(match { it.platform.uid == active.uid }) }
    }

    @Test
    fun `immediate provider failure switches inside same model turn`() = runTest {
        val primary = platform("Primary", "custom")
        val fallback = platform("Gemini", "gemini")

        coEvery { failover.resolveStartPlatform(primary) } returns primary
        coEvery { gateway.streamTurn(match { it.platform.uid == primary.uid }) } returns
            flowOf(AgentModelEvent.Failed("HTTP 429"))
        coEvery { gateway.streamTurn(match { it.platform.uid == fallback.uid }) } returns
            flowOf(
                AgentModelEvent.OutputDelta("ok"),
                AgentModelEvent.Completed(finalText = "ok"),
            )
        coEvery { failover.handleFailure(primary.uid) } returns
            FreeAiFailoverCoordinator.Result.Switched(
                fromPlatformUid = primary.uid,
                toPlatform = fallback,
                activatedFreeAi = true,
            )

        val events = router.streamTurn(request(primary)).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is AgentModelEvent.OutputDelta)
        assertTrue(events[1] is AgentModelEvent.Completed)
        coVerify(exactly = 1) { failover.handleFailure(primary.uid) }
        coVerify(exactly = 1) { gateway.streamTurn(match { it.platform.uid == fallback.uid }) }
    }

    @Test
    fun `provider exception before output switches to fallback`() = runTest {
        val primary = platform("Primary", "custom")
        val fallback = platform("Groq", "groq")

        coEvery { failover.resolveStartPlatform(primary) } returns primary
        coEvery { gateway.streamTurn(match { it.platform.uid == primary.uid }) } throws
            IllegalStateException("socket closed")
        coEvery { gateway.streamTurn(match { it.platform.uid == fallback.uid }) } returns
            flowOf(AgentModelEvent.Completed(finalText = "recovered"))
        coEvery { failover.handleFailure(primary.uid) } returns
            FreeAiFailoverCoordinator.Result.Switched(
                fromPlatformUid = primary.uid,
                toPlatform = fallback,
                activatedFreeAi = true,
            )

        val events = router.streamTurn(request(primary)).toList()

        assertEquals(1, events.size)
        assertTrue(events.single() is AgentModelEvent.Completed)
        coVerify(exactly = 1) { failover.handleFailure(primary.uid) }
    }

    @Test
    fun `partial output failure is surfaced without switching providers`() = runTest {
        val primary = platform("Primary", "custom")

        coEvery { failover.resolveStartPlatform(primary) } returns primary
        coEvery { gateway.streamTurn(any()) } returns
            flowOf(
                AgentModelEvent.OutputDelta("partial"),
                AgentModelEvent.Failed("connection lost"),
            )

        val events = router.streamTurn(request(primary)).toList()

        assertEquals(2, events.size)
        assertTrue(events[0] is AgentModelEvent.OutputDelta)
        assertTrue(events[1] is AgentModelEvent.Failed)
        coVerify(exactly = 0) { failover.handleFailure(any()) }
    }

    @Test
    fun `manual mode surfaces original provider failure`() = runTest {
        val primary = platform("Primary", "custom")

        coEvery { failover.resolveStartPlatform(primary) } returns primary
        coEvery { gateway.streamTurn(any()) } returns
            flowOf(AgentModelEvent.Failed("provider unavailable"))
        coEvery { failover.handleFailure(primary.uid) } returns
            FreeAiFailoverCoordinator.Result.ManualMode

        val events = router.streamTurn(request(primary)).toList()

        assertEquals(1, events.size)
        val failed = events.single() as AgentModelEvent.Failed
        assertEquals("provider unavailable", failed.message)
    }

    private fun request(platform: PlatformV2) = AgentModelRequest(
        platform = platform,
        conversation = emptyList(),
        fullConversation = emptyList(),
        tools = emptyList(),
        policy = AgentLoopPolicy(),
    )

    private fun platform(
        name: String,
        provider: String,
    ) = PlatformV2(
        name = name,
        compatibleType = ClientType.CUSTOM,
        apiUrl = "https://example.test/v1",
        token = "key",
        model = "model",
        provider = provider,
        isFree = provider != "custom",
    )
}

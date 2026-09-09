package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentLoopPolicy
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiFailoverCoordinatorTest {

    private val router = FreeAiRouter()
    private val bootstrapper = mockk<FreeAiBootstrapper>()
    private val helperPolicy = mockk<HHelperRoutingPolicy>()
    private val runtimeAvailability = mockk<FreeAiRuntimeAvailability>()
    private val coordinator = FreeAiFailoverCoordinator(
        router,
        bootstrapper,
        helperPolicy,
        runtimeAvailability,
    )

    @Test
    fun `start route is owned by H helper policy instead of external provider state`() = runTest {
        val core = platform("H Core", "internal:local", null, true)
        val paid = platform("Owner helper", "external:custom", "paid", false, enabled = true)
        val request = request(core)
        val platforms = listOf(core, paid)
        val snapshot = snapshot(platforms)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { runtimeAvailability.evaluate(platforms) } returns snapshot
        every {
            helperPolicy.select(
                request = request,
                allPlatforms = platforms,
                usablePlatforms = platforms,
                excludedPlatformUids = any(),
                nowMs = any(),
            )
        } returns HHelperRoutingPolicy.Decision(
            platform = core,
            role = HHelperRoutingPolicy.Role.H_CORE,
            escalation = HHelperRoutingPolicy.Escalation.NONE,
            task = AiTaskProfile(AiTaskKind.LIGHT_CHAT, false, 1),
        )

        assertEquals(core.uid, coordinator.resolveStartPlatform(request).uid)
    }

    @Test
    fun `paid helper failure excludes every external lane before falling back`() = runTest {
        val paid = platform("Paid helper", "external:custom", "paid", false, enabled = true)
        val secondExternal = platform("Other external", "external:gemini", "other", false, enabled = true)
        val hidden = platform(
            "Hidden H helper",
            "internal:blockrun",
            null,
            true,
            apiUrl = FreeAiRouter.BLOCKRUN_API_BASE,
        )
        val request = request(paid)
        val platforms = listOf(paid, secondExternal, hidden)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { runtimeAvailability.evaluate(platforms) } returns snapshot(platforms)
        every {
            helperPolicy.select(
                request = request,
                allPlatforms = platforms,
                usablePlatforms = platforms,
                excludedPlatformUids = match {
                    paid.uid in it && secondExternal.uid in it
                },
                nowMs = any(),
            )
        } returns HHelperRoutingPolicy.Decision(
            platform = hidden,
            role = HHelperRoutingPolicy.Role.HIDDEN_HELPER,
            escalation = HHelperRoutingPolicy.Escalation.STRONG_HELPER,
            task = AiTaskProfile(AiTaskKind.PROJECT_COMPLEX, true, 5),
        )

        val result = coordinator.handleFailure(
            failedPlatformUid = paid.uid,
            request = request,
            attemptedPlatformUids = setOf(paid.uid),
        ) as FreeAiFailoverCoordinator.Result.Switched

        assertEquals(hidden.uid, result.toPlatform.uid)
        verify {
            helperPolicy.select(
                request = request,
                allPlatforms = platforms,
                usablePlatforms = platforms,
                excludedPlatformUids = match {
                    paid.uid in it && secondExternal.uid in it
                },
                nowMs = any(),
            )
        }
    }

    @Test
    fun `legacy route never promotes an external helper to H core`() = runTest {
        val core = platform("H Core", "internal:local", null, true)
        val paid = platform("Paid helper", "external:custom", "paid", false, enabled = true)
        val platforms = listOf(paid, core)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { runtimeAvailability.evaluate(platforms) } returns snapshot(platforms)
        every { helperPolicy.isCore(core) } returns true
        every { helperPolicy.isCore(paid) } returns false

        val selected = coordinator.resolveStartPlatform(paid)

        assertEquals(core.uid, selected.uid)
    }

    @Test
    fun `no remaining route ends helper attempt without changing H identity`() = runTest {
        val paid = platform("Paid helper", "external:custom", "paid", false, enabled = true)
        val request = request(paid)
        val platforms = listOf(paid)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { runtimeAvailability.evaluate(platforms) } returns snapshot(platforms)
        every { helperPolicy.select(any(), any(), any(), any(), any()) } returns null

        val result = coordinator.handleFailure(
            failedPlatformUid = paid.uid,
            request = request,
            attemptedPlatformUids = setOf(paid.uid),
        )

        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
    }

    private fun request(platform: PlatformV2) = AgentModelRequest(
        platform = platform,
        conversation = listOf(
            AgentConversationItem(
                role = AgentMessageRole.USER,
                text = "test turn",
            )
        ),
        fullConversation = emptyList(),
        tools = emptyList(),
        policy = AgentLoopPolicy(),
    )

    private fun snapshot(platforms: List<PlatformV2>) = FreeAiRuntimeAvailability.Snapshot(
        usablePlatforms = platforms,
        networkAvailable = true,
        openRouterCredentialMissing = false,
        localModelAvailable = platforms.any { it.provider == "internal:local" },
    )

    private fun platform(
        name: String,
        provider: String,
        token: String?,
        isFree: Boolean,
        enabled: Boolean = false,
        apiUrl: String = if (provider == "internal:local") {
            FreeAiRouter.H_LOCAL_API_URL
        } else {
            "https://example.test/v1"
        },
    ) = PlatformV2(
        name = name,
        compatibleType = ClientType.CUSTOM,
        enabled = enabled,
        apiUrl = apiUrl,
        token = token,
        model = if (provider == "internal:local") FreeAiBootstrapper.H_LOCAL_MODEL else "test-model",
        provider = provider,
        isFree = isFree,
    )
}

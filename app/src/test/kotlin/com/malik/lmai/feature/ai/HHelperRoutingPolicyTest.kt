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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HHelperRoutingPolicyTest {

    private val router = FreeAiRouter()
    private val classifier = AiTaskClassifier()
    private val health = mockk<ProviderHealthTracker>()
    private val orchestrator = SmartFreeAiOrchestrator(router, classifier, health)
    private val policy = HHelperRoutingPolicy(router, classifier, orchestrator, health)

    init {
        every { health.snapshot(any()) } returns ProviderHealthTracker.Snapshot()
        every { health.scoreAdjustment(any(), any()) } returns 0
        every { health.interactiveScoreAdjustment(any(), any()) } returns 0
    }

    @Test
    fun `ordinary turn stays on H core even when paid helper is configured`() {
        val core = localCore()
        val paid = paidHelper(enabled = true)
        val hidden = hiddenHelper()

        val decision = policy.select(
            request = request("هلا كيفك"),
            allPlatforms = listOf(core, paid, hidden),
            usablePlatforms = listOf(core, paid, hidden),
            nowMs = 1_000L,
        )!!

        assertEquals(core.uid, decision.platform.uid)
        assertEquals(HHelperRoutingPolicy.Role.H_CORE, decision.role)
        assertEquals(HHelperRoutingPolicy.Escalation.NONE, decision.escalation)
    }

    @Test
    fun `knowledge enrichment uses hidden helper and does not spend paid helper`() {
        val core = localCore()
        val paid = paidHelper(enabled = true)
        val hidden = hiddenHelper()

        val decision = policy.select(
            request = request("وش هو OAuth وكيف يعمل في Android"),
            allPlatforms = listOf(core, paid, hidden),
            usablePlatforms = listOf(core, paid, hidden),
            nowMs = 1_000L,
        )!!

        assertEquals(hidden.uid, decision.platform.uid)
        assertEquals(HHelperRoutingPolicy.Role.HIDDEN_HELPER, decision.role)
        assertEquals(HHelperRoutingPolicy.Escalation.FREE_ENRICHMENT, decision.escalation)
        assertNotEquals(paid.uid, decision.platform.uid)
    }

    @Test
    fun `hard task consults configured paid helper while H remains policy owner`() {
        val core = localCore()
        val paid = paidHelper(enabled = true)
        val hidden = hiddenHelper()

        val decision = policy.select(
            request = request(
                text = "انشئ تطبيق كامل وابن APK",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            ),
            allPlatforms = listOf(core, paid, hidden),
            usablePlatforms = listOf(core, paid, hidden),
            nowMs = 1_000L,
        )!!

        assertEquals(paid.uid, decision.platform.uid)
        assertEquals(HHelperRoutingPolicy.Role.USER_HELPER, decision.role)
        assertEquals(HHelperRoutingPolicy.Escalation.STRONG_HELPER, decision.escalation)
    }

    @Test
    fun `exhausted paid helper falls back to hidden free helper`() {
        val core = localCore()
        val paid = paidHelper(enabled = true)
        val hidden = hiddenHelper()
        every { health.snapshot(paid.uid) } returns ProviderHealthTracker.Snapshot(
            cooldownUntilMs = 50_000L,
        )

        val decision = policy.select(
            request = request(
                text = "اصلح كل المشروع وابن APK",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            ),
            allPlatforms = listOf(core, paid, hidden),
            usablePlatforms = listOf(core, paid, hidden),
            nowMs = 10_000L,
        )!!

        assertEquals(hidden.uid, decision.platform.uid)
        assertEquals(HHelperRoutingPolicy.Role.HIDDEN_HELPER, decision.role)
    }

    @Test
    fun `missing local core uses hidden route only as H continuity`() {
        val hidden = hiddenHelper()
        val paid = paidHelper(enabled = true)

        val decision = policy.select(
            request = request("هلا"),
            allPlatforms = listOf(paid, hidden),
            usablePlatforms = listOf(paid, hidden),
            nowMs = 1_000L,
        )!!

        assertEquals(hidden.uid, decision.platform.uid)
        assertEquals(HHelperRoutingPolicy.Role.H_CONTINUITY, decision.role)
        assertNotEquals(paid.uid, decision.platform.uid)
    }

    @Test
    fun `disabled external provider is never selected as helper`() {
        val core = localCore()
        val paid = paidHelper(enabled = false)
        val hidden = hiddenHelper()

        val decision = policy.select(
            request = request(
                text = "انشئ تطبيق كامل وابن APK",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            ),
            allPlatforms = listOf(core, paid, hidden),
            usablePlatforms = listOf(core, paid, hidden),
            nowMs = 1_000L,
        )!!

        assertTrue(router.isInternalFree(decision.platform))
        assertEquals(hidden.uid, decision.platform.uid)
    }

    private fun request(
        text: String,
        toolChoice: AgentToolChoiceMode = AgentToolChoiceMode.AUTO,
    ) = AgentModelRequest(
        platform = localCore(),
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

    private fun localCore() = PlatformV2(
        name = FreeAiBootstrapper.H_LOCAL_DISPLAY_NAME,
        compatibleType = ClientType.CUSTOM,
        enabled = false,
        apiUrl = FreeAiRouter.H_LOCAL_API_URL,
        token = null,
        model = FreeAiBootstrapper.H_LOCAL_MODEL,
        provider = "internal:local",
        isFree = true,
    )

    private fun hiddenHelper() = PlatformV2(
        name = "H hidden specialist",
        compatibleType = ClientType.CUSTOM,
        enabled = false,
        apiUrl = FreeAiRouter.BLOCKRUN_API_BASE,
        token = null,
        model = FreeAiBootstrapper.BLOCKRUN_CODE_MODEL,
        provider = "internal:blockrun",
        isFree = true,
    )

    private fun paidHelper(enabled: Boolean) = PlatformV2(
        name = "Owner specialist",
        compatibleType = ClientType.CUSTOM,
        enabled = enabled,
        apiUrl = "https://owner.example/v1",
        token = "owner-key",
        model = "owner-strong-model",
        provider = "external:custom",
        isFree = false,
    )
}

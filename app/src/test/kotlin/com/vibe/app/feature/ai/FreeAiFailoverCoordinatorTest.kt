package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.repository.SettingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiFailoverCoordinatorTest {

    private val repository = mockk<SettingRepository>(relaxed = true)
    private val router = FreeAiRouter()
    private val bootstrapper = mockk<FreeAiBootstrapper>()
    private val smartOrchestrator = mockk<SmartFreeAiOrchestrator>(relaxed = true)
    private val runtimeAvailability = mockk<FreeAiRuntimeAvailability>()
    private val coordinator = FreeAiFailoverCoordinator(
        repository,
        router,
        bootstrapper,
        smartOrchestrator,
        runtimeAvailability,
    )

    init {
        coEvery { runtimeAvailability.evaluate(any()) } answers {
            val platforms = firstArg<List<PlatformV2>>()
            FreeAiRuntimeAvailability.Snapshot(
                usablePlatforms = platforms,
                networkAvailable = true,
                openRouterCredentialMissing = false,
            )
        }
    }

    @Test
    fun `external provider failure activates best hidden free provider`() = runTest {
        val external = platform(
            name = "Private API",
            provider = "external:custom",
            token = "paid-key",
            isFree = false,
            enabled = true,
        )
        val internalGroq = platform(
            name = "Hidden Groq",
            provider = "internal:groq",
            token = "groq-internal",
            isFree = true,
        )
        val internalGemini = platform(
            name = "Hidden Gemini",
            provider = "internal:gemini",
            token = "gemini-internal",
            isFree = true,
        )
        val platforms = listOf(external, internalGroq, internalGemini)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { repository.getFreeAiEnabled() } returns false

        val result = coordinator.handleFailure(external.uid)

        val switched = result as FreeAiFailoverCoordinator.Result.Switched
        assertEquals(internalGemini.uid, switched.toPlatform.uid)
        assertTrue(switched.activatedFreeAi)
        coVerify(exactly = 1) { repository.updateFreeAiEnabled(true) }
        coVerify { repository.updatePlatformV2(match { it.uid == external.uid && !it.enabled }) }
        coVerify { repository.updatePlatformV2(match { it.uid == internalGemini.uid && it.enabled }) }
    }

    @Test
    fun `external gemini and internal gemini are isolated during failover`() = runTest {
        val externalGemini = PlatformV2(
            name = "My Google AI Studio",
            compatibleType = ClientType.GOOGLE_AI_STUDIO,
            enabled = true,
            apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            token = "my-user-key",
            model = "my-model",
            provider = "external:gemini",
            isFree = true,
        )
        val internalGemini = platform(
            name = "Hidden Gemini",
            provider = "internal:gemini",
            token = "hidden-key",
            isFree = true,
        )
        val platforms = listOf(externalGemini, internalGemini)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { repository.getFreeAiEnabled() } returns false

        val start = coordinator.resolveStartPlatform(externalGemini)
        assertEquals(externalGemini.uid, start.uid)

        val result = coordinator.handleFailure(externalGemini.uid)
        val switched = result as FreeAiFailoverCoordinator.Result.Switched

        assertEquals(internalGemini.uid, switched.toPlatform.uid)
        assertFalse(router.isFreeCandidate(externalGemini))
        assertTrue(router.isFreeCandidate(internalGemini))
        coVerify { repository.updatePlatformV2(match { it.uid == externalGemini.uid && !it.enabled }) }
    }

    @Test
    fun `free provider failure advances to next hidden cloud provider`() = runTest {
        val gemini = platform(
            name = "Hidden Gemini",
            provider = "internal:gemini",
            token = "gemini-key",
            isFree = true,
            enabled = true,
        )
        val groq = platform(
            name = "Hidden Groq",
            provider = "internal:groq",
            token = "groq-key",
            isFree = true,
        )

        coEvery { bootstrapper.ensureReady() } returns listOf(gemini, groq)
        coEvery { repository.getFreeAiEnabled() } returns true

        val result = coordinator.handleFailure(gemini.uid)

        val switched = result as FreeAiFailoverCoordinator.Result.Switched
        assertEquals(groq.uid, switched.toPlatform.uid)
        assertFalse(switched.activatedFreeAi)
    }

    @Test
    fun `last hidden cloud provider failure ends chain without wrapping`() = runTest {
        val gemini = platform(
            name = "Hidden Gemini",
            provider = "internal:gemini",
            token = "gemini-key",
            isFree = true,
        )
        val groq = platform(
            name = "Hidden Groq",
            provider = "internal:groq",
            token = "groq-key",
            isFree = true,
            enabled = true,
        )

        coEvery { bootstrapper.ensureReady() } returns listOf(gemini, groq)
        coEvery { repository.getFreeAiEnabled() } returns true

        val result = coordinator.handleFailure(groq.uid)

        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
    }

    @Test
    fun `failed external stays disabled even when no free fallback exists`() = runTest {
        val external = platform(
            name = "External only",
            provider = "external:custom",
            token = "user-key",
            isFree = false,
            enabled = true,
        )

        coEvery { bootstrapper.ensureReady() } returns listOf(external)
        coEvery { repository.getFreeAiEnabled() } returns false

        val result = coordinator.handleFailure(external.uid)

        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
        coVerify { repository.updatePlatformV2(match { it.uid == external.uid && !it.enabled }) }
    }

    @Test
    fun `validated internet allows connected OpenRouter free route`() = runTest {
        val openRouter = platform(
            name = "OpenRouter Free",
            provider = "internal:openrouter",
            token = "oauth://openrouter",
            isFree = true,
            enabled = false,
        )
        val platforms = listOf(openRouter)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { repository.getFreeAiEnabled() } returns true
        coEvery { runtimeAvailability.evaluate(platforms) } returns
            FreeAiRuntimeAvailability.Snapshot(
                usablePlatforms = platforms,
                networkAvailable = true,
                openRouterCredentialMissing = false,
            )

        val result = coordinator.resolveStartPlatform(openRouter)

        assertEquals(openRouter.uid, result.uid)
        assertTrue(router.isFreeCandidate(openRouter))
        coVerify { repository.updatePlatformV2(match { it.uid == openRouter.uid && it.enabled }) }
    }

    @Test
    fun `offline cloud AI returns actionable failure without disabling free route`() = runTest {
        val openRouter = platform(
            name = "OpenRouter Free",
            provider = "internal:openrouter",
            token = "oauth://openrouter",
            isFree = true,
            enabled = true,
        )
        val platforms = listOf(openRouter)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { repository.getFreeAiEnabled() } returns true
        coEvery { runtimeAvailability.evaluate(platforms) } returns
            FreeAiRuntimeAvailability.Snapshot(
                usablePlatforms = emptyList(),
                networkAvailable = false,
                openRouterCredentialMissing = false,
            )

        val error = runCatching { coordinator.resolveStartPlatform(openRouter) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("CLOUD_AI_OFFLINE"))
        coVerify(exactly = 0) { repository.updatePlatformV2(match { it.uid == openRouter.uid && !it.enabled }) }
    }

    @Test
    fun `online but unconnected cloud AI returns connection guidance`() = runTest {
        val platforms = emptyList<PlatformV2>()

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { repository.getFreeAiEnabled() } returns true
        coEvery { runtimeAvailability.evaluate(platforms) } returns
            FreeAiRuntimeAvailability.Snapshot(
                usablePlatforms = emptyList(),
                networkAvailable = true,
                openRouterCredentialMissing = false,
            )

        val placeholder = platform(
            name = "Placeholder",
            provider = "external:custom",
            token = "key",
            isFree = false,
        )

        val error = runCatching { coordinator.resolveStartPlatform(placeholder) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("CLOUD_AI_NOT_CONNECTED"))
    }

    private fun platform(
        name: String,
        provider: String,
        token: String?,
        isFree: Boolean,
        enabled: Boolean = false,
    ) = PlatformV2(
        name = name,
        compatibleType = ClientType.CUSTOM,
        enabled = enabled,
        apiUrl = "https://example.test/v1",
        token = token,
        model = "test-model",
        provider = provider,
        isFree = isFree,
    )
}

package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiFailoverCoordinatorTest {

    private val router = FreeAiRouter()
    private val bootstrapper = mockk<FreeAiBootstrapper>()
    private val smartOrchestrator = mockk<SmartFreeAiOrchestrator>(relaxed = true)
    private val runtimeAvailability = mockk<FreeAiRuntimeAvailability>()
    private val coordinator = FreeAiFailoverCoordinator(
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
    fun `external provider failure never crosses into hidden free pool`() = runTest {
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
        coEvery { bootstrapper.ensureReady() } returns listOf(external, internalGroq, internalGemini)

        val result = coordinator.handleFailure(external.uid)

        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
    }

    @Test
    fun `external gemini and internal gemini remain isolated`() = runTest {
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

        val start = coordinator.resolveStartPlatform(externalGemini)
        assertEquals(externalGemini.uid, start.uid)

        val result = coordinator.handleFailure(externalGemini.uid)
        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
        assertFalse(router.isFreeCandidate(externalGemini))
        assertTrue(router.isFreeCandidate(internalGemini))
    }

    @Test
    fun `free provider failure advances to next independent hidden provider`() = runTest {
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

        val result = coordinator.handleFailure(groq.uid)

        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
    }

    @Test
    fun `failed external remains selected when no fallback is permitted`() = runTest {
        val external = platform(
            name = "External only",
            provider = "external:custom",
            token = "user-key",
            isFree = false,
            enabled = true,
        )

        coEvery { bootstrapper.ensureReady() } returns listOf(external)

        val result = coordinator.handleFailure(external.uid)

        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
    }

    @Test
    fun `validated internet allows connected OpenRouter route without persisting selection`() = runTest {
        val openRouter = platform(
            name = "مساعد H الرقمي · OpenRouter",
            provider = "internal:openrouter",
            token = "oauth://openrouter",
            isFree = true,
            enabled = false,
        )
        val platforms = listOf(openRouter)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { runtimeAvailability.evaluate(platforms) } returns
            FreeAiRuntimeAvailability.Snapshot(
                usablePlatforms = platforms,
                networkAvailable = true,
                openRouterCredentialMissing = false,
            )

        val result = coordinator.resolveStartPlatform(openRouter)

        assertEquals(openRouter.uid, result.uid)
        assertTrue(router.isFreeCandidate(openRouter))
    }

    @Test
    fun `offline before local model is ready returns actionable preparation guidance`() = runTest {
        val openRouter = platform(
            name = "مساعد H الرقمي · OpenRouter",
            provider = "internal:openrouter",
            token = "oauth://openrouter",
            isFree = true,
            enabled = true,
        )
        val platforms = listOf(openRouter)

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { runtimeAvailability.evaluate(platforms) } returns
            FreeAiRuntimeAvailability.Snapshot(
                usablePlatforms = emptyList(),
                networkAvailable = false,
                openRouterCredentialMissing = false,
                localModelAvailable = false,
                localModelPreparing = false,
            )

        val error = runCatching { coordinator.resolveStartPlatform(openRouter) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("H_OFFLINE_NOT_READY"))
    }

    @Test
    fun `online with no usable route returns H route guidance`() = runTest {
        val platforms = emptyList<PlatformV2>()

        coEvery { bootstrapper.ensureReady() } returns platforms
        coEvery { runtimeAvailability.evaluate(platforms) } returns
            FreeAiRuntimeAvailability.Snapshot(
                usablePlatforms = emptyList(),
                networkAvailable = true,
                openRouterCredentialMissing = false,
                localModelAvailable = false,
                localModelPreparing = true,
            )

        val placeholder = platform(
            name = "Placeholder",
            provider = "external:custom",
            token = "key",
            isFree = false,
        )

        val error = runCatching { coordinator.resolveStartPlatform(placeholder) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("H_NO_ROUTE"))
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

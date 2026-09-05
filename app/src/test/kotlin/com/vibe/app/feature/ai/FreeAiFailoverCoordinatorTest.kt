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
    private val coordinator = FreeAiFailoverCoordinator(repository, router)

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

        coEvery { repository.fetchPlatformV2s() } returns listOf(external, internalGroq, internalGemini)
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

        coEvery { repository.fetchPlatformV2s() } returns listOf(externalGemini, internalGemini)
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
    fun `free provider failure advances to next hidden free provider`() = runTest {
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

        coEvery { repository.fetchPlatformV2s() } returns listOf(gemini, groq)
        coEvery { repository.getFreeAiEnabled() } returns true

        val result = coordinator.handleFailure(gemini.uid)

        val switched = result as FreeAiFailoverCoordinator.Result.Switched
        assertEquals(groq.uid, switched.toPlatform.uid)
        assertFalse(switched.activatedFreeAi)
    }

    @Test
    fun `last hidden free provider failure ends chain without wrapping`() = runTest {
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

        coEvery { repository.fetchPlatformV2s() } returns listOf(gemini, groq)
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

        coEvery { repository.fetchPlatformV2s() } returns listOf(external)
        coEvery { repository.getFreeAiEnabled() } returns false

        val result = coordinator.handleFailure(external.uid)

        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
        coVerify { repository.updatePlatformV2(match { it.uid == external.uid && !it.enabled }) }
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

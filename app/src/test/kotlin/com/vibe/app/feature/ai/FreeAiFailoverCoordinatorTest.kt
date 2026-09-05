package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.repository.SettingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiFailoverCoordinatorTest {

    private val repository = mockk<SettingRepository>(relaxed = true)
    private val router = FreeAiRouter()
    private val coordinator = FreeAiFailoverCoordinator(repository, router)

    @Test
    fun `manual mode never switches provider`() = runTest {
        coEvery { repository.getAiExecutionMode() } returns "MANUAL"

        val result = coordinator.handleFailure("failed")

        assertTrue(result is FreeAiFailoverCoordinator.Result.ManualMode)
        coVerify(exactly = 0) { repository.fetchPlatformV2s() }
    }

    @Test
    fun `custom provider failure activates free AI and best free provider`() = runTest {
        val custom = platform(
            name = "Private API",
            provider = "custom",
            token = "paid-key",
            isFree = false,
            enabled = true,
        )
        val groq = platform(
            name = "Groq Free",
            provider = "groq",
            token = "groq-key",
            isFree = true,
        )
        val gemini = platform(
            name = "Gemini Free",
            provider = "gemini",
            token = "gemini-key",
            isFree = true,
        )

        coEvery { repository.getAiExecutionMode() } returns "AUTOMATIC"
        coEvery { repository.fetchPlatformV2s() } returns listOf(custom, groq, gemini)
        coEvery { repository.getFreeAiEnabled() } returns false

        val result = coordinator.handleFailure(custom.uid)

        val switched = result as FreeAiFailoverCoordinator.Result.Switched
        assertEquals(gemini.uid, switched.toPlatform.uid)
        assertTrue(switched.activatedFreeAi)
        coVerify(exactly = 1) { repository.updateFreeAiEnabled(true) }
        coVerify { repository.updatePlatformV2(match { it.uid == custom.uid && !it.enabled }) }
        coVerify { repository.updatePlatformV2(match { it.uid == gemini.uid && it.enabled }) }
    }

    @Test
    fun `free provider failure advances to the next free provider`() = runTest {
        val gemini = platform(
            name = "Gemini Free",
            provider = "gemini",
            token = "gemini-key",
            isFree = true,
            enabled = true,
        )
        val groq = platform(
            name = "Groq Free",
            provider = "groq",
            token = "groq-key",
            isFree = true,
        )

        coEvery { repository.getAiExecutionMode() } returns "AUTOMATIC"
        coEvery { repository.fetchPlatformV2s() } returns listOf(gemini, groq)
        coEvery { repository.getFreeAiEnabled() } returns true

        val result = coordinator.handleFailure(gemini.uid)

        val switched = result as FreeAiFailoverCoordinator.Result.Switched
        assertEquals(groq.uid, switched.toPlatform.uid)
        assertEquals(false, switched.activatedFreeAi)
    }

    @Test
    fun `last free provider failure ends the chain without wrapping`() = runTest {
        val gemini = platform(
            name = "Gemini Free",
            provider = "gemini",
            token = "gemini-key",
            isFree = true,
        )
        val groq = platform(
            name = "Groq Free",
            provider = "groq",
            token = "groq-key",
            isFree = true,
            enabled = true,
        )

        coEvery { repository.getAiExecutionMode() } returns "AUTOMATIC"
        coEvery { repository.fetchPlatformV2s() } returns listOf(gemini, groq)
        coEvery { repository.getFreeAiEnabled() } returns true

        val result = coordinator.handleFailure(groq.uid)

        assertTrue(result is FreeAiFailoverCoordinator.Result.NoFallbackAvailable)
        coVerify(exactly = 0) { repository.updatePlatformV2(any()) }
    }

    private fun platform(
        name: String,
        provider: String,
        token: String?,
        isFree: Boolean,
        enabled: Boolean = false,
    ) = PlatformV2(
        name = name,
        compatibleType = ClientType.OPENAI,
        enabled = enabled,
        apiUrl = "https://example.test/v1",
        token = token,
        model = "test-model",
        provider = provider,
        isFree = isFree,
    )
}

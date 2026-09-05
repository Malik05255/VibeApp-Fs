package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.ai.openrouter.OpenRouterCredentialStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiRuntimeAvailabilityTest {

    private val router = FreeAiRouter()
    private val localRuntime = mockk<LocalNanoRuntime>()
    private val credentialStore = mockk<OpenRouterCredentialStore>()
    private val availability = FreeAiRuntimeAvailability(
        router,
        localRuntime,
        credentialStore,
    )

    @Test
    fun `explicitly unsupported local Nano is removed`() = runTest {
        val local = localPlatform()
        coEvery { localRuntime.isSupportedByDevice() } returns false

        val snapshot = availability.evaluate(listOf(local))

        assertTrue(snapshot.usablePlatforms.isEmpty())
        assertTrue(snapshot.localNanoUnsupported)
        assertFalse(snapshot.hasUsableInternalFreeRoute)
    }

    @Test
    fun `OpenRouter OAuth sentinel is removed when encrypted key is missing`() = runTest {
        val openRouter = openRouterPlatform()
        every { credentialStore.getApiKey() } returns null

        val snapshot = availability.evaluate(listOf(openRouter))

        assertTrue(snapshot.usablePlatforms.isEmpty())
        assertTrue(snapshot.openRouterCredentialMissing)
    }

    @Test
    fun `OpenRouter OAuth route remains usable when encrypted key exists`() = runTest {
        val openRouter = openRouterPlatform()
        every { credentialStore.getApiKey() } returns "sk-or-v1-test"

        val snapshot = availability.evaluate(listOf(openRouter))

        assertEquals(listOf(openRouter), snapshot.usablePlatforms)
        assertFalse(snapshot.openRouterCredentialMissing)
        assertTrue(snapshot.hasUsableInternalFreeRoute)
    }

    private fun localPlatform() = PlatformV2(
        name = "Local Gemini Nano",
        compatibleType = ClientType.CUSTOM,
        enabled = true,
        apiUrl = "local://android-aicore",
        token = null,
        model = "gemini-nano",
        provider = "internal:local",
        isFree = true,
    )

    private fun openRouterPlatform() = PlatformV2(
        name = "OpenRouter Free",
        compatibleType = ClientType.OPEN_ROUTER,
        enabled = false,
        apiUrl = "https://openrouter.ai/api/v1",
        token = OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL,
        model = "openrouter/free",
        provider = "internal:openrouter",
        isFree = true,
    )
}

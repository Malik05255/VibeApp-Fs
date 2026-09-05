package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.ai.openrouter.OpenRouterCredentialStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiRuntimeAvailabilityTest {

    private val router = FreeAiRouter()
    private val credentialStore = mockk<OpenRouterCredentialStore>()
    private val networkAvailability = mockk<NetworkAvailability>()
    private val availability = FreeAiRuntimeAvailability(
        router,
        credentialStore,
        networkAvailability,
    )

    @Test
    fun `cloud route is withheld while internet is unavailable`() = runTest {
        val openRouter = openRouterPlatform()
        every { networkAvailability.hasValidatedInternet() } returns false
        every { credentialStore.getApiKey() } returns "sk-or-v1-test"

        val snapshot = availability.evaluate(listOf(openRouter))

        assertTrue(snapshot.usablePlatforms.isEmpty())
        assertFalse(snapshot.networkAvailable)
        assertFalse(snapshot.hasUsableInternalFreeRoute)
    }

    @Test
    fun `OpenRouter OAuth sentinel is removed when encrypted key is missing`() = runTest {
        val openRouter = openRouterPlatform()
        every { networkAvailability.hasValidatedInternet() } returns true
        every { credentialStore.getApiKey() } returns null

        val snapshot = availability.evaluate(listOf(openRouter))

        assertTrue(snapshot.usablePlatforms.isEmpty())
        assertTrue(snapshot.networkAvailable)
        assertTrue(snapshot.openRouterCredentialMissing)
    }

    @Test
    fun `OpenRouter OAuth route remains usable when internet and key exist`() = runTest {
        val openRouter = openRouterPlatform()
        every { networkAvailability.hasValidatedInternet() } returns true
        every { credentialStore.getApiKey() } returns "sk-or-v1-test"

        val snapshot = availability.evaluate(listOf(openRouter))

        assertEquals(listOf(openRouter), snapshot.usablePlatforms)
        assertTrue(snapshot.networkAvailable)
        assertFalse(snapshot.openRouterCredentialMissing)
        assertTrue(snapshot.hasUsableInternalFreeRoute)
    }

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

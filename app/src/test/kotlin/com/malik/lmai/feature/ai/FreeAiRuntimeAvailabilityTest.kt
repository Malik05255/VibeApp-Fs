package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
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
    private val localGateway = mockk<HMediaPipeAgentGateway>(relaxed = true)
    private val availability = FreeAiRuntimeAvailability(
        router,
        credentialStore,
        networkAvailability,
        localGateway,
    )

    init {
        every { localGateway.isReady() } returns false
    }

    @Test
    fun `cloud routes are withheld while internet is unavailable`() = runTest {
        val blockRun = blockRunPlatform()
        val openRouter = openRouterPlatform()
        every { networkAvailability.hasValidatedInternet() } returns false

        val snapshot = availability.evaluate(listOf(blockRun, openRouter))

        assertTrue(snapshot.usablePlatforms.isEmpty())
        assertFalse(snapshot.networkAvailable)
        assertFalse(snapshot.localModelAvailable)
        assertFalse(snapshot.localModelPreparing)
        assertFalse(snapshot.hasUsableInternalFreeRoute)
    }

    @Test
    fun `BlockRun remains usable online without credentials`() = runTest {
        val blockRun = blockRunPlatform()
        every { networkAvailability.hasValidatedInternet() } returns true

        val snapshot = availability.evaluate(listOf(blockRun))

        assertEquals(listOf(blockRun), snapshot.usablePlatforms)
        assertTrue(snapshot.networkAvailable)
        assertFalse(snapshot.openRouterCredentialMissing)
        assertFalse(snapshot.localModelAvailable)
        assertTrue(snapshot.localModelPreparing)
        assertTrue(snapshot.hasUsableInternalFreeRoute)
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
    fun `zero-key route survives when optional OpenRouter credential is missing`() = runTest {
        val blockRun = blockRunPlatform()
        val openRouter = openRouterPlatform()
        every { networkAvailability.hasValidatedInternet() } returns true
        every { credentialStore.getApiKey() } returns null

        val snapshot = availability.evaluate(listOf(openRouter, blockRun))

        assertEquals(listOf(blockRun), snapshot.usablePlatforms)
        assertTrue(snapshot.openRouterCredentialMissing)
        assertTrue(snapshot.hasUsableInternalFreeRoute)
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

    private fun blockRunPlatform() = PlatformV2(
        name = "مساعد H الرقمي · Code",
        compatibleType = ClientType.CUSTOM,
        enabled = true,
        apiUrl = FreeAiRouter.BLOCKRUN_API_BASE,
        token = null,
        model = FreeAiBootstrapper.BLOCKRUN_CODE_MODEL,
        provider = "internal:blockrun",
        isFree = true,
    )

    private fun openRouterPlatform() = PlatformV2(
        name = "مساعد H الرقمي · OpenRouter",
        compatibleType = ClientType.OPEN_ROUTER,
        enabled = false,
        apiUrl = "https://openrouter.ai/api/v1",
        token = OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL,
        model = "openrouter/free",
        provider = "internal:openrouter",
        isFree = true,
    )
}

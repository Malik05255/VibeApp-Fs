package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.data.repository.SettingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiBootstrapperTest {

    private val repository = mockk<SettingRepository>(relaxed = true)
    private val router = FreeAiRouter()
    private val bootstrapper = FreeAiBootstrapper(repository, router)

    @Test
    fun `fresh install provisions and enables zero-key cloud route`() = runTest {
        var platforms = emptyList<PlatformV2>()

        coEvery { repository.fetchPlatformV2s() } answers { platforms }
        coEvery { repository.getFreeAiEnabled() } returns false
        coEvery { repository.addPlatformV2(any()) } answers {
            val added = invocation.args[0] as PlatformV2
            platforms = platforms + added
        }
        coEvery { repository.updatePlatformV2(any()) } answers {
            val updated = invocation.args[0] as PlatformV2
            platforms = platforms.map { current ->
                if (current.uid == updated.uid) updated else current
            }
        }

        val result = bootstrapper.ensureReady()

        assertEquals(4, result.size)
        val blockRunRoutes = result.filter {
            router.detectProvider(it) == FreeAiRouter.Provider.BLOCKRUN
        }
        assertEquals(3, blockRunRoutes.size)
        assertEquals(1, blockRunRoutes.count { it.enabled })
        assertTrue(blockRunRoutes.all { it.token == null })
        assertFalse(result.any { router.detectProvider(it) == FreeAiRouter.Provider.LOCAL })
        assertEquals(1, result.count { router.detectProvider(it) == FreeAiRouter.Provider.OPENROUTER })
        coVerify(exactly = 4) { repository.addPlatformV2(any()) }
        coVerify(exactly = 1) { repository.updateFreeAiEnabled(true) }
    }

    @Test
    fun `legacy local route is deleted and zero-key cloud route becomes baseline`() = runTest {
        val local = localPlatform()
        val openRouter = openRouterPlatform(enabled = false)
        var platforms = listOf(local, openRouter)

        coEvery { repository.fetchPlatformV2s() } answers { platforms }
        coEvery { repository.getFreeAiEnabled() } returns true
        coEvery { repository.deletePlatformV2(any()) } answers {
            val deleted = invocation.args[0] as PlatformV2
            platforms = platforms.filterNot { it.uid == deleted.uid }
        }
        coEvery { repository.addPlatformV2(any()) } answers {
            val added = invocation.args[0] as PlatformV2
            platforms = platforms + added
        }
        coEvery { repository.updatePlatformV2(any()) } answers {
            val updated = invocation.args[0] as PlatformV2
            platforms = platforms.map { current ->
                if (current.uid == updated.uid) updated else current
            }
        }

        val result = bootstrapper.ensureReady()

        assertFalse(result.any { router.detectProvider(it) == FreeAiRouter.Provider.LOCAL })
        assertEquals(
            1,
            result.count {
                it.enabled && router.detectProvider(it) == FreeAiRouter.Provider.BLOCKRUN
            },
        )
        assertFalse(result.single { it.uid == openRouter.uid }.enabled)
        coVerify(exactly = 1) { repository.deletePlatformV2(match { it.uid == local.uid }) }
        coVerify(exactly = 3) { repository.addPlatformV2(any()) }
    }

    @Test
    fun `active external API keeps every internal free route on standby`() = runTest {
        val external = PlatformV2(
            name = "My API",
            compatibleType = ClientType.CUSTOM,
            enabled = true,
            apiUrl = "https://example.test/v1",
            token = "user-key",
            model = "model",
            provider = "external:custom",
            isFree = false,
        )
        val openRouter = openRouterPlatform(enabled = true)
        var platforms = listOf(external, openRouter)

        coEvery { repository.fetchPlatformV2s() } answers { platforms }
        coEvery { repository.getFreeAiEnabled() } returns true
        coEvery { repository.addPlatformV2(any()) } answers {
            val added = invocation.args[0] as PlatformV2
            platforms = platforms + added
        }
        coEvery { repository.updatePlatformV2(any()) } answers {
            val updated = invocation.args[0] as PlatformV2
            platforms = platforms.map { current ->
                if (current.uid == updated.uid) updated else current
            }
        }

        val result = bootstrapper.ensureReady()

        assertTrue(result.first { it.uid == external.uid }.enabled)
        assertTrue(result.filter(router::isInternalFree).none { it.enabled })
        coVerify(exactly = 1) { repository.updateFreeAiEnabled(false) }
    }

    private fun localPlatform() = PlatformV2(
        name = "Legacy Local AI",
        compatibleType = ClientType.CUSTOM,
        enabled = true,
        apiUrl = "local://android-aicore",
        token = null,
        model = "legacy-local",
        provider = "internal:local",
        isFree = true,
    )

    private fun openRouterPlatform(enabled: Boolean) = PlatformV2(
        name = "OpenRouter Free",
        compatibleType = ClientType.OPEN_ROUTER,
        enabled = enabled,
        apiUrl = "https://openrouter.ai/api/v1",
        token = "oauth://openrouter",
        model = "openrouter/free",
        provider = "internal:openrouter",
        isFree = true,
    )
}

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
    fun `fresh install provisions H cloud routes plus independent local standby`() = runTest {
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

        assertEquals(5, result.size)
        val blockRunRoutes = result.filter {
            router.detectProvider(it) == FreeAiRouter.Provider.BLOCKRUN
        }
        assertEquals(3, blockRunRoutes.size)
        assertEquals(1, blockRunRoutes.count { it.enabled })
        assertTrue(blockRunRoutes.all { it.token == null })

        val local = result.single {
            router.detectProvider(it) == FreeAiRouter.Provider.LOCAL
        }
        assertEquals(FreeAiRouter.H_LOCAL_API_URL, local.apiUrl)
        assertEquals(FreeAiBootstrapper.H_LOCAL_MODEL, local.model)
        assertFalse(local.enabled)

        assertEquals(1, result.count { router.detectProvider(it) == FreeAiRouter.Provider.OPENROUTER })
        coVerify(exactly = 5) { repository.addPlatformV2(any()) }
        coVerify(exactly = 1) { repository.updateFreeAiEnabled(true) }
    }

    @Test
    fun `legacy local route is upgraded in place to trusted H local standby`() = runTest {
        val local = localPlatform()
        val openRouter = openRouterPlatform(enabled = false)
        var platforms = listOf(local, openRouter)

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

        val migratedLocal = result.single { it.uid == local.uid }
        assertEquals(FreeAiRouter.Provider.LOCAL, router.detectProvider(migratedLocal))
        assertEquals(FreeAiRouter.H_LOCAL_API_URL, migratedLocal.apiUrl)
        assertEquals(FreeAiBootstrapper.H_LOCAL_MODEL, migratedLocal.model)
        assertEquals(FreeAiBootstrapper.H_LOCAL_DISPLAY_NAME, migratedLocal.name)
        assertFalse(migratedLocal.enabled)

        assertEquals(
            1,
            result.count {
                it.enabled && router.detectProvider(it) == FreeAiRouter.Provider.BLOCKRUN
            },
        )
        assertFalse(result.single { it.uid == openRouter.uid }.enabled)
        coVerify(exactly = 0) { repository.deletePlatformV2(any()) }
        coVerify(exactly = 3) { repository.addPlatformV2(any()) }
        coVerify(atLeast = 1) {
            repository.updatePlatformV2(match {
                it.uid == local.uid && it.apiUrl == FreeAiRouter.H_LOCAL_API_URL
            })
        }
    }

    @Test
    fun `active external API keeps every internal H route on standby`() = runTest {
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
        name = "مساعد H الرقمي · OpenRouter",
        compatibleType = ClientType.OPEN_ROUTER,
        enabled = enabled,
        apiUrl = "https://openrouter.ai/api/v1",
        token = "oauth://openrouter",
        model = "openrouter/free",
        provider = "internal:openrouter",
        isFree = true,
    )
}

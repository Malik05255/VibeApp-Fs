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

    init {
        coEvery { repository.getHAutoCloudRoutesEnabled() } returns true
    }

    @Test
    fun `fresh install provisions hidden routes without choosing a runtime provider`() = runTest {
        var platforms = emptyList<PlatformV2>()
        wireRepository(platformsProvider = { platforms }, platformsUpdater = { platforms = it })

        val result = bootstrapper.ensureReady()

        assertEquals(5, result.size)
        val blockRunRoutes = result.filter {
            router.detectProvider(it) == FreeAiRouter.Provider.BLOCKRUN
        }
        assertEquals(3, blockRunRoutes.size)
        assertTrue(blockRunRoutes.none { it.enabled })
        assertTrue(blockRunRoutes.all { it.token == null })

        val local = result.single {
            router.detectProvider(it) == FreeAiRouter.Provider.LOCAL
        }
        assertEquals(FreeAiRouter.H_LOCAL_API_URL, local.apiUrl)
        assertEquals(FreeAiBootstrapper.H_LOCAL_MODEL, local.model)
        assertFalse(local.enabled)

        assertEquals(1, result.count { router.detectProvider(it) == FreeAiRouter.Provider.OPENROUTER })
        coVerify(exactly = 5) { repository.addPlatformV2(any()) }
    }

    @Test
    fun `legacy local route is upgraded without changing its enabled preference`() = runTest {
        val local = localPlatform()
        val openRouter = openRouterPlatform(enabled = false)
        var platforms = listOf(local, openRouter)
        wireRepository(platformsProvider = { platforms }, platformsUpdater = { platforms = it })

        val result = bootstrapper.ensureReady()

        val migratedLocal = result.single { it.uid == local.uid }
        assertEquals(FreeAiRouter.Provider.LOCAL, router.detectProvider(migratedLocal))
        assertEquals(FreeAiRouter.H_LOCAL_API_URL, migratedLocal.apiUrl)
        assertEquals(FreeAiBootstrapper.H_LOCAL_MODEL, migratedLocal.model)
        assertEquals(FreeAiBootstrapper.H_LOCAL_DISPLAY_NAME, migratedLocal.name)
        assertTrue(migratedLocal.enabled)
        assertFalse(result.single { it.uid == openRouter.uid }.enabled)

        coVerify(exactly = 0) { repository.deletePlatformV2(any()) }
        coVerify(exactly = 3) { repository.addPlatformV2(any()) }
        coVerify(atLeast = 1) {
            repository.updatePlatformV2(match {
                it.uid == local.uid &&
                    it.apiUrl == FreeAiRouter.H_LOCAL_API_URL &&
                    it.enabled == local.enabled
            })
        }
    }

    @Test
    fun `bootstrap never rewrites provider selection when external API is active`() = runTest {
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
        wireRepository(platformsProvider = { platforms }, platformsUpdater = { platforms = it })

        val result = bootstrapper.ensureReady()

        assertTrue(result.first { it.uid == external.uid }.enabled)
        assertTrue(result.first { it.uid == openRouter.uid }.enabled)
        coVerify(exactly = 0) {
            repository.updatePlatformV2(match { it.uid == openRouter.uid && !it.enabled })
        }
    }

    @Test
    fun `disabling automatic cloud routes deletes managed cloud rows but preserves H local and external`() = runTest {
        val local = canonicalLocalPlatform(enabled = false)
        val openRouter = openRouterPlatform(enabled = false)
        val blockRun = blockRunPlatform(FreeAiBootstrapper.BLOCKRUN_CODE_MODEL)
        val external = PlatformV2(
            name = "Owner API",
            compatibleType = ClientType.CUSTOM,
            enabled = true,
            apiUrl = "https://owner.example/v1",
            token = "owner-key",
            model = "owner-model",
            provider = "external:custom",
            isFree = false,
        )
        var platforms = listOf(local, openRouter, blockRun, external)
        coEvery { repository.getHAutoCloudRoutesEnabled() } returns false
        wireRepository(platformsProvider = { platforms }, platformsUpdater = { platforms = it })

        val result = bootstrapper.ensureReady()

        assertEquals(setOf(local.uid, external.uid), result.mapTo(hashSetOf()) { it.uid })
        assertTrue(result.any { router.detectProvider(it) == FreeAiRouter.Provider.LOCAL })
        assertTrue(result.any { it.uid == external.uid })
        assertTrue(result.none { router.detectProvider(it) == FreeAiRouter.Provider.BLOCKRUN })
        assertTrue(result.none { router.detectProvider(it) == FreeAiRouter.Provider.OPENROUTER })
    }

    @Test
    fun `retired managed model is pruned and active registry is restored`() = runTest {
        val local = canonicalLocalPlatform(enabled = false)
        val retired = blockRunPlatform("retired/model")
        var platforms = listOf(local, retired)
        wireRepository(platformsProvider = { platforms }, platformsUpdater = { platforms = it })

        val result = bootstrapper.ensureReady()

        assertTrue(result.none { it.uid == retired.uid })
        assertEquals(
            3,
            result.count { router.detectProvider(it) == FreeAiRouter.Provider.BLOCKRUN },
        )
        assertEquals(
            1,
            result.count { router.detectProvider(it) == FreeAiRouter.Provider.OPENROUTER },
        )
    }

    private fun wireRepository(
        platformsProvider: () -> List<PlatformV2>,
        platformsUpdater: (List<PlatformV2>) -> Unit,
    ) {
        coEvery { repository.fetchPlatformV2s() } answers { platformsProvider() }
        coEvery { repository.addPlatformV2(any()) } answers {
            val added = invocation.args[0] as PlatformV2
            platformsUpdater(platformsProvider() + added)
        }
        coEvery { repository.updatePlatformV2(any()) } answers {
            val updated = invocation.args[0] as PlatformV2
            platformsUpdater(
                platformsProvider().map { current ->
                    if (current.uid == updated.uid) updated else current
                }
            )
        }
        coEvery { repository.deletePlatformV2(any()) } answers {
            val deleted = invocation.args[0] as PlatformV2
            platformsUpdater(platformsProvider().filterNot { it.uid == deleted.uid })
        }
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

    private fun canonicalLocalPlatform(enabled: Boolean) = PlatformV2(
        name = FreeAiBootstrapper.H_LOCAL_DISPLAY_NAME,
        compatibleType = ClientType.CUSTOM,
        enabled = enabled,
        apiUrl = FreeAiRouter.H_LOCAL_API_URL,
        token = null,
        model = FreeAiBootstrapper.H_LOCAL_MODEL,
        provider = "internal:local",
        isFree = true,
    )

    private fun openRouterPlatform(enabled: Boolean) = PlatformV2(
        name = FreeAiBootstrapper.H_OPENROUTER_DISPLAY_NAME,
        compatibleType = ClientType.OPEN_ROUTER,
        enabled = enabled,
        apiUrl = "https://openrouter.ai/api/v1",
        token = "oauth://openrouter",
        model = "openrouter/free",
        provider = "internal:openrouter",
        isFree = true,
    )

    private fun blockRunPlatform(model: String) = PlatformV2(
        name = "Hidden route",
        compatibleType = ClientType.CUSTOM,
        enabled = false,
        apiUrl = FreeAiRouter.BLOCKRUN_API_BASE,
        token = null,
        model = model,
        provider = "internal:blockrun",
        isFree = true,
    )
}

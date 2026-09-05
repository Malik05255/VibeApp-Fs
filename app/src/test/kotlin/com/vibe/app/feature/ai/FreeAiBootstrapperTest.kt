package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.repository.SettingRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiBootstrapperTest {

    private val repository = mockk<SettingRepository>(relaxed = true)
    private val router = FreeAiRouter()
    private val bootstrapper = FreeAiBootstrapper(repository, router)

    @Test
    fun `fresh install never creates a local model route`() = runTest {
        val platforms = emptyList<PlatformV2>()

        coEvery { repository.fetchPlatformV2s() } returns platforms
        coEvery { repository.getFreeAiEnabled() } returns false

        val result = bootstrapper.ensureReady()

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { repository.addPlatformV2(any()) }
        coVerify(exactly = 1) { repository.updateFreeAiEnabled(true) }
    }

    @Test
    fun `legacy local route is deleted and cloud route becomes baseline`() = runTest {
        val local = localPlatform()
        val cloud = openRouterPlatform(enabled = false)
        var platforms = listOf(local, cloud)

        coEvery { repository.fetchPlatformV2s() } answers { platforms }
        coEvery { repository.getFreeAiEnabled() } returns true
        coEvery { repository.deletePlatformV2(any()) } answers {
            val deleted = invocation.args[0] as PlatformV2
            platforms = platforms.filterNot { it.uid == deleted.uid }
        }
        coEvery { repository.updatePlatformV2(any()) } answers {
            val updated = invocation.args[0] as PlatformV2
            platforms = platforms.map { current ->
                if (current.uid == updated.uid) updated else current
            }
        }

        val result = bootstrapper.ensureReady()

        assertFalse(result.any { router.detectProvider(it) == FreeAiRouter.Provider.LOCAL })
        assertTrue(result.single { it.uid == cloud.uid }.enabled)
        coVerify(exactly = 1) { repository.deletePlatformV2(match { it.uid == local.uid }) }
        coVerify(exactly = 0) { repository.addPlatformV2(any()) }
    }

    @Test
    fun `active external API keeps internal cloud route on standby`() = runTest {
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
        val cloud = openRouterPlatform(enabled = true)
        var platforms = listOf(external, cloud)

        coEvery { repository.fetchPlatformV2s() } answers { platforms }
        coEvery { repository.getFreeAiEnabled() } returns true
        coEvery { repository.updatePlatformV2(any()) } answers {
            val updated = invocation.args[0] as PlatformV2
            platforms = platforms.map { current ->
                if (current.uid == updated.uid) updated else current
            }
        }

        val result = bootstrapper.ensureReady()

        assertTrue(result.first { it.uid == external.uid }.enabled)
        assertFalse(result.first { it.uid == cloud.uid }.enabled)
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

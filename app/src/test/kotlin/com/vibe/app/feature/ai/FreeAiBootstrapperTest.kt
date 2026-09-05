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

class FreeAiBootstrapperTest {

    private val repository = mockk<SettingRepository>(relaxed = true)
    private val router = FreeAiRouter()
    private val bootstrapper = FreeAiBootstrapper(repository, router)

    @Test
    fun `fresh install creates and enables zero key local route`() = runTest {
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
        val local = result.single()

        assertEquals("internal:local", local.provider)
        assertEquals("gemini-nano", local.model)
        assertTrue(local.enabled)
        assertTrue(local.token.isNullOrBlank())
        coVerify(exactly = 1) { repository.addPlatformV2(any()) }
        coVerify(exactly = 1) { repository.updateFreeAiEnabled(true) }
    }

    @Test
    fun `active external API keeps local route hidden on standby`() = runTest {
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
        var platforms = listOf(external)

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
        val local = result.first { it.provider == "internal:local" }

        assertFalse(local.enabled)
        assertTrue(result.first { it.uid == external.uid }.enabled)
        coVerify(exactly = 1) { repository.updateFreeAiEnabled(false) }
    }

    @Test
    fun `existing local route is never duplicated`() = runTest {
        val local = PlatformV2(
            name = "Local Gemini Nano",
            compatibleType = ClientType.CUSTOM,
            enabled = true,
            apiUrl = "local://android-aicore",
            token = null,
            model = "gemini-nano",
            provider = "internal:local",
            isFree = true,
        )

        coEvery { repository.fetchPlatformV2s() } returns listOf(local)
        coEvery { repository.getFreeAiEnabled() } returns true

        val result = bootstrapper.ensureReady()

        assertEquals(1, result.count { it.provider == "internal:local" })
        coVerify(exactly = 0) { repository.addPlatformV2(any()) }
    }
}

package com.vibe.app.data.repository

import com.vibe.app.data.database.dao.ChatPlatformModelV2Dao
import com.vibe.app.data.database.dao.PlatformV2Dao
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.datastore.SettingDataSource
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.network.OpenRouterModelsAPI
import com.vibe.app.feature.ai.FreeAiRouter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingRepositoryImplProviderStateTest {

    private val dataSource = mockk<SettingDataSource>(relaxed = true)
    private val platformDao = mockk<PlatformV2Dao>(relaxed = true)
    private val chatPlatformDao = mockk<ChatPlatformModelV2Dao>(relaxed = true)
    private val modelsApi = mockk<OpenRouterModelsAPI>(relaxed = true)
    private val router = FreeAiRouter()

    private val repository = SettingRepositoryImpl(
        settingDataSource = dataSource,
        platformV2Dao = platformDao,
        chatPlatformModelV2Dao = chatPlatformDao,
        openRouterModelsAPI = modelsApi,
        freeAiRouter = router,
    )

    @Test
    fun `disabling last external provider activates hidden free provider immediately`() = runTest {
        val external = platform(
            id = 1,
            name = "My Gemini",
            provider = "external:gemini",
            enabled = true,
            isFree = true,
        )
        val disabledExternal = external.copy(enabled = false)
        val internal = platform(
            id = 2,
            name = "Hidden Gemini",
            provider = "internal:gemini",
            enabled = false,
            isFree = true,
        )

        coEvery { platformDao.getPlatform(external.id) } returns external
        coEvery { platformDao.getPlatforms() } returns listOf(disabledExternal, internal)

        repository.updatePlatformV2(disabledExternal)

        coVerify(exactly = 1) { dataSource.updateFreeAiEnabled(true) }
        coVerify { platformDao.editPlatform(match { it.uid == internal.uid && it.enabled }) }
    }

    @Test
    fun `enabling external provider puts every hidden free route on standby`() = runTest {
        val external = platform(
            id = 1,
            name = "My Groq",
            provider = "external:groq",
            enabled = true,
            isFree = true,
        )
        val internal = platform(
            id = 2,
            name = "Hidden Groq",
            provider = "internal:groq",
            enabled = true,
            isFree = true,
        )

        coEvery { platformDao.getPlatform(external.id) } returns external.copy(enabled = false)
        coEvery { platformDao.getPlatforms() } returns listOf(external, internal)

        repository.updatePlatformV2(external)

        coVerify(exactly = 1) { dataSource.updateFreeAiEnabled(false) }
        coVerify { platformDao.editPlatform(match { it.uid == internal.uid && !it.enabled }) }
    }

    private fun platform(
        id: Int,
        name: String,
        provider: String,
        enabled: Boolean,
        isFree: Boolean,
    ) = PlatformV2(
        id = id,
        name = name,
        compatibleType = ClientType.CUSTOM,
        enabled = enabled,
        apiUrl = "https://example.test/v1",
        token = "key",
        model = "model",
        provider = provider,
        isFree = isFree,
    )
}

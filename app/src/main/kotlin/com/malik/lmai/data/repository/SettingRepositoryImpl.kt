package com.malik.lmai.data.repository

import com.malik.lmai.data.database.dao.ChatPlatformModelV2Dao
import com.malik.lmai.data.database.dao.PlatformV2Dao
import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.datastore.SettingDataSource
import com.malik.lmai.data.dto.OpenRouterModel
import com.malik.lmai.data.dto.ThemeSetting
import com.malik.lmai.data.model.DynamicTheme
import com.malik.lmai.data.model.ThemeMode
import com.malik.lmai.data.network.OpenRouterModelsAPI
import com.malik.lmai.feature.ai.FreeAiRouter
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
import javax.inject.Inject

class SettingRepositoryImpl @Inject constructor(
    private val settingDataSource: SettingDataSource,
    private val platformV2Dao: PlatformV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val openRouterModelsAPI: OpenRouterModelsAPI,
    private val freeAiRouter: FreeAiRouter,
    private val openRouterCredentialStore: OpenRouterCredentialStore,
) : SettingRepository {

    /**
     * Return persisted platform metadata exactly as stored in Room.
     *
     * Hidden OpenRouter Free rows intentionally keep a non-secret sentinel in Room.
     * Replacing that sentinel with the runtime OAuth key here makes a disconnected
     * OpenRouter row look like it has a null token, which prevents FreeAiRouter from
     * recognizing the hidden baseline and can disable the chat composer. Runtime
     * credential resolution belongs in ProviderAgentGatewayRouter instead.
     */
    override suspend fun fetchPlatformV2s(): List<PlatformV2> =
        platformV2Dao.getPlatforms()

    override suspend fun fetchThemes(): ThemeSetting =
        ThemeSetting(
            dynamicTheme = settingDataSource.getDynamicTheme() ?: DynamicTheme.OFF,
            themeMode = settingDataSource.getThemeMode() ?: ThemeMode.SYSTEM
        )

    override suspend fun updateThemes(themeSetting: ThemeSetting) {
        settingDataSource.updateDynamicTheme(themeSetting.dynamicTheme)
        settingDataSource.updateThemeMode(themeSetting.themeMode)
    }

    override suspend fun addPlatformV2(platform: PlatformV2) {
        platformV2Dao.addPlatform(platform)
        if (platform.enabled && freeAiRouter.isExternal(platform)) putFreeAiOnStandby()
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) {
        val previous = platformV2Dao.getPlatform(platform.id)
        platformV2Dao.editPlatform(persistablePlatform(platform))

        if (platform.enabled && freeAiRouter.isExternal(platform)) {
            putFreeAiOnStandby()
            return
        }

        if (previous?.enabled == true && !platform.enabled && freeAiRouter.isExternal(platform)) {
            activateFreeAiIfNoExternalIsActive()
        }
    }

    override suspend fun deletePlatformV2(platform: PlatformV2) {
        val wasActiveExternal = platform.enabled && freeAiRouter.isExternal(platform)
        chatPlatformModelV2Dao.deleteByPlatformUid(platform.uid)
        platformV2Dao.deletePlatform(persistablePlatform(platform))
        if (wasActiveExternal) activateFreeAiIfNoExternalIsActive()
    }

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? =
        platformV2Dao.getPlatform(id)

    override suspend fun fetchOpenRouterModels(apiKey: String, isFreeOnly: Boolean): List<OpenRouterModel> =
        openRouterModelsAPI.fetchOpenRouterModels(apiKey = apiKey, isFreeOnly = isFreeOnly)

    override suspend fun getDebugMode(): Boolean = settingDataSource.getDebugMode()

    override suspend fun updateDebugMode(enabled: Boolean) {
        settingDataSource.updateDebugMode(enabled)
    }

    override suspend fun saveApiSettings(provider: String, apiKey: String, customUrl: String) {
        settingDataSource.updateApiProvider(provider)
        settingDataSource.updateApiKey(apiKey)
        settingDataSource.updateCustomApiUrl(customUrl)
    }

    override suspend fun getApiProvider(): String = settingDataSource.getApiProvider()
    override suspend fun getApiKey(): String = settingDataSource.getApiKey()
    override suspend fun getCustomApiUrl(): String = settingDataSource.getCustomApiUrl()
    override suspend fun getFreeAiEnabled(): Boolean = settingDataSource.getFreeAiEnabled()

    override suspend fun updateFreeAiEnabled(enabled: Boolean) {
        settingDataSource.updateFreeAiEnabled(enabled)
    }

    override suspend fun getAiExecutionMode(): String = settingDataSource.getAiExecutionMode()

    override suspend fun updateAiExecutionMode(mode: String) {
        settingDataSource.updateAiExecutionMode(mode)
    }

    private fun persistablePlatform(platform: PlatformV2): PlatformV2 {
        val isInternalOpenRouter = freeAiRouter.isInternalFree(platform) &&
            freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER
        if (!isInternalOpenRouter) return platform
        val runtimeKey = openRouterCredentialStore.getApiKey()
        return if (!runtimeKey.isNullOrBlank() && platform.token == runtimeKey) {
            platform.copy(token = OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL)
        } else platform
    }

    private suspend fun putFreeAiOnStandby() {
        settingDataSource.updateFreeAiEnabled(false)
        platformV2Dao.getPlatforms()
            .filter { it.enabled && freeAiRouter.isInternalFree(it) }
            .forEach { internal -> platformV2Dao.editPlatform(internal.copy(enabled = false)) }
    }

    private suspend fun activateFreeAiIfNoExternalIsActive() {
        val platforms = platformV2Dao.getPlatforms()
        if (platforms.any { it.enabled && freeAiRouter.isExternal(it) }) return

        settingDataSource.updateFreeAiEnabled(true)
        val target = freeAiRouter.selectBest(platforms) ?: return

        platforms
            .filter(freeAiRouter::isInternalFree)
            .forEach { internal ->
                val shouldEnable = internal.uid == target.uid
                if (internal.enabled != shouldEnable) {
                    platformV2Dao.editPlatform(internal.copy(enabled = shouldEnable))
                }
            }
    }
}

package com.vibe.app.data.repository

import com.vibe.app.data.database.dao.ChatPlatformModelV2Dao
import com.vibe.app.data.database.dao.PlatformV2Dao
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.datastore.SettingDataSource
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.dto.ThemeSetting
import com.vibe.app.data.model.DynamicTheme
import com.vibe.app.data.model.ThemeMode
import com.vibe.app.data.network.OpenRouterModelsAPI
import com.vibe.app.feature.ai.FreeAiRouter
import javax.inject.Inject

class SettingRepositoryImpl @Inject constructor(
    private val settingDataSource: SettingDataSource,
    private val platformV2Dao: PlatformV2Dao,
    private val chatPlatformModelV2Dao: ChatPlatformModelV2Dao,
    private val openRouterModelsAPI: OpenRouterModelsAPI,
    private val freeAiRouter: FreeAiRouter,
) : SettingRepository {

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
        if (platform.enabled && freeAiRouter.isExternal(platform)) {
            putFreeAiOnStandby()
        }
    }

    override suspend fun updatePlatformV2(platform: PlatformV2) {
        val previous = platformV2Dao.getPlatform(platform.id)
        platformV2Dao.editPlatform(platform)

        if (platform.enabled && freeAiRouter.isExternal(platform)) {
            putFreeAiOnStandby()
            return
        }

        if (
            previous?.enabled == true &&
            !platform.enabled &&
            freeAiRouter.isExternal(platform)
        ) {
            activateFreeAiIfNoExternalIsActive()
        }
    }

    override suspend fun deletePlatformV2(platform: PlatformV2) {
        val wasActiveExternal = platform.enabled && freeAiRouter.isExternal(platform)
        chatPlatformModelV2Dao.deleteByPlatformUid(platform.uid)
        platformV2Dao.deletePlatform(platform)

        if (wasActiveExternal) {
            activateFreeAiIfNoExternalIsActive()
        }
    }

    override suspend fun getPlatformV2ById(id: Int): PlatformV2? =
        platformV2Dao.getPlatform(id)

    override suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean
    ): List<OpenRouterModel> =
        openRouterModelsAPI.fetchOpenRouterModels(
            apiKey = apiKey,
            isFreeOnly = isFreeOnly
        )

    override suspend fun getDebugMode(): Boolean =
        settingDataSource.getDebugMode()

    override suspend fun updateDebugMode(enabled: Boolean) {
        settingDataSource.updateDebugMode(enabled)
    }

    override suspend fun saveApiSettings(
        provider: String,
        apiKey: String,
        customUrl: String
    ) {
        settingDataSource.updateApiProvider(provider)
        settingDataSource.updateApiKey(apiKey)
        settingDataSource.updateCustomApiUrl(customUrl)
    }

    override suspend fun getApiProvider(): String =
        settingDataSource.getApiProvider()

    override suspend fun getApiKey(): String =
        settingDataSource.getApiKey()

    override suspend fun getCustomApiUrl(): String =
        settingDataSource.getCustomApiUrl()

    override suspend fun getFreeAiEnabled(): Boolean =
        settingDataSource.getFreeAiEnabled()

    override suspend fun updateFreeAiEnabled(enabled: Boolean) {
        settingDataSource.updateFreeAiEnabled(enabled)
    }

    override suspend fun getAiExecutionMode(): String =
        settingDataSource.getAiExecutionMode()

    override suspend fun updateAiExecutionMode(mode: String) {
        settingDataSource.updateAiExecutionMode(mode)
    }

    /**
     * Provider-state invariant shared by every UI and agent path:
     * an explicitly enabled external API owns the foreground route, while all
     * hidden internal Free AI routes remain on standby.
     */
    private suspend fun putFreeAiOnStandby() {
        settingDataSource.updateFreeAiEnabled(false)
        platformV2Dao.getPlatforms()
            .filter { it.enabled && freeAiRouter.isInternalFree(it) }
            .forEach { internal ->
                platformV2Dao.editPlatform(internal.copy(enabled = false))
            }
    }

    /**
     * If the last active external API is disabled/deleted, wake Free AI now —
     * not only on the next chat request or after navigating back to settings.
     */
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

package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.repository.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares Free AI without bundling or provisioning an on-device LLM.
 *
 * Legacy Local/Nano rows are removed during bootstrap. The remaining hidden
 * Free AI routes are cloud providers such as OpenRouter, activated only when no
 * explicit user-managed provider is enabled.
 */
@Singleton
class FreeAiBootstrapper @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
) {

    suspend fun ensureReady(): List<PlatformV2> {
        var platforms = settingRepository.fetchPlatformV2s()

        val legacyLocalRoutes = platforms.filter { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL
        }

        if (legacyLocalRoutes.isNotEmpty()) {
            for (localRoute in legacyLocalRoutes) {
                settingRepository.deletePlatformV2(localRoute)
            }
            platforms = settingRepository.fetchPlatformV2s()
        }

        val externalActive = platforms.any { platform ->
            platform.enabled && freeAiRouter.isExternal(platform)
        }

        if (externalActive) {
            if (settingRepository.getFreeAiEnabled()) {
                settingRepository.updateFreeAiEnabled(false)
            }

            for (internal in platforms.filter { it.enabled && freeAiRouter.isInternalFree(it) }) {
                settingRepository.updatePlatformV2(internal.copy(enabled = false))
            }

            return settingRepository.fetchPlatformV2s()
        }

        if (!settingRepository.getFreeAiEnabled()) {
            settingRepository.updateFreeAiEnabled(true)
        }

        val target = freeAiRouter.selectBest(platforms)
            ?: return settingRepository.fetchPlatformV2s()

        for (internal in platforms.filter(freeAiRouter::isInternalFree)) {
            val shouldEnable = internal.uid == target.uid
            if (internal.enabled != shouldEnable) {
                settingRepository.updatePlatformV2(
                    internal.copy(enabled = shouldEnable)
                )
            }
        }

        return settingRepository.fetchPlatformV2s()
    }
}

package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.repository.SettingRepository
import com.vibe.app.feature.ai.openrouter.OpenRouterCredentialStore
import com.vibe.app.feature.ai.openrouter.OpenRouterOAuthCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares Free AI without bundling or provisioning an on-device LLM.
 *
 * Legacy Local/Nano rows are removed during bootstrap. A lightweight hidden
 * OpenRouter Free baseline is always present so chat never falls back to the
 * old "add API key" empty state. The real OAuth credential remains encrypted
 * in Android Keystore and is validated only when a request is executed.
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

        platforms = ensureCloudBaseline(platforms)

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

    private suspend fun ensureCloudBaseline(platforms: List<PlatformV2>): List<PlatformV2> {
        val hasOpenRouterBaseline = platforms.any { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER
        }
        if (hasOpenRouterBaseline) return platforms

        settingRepository.addPlatformV2(
            PlatformV2(
                name = OpenRouterOAuthCoordinator.DISPLAY_NAME,
                compatibleType = ClientType.OPEN_ROUTER,
                enabled = false,
                apiUrl = OpenRouterOAuthCoordinator.API_URL,
                token = OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL,
                model = OpenRouterOAuthCoordinator.FREE_MODEL,
                provider = AiProviderOrigin.internalProviderCode("openrouter"),
                isFree = true,
                temperature = 0.7f,
                topP = 0.95f,
                stream = true,
                reasoning = false,
                timeout = 90,
            )
        )

        return settingRepository.fetchPlatformV2s()
    }
}

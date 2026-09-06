package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.data.repository.SettingRepository
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
import com.malik.lmai.feature.ai.openrouter.OpenRouterOAuthCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares Free AI without bundling or provisioning an on-device LLM.
 *
 * Legacy Local/Nano rows are removed during bootstrap. Fresh installs receive
 * credentialless BlockRun free routes immediately, plus the optional OpenRouter
 * OAuth route as an additional fallback. No model weights are stored on-device.
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

        platforms = ensureCloudBaselines(platforms)

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

    private suspend fun ensureCloudBaselines(platforms: List<PlatformV2>): List<PlatformV2> {
        var current = platforms

        for (route in BLOCKRUN_ROUTES) {
            val exists = current.any { platform ->
                freeAiRouter.isInternalFree(platform) &&
                    freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.BLOCKRUN &&
                    platform.model == route.model
            }
            if (!exists) {
                settingRepository.addPlatformV2(
                    PlatformV2(
                        name = route.name,
                        compatibleType = ClientType.CUSTOM,
                        enabled = false,
                        apiUrl = FreeAiRouter.BLOCKRUN_API_BASE,
                        token = null,
                        model = route.model,
                        provider = AiProviderOrigin.internalProviderCode("blockrun"),
                        isFree = true,
                        temperature = 0.7f,
                        topP = 0.95f,
                        stream = true,
                        reasoning = route.reasoning,
                        timeout = 120,
                    )
                )
                current = settingRepository.fetchPlatformV2s()
            }
        }

        val hasOpenRouterBaseline = current.any { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER
        }
        if (!hasOpenRouterBaseline) {
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
            current = settingRepository.fetchPlatformV2s()
        }

        return current
    }

    private data class BaselineRoute(
        val name: String,
        val model: String,
        val reasoning: Boolean = false,
    )

    companion object {
        const val BLOCKRUN_CODE_MODEL = "cohere/north-mini-code"
        const val BLOCKRUN_FAST_CODE_MODEL = "poolside/laguna-xs-2.1"
        const val BLOCKRUN_REASONING_MODEL = "nvidia/nemotron-3.5-lightning"

        private val BLOCKRUN_ROUTES = listOf(
            BaselineRoute(
                name = "Free AI · Code",
                model = BLOCKRUN_CODE_MODEL,
            ),
            BaselineRoute(
                name = "Free AI · Fast Code",
                model = BLOCKRUN_FAST_CODE_MODEL,
            ),
            BaselineRoute(
                name = "Free AI · Reasoning",
                model = BLOCKRUN_REASONING_MODEL,
                reasoning = true,
            ),
        )
    }
}

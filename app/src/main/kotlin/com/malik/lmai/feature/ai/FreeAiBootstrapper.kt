package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.data.repository.SettingRepository
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
import com.malik.lmai.feature.ai.openrouter.OpenRouterOAuthCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prepares the built-in مساعد H الرقمي routes.
 *
 * Cloud routes provide maximum capability when online. A system-hosted Gemini Nano
 * route is also retained for offline continuity on supported devices. Nano model
 * weights are managed by Android AICore and are not bundled into the APK.
 */
@Singleton
class FreeAiBootstrapper @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
) {

    suspend fun ensureReady(): List<PlatformV2> {
        var platforms = settingRepository.fetchPlatformV2s()
        platforms = ensureBaselines(platforms)

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

        // Persistent enabled state is only a UI/default hint. Runtime routing can
        // choose a different cloud/local candidate for each request based on network,
        // local availability, task fit and provider health.
        val target = freeAiRouter.selectBest(platforms)
            ?: return settingRepository.fetchPlatformV2s()

        for (internal in platforms.filter(freeAiRouter::isInternalFree)) {
            val shouldEnable = internal.uid == target.uid
            if (internal.enabled != shouldEnable) {
                settingRepository.updatePlatformV2(internal.copy(enabled = shouldEnable))
            }
        }

        return settingRepository.fetchPlatformV2s()
    }

    private suspend fun ensureBaselines(platforms: List<PlatformV2>): List<PlatformV2> {
        var current = platforms

        val localExisting = current.firstOrNull { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL
        }
        if (localExisting == null) {
            settingRepository.addPlatformV2(
                PlatformV2(
                    name = H_LOCAL_DISPLAY_NAME,
                    compatibleType = ClientType.CUSTOM,
                    enabled = false,
                    apiUrl = FreeAiRouter.H_LOCAL_API_URL,
                    token = null,
                    model = H_LOCAL_MODEL,
                    provider = AiProviderOrigin.internalProviderCode("local"),
                    isFree = true,
                    temperature = 0.25f,
                    topP = 0.9f,
                    stream = true,
                    reasoning = true,
                    timeout = 90,
                )
            )
            current = settingRepository.fetchPlatformV2s()
        } else if (
            localExisting.name != H_LOCAL_DISPLAY_NAME ||
            localExisting.apiUrl != FreeAiRouter.H_LOCAL_API_URL ||
            localExisting.model != H_LOCAL_MODEL
        ) {
            settingRepository.updatePlatformV2(
                localExisting.copy(
                    name = H_LOCAL_DISPLAY_NAME,
                    apiUrl = FreeAiRouter.H_LOCAL_API_URL,
                    model = H_LOCAL_MODEL,
                    provider = AiProviderOrigin.internalProviderCode("local"),
                    isFree = true,
                )
            )
            current = settingRepository.fetchPlatformV2s()
        }

        for (route in BLOCKRUN_ROUTES) {
            val existing = current.firstOrNull { platform ->
                freeAiRouter.isInternalFree(platform) &&
                    freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.BLOCKRUN &&
                    platform.model == route.model
            }
            if (existing == null) {
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
            } else if (existing.name != route.name) {
                settingRepository.updatePlatformV2(existing.copy(name = route.name))
                current = settingRepository.fetchPlatformV2s()
            }
        }

        val openRouterExisting = current.firstOrNull { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER
        }
        if (openRouterExisting == null) {
            settingRepository.addPlatformV2(
                PlatformV2(
                    name = H_OPENROUTER_DISPLAY_NAME,
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
        } else if (openRouterExisting.name != H_OPENROUTER_DISPLAY_NAME) {
            settingRepository.updatePlatformV2(openRouterExisting.copy(name = H_OPENROUTER_DISPLAY_NAME))
        }

        return settingRepository.fetchPlatformV2s()
    }

    private data class BaselineRoute(
        val name: String,
        val model: String,
        val reasoning: Boolean = false,
    )

    companion object {
        const val H_LOCAL_MODEL = "gemini-nano"
        const val H_LOCAL_DISPLAY_NAME = "مساعد H الرقمي · محلي"
        const val H_OPENROUTER_DISPLAY_NAME = "مساعد H الرقمي · OpenRouter"

        const val BLOCKRUN_CODE_MODEL = "cohere/north-mini-code"
        const val BLOCKRUN_FAST_CODE_MODEL = "poolside/laguna-xs-2.1"
        const val BLOCKRUN_REASONING_MODEL = "nvidia/nemotron-3.5-lightning"

        private val BLOCKRUN_ROUTES = listOf(
            BaselineRoute(
                name = "مساعد H الرقمي · برمجة",
                model = BLOCKRUN_CODE_MODEL,
            ),
            BaselineRoute(
                name = "مساعد H الرقمي · برمجة سريعة",
                model = BLOCKRUN_FAST_CODE_MODEL,
            ),
            BaselineRoute(
                name = "مساعد H الرقمي · تفكير",
                model = BLOCKRUN_REASONING_MODEL,
                reasoning = true,
            ),
        )
    }
}

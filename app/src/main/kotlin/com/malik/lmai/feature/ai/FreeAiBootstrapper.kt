package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.data.repository.SettingRepository
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
import com.malik.lmai.feature.ai.openrouter.OpenRouterOAuthCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maintains H's private execution routes.
 *
 * Internal routes are never user-facing providers. The local route always exists so H
 * remains H even when cloud capacity is disabled. Cloud routes are a replaceable pool:
 * H can provision or remove them without changing assistant identity, memory, reminders,
 * projects, or any user-managed API configuration.
 */
@Singleton
class FreeAiBootstrapper @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
) {

    suspend fun ensureReady(): List<PlatformV2> {
        var current = ensureLocal(settingRepository.fetchPlatformV2s())
        val automaticCloudRoutes = runCatching {
            settingRepository.getHAutoCloudRoutesEnabled()
        }.getOrDefault(true)

        if (!automaticCloudRoutes) {
            return removeManagedCloudRoutes(current)
        }

        current = removeRetiredManagedRoutes(current)
        current = ensureBlockRunRoutes(current)
        current = ensureOpenRouterRoute(current)
        return current
    }

    private suspend fun ensureLocal(platforms: List<PlatformV2>): List<PlatformV2> {
        var current = platforms
        val localExisting = current.firstOrNull(::isLocalRoute)

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
                    reasoning = false,
                    timeout = 120,
                )
            )
            current = settingRepository.fetchPlatformV2s()
        } else if (
            localExisting.name != H_LOCAL_DISPLAY_NAME ||
            localExisting.apiUrl != FreeAiRouter.H_LOCAL_API_URL ||
            localExisting.model != H_LOCAL_MODEL ||
            localExisting.provider != AiProviderOrigin.internalProviderCode("local") ||
            !localExisting.isFree
        ) {
            settingRepository.updatePlatformV2(
                localExisting.copy(
                    name = H_LOCAL_DISPLAY_NAME,
                    apiUrl = FreeAiRouter.H_LOCAL_API_URL,
                    model = H_LOCAL_MODEL,
                    provider = AiProviderOrigin.internalProviderCode("local"),
                    isFree = true,
                    reasoning = false,
                )
            )
            current = settingRepository.fetchPlatformV2s()
        }

        val canonical = current.firstOrNull(::isLocalRoute) ?: return current
        current.filter { it.uid != canonical.uid && isLocalRoute(it) }.forEach { duplicate ->
            runCatching { settingRepository.deletePlatformV2(duplicate) }
        }
        return settingRepository.fetchPlatformV2s()
    }

    private suspend fun ensureBlockRunRoutes(platforms: List<PlatformV2>): List<PlatformV2> {
        var current = platforms
        for (route in BLOCKRUN_ROUTES) {
            val existing = current.firstOrNull { platform ->
                isBlockRunRoute(platform) && platform.model == route.model
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
            } else if (
                existing.name != route.name ||
                existing.apiUrl.trim().trimEnd('/') != FreeAiRouter.BLOCKRUN_API_BASE ||
                existing.provider != AiProviderOrigin.internalProviderCode("blockrun") ||
                !existing.isFree ||
                existing.reasoning != route.reasoning
            ) {
                settingRepository.updatePlatformV2(
                    existing.copy(
                        name = route.name,
                        apiUrl = FreeAiRouter.BLOCKRUN_API_BASE,
                        provider = AiProviderOrigin.internalProviderCode("blockrun"),
                        isFree = true,
                        reasoning = route.reasoning,
                    )
                )
                current = settingRepository.fetchPlatformV2s()
            }
        }
        return current
    }

    private suspend fun ensureOpenRouterRoute(platforms: List<PlatformV2>): List<PlatformV2> {
        var current = platforms
        val existing = current.firstOrNull(::isOpenRouterRoute)
        if (existing == null) {
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
            current = settingRepository.fetchPlatformV2s()
        } else if (
            existing.name != H_OPENROUTER_DISPLAY_NAME ||
            existing.apiUrl != OpenRouterOAuthCoordinator.API_URL ||
            existing.model != OpenRouterOAuthCoordinator.FREE_MODEL ||
            existing.provider != AiProviderOrigin.internalProviderCode("openrouter") ||
            !existing.isFree
        ) {
            settingRepository.updatePlatformV2(
                existing.copy(
                    name = H_OPENROUTER_DISPLAY_NAME,
                    compatibleType = ClientType.OPEN_ROUTER,
                    apiUrl = OpenRouterOAuthCoordinator.API_URL,
                    token = OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL,
                    model = OpenRouterOAuthCoordinator.FREE_MODEL,
                    provider = AiProviderOrigin.internalProviderCode("openrouter"),
                    isFree = true,
                )
            )
            current = settingRepository.fetchPlatformV2s()
        }

        val canonical = current.firstOrNull(::isOpenRouterRoute) ?: return current
        current.filter { it.uid != canonical.uid && isOpenRouterRoute(it) }.forEach { duplicate ->
            runCatching { settingRepository.deletePlatformV2(duplicate) }
        }
        return settingRepository.fetchPlatformV2s()
    }

    /** Remove only H-managed cloud rows. External/BYOK rows are never touched. */
    private suspend fun removeManagedCloudRoutes(platforms: List<PlatformV2>): List<PlatformV2> {
        platforms.filter(::isManagedCloudRoute).forEach { platform ->
            runCatching { settingRepository.deletePlatformV2(platform) }
        }
        return settingRepository.fetchPlatformV2s()
    }

    /**
     * Self-healing cleanup for routes that belonged to H but are no longer in the active
     * registry. A provider/model can disappear in a future release with no stale UI or
     * migration prompt.
     */
    private suspend fun removeRetiredManagedRoutes(platforms: List<PlatformV2>): List<PlatformV2> {
        val activeBlockRunModels = BLOCKRUN_ROUTES.mapTo(hashSetOf()) { it.model }
        platforms.filter { platform ->
            isBlockRunRoute(platform) && platform.model !in activeBlockRunModels
        }.forEach { retired ->
            runCatching { settingRepository.deletePlatformV2(retired) }
        }
        return settingRepository.fetchPlatformV2s()
    }

    private fun isLocalRoute(platform: PlatformV2): Boolean =
        freeAiRouter.isInternalFree(platform) &&
            freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL

    private fun isBlockRunRoute(platform: PlatformV2): Boolean =
        freeAiRouter.isInternalFree(platform) &&
            freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.BLOCKRUN

    private fun isOpenRouterRoute(platform: PlatformV2): Boolean =
        freeAiRouter.isInternalFree(platform) &&
            freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER

    private fun isManagedCloudRoute(platform: PlatformV2): Boolean =
        isBlockRunRoute(platform) || isOpenRouterRoute(platform)

    private data class BaselineRoute(
        val name: String,
        val model: String,
        val reasoning: Boolean = false,
    )

    companion object {
        const val H_LOCAL_MODEL = "qwen2.5-0.5b-instruct-q8"
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

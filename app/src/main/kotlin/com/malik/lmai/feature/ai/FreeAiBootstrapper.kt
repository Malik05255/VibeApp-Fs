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
        val automaticCloudRoutes = runCatching { settingRepository.getFreeAiEnabled() }
            .getOrDefault(true)

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
        val localRoutes = current.filter { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL
        }
        val localExisting = localRoutes.firstOrNull()

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

        // Local is singular. Old duplicates are implementation debris, not user data.
        current.filter { platform ->
            platform.uid != localExisting?.uid &&
                freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL
        }.forEach { duplicate ->
            runCatching { settingRepository.deletePlatformV2(duplicate) }
        }
        return settingRepository.fetchPlatformV2s()
    }

    private suspend fun ensureBlockRunRoutes(platforms: List<PlatformV2>): List<PlatformV2> {
        var current = platforms
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
        val routes = current.filter { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER
        }
        val existing = routes.firstOrNull()
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

        // Only one internal OpenRouter route is valid; stale duplicates disappear quietly.
        current.filter { platform ->
            platform.uid != existing?.uid &&
                freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER
        }.forEach { duplicate ->
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
     * registry. This lets a provider/model disappear in a future release with no broken
     * card, stale selection, or migration UI.
     */
    private suspend fun removeRetiredManagedRoutes(platforms: List<PlatformV2>): List<PlatformV2> {
        val activeBlockRunModels = BLOCKRUN_ROUTES.mapTo(hashSetOf()) { it.model }
        platforms.filter { platform ->
            if (!isManagedCloudRoute(platform)) return@filter false
            when (freeAiRouter.detectProvider(platform)) {
                FreeAiRouter.Provider.BLOCKRUN -> platform.model !in activeBlockRunModels
                FreeAiRouter.Provider.OPENROUTER -> false
                else -> false
            }
        }.forEach { retired ->
            runCatching { settingRepository.deletePlatformV2(retired) }
        }
        return settingRepository.fetchPlatformV2s()
    }

    private fun isManagedCloudRoute(platform: PlatformV2): Boolean {
        if (!freeAiRouter.isInternalFree(platform)) return false
        return freeAiRouter.detectProvider(platform) in setOf(
            FreeAiRouter.Provider.BLOCKRUN,
            FreeAiRouter.Provider.OPENROUTER,
        )
    }

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

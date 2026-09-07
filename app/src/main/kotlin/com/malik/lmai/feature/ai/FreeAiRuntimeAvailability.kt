package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime validation for built-in مساعد H الرقمي routes.
 *
 * Cloud routes require validated internet. The independent MediaPipe/Qwen local
 * route requires only the verified app-private model file and works offline.
 */
@Singleton
class FreeAiRuntimeAvailability @Inject constructor(
    private val freeAiRouter: FreeAiRouter,
    private val openRouterCredentialStore: OpenRouterCredentialStore,
    private val networkAvailability: NetworkAvailability,
    private val hMediaPipeAgentGateway: HMediaPipeAgentGateway,
) {

    data class Snapshot(
        val usablePlatforms: List<PlatformV2>,
        val networkAvailable: Boolean,
        val openRouterCredentialMissing: Boolean,
        val localModelAvailable: Boolean = false,
        val localModelPreparing: Boolean = false,
    ) {
        val hasUsableInternalFreeRoute: Boolean
            get() = usablePlatforms.any { platform ->
                AiProviderOrigin.of(platform) == AiProviderOrigin.INTERNAL_FREE
            }
    }

    suspend fun evaluate(platforms: List<PlatformV2>): Snapshot {
        val networkAvailable = networkAvailability.hasValidatedInternet()
        var openRouterCredentialMissing = false
        val localModelAvailable = hMediaPipeAgentGateway.isReady()

        // Preparing means the local model is not ready yet, regardless of whether the
        // device happens to be online at this exact instant. The old expression tied
        // this flag to networkAvailable, making the offline "still preparing" branch
        // logically impossible and producing misleading cloud/quota errors instead.
        val localModelPreparing = !localModelAvailable
        if (localModelPreparing && networkAvailable) {
            hMediaPipeAgentGateway.schedulePreparation()
        }

        val usable = ArrayList<PlatformV2>(platforms.size)
        for (platform in platforms) {
            if (!freeAiRouter.isInternalFree(platform)) {
                usable += platform
                continue
            }

            val provider = freeAiRouter.detectProvider(platform)
            val isUsable = when (provider) {
                FreeAiRouter.Provider.LOCAL ->
                    localModelAvailable && freeAiRouter.isFreeCandidate(platform, provider)

                FreeAiRouter.Provider.OPENROUTER -> {
                    if (!networkAvailable) {
                        false
                    } else if (platform.token == OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL) {
                        val credentialPresent = !openRouterCredentialStore
                            .getApiKey()
                            .isNullOrBlank()
                        if (!credentialPresent) openRouterCredentialMissing = true
                        credentialPresent
                    } else {
                        freeAiRouter.isFreeCandidate(platform, provider)
                    }
                }

                else -> networkAvailable && freeAiRouter.isFreeCandidate(platform, provider)
            }

            if (isUsable) usable += platform
        }

        return Snapshot(
            usablePlatforms = usable,
            networkAvailable = networkAvailable,
            openRouterCredentialMissing = openRouterCredentialMissing,
            localModelAvailable = localModelAvailable,
            localModelPreparing = localModelPreparing,
        )
    }
}

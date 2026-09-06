package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime validation for built-in مساعد H الرقمي routes.
 *
 * Cloud routes require validated internet. The local Gemini Nano route is usable
 * without internet once Android AICore reports it as available. When internet is
 * present and Nano is downloadable, preparation is started quietly for future
 * offline use without running inference in the background.
 */
@Singleton
class FreeAiRuntimeAvailability @Inject constructor(
    private val freeAiRouter: FreeAiRouter,
    private val openRouterCredentialStore: OpenRouterCredentialStore,
    private val networkAvailability: NetworkAvailability,
    private val hOnDeviceAgentGateway: HOnDeviceAgentGateway,
) {

    data class Snapshot(
        val usablePlatforms: List<PlatformV2>,
        val networkAvailable: Boolean,
        val openRouterCredentialMissing: Boolean,
        val localModelAvailable: Boolean,
        val localModelPreparing: Boolean,
    ) {
        val hasUsableInternalFreeRoute: Boolean
            get() = usablePlatforms.any { platform ->
                AiProviderOrigin.of(platform) == AiProviderOrigin.INTERNAL_FREE
            }
    }

    suspend fun evaluate(platforms: List<PlatformV2>): Snapshot {
        val networkAvailable = networkAvailability.hasValidatedInternet()
        var openRouterCredentialMissing = false
        var localModelAvailable = false
        var localModelPreparing = false
        val usable = ArrayList<PlatformV2>(platforms.size)

        val localStatus = hOnDeviceAgentGateway.availability()
        when (localStatus) {
            HOnDeviceAgentGateway.Availability.AVAILABLE -> localModelAvailable = true
            HOnDeviceAgentGateway.Availability.DOWNLOADABLE -> {
                localModelPreparing = networkAvailable
                if (networkAvailable) hOnDeviceAgentGateway.prepareForOfflineUse()
            }
            HOnDeviceAgentGateway.Availability.DOWNLOADING -> localModelPreparing = true
            HOnDeviceAgentGateway.Availability.UNAVAILABLE -> Unit
        }

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

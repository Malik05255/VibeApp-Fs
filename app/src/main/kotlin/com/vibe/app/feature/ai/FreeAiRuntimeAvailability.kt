package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.feature.ai.openrouter.OpenRouterCredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime validation for hidden Free AI routes.
 *
 * Free AI is cloud-first and intentionally carries no local LLM. A route is
 * usable only when Android reports validated internet access and any required
 * credential is available. This keeps the phone cool and the APK small.
 */
@Singleton
class FreeAiRuntimeAvailability @Inject constructor(
    private val freeAiRouter: FreeAiRouter,
    private val openRouterCredentialStore: OpenRouterCredentialStore,
    private val networkAvailability: NetworkAvailability,
) {

    data class Snapshot(
        val usablePlatforms: List<PlatformV2>,
        val networkAvailable: Boolean,
        val openRouterCredentialMissing: Boolean,
    ) {
        val hasUsableInternalFreeRoute: Boolean
            get() = usablePlatforms.any { platform ->
                AiProviderOrigin.of(platform) == AiProviderOrigin.INTERNAL_FREE
            }
    }

    suspend fun evaluate(platforms: List<PlatformV2>): Snapshot {
        val networkAvailable = networkAvailability.hasValidatedInternet()
        var openRouterCredentialMissing = false
        val usable = ArrayList<PlatformV2>(platforms.size)

        for (platform in platforms) {
            if (!freeAiRouter.isInternalFree(platform)) {
                usable += platform
                continue
            }

            if (!networkAvailable) continue

            val provider = freeAiRouter.detectProvider(platform)
            val isUsable = when (provider) {
                FreeAiRouter.Provider.OPENROUTER -> {
                    if (platform.token == OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL) {
                        val credentialPresent = !openRouterCredentialStore
                            .getApiKey()
                            .isNullOrBlank()
                        if (!credentialPresent) openRouterCredentialMissing = true
                        credentialPresent
                    } else {
                        freeAiRouter.isFreeCandidate(platform, provider)
                    }
                }

                // Local inference is retired. Keep legacy detection only so old
                // database rows can be removed safely by FreeAiBootstrapper.
                FreeAiRouter.Provider.LOCAL -> false

                else -> freeAiRouter.isFreeCandidate(platform, provider)
            }

            if (isUsable) usable += platform
        }

        return Snapshot(
            usablePlatforms = usable,
            networkAvailable = networkAvailable,
            openRouterCredentialMissing = openRouterCredentialMissing,
        )
    }
}

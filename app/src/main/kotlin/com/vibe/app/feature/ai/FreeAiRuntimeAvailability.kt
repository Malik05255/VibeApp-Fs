package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.feature.ai.openrouter.OpenRouterCredentialStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Runtime validation for hidden Free AI routes.
 *
 * Database rows describe configured routes, but they do not prove that the
 * current device can execute Gemini Nano or that an OAuth-backed OpenRouter row
 * still has a decryptable credential. Keep those checks out of FreeAiRouter so
 * its deterministic/pure ordering remains testable.
 */
@Singleton
class FreeAiRuntimeAvailability @Inject constructor(
    private val freeAiRouter: FreeAiRouter,
    private val localNanoRuntime: LocalNanoRuntime,
    private val openRouterCredentialStore: OpenRouterCredentialStore,
) {

    data class Snapshot(
        val usablePlatforms: List<PlatformV2>,
        val localNanoUnsupported: Boolean,
        val openRouterCredentialMissing: Boolean,
    ) {
        val hasUsableInternalFreeRoute: Boolean
            get() = usablePlatforms.any { platform ->
                AiProviderOrigin.of(platform) == AiProviderOrigin.INTERNAL_FREE
            }
    }

    suspend fun evaluate(platforms: List<PlatformV2>): Snapshot {
        var localNanoUnsupported = false
        var openRouterCredentialMissing = false
        val usable = ArrayList<PlatformV2>(platforms.size)

        for (platform in platforms) {
            if (!freeAiRouter.isInternalFree(platform)) {
                usable += platform
                continue
            }

            val provider = freeAiRouter.detectProvider(platform)
            val isUsable = when (provider) {
                FreeAiRouter.Provider.LOCAL -> {
                    val supported = try {
                        localNanoRuntime.isSupportedByDevice()
                    } catch (e: CancellationException) {
                        throw e
                    }
                    if (!supported) localNanoUnsupported = true
                    supported
                }

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

                else -> freeAiRouter.isFreeCandidate(platform, provider)
            }

            if (isUsable) usable += platform
        }

        return Snapshot(
            usablePlatforms = usable,
            localNanoUnsupported = localNanoUnsupported,
            openRouterCredentialMissing = openRouterCredentialMissing,
        )
    }
}

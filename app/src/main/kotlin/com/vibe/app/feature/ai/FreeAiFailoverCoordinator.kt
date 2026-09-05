package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.repository.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeAiFailoverCoordinator @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
    private val freeAiBootstrapper: FreeAiBootstrapper,
) {

    sealed class Result {
        data class Switched(
            val fromPlatformUid: String,
            val toPlatform: PlatformV2,
            val activatedFreeAi: Boolean,
        ) : Result()

        data object ManualMode : Result()
        data object FreeAiDisabled : Result()
        data object NoFallbackAvailable : Result()
    }

    /**
     * External API providers are opt-in and are only reactivated by the user.
     * Free AI routing is automatic and independent from task execution mode.
     */
    suspend fun resolveStartPlatform(requestedPlatform: PlatformV2): PlatformV2 {
        // Runtime defense against startup races: by the time a chat request is
        // routed, the hidden zero-key local candidate must already exist.
        val platforms = freeAiBootstrapper.ensureReady()

        val enabledExternal = platforms.firstOrNull { platform ->
            platform.enabled && freeAiRouter.isExternal(platform)
        }
        if (enabledExternal != null) {
            if (settingRepository.getFreeAiEnabled()) {
                settingRepository.updateFreeAiEnabled(false)
            }
            return enabledExternal
        }

        if (!settingRepository.getFreeAiEnabled()) {
            settingRepository.updateFreeAiEnabled(true)
        }

        val enabledFree = platforms.firstOrNull { platform ->
            platform.enabled && freeAiRouter.isFreeCandidate(platform)
        }
        if (enabledFree != null) return enabledFree

        val fallback = freeAiRouter.selectBest(platforms)
        if (fallback != null) {
            activateOnly(platforms, fallback.uid)
            return fallback
        }

        throw IllegalStateException(
            "No active AI provider is available. Free AI has no usable route and external APIs remain off until enabled manually."
        )
    }

    suspend fun handleFailure(failedPlatformUid: String): Result {
        // Ensure local fallback exists even if the first ever request starts
        // before application bootstrap finishes.
        val platforms = freeAiBootstrapper.ensureReady()
        val failedPlatform = platforms.firstOrNull { it.uid == failedPlatformUid }
        val failedWasInternal = failedPlatform?.let(freeAiRouter::isInternalFree) == true

        val target = if (failedWasInternal) {
            freeAiRouter.nextAfter(platforms, failedPlatformUid)
        } else {
            freeAiRouter.selectBest(platforms)
        }

        val freeAiWasEnabled = settingRepository.getFreeAiEnabled()
        if (!freeAiWasEnabled) {
            settingRepository.updateFreeAiEnabled(true)
        }

        if (target == null) {
            if (
                failedPlatform != null &&
                freeAiRouter.isExternal(failedPlatform) &&
                failedPlatform.enabled
            ) {
                settingRepository.updatePlatformV2(
                    failedPlatform.copy(enabled = false)
                )
            }
            return Result.NoFallbackAvailable
        }

        activateOnly(platforms, target.uid)

        return Result.Switched(
            fromPlatformUid = failedPlatformUid,
            toPlatform = target,
            activatedFreeAi = !freeAiWasEnabled,
        )
    }

    private suspend fun activateOnly(
        platforms: List<PlatformV2>,
        targetUid: String,
    ) {
        platforms.forEach { platform ->
            val shouldEnable = platform.uid == targetUid
            if (platform.enabled != shouldEnable) {
                settingRepository.updatePlatformV2(platform.copy(enabled = shouldEnable))
            }
        }
    }
}
package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.repository.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeAiFailoverCoordinator @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
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
     * Free AI is automatic: when no external provider is enabled, the best
     * configured free provider becomes active without requiring user action.
     */
    suspend fun resolveStartPlatform(requestedPlatform: PlatformV2): PlatformV2 {
        val platforms = settingRepository.fetchPlatformV2s()

        // A manually enabled external provider always has priority. Never
        // reactivate one here if it has already been disabled after failure.
        val enabledExternal = platforms.firstOrNull { platform ->
            platform.enabled && !freeAiRouter.isFreeCandidate(platform)
        }
        if (enabledExternal != null) {
            if (settingRepository.getFreeAiEnabled()) {
                settingRepository.updateFreeAiEnabled(false)
            }
            return enabledExternal
        }

        // No external API is active, therefore free AI must be available
        // automatically. This also covers a provider the user switched off.
        if (!settingRepository.getFreeAiEnabled()) {
            settingRepository.updateFreeAiEnabled(true)
        }

        val enabledFree = platforms.firstOrNull { platform ->
            platform.enabled && freeAiRouter.isFreeCandidate(platform)
        }
        if (enabledFree != null) return enabledFree

        val fallback = freeAiRouter.selectBest(platforms)
            ?: return requestedPlatform

        activateOnly(platforms, fallback.uid)
        return fallback
    }

    suspend fun handleFailure(failedPlatformUid: String): Result {
        val platforms = settingRepository.fetchPlatformV2s()
        val failedPlatform = platforms.firstOrNull { it.uid == failedPlatformUid }
        val failedWasFree = failedPlatform?.let { freeAiRouter.isFreeCandidate(it) } == true

        val target = if (failedWasFree) {
            // Continue through the free chain. Never wrap back to a provider
            // that already failed during the same fallback sequence.
            freeAiRouter.nextAfter(platforms, failedPlatformUid)
        } else {
            // External APIs are never re-enabled automatically. Their failure
            // permanently hands control to free AI until the user manually
            // enables an external provider again.
            freeAiRouter.selectBest(platforms)
        } ?: return Result.NoFallbackAvailable

        val freeAiWasEnabled = settingRepository.getFreeAiEnabled()
        if (!freeAiWasEnabled) {
            settingRepository.updateFreeAiEnabled(true)
        }

        // Persist the fallback as the only active provider. This disables the
        // failed external API so resolveStartPlatform() cannot revive it later.
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

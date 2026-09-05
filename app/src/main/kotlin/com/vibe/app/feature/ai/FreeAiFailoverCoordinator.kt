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
     * Free AI routing is automatic and independent from task execution mode.
     */
    suspend fun resolveStartPlatform(requestedPlatform: PlatformV2): PlatformV2 {
        val platforms = settingRepository.fetchPlatformV2s()

        // A manually enabled external provider always has priority. Vendor name
        // is irrelevant: external:gemini and internal:gemini are separate routes.
        val enabledExternal = platforms.firstOrNull { platform ->
            platform.enabled && freeAiRouter.isExternal(platform)
        }
        if (enabledExternal != null) {
            if (settingRepository.getFreeAiEnabled()) {
                settingRepository.updateFreeAiEnabled(false)
            }
            return enabledExternal
        }

        // No external API is active. Free AI wakes automatically, including
        // after an external API was manually disabled or disabled after failure.
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
        val failedWasInternal = failedPlatform?.let(freeAiRouter::isInternalFree) == true

        val target = if (failedWasInternal) {
            // Continue through hidden Free AI candidates only. Never wrap to a
            // provider that already failed in the same sequence.
            freeAiRouter.nextAfter(platforms, failedPlatformUid)
        } else {
            // External APIs never switch to another external API automatically.
            // Their failure hands control only to the hidden Free AI pool.
            freeAiRouter.selectBest(platforms)
        }

        val freeAiWasEnabled = settingRepository.getFreeAiEnabled()
        if (!freeAiWasEnabled) {
            settingRepository.updateFreeAiEnabled(true)
        }

        if (target == null) {
            // Even when no fallback is configured, a failed external API must
            // remain off until the user explicitly enables it again.
            if (failedPlatform != null && freeAiRouter.isExternal(failedPlatform) && failedPlatform.enabled) {
                settingRepository.updatePlatformV2(failedPlatform.copy(enabled = false))
            }
            return Result.NoFallbackAvailable
        }

        // The selected hidden fallback becomes the only active route. This also
        // disables the failed external API and guarantees it cannot self-revive.
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

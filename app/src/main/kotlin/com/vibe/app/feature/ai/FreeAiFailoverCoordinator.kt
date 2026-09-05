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

    suspend fun handleFailure(failedPlatformUid: String): Result {
        val mode = AiExecutionMode.fromStoredValue(settingRepository.getAiExecutionMode())
        if (mode == AiExecutionMode.MANUAL) return Result.ManualMode

        val platforms = settingRepository.fetchPlatformV2s()
        val failedPlatform = platforms.firstOrNull { it.uid == failedPlatformUid }
        val failedWasFree = failedPlatform?.let { freeAiRouter.isFreeCandidate(it) } == true
        val freeAiEnabled = settingRepository.getFreeAiEnabled()

        if (failedWasFree && !freeAiEnabled) {
            return Result.FreeAiDisabled
        }

        val target = if (failedWasFree) {
            // Never wrap to the first provider. Once the ordered free chain is
            // exhausted, the failure must be surfaced instead of looping forever.
            freeAiRouter.nextAfter(platforms, failedPlatformUid)
        } else {
            // A custom/private provider failure wakes the free chain from its
            // highest-priority configured candidate.
            freeAiRouter.selectBest(platforms)
        } ?: return Result.NoFallbackAvailable

        var activatedFreeAi = false
        if (!failedWasFree && !freeAiEnabled) {
            settingRepository.updateFreeAiEnabled(true)
            activatedFreeAi = true
        }

        activateOnly(platforms, target.uid)

        return Result.Switched(
            fromPlatformUid = failedPlatformUid,
            toPlatform = target,
            activatedFreeAi = activatedFreeAi,
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

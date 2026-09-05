package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.repository.SettingRepository
import com.vibe.app.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeAiFailoverCoordinator @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
    private val freeAiBootstrapper: FreeAiBootstrapper,
    private val smartOrchestrator: SmartFreeAiOrchestrator,
    private val runtimeAvailability: FreeAiRuntimeAvailability,
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
     * Smart per-turn entry point used by the Agent.
     *
     * A manually enabled external API always wins. Otherwise Free AI chooses a
     * hidden route for this specific task. Runtime-only capabilities are checked
     * before selection so an unsupported Gemini Nano route is never advertised
     * as the active provider merely because a database row exists.
     */
    suspend fun resolveStartPlatform(request: AgentModelRequest): PlatformV2 {
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

        val availability = runtimeAvailability.evaluate(platforms)
        val target = smartOrchestrator.selectBest(
            request = request,
            platforms = availability.usablePlatforms,
        )

        if (target == null) {
            deactivateInternalPlatforms(platforms)
            throw IllegalStateException(noRouteMessage(availability))
        }

        activateOnly(platforms, target.uid)
        return target
    }

    /**
     * Compatibility entry point for older callers/tests that do not have a full
     * AgentModelRequest. It preserves deterministic provider ordering while still
     * applying runtime availability checks.
     */
    suspend fun resolveStartPlatform(requestedPlatform: PlatformV2): PlatformV2 {
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

        val availability = runtimeAvailability.evaluate(platforms)
        val usablePlatforms = availability.usablePlatforms

        val enabledFree = usablePlatforms.firstOrNull { platform ->
            platform.enabled && freeAiRouter.isFreeCandidate(platform)
        }
        if (enabledFree != null) return enabledFree

        val fallback = freeAiRouter.selectBest(usablePlatforms)
        if (fallback != null) {
            activateOnly(platforms, fallback.uid)
            return fallback
        }

        deactivateInternalPlatforms(platforms)
        throw IllegalStateException(noRouteMessage(availability))
    }

    suspend fun handleFailure(
        failedPlatformUid: String,
        request: AgentModelRequest? = null,
        attemptedPlatformUids: Set<String> = emptySet(),
    ): Result {
        val platforms = freeAiBootstrapper.ensureReady()
        val availability = runtimeAvailability.evaluate(platforms)
        val usablePlatforms = availability.usablePlatforms
        val failedPlatform = platforms.firstOrNull { it.uid == failedPlatformUid }
        val failedWasInternal = failedPlatform?.let(freeAiRouter::isInternalFree) == true

        val excluded = attemptedPlatformUids + failedPlatformUid
        val target = when {
            request != null -> smartOrchestrator.selectBest(
                request = request,
                platforms = usablePlatforms,
                excludedPlatformUids = excluded,
            )

            failedWasInternal -> freeAiRouter.nextAfter(usablePlatforms, failedPlatformUid)
            else -> freeAiRouter.selectBest(usablePlatforms)
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

            // Do not leave an explicitly unusable local/OAuth route marked as
            // enabled after the failover chain has proved there is nowhere to go.
            val usableInternalUids = usablePlatforms
                .filter(freeAiRouter::isInternalFree)
                .mapTo(hashSetOf()) { it.uid }
            platforms
                .filter { platform ->
                    platform.enabled &&
                        freeAiRouter.isInternalFree(platform) &&
                        platform.uid !in usableInternalUids
                }
                .forEach { platform ->
                    settingRepository.updatePlatformV2(platform.copy(enabled = false))
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

    private fun noRouteMessage(
        availability: FreeAiRuntimeAvailability.Snapshot,
    ): String = when {
        availability.localNanoUnsupported ->
            "LOCAL_AI_UNAVAILABLE_NO_FALLBACK: Gemini Nano is not available on this device. Connect OpenRouter Free in Settings > AI providers and try again."

        availability.openRouterCredentialMissing ->
            "OPENROUTER_OAUTH_CREDENTIAL_MISSING: OpenRouter Free is configured but its OAuth credential is unavailable. Reconnect OpenRouter Free in Settings > AI providers."

        else ->
            "No active AI provider is available. Free AI has no usable route and external APIs remain off until enabled manually."
    }

    private suspend fun deactivateInternalPlatforms(platforms: List<PlatformV2>) {
        platforms
            .filter { platform ->
                platform.enabled && freeAiRouter.isInternalFree(platform)
            }
            .forEach { platform ->
                settingRepository.updatePlatformV2(platform.copy(enabled = false))
            }
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

package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.repository.SettingRepository
import com.malik.lmai.feature.agent.AgentModelRequest
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
     * hidden cloud route only when validated internet and credentials are ready.
     * Runtime unavailability must never persistently disable the hidden Free AI
     * row: doing so leaves the chat composer locked even after connectivity or
     * credentials recover.
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
            throw IllegalStateException(noRouteMessage(availability))
        }

        activateOnly(platforms, target.uid)
        return target
    }

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

            // Do not disable hidden Free AI here. A transient network outage,
            // expired OAuth session, or exhausted provider must not poison the
            // persistent UI state and lock the chat composer on the next turn.
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
        !availability.networkAvailable ->
            "CLOUD_AI_OFFLINE: Free AI uses lightweight cloud inference. Connect to the internet and try again."

        availability.openRouterCredentialMissing ->
            "OPENROUTER_OAUTH_CREDENTIAL_MISSING: OpenRouter Free is configured but its OAuth credential is unavailable. Reconnect OpenRouter Free in Settings > AI providers."

        else ->
            "CLOUD_AI_NOT_CONNECTED: Connect OpenRouter Free in Settings > AI providers, then try again."
    }

    private suspend fun activateOnly(
        platforms: List<PlatformV2>,
        targetUid: String,
    ) {
        for (platform in platforms) {
            val shouldEnable = platform.uid == targetUid
            if (platform.enabled != shouldEnable) {
                settingRepository.updatePlatformV2(platform.copy(enabled = shouldEnable))
            }
        }
    }
}

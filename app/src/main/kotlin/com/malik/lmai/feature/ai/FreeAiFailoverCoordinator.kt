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
     * Smart per-turn entry point for محمد / مساعد H الرقمي.
     *
     * A manually enabled external API always wins. Otherwise H chooses the strongest
     * usable cloud route and automatically falls back to its independent local model
     * when connectivity is absent and the local model is ready.
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
        !availability.networkAvailable && availability.localModelPreparing ->
            "H_LOCAL_MODEL_PREPARING: محمد المحلي لم يكتمل تنزيله بعد. اتصل بـ Wi‑Fi وسيكمل التحضير تلقائيًا."

        !availability.networkAvailable && !availability.localModelAvailable ->
            "H_OFFLINE_NOT_READY: لا يوجد إنترنت ومحمد المحلي غير جاهز بعد. وصّل Wi‑Fi مرة واحدة لإكمال النموذج المحلي."

        availability.openRouterCredentialMissing ->
            "H_OPENROUTER_CREDENTIAL_MISSING: تعذر استخدام OpenRouter، وسيحاول محمد بقية المسارات المتاحة تلقائيًا."

        else ->
            "H_NO_ROUTE: لا يوجد مسار متاح لمحمد حاليًا. سيعيد المحاولة تلقائيًا عند توفر اتصال مناسب."
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

package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime route selection and failover for H.
 *
 * H is always the assistant identity. When the user explicitly enables an external
 * provider, that provider is an isolated execution lane: H must never silently consume
 * hidden/free provider capacity after an external-provider failure. Hidden free failover
 * is only allowed while no user-managed provider is enabled.
 *
 * Provider selection is ephemeral. A transient timeout, rate limit, or outage never
 * rewrites the user's persisted enabled-provider configuration.
 */
@Singleton
class FreeAiFailoverCoordinator @Inject constructor(
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

    /** Selects the best route for this turn without mutating saved provider state. */
    suspend fun resolveStartPlatform(request: AgentModelRequest): PlatformV2 {
        val platforms = freeAiBootstrapper.ensureReady()

        // Explicit user choice is exclusive. H keeps its identity but uses only this lane.
        enabledExternal(platforms)?.let { return it }

        val availability = runtimeAvailability.evaluate(platforms)
        return smartOrchestrator.selectBest(
            request = request,
            platforms = availability.usablePlatforms,
        ) ?: throw IllegalStateException(noRouteMessage(availability))
    }

    /** Legacy entry point kept for callers that do not yet provide a full request. */
    suspend fun resolveStartPlatform(requestedPlatform: PlatformV2): PlatformV2 {
        val platforms = freeAiBootstrapper.ensureReady()

        enabledExternal(platforms)?.let { return it }

        val availability = runtimeAvailability.evaluate(platforms)
        val usablePlatforms = availability.usablePlatforms

        usablePlatforms.firstOrNull { platform ->
            platform.uid == requestedPlatform.uid && freeAiRouter.isFreeCandidate(platform)
        }?.let { return it }

        return freeAiRouter.selectBest(usablePlatforms)
            ?: throw IllegalStateException(noRouteMessage(availability))
    }

    suspend fun handleFailure(
        failedPlatformUid: String,
        request: AgentModelRequest? = null,
        attemptedPlatformUids: Set<String> = emptySet(),
    ): Result {
        // If the owner selected an external API, never cross the boundary into H's hidden
        // pool. A failure must be reported/retried on that same external lane only.
        val platforms = freeAiBootstrapper.ensureReady()
        if (enabledExternal(platforms) != null) {
            return Result.NoFallbackAvailable
        }

        // Interactive turns should fail over once to a genuinely independent hidden
        // provider, not hop through several sibling models behind the same backend.
        if (
            request != null &&
            request.tools.isEmpty() &&
            attemptedPlatformUids.size >= MAX_INTERACTIVE_PROVIDER_ATTEMPTS
        ) {
            return Result.NoFallbackAvailable
        }

        val availability = runtimeAvailability.evaluate(platforms)
        val usablePlatforms = availability.usablePlatforms
        val failedPlatform = platforms.firstOrNull { it.uid == failedPlatformUid }
        val failedWasInternal = failedPlatform?.let(freeAiRouter::isInternalFree) == true

        // Unknown/external failures are never permission to enter the hidden pool.
        if (!failedWasInternal) return Result.NoFallbackAvailable

        val excluded = buildSet {
            addAll(attemptedPlatformUids)
            add(failedPlatformUid)

            // For ordinary chat/knowledge turns, skip all sibling models belonging to
            // the same hidden provider. An outage or quota problem is usually shared.
            if (request != null && request.tools.isEmpty() && failedPlatform != null) {
                val failedProvider = freeAiRouter.detectProvider(failedPlatform)
                usablePlatforms
                    .filter { platform ->
                        freeAiRouter.isInternalFree(platform) &&
                            freeAiRouter.detectProvider(platform) == failedProvider
                    }
                    .forEach { add(it.uid) }
            }
        }

        val target = when {
            request != null -> smartOrchestrator.selectBest(
                request = request,
                platforms = usablePlatforms,
                excludedPlatformUids = excluded,
            )

            else -> freeAiRouter.nextAfter(usablePlatforms, failedPlatformUid)
        }

        if (target == null || !freeAiRouter.isInternalFree(target)) {
            return Result.NoFallbackAvailable
        }

        return Result.Switched(
            fromPlatformUid = failedPlatformUid,
            toPlatform = target,
            activatedFreeAi = false,
        )
    }

    private fun enabledExternal(platforms: List<PlatformV2>): PlatformV2? =
        platforms.firstOrNull { platform ->
            platform.enabled && freeAiRouter.isExternal(platform)
        }

    private fun noRouteMessage(
        availability: FreeAiRuntimeAvailability.Snapshot,
    ): String = when {
        !availability.networkAvailable && availability.localModelPreparing ->
            "H_LOCAL_MODEL_PREPARING: المساعد الشخصي H المحلي لم يكتمل تنزيله بعد. اتصل بـ Wi‑Fi وسيكمل التحضير تلقائيًا."

        !availability.networkAvailable && !availability.localModelAvailable ->
            "H_OFFLINE_NOT_READY: لا يوجد إنترنت والمساعد الشخصي H المحلي غير جاهز بعد. وصّل Wi‑Fi مرة واحدة لإكمال النموذج المحلي."

        availability.openRouterCredentialMissing ->
            "H_OPENROUTER_CREDENTIAL_MISSING: تعذر استخدام أحد مسارات H السحابية، وسيحاول H بقية مساراته الداخلية المتاحة تلقائيًا."

        else ->
            "H_NO_ROUTE: لا يوجد مسار متاح للمساعد الشخصي H حاليًا. سيعيد المحاولة تلقائيًا عند توفر اتصال مناسب."
    }

    companion object {
        private const val MAX_INTERACTIVE_PROVIDER_ATTEMPTS = 2
    }
}

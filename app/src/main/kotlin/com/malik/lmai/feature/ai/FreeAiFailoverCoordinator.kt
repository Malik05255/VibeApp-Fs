package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy class name retained for call-site compatibility.
 *
 * This is no longer a "switch H to another provider" coordinator. H owns every turn.
 * HHelperRoutingPolicy decides whether the turn stays on H Core or temporarily consults
 * a specialist. The selected platform is ephemeral and is never persisted as H's identity.
 */
@Singleton
class FreeAiFailoverCoordinator @Inject constructor(
    private val freeAiRouter: FreeAiRouter,
    private val freeAiBootstrapper: FreeAiBootstrapper,
    private val helperRoutingPolicy: HHelperRoutingPolicy,
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
     * Resolve this turn's execution route. Ordinary turns prefer H Core. A configured
     * external model is considered only when H classifies the turn as genuinely hard.
     */
    suspend fun resolveStartPlatform(request: AgentModelRequest): PlatformV2 {
        val platforms = freeAiBootstrapper.ensureReady()
        val availability = runtimeAvailability.evaluate(platforms)
        return helperRoutingPolicy.select(
            request = request,
            allPlatforms = platforms,
            usablePlatforms = availability.usablePlatforms,
        )?.platform ?: throw IllegalStateException(noRouteMessage(availability))
    }

    /**
     * Legacy entry point for callers without a full request. Never promotes an external
     * provider to H. Prefer H Core, then a hidden H continuity route.
     */
    suspend fun resolveStartPlatform(requestedPlatform: PlatformV2): PlatformV2 {
        val platforms = freeAiBootstrapper.ensureReady()
        val availability = runtimeAvailability.evaluate(platforms)
        val usable = availability.usablePlatforms

        usable.firstOrNull(helperRoutingPolicy::isCore)?.let { return it }

        usable.firstOrNull { platform ->
            platform.uid == requestedPlatform.uid && freeAiRouter.isInternalFree(platform)
        }?.let { return it }

        return freeAiRouter.selectBest(usable.filter(freeAiRouter::isInternalFree))
            ?: throw IllegalStateException(noRouteMessage(availability))
    }

    suspend fun handleFailure(
        failedPlatformUid: String,
        request: AgentModelRequest? = null,
        attemptedPlatformUids: Set<String> = emptySet(),
    ): Result {
        val platforms = freeAiBootstrapper.ensureReady()
        val availability = runtimeAvailability.evaluate(platforms)
        val usable = availability.usablePlatforms
        val failedPlatform = platforms.firstOrNull { it.uid == failedPlatformUid }

        val excluded = buildSet {
            addAll(attemptedPlatformUids)
            add(failedPlatformUid)

            // If the owner's paid/BYOK specialist is exhausted or unhealthy, H falls
            // back to its hidden pool for this turn. Do not hop to another external API.
            if (failedPlatform?.let(freeAiRouter::isExternal) == true) {
                platforms.filter(freeAiRouter::isExternal).forEach { add(it.uid) }
            }

            // A quota/outage generally affects sibling models behind the same hidden
            // backend, so an interactive turn should move to an independent backend.
            if (request != null && request.tools.isEmpty() && failedPlatform != null) {
                val failedProvider = freeAiRouter.detectProvider(failedPlatform)
                usable.filter { platform ->
                    freeAiRouter.isInternalFree(platform) &&
                        freeAiRouter.detectProvider(platform) == failedProvider
                }.forEach { add(it.uid) }
            }
        }

        if (
            request != null &&
            request.tools.isEmpty() &&
            attemptedPlatformUids.size >= MAX_INTERACTIVE_ROUTE_ATTEMPTS
        ) {
            return Result.NoFallbackAvailable
        }

        val target = if (request != null) {
            helperRoutingPolicy.select(
                request = request,
                allPlatforms = platforms,
                usablePlatforms = usable,
                excludedPlatformUids = excluded,
            )?.platform
        } else {
            // No request means no safe basis for paid-helper escalation. Stay inside H.
            val hidden = usable.filter { platform ->
                platform.uid !in excluded &&
                    freeAiRouter.isInternalFree(platform) &&
                    !helperRoutingPolicy.isCore(platform)
            }
            freeAiRouter.selectBest(hidden)
                ?: usable.firstOrNull { it.uid !in excluded && helperRoutingPolicy.isCore(it) }
        }

        if (target == null || target.uid in excluded) {
            return Result.NoFallbackAvailable
        }

        return Result.Switched(
            fromPlatformUid = failedPlatformUid,
            toPlatform = target,
            activatedFreeAi = false,
        )
    }

    private fun noRouteMessage(
        availability: FreeAiRuntimeAvailability.Snapshot,
    ): String = when {
        !availability.networkAvailable && availability.localModelPreparing ->
            "H_LOCAL_MODEL_PREPARING: H Core is still being prepared for offline continuity."

        !availability.networkAvailable && !availability.localModelAvailable ->
            "H_OFFLINE_NOT_READY: H Core is not ready on this device yet."

        availability.openRouterCredentialMissing ->
            "H_HELPER_UNAVAILABLE: one hidden H helper is unavailable; H will use another route when possible."

        else ->
            "H_NO_ROUTE: H has no executable route at this moment and will retry when capacity is available."
    }

    companion object {
        private const val MAX_INTERACTIVE_ROUTE_ATTEMPTS = 3
    }
}

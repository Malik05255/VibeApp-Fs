package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolChoiceMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ranks hidden Free AI routes by task, device capability, provider health and
 * model hints. The user still sees a single "Free AI" service.
 */
@Singleton
class SmartFreeAiOrchestrator @Inject constructor(
    private val freeAiRouter: FreeAiRouter,
    private val taskClassifier: AiTaskClassifier,
    private val deviceCapabilityProfiler: DeviceCapabilityProfiler,
    private val providerHealthTracker: ProviderHealthTracker,
) {

    data class RankedCandidate(
        val platform: PlatformV2,
        val provider: FreeAiRouter.Provider,
        val score: Int,
        val task: AiTaskProfile,
    )

    fun rank(
        request: AgentModelRequest,
        platforms: List<PlatformV2>,
        excludedPlatformUids: Set<String> = emptySet(),
    ): List<RankedCandidate> {
        val task = taskClassifier.classify(request)
        val device = deviceCapabilityProfiler.snapshot()
        val candidates = freeAiRouter.orderedCandidates(platforms)

        return candidates
            .asSequence()
            .filterNot { it.platform.uid in excludedPlatformUids }
            .map { candidate ->
                RankedCandidate(
                    platform = candidate.platform,
                    provider = candidate.provider,
                    score = scoreCandidate(
                        candidate = candidate,
                        task = task,
                        device = device,
                        request = request,
                    ),
                    task = task,
                )
            }
            .sortedWith(
                compareByDescending<RankedCandidate> { it.score }
                    .thenBy { it.provider.priority }
                    .thenBy { it.platform.name.lowercase() }
            )
            .toList()
    }

    fun selectBest(
        request: AgentModelRequest,
        platforms: List<PlatformV2>,
        excludedPlatformUids: Set<String> = emptySet(),
    ): PlatformV2? = rank(request, platforms, excludedPlatformUids)
        .firstOrNull()
        ?.platform

    private fun scoreCandidate(
        candidate: FreeAiRouter.Candidate,
        task: AiTaskProfile,
        device: DeviceCapabilitySnapshot,
        request: AgentModelRequest,
    ): Int {
        val provider = candidate.provider
        val platform = candidate.platform

        var score = BASE_QUALITY.getValue(provider)
        score += taskAdjustment(provider, task.kind)
        score += deviceAdjustment(provider, device.profile)
        score += providerHealthTracker.scoreAdjustment(platform.uid)
        score += modelHintAdjustment(platform.model)

        val toolsActuallyRequired =
            request.policy.toolChoiceMode == AgentToolChoiceMode.REQUIRED ||
                task.requiresProjectTools

        if (toolsActuallyRequired && provider == FreeAiRouter.Provider.LOCAL) {
            // Gemini Nano is the zero-key chat baseline, not the app-building
            // engine. Keep it as an emergency final response only.
            score -= 100
        }

        if (request.tools.isNotEmpty() && provider == FreeAiRouter.Provider.LOCAL) {
            score -= 45
        }

        return score
    }

    private fun taskAdjustment(
        provider: FreeAiRouter.Provider,
        task: AiTaskKind,
    ): Int = when (task) {
        AiTaskKind.LIGHT_CHAT -> when (provider) {
            FreeAiRouter.Provider.LOCAL -> 35
            FreeAiRouter.Provider.GROQ -> 8
            FreeAiRouter.Provider.GEMINI -> 4
            else -> 0
        }

        AiTaskKind.EXPLANATION -> when (provider) {
            FreeAiRouter.Provider.LOCAL -> 22
            FreeAiRouter.Provider.GEMINI -> 10
            FreeAiRouter.Provider.OPENROUTER -> 5
            else -> 0
        }

        AiTaskKind.CODE_EDIT -> when (provider) {
            FreeAiRouter.Provider.GEMINI -> 20
            FreeAiRouter.Provider.OPENROUTER -> 18
            FreeAiRouter.Provider.GROQ -> 14
            FreeAiRouter.Provider.MISTRAL -> 8
            FreeAiRouter.Provider.LOCAL -> -35
            else -> 0
        }

        AiTaskKind.BUG_FIX -> when (provider) {
            FreeAiRouter.Provider.GEMINI -> 24
            FreeAiRouter.Provider.OPENROUTER -> 21
            FreeAiRouter.Provider.GROQ -> 15
            FreeAiRouter.Provider.MISTRAL -> 9
            FreeAiRouter.Provider.LOCAL -> -45
            else -> 0
        }

        AiTaskKind.PROJECT_COMPLEX -> when (provider) {
            FreeAiRouter.Provider.GEMINI -> 30
            FreeAiRouter.Provider.OPENROUTER -> 26
            FreeAiRouter.Provider.GROQ -> 17
            FreeAiRouter.Provider.MISTRAL -> 12
            FreeAiRouter.Provider.CLOUDFLARE -> 4
            FreeAiRouter.Provider.LOCAL -> -60
            else -> 0
        }
    }

    private fun deviceAdjustment(
        provider: FreeAiRouter.Provider,
        profile: DeviceAiProfile,
    ): Int {
        if (provider != FreeAiRouter.Provider.LOCAL) {
            return when (profile) {
                DeviceAiProfile.LOCAL_FULL -> 0
                DeviceAiProfile.LOCAL_LIGHT -> 1
                DeviceAiProfile.CLOUD_FIRST -> 5
                DeviceAiProfile.CLOUD_ONLY -> 8
            }
        }

        return when (profile) {
            DeviceAiProfile.LOCAL_FULL -> 12
            DeviceAiProfile.LOCAL_LIGHT -> 7
            DeviceAiProfile.CLOUD_FIRST -> -8
            DeviceAiProfile.CLOUD_ONLY -> -22
        }
    }

    private fun modelHintAdjustment(model: String): Int {
        val normalized = model.lowercase()
        var score = 0

        if (
            "code" in normalized ||
            "coder" in normalized ||
            "dev" in normalized
        ) {
            score += 8
        }

        if (
            "reason" in normalized ||
            "thinking" in normalized ||
            "pro" in normalized
        ) {
            score += 6
        }

        if (
            "mini" in normalized ||
            "flash" in normalized ||
            "lite" in normalized
        ) {
            score += 2
        }

        return score
    }

    companion object {
        private val BASE_QUALITY = mapOf(
            FreeAiRouter.Provider.GEMINI to 96,
            FreeAiRouter.Provider.OPENROUTER to 93,
            FreeAiRouter.Provider.GROQ to 90,
            FreeAiRouter.Provider.MISTRAL to 87,
            FreeAiRouter.Provider.CLOUDFLARE to 82,
            FreeAiRouter.Provider.LOCAL to 68,
            FreeAiRouter.Provider.UNKNOWN to 0,
        )
    }
}

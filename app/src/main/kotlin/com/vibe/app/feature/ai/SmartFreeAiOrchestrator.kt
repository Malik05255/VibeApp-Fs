package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ranks hidden cloud AI routes by task fit, learned provider health and model
 * hints. No device capability profiling is needed because no LLM runs locally.
 */
@Singleton
class SmartFreeAiOrchestrator @Inject constructor(
    private val freeAiRouter: FreeAiRouter,
    private val taskClassifier: AiTaskClassifier,
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
    ): Int {
        val provider = candidate.provider
        val platform = candidate.platform

        var score = BASE_QUALITY.getValue(provider)
        score += taskAdjustment(provider, task.kind)
        score += providerHealthTracker.scoreAdjustment(platform.uid)
        score += modelHintAdjustment(platform.model)
        return score
    }

    private fun taskAdjustment(
        provider: FreeAiRouter.Provider,
        task: AiTaskKind,
    ): Int = when (task) {
        AiTaskKind.LIGHT_CHAT -> when (provider) {
            FreeAiRouter.Provider.OPENROUTER -> 12
            FreeAiRouter.Provider.GROQ -> 10
            FreeAiRouter.Provider.GEMINI -> 7
            else -> 0
        }

        AiTaskKind.EXPLANATION -> when (provider) {
            FreeAiRouter.Provider.GEMINI -> 12
            FreeAiRouter.Provider.OPENROUTER -> 10
            FreeAiRouter.Provider.GROQ -> 5
            else -> 0
        }

        AiTaskKind.CODE_EDIT -> when (provider) {
            FreeAiRouter.Provider.GEMINI -> 20
            FreeAiRouter.Provider.OPENROUTER -> 19
            FreeAiRouter.Provider.GROQ -> 14
            FreeAiRouter.Provider.MISTRAL -> 8
            else -> 0
        }

        AiTaskKind.BUG_FIX -> when (provider) {
            FreeAiRouter.Provider.GEMINI -> 24
            FreeAiRouter.Provider.OPENROUTER -> 22
            FreeAiRouter.Provider.GROQ -> 15
            FreeAiRouter.Provider.MISTRAL -> 9
            else -> 0
        }

        AiTaskKind.PROJECT_COMPLEX -> when (provider) {
            FreeAiRouter.Provider.GEMINI -> 30
            FreeAiRouter.Provider.OPENROUTER -> 28
            FreeAiRouter.Provider.GROQ -> 17
            FreeAiRouter.Provider.MISTRAL -> 12
            FreeAiRouter.Provider.CLOUDFLARE -> 4
            else -> 0
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
            FreeAiRouter.Provider.OPENROUTER to 98,
            FreeAiRouter.Provider.GEMINI to 96,
            FreeAiRouter.Provider.GROQ to 90,
            FreeAiRouter.Provider.MISTRAL to 87,
            FreeAiRouter.Provider.CLOUDFLARE to 82,
            FreeAiRouter.Provider.LOCAL to 0,
            FreeAiRouter.Provider.UNKNOWN to 0,
        )
    }
}

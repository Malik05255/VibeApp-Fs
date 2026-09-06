package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Ranks مساعد H الرقمي routes by task fit and learned provider/model health. */
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
        return freeAiRouter.orderedCandidates(platforms)
            .asSequence()
            .filterNot { it.platform.uid in excludedPlatformUids }
            // The 0.5B local model is a conversation/code-review fallback. Project
            // mutation remains on the validated tool-capable cloud agent path.
            .filterNot {
                request.tools.isNotEmpty() && it.provider == FreeAiRouter.Provider.LOCAL
            }
            .map { candidate ->
                RankedCandidate(
                    platform = candidate.platform,
                    provider = candidate.provider,
                    score = scoreCandidate(candidate, task),
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
        score += modelHintAdjustment(platform.model, task.kind)
        return score
    }

    private fun taskAdjustment(
        provider: FreeAiRouter.Provider,
        task: AiTaskKind,
    ): Int = when (task) {
        AiTaskKind.LIGHT_CHAT -> when (provider) {
            FreeAiRouter.Provider.BLOCKRUN -> 12
            FreeAiRouter.Provider.OPENROUTER -> 10
            FreeAiRouter.Provider.GROQ -> 9
            FreeAiRouter.Provider.GEMINI -> 7
            FreeAiRouter.Provider.LOCAL -> 10
            else -> 0
        }

        AiTaskKind.EXPLANATION -> when (provider) {
            FreeAiRouter.Provider.BLOCKRUN -> 14
            FreeAiRouter.Provider.GEMINI -> 12
            FreeAiRouter.Provider.OPENROUTER -> 10
            FreeAiRouter.Provider.GROQ -> 5
            FreeAiRouter.Provider.LOCAL -> 8
            else -> 0
        }

        AiTaskKind.CODE_EDIT -> when (provider) {
            FreeAiRouter.Provider.BLOCKRUN -> 26
            FreeAiRouter.Provider.GEMINI -> 20
            FreeAiRouter.Provider.OPENROUTER -> 19
            FreeAiRouter.Provider.GROQ -> 14
            FreeAiRouter.Provider.MISTRAL -> 8
            FreeAiRouter.Provider.LOCAL -> 5
            else -> 0
        }

        AiTaskKind.BUG_FIX -> when (provider) {
            FreeAiRouter.Provider.BLOCKRUN -> 28
            FreeAiRouter.Provider.GEMINI -> 24
            FreeAiRouter.Provider.OPENROUTER -> 22
            FreeAiRouter.Provider.GROQ -> 15
            FreeAiRouter.Provider.MISTRAL -> 9
            FreeAiRouter.Provider.LOCAL -> 5
            else -> 0
        }

        AiTaskKind.PROJECT_COMPLEX -> when (provider) {
            FreeAiRouter.Provider.BLOCKRUN -> 32
            FreeAiRouter.Provider.GEMINI -> 30
            FreeAiRouter.Provider.OPENROUTER -> 28
            FreeAiRouter.Provider.GROQ -> 17
            FreeAiRouter.Provider.MISTRAL -> 12
            FreeAiRouter.Provider.CLOUDFLARE -> 4
            FreeAiRouter.Provider.LOCAL -> 0
            else -> 0
        }
    }

    private fun modelHintAdjustment(
        model: String,
        task: AiTaskKind,
    ): Int {
        val normalized = model.lowercase()
        var score = 0
        val codingModel =
            "code" in normalized ||
                "coder" in normalized ||
                "laguna" in normalized ||
                "dev" in normalized
        val reasoningModel =
            "reason" in normalized ||
                "thinking" in normalized ||
                "nemotron-3.5" in normalized ||
                "pro" in normalized

        if (codingModel) {
            score += when (task) {
                AiTaskKind.CODE_EDIT, AiTaskKind.BUG_FIX, AiTaskKind.PROJECT_COMPLEX -> 14
                else -> 4
            }
        }

        if (reasoningModel) {
            score += when (task) {
                AiTaskKind.EXPLANATION, AiTaskKind.PROJECT_COMPLEX -> 12
                AiTaskKind.BUG_FIX -> 8
                else -> 3
            }
        }

        if (
            "mini" in normalized ||
            "flash" in normalized ||
            "lite" in normalized ||
            "laguna" in normalized ||
            "0.5b" in normalized
        ) {
            score += 2
        }

        return score
    }

    companion object {
        private val BASE_QUALITY = mapOf(
            FreeAiRouter.Provider.BLOCKRUN to 99,
            FreeAiRouter.Provider.OPENROUTER to 98,
            FreeAiRouter.Provider.GEMINI to 96,
            FreeAiRouter.Provider.GROQ to 90,
            FreeAiRouter.Provider.MISTRAL to 87,
            FreeAiRouter.Provider.CLOUDFLARE to 82,
            // Local is intentionally lower quality than cloud; it exists for privacy,
            // speed and offline continuity, not to replace stronger online models.
            FreeAiRouter.Provider.LOCAL to 62,
            FreeAiRouter.Provider.UNKNOWN to 0,
        )
    }
}

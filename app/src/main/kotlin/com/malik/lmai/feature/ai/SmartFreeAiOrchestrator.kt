package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Ranks مساعد H الرقمي routes by task fit, perceived latency and learned health. */
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
        val hasImageAttachments =
            request.fullConversation.any { it.attachments.isNotEmpty() } ||
                request.conversation.any { it.attachments.isNotEmpty() }

        return freeAiRouter.orderedCandidates(platforms)
            .asSequence()
            .filterNot { it.platform.uid in excludedPlatformUids }
            .filterNot {
                request.tools.isNotEmpty() && it.provider == FreeAiRouter.Provider.LOCAL
            }
            .filter {
                !hasImageAttachments || it.provider == FreeAiRouter.Provider.OPENROUTER
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

        // Conversation and interactive code diagnosis both need a fast first token.
        // Full project mutations still prioritize reliability/quality over TTFT because
        // tool execution and build validation are more important than shaving seconds.
        score += if (
            task.kind == AiTaskKind.LIGHT_CHAT ||
            task.kind == AiTaskKind.EXPLANATION ||
            ((task.kind == AiTaskKind.CODE_EDIT || task.kind == AiTaskKind.BUG_FIX) &&
                !task.requiresProjectTools)
        ) {
            providerHealthTracker.interactiveScoreAdjustment(platform.uid)
        } else {
            providerHealthTracker.scoreAdjustment(platform.uid)
        }

        score += modelHintAdjustment(platform.model, task.kind)
        score += codeSpeedAdjustment(platform, task)
        return score
    }

    private fun taskAdjustment(
        provider: FreeAiRouter.Provider,
        task: AiTaskKind,
    ): Int = when (task) {
        AiTaskKind.LIGHT_CHAT -> when (provider) {
            // runtimeAvailability only exposes LOCAL here after the model has been
            // downloaded and verified. Once ready, ordinary conversation should use
            // the on-device model first: zero provider quota, no network round-trip,
            // and predictable low latency.
            FreeAiRouter.Provider.LOCAL -> 72
            FreeAiRouter.Provider.GROQ -> 24
            FreeAiRouter.Provider.OPENROUTER -> 22
            FreeAiRouter.Provider.GEMINI -> 18
            FreeAiRouter.Provider.MISTRAL -> 12
            FreeAiRouter.Provider.CLOUDFLARE -> 10
            FreeAiRouter.Provider.BLOCKRUN -> 4
            else -> 0
        }

        AiTaskKind.EXPLANATION -> when (provider) {
            // Short factual/explanatory chat also stays local when possible. Stronger
            // cloud routes remain preferred for code repair and project execution.
            FreeAiRouter.Provider.LOCAL -> 54
            FreeAiRouter.Provider.GEMINI -> 20
            FreeAiRouter.Provider.OPENROUTER -> 18
            FreeAiRouter.Provider.GROQ -> 14
            FreeAiRouter.Provider.BLOCKRUN -> 8
            else -> 0
        }

        AiTaskKind.CODE_EDIT -> when (provider) {
            FreeAiRouter.Provider.BLOCKRUN -> 30
            FreeAiRouter.Provider.OPENROUTER -> 23
            FreeAiRouter.Provider.GEMINI -> 22
            FreeAiRouter.Provider.GROQ -> 16
            FreeAiRouter.Provider.MISTRAL -> 10
            FreeAiRouter.Provider.LOCAL -> 4
            else -> 0
        }

        AiTaskKind.BUG_FIX -> when (provider) {
            FreeAiRouter.Provider.BLOCKRUN -> 32
            FreeAiRouter.Provider.OPENROUTER -> 25
            FreeAiRouter.Provider.GEMINI -> 24
            FreeAiRouter.Provider.GROQ -> 17
            FreeAiRouter.Provider.MISTRAL -> 10
            FreeAiRouter.Provider.LOCAL -> 4
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

    private fun codeSpeedAdjustment(
        platform: PlatformV2,
        task: AiTaskProfile,
    ): Int {
        val model = platform.model.lowercase()
        return when {
            task.kind !in setOf(AiTaskKind.CODE_EDIT, AiTaskKind.BUG_FIX) -> 0

            !task.requiresProjectTools &&
                model == FreeAiBootstrapper.BLOCKRUN_FAST_CODE_MODEL.lowercase() -> 24

            !task.requiresProjectTools &&
                model == FreeAiBootstrapper.BLOCKRUN_CODE_MODEL.lowercase() -> 12

            task.requiresProjectTools &&
                model == FreeAiBootstrapper.BLOCKRUN_CODE_MODEL.lowercase() -> 18

            task.requiresProjectTools &&
                model == FreeAiBootstrapper.BLOCKRUN_FAST_CODE_MODEL.lowercase() -> 8

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
                AiTaskKind.EXPLANATION -> -4
                AiTaskKind.LIGHT_CHAT -> -10
            }
        }

        if (reasoningModel) {
            score += when (task) {
                AiTaskKind.EXPLANATION, AiTaskKind.PROJECT_COMPLEX -> 12
                AiTaskKind.BUG_FIX -> 8
                AiTaskKind.CODE_EDIT -> 4
                AiTaskKind.LIGHT_CHAT -> -8
            }
        }

        if (
            "mini" in normalized ||
            "flash" in normalized ||
            "lite" in normalized ||
            "laguna" in normalized ||
            "0.5b" in normalized
        ) {
            score += when (task) {
                AiTaskKind.LIGHT_CHAT -> 8
                AiTaskKind.EXPLANATION -> 4
                AiTaskKind.CODE_EDIT, AiTaskKind.BUG_FIX -> 6
                else -> 2
            }
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
            FreeAiRouter.Provider.LOCAL to 62,
            FreeAiRouter.Provider.UNKNOWN to 0,
        )
    }
}

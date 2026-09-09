package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * H-first execution policy.
 *
 * H is the permanent assistant/model identity. Other models are temporary specialists:
 * - ordinary turns stay on H Core;
 * - knowledge-heavy but non-hard turns may consult a hidden no-cost helper;
 * - genuinely hard turns may consult the owner's enabled external helper;
 * - an unavailable/exhausted external helper immediately falls back to H's hidden pool;
 * - no decision here mutates a persisted provider selection.
 */
@Singleton
class HHelperRoutingPolicy @Inject constructor(
    private val freeAiRouter: FreeAiRouter,
    private val taskClassifier: AiTaskClassifier,
    private val smartOrchestrator: SmartFreeAiOrchestrator,
    private val providerHealthTracker: ProviderHealthTracker,
) {

    enum class Role {
        H_CORE,
        USER_HELPER,
        HIDDEN_HELPER,
        H_CONTINUITY,
    }

    enum class Escalation {
        NONE,
        FREE_ENRICHMENT,
        STRONG_HELPER,
    }

    data class Decision(
        val platform: PlatformV2,
        val role: Role,
        val escalation: Escalation,
        val task: AiTaskProfile,
    )

    fun select(
        request: AgentModelRequest,
        allPlatforms: List<PlatformV2>,
        usablePlatforms: List<PlatformV2>,
        excludedPlatformUids: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
    ): Decision? {
        val task = taskClassifier.classify(request)
        val escalation = escalationFor(request, task)
        val core = usablePlatforms.firstOrNull { platform ->
            platform.uid !in excludedPlatformUids && isCore(platform)
        }

        if (escalation == Escalation.NONE && core != null) {
            return Decision(core, Role.H_CORE, escalation, task)
        }

        if (escalation == Escalation.STRONG_HELPER) {
            val userHelper = allPlatforms.firstOrNull { platform ->
                platform.uid !in excludedPlatformUids &&
                    platform.enabled &&
                    freeAiRouter.isExternal(platform) &&
                    usablePlatforms.any { it.uid == platform.uid } &&
                    !providerHealthTracker.snapshot(platform.uid).isCoolingDown(nowMs)
            }
            if (userHelper != null) {
                return Decision(userHelper, Role.USER_HELPER, escalation, task)
            }
        }

        val hiddenCandidates = usablePlatforms.filter { platform ->
            platform.uid !in excludedPlatformUids &&
                freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) != FreeAiRouter.Provider.LOCAL &&
                !providerHealthTracker.snapshot(platform.uid).isCoolingDown(nowMs)
        }
        val hidden = smartOrchestrator.selectBest(
            request = request,
            platforms = hiddenCandidates,
            excludedPlatformUids = excludedPlatformUids,
        )
        if (hidden != null) {
            val role = if (escalation == Escalation.NONE) Role.H_CONTINUITY else Role.HIDDEN_HELPER
            return Decision(hidden, role, escalation, task)
        }

        // H Core is the final continuity anchor. A missing helper must never replace H.
        if (core != null) {
            return Decision(core, Role.H_CORE, escalation, task)
        }

        return null
    }

    fun escalationFor(request: AgentModelRequest, task: AiTaskProfile): Escalation {
        val hasAttachments = request.fullConversation.any { it.attachments.isNotEmpty() } ||
            request.conversation.any { it.attachments.isNotEmpty() }

        if (
            hasAttachments ||
            task.requiresProjectTools ||
            task.complexity >= 3 ||
            task.kind == AiTaskKind.PROJECT_COMPLEX ||
            task.kind == AiTaskKind.BUG_FIX ||
            task.kind == AiTaskKind.CODE_EDIT
        ) {
            return Escalation.STRONG_HELPER
        }

        // H can enrich factual/technical explanations without spending the owner's
        // configured paid helper. This keeps the normal experience inexpensive.
        if (task.kind == AiTaskKind.EXPLANATION) {
            return Escalation.FREE_ENRICHMENT
        }

        return Escalation.NONE
    }

    fun isCore(platform: PlatformV2): Boolean =
        freeAiRouter.isInternalFree(platform) &&
            freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL
}

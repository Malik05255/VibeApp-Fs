package com.vibe.app.feature.ai

import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolChoiceMode
import javax.inject.Inject
import javax.inject.Singleton

enum class AiTaskKind {
    LIGHT_CHAT,
    EXPLANATION,
    CODE_EDIT,
    BUG_FIX,
    PROJECT_COMPLEX,
}

data class AiTaskProfile(
    val kind: AiTaskKind,
    val requiresProjectTools: Boolean,
    val complexity: Int,
)

/**
 * Fast deterministic classifier used before model selection.
 *
 * It intentionally does not call an LLM: routing must still work when the
 * network is unavailable or every cloud provider is rate-limited.
 */
@Singleton
class AiTaskClassifier @Inject constructor() {

    fun classify(request: AgentModelRequest): AiTaskProfile {
        val latestUserText = request.fullConversation
            .asReversed()
            .firstOrNull { it.role == AgentMessageRole.USER }
            ?.text
            ?: request.conversation
                .asReversed()
                .firstOrNull { it.role == AgentMessageRole.USER }
                ?.text
            ?: ""

        return classifyText(
            latestUserText = latestUserText,
            toolsAvailable = request.tools.isNotEmpty(),
            toolsRequired = request.policy.toolChoiceMode == AgentToolChoiceMode.REQUIRED,
        )
    }

    /**
     * Classifies a user turn before an AgentModelRequest exists.
     *
     * The chat session uses this to keep greetings, questions and explanations
     * on a one-turn fast path without loading project tools or the build-agent
     * prompt. Explicit app/code/build work still enters the full agent loop.
     */
    fun classifyText(
        latestUserText: String,
        toolsAvailable: Boolean = true,
        toolsRequired: Boolean = false,
    ): AiTaskProfile {
        val text = latestUserText.lowercase()

        val complex = containsAny(text, COMPLEX_TERMS) || toolsRequired
        if (complex) {
            return AiTaskProfile(
                kind = AiTaskKind.PROJECT_COMPLEX,
                requiresProjectTools = toolsRequired || toolsAvailable,
                complexity = 5,
            )
        }

        if (containsAny(text, BUG_TERMS)) {
            return AiTaskProfile(
                kind = AiTaskKind.BUG_FIX,
                requiresProjectTools = toolsAvailable,
                complexity = 4,
            )
        }

        if (containsAny(text, CODE_EDIT_TERMS)) {
            return AiTaskProfile(
                kind = AiTaskKind.CODE_EDIT,
                requiresProjectTools = toolsAvailable,
                complexity = 3,
            )
        }

        if (containsAny(text, EXPLANATION_TERMS)) {
            return AiTaskProfile(
                kind = AiTaskKind.EXPLANATION,
                requiresProjectTools = false,
                complexity = 2,
            )
        }

        return AiTaskProfile(
            kind = AiTaskKind.LIGHT_CHAT,
            requiresProjectTools = false,
            complexity = 1,
        )
    }

    private fun containsAny(text: String, terms: Set<String>): Boolean =
        terms.any { term -> text.contains(term) }

    companion object {
        // Arabic routing keywords are represented with Unicode escapes so the
        // localization audit can continue to reject user-facing Arabic literals
        // in Kotlin while the language-neutral task classifier still supports
        // Arabic input regardless of the selected UI locale.
        private val COMPLEX_TERMS = setOf(
            "create app", "build app", "new app", "full app", "entire app",
            "make app", "make an app", "i want an app", "i need an app",
            "architecture", "database migration", "authentication", "gradle",
            "build apk", "release apk", "project wide", "whole project",
            "\u0627\u0646\u0634\u0626 \u062a\u0637\u0628\u064a\u0642",
            "\u0623\u0646\u0634\u0626 \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0635\u0646\u0639 \u062a\u0637\u0628\u064a\u0642",
            "\u0633\u0648 \u0644\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0633\u0648\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0628\u0646\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0628\u0646\u0627\u0621 \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0628\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0623\u0628\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0628\u063a\u0649 \u062a\u0637\u0628\u064a\u0642",
            "\u0623\u0628\u063a\u0649 \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0631\u064a\u062f \u062a\u0637\u0628\u064a\u0642",
            "\u0623\u0631\u064a\u062f \u062a\u0637\u0628\u064a\u0642",
            "\u0645\u0634\u0631\u0648\u0639 \u0643\u0627\u0645\u0644",
            "\u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u0643\u0627\u0645\u0644",
            "\u0627\u0644\u0645\u0634\u0631\u0648\u0639 \u0643\u0627\u0645\u0644",
            "\u0645\u0639\u0645\u0627\u0631\u064a\u0629",
            "\u0642\u0627\u0639\u062f\u0629 \u0627\u0644\u0628\u064a\u0627\u0646\u0627\u062a",
            "\u062a\u0633\u062c\u064a\u0644 \u0627\u0644\u062f\u062e\u0648\u0644",
            "\u0627\u0628\u0646 apk",
            "\u0628\u0646\u0627\u0621 apk",
        )

        private val BUG_TERMS = setOf(
            "bug", "crash", "exception", "stacktrace", "error", "failed",
            "compile error", "build failed", "fix error", "repair",
            "\u062e\u0637\u0623", "\u0627\u062e\u0637\u0627\u0621",
            "\u0623\u062e\u0637\u0627\u0621", "\u0643\u0631\u0627\u0634",
            "\u062a\u0639\u0637\u0644", "\u0641\u0634\u0644",
            "\u0627\u0635\u0644\u062d", "\u0623\u0635\u0644\u062d",
            "\u0645\u0634\u0643\u0644\u0629",
            "\u062d\u0644 \u0627\u0644\u0645\u0634\u0643\u0644\u0629",
        )

        private val CODE_EDIT_TERMS = setOf(
            "edit code", "change code", "modify", "refactor", "implement",
            "add feature", "update screen", "change screen", "function", "class",
            "\u0639\u062f\u0644", "\u062a\u0639\u062f\u064a\u0644",
            "\u063a\u064a\u0631", "\u063a\u064a\u0651\u0631",
            "\u0627\u0636\u0641", "\u0623\u0636\u0641",
            "\u0646\u0641\u0630", "\u0646\u0641\u0651\u0630",
            "\u0634\u0627\u0634\u0629", "\u0643\u0648\u062f",
            "\u062f\u0627\u0644\u0629", "\u0643\u0644\u0627\u0633",
        )

        private val EXPLANATION_TERMS = setOf(
            "explain", "summarize", "what does", "how does", "review this",
            "\u0627\u0634\u0631\u062d", "\u0644\u062e\u0635",
            "\u0645\u0644\u062e\u0635",
            "\u0648\u0634 \u064a\u0639\u0646\u064a",
            "\u0643\u064a\u0641 \u064a\u0639\u0645\u0644",
            "\u0641\u0633\u0631", "\u0641\u0633\u0651\u0631",
        )
    }
}

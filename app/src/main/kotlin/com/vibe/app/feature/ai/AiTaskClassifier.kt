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

        val text = latestUserText.lowercase()
        val toolsRequired = request.policy.toolChoiceMode == AgentToolChoiceMode.REQUIRED
        val toolsAvailable = request.tools.isNotEmpty()

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
        terms.any(text::contains)

    companion object {
        private val COMPLEX_TERMS = setOf(
            "create app", "build app", "new app", "full app", "entire app",
            "architecture", "database migration", "authentication", "gradle",
            "build apk", "release apk", "project wide", "whole project",
            "انشئ تطبيق", "أنشئ تطبيق", "اصنع تطبيق", "سو لي تطبيق", "ابني تطبيق",
            "بناء تطبيق", "مشروع كامل", "التطبيق كامل", "المشروع كامل",
            "معمارية", "قاعدة البيانات", "تسجيل الدخول", "ابن apk", "بناء apk",
        )

        private val BUG_TERMS = setOf(
            "bug", "crash", "exception", "stacktrace", "error", "failed",
            "compile error", "build failed", "fix error", "repair",
            "خطأ", "اخطاء", "أخطاء", "كراش", "تعطل", "فشل", "اصلح", "أصلح",
            "مشكلة", "حل المشكلة",
        )

        private val CODE_EDIT_TERMS = setOf(
            "edit code", "change code", "modify", "refactor", "implement",
            "add feature", "update screen", "change screen", "function", "class",
            "عدل", "تعديل", "غير", "غيّر", "اضف", "أضف", "نفذ", "نفّذ",
            "شاشة", "كود", "دالة", "كلاس",
        )

        private val EXPLANATION_TERMS = setOf(
            "explain", "summarize", "what does", "how does", "review this",
            "اشرح", "لخص", "ملخص", "وش يعني", "كيف يعمل", "فسر", "فسّر",
        )
    }
}

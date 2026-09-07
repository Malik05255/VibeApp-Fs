package com.malik.lmai.feature.ai

import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolChoiceMode
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
 * Classifies the current turn, not the size of the containing project.
 *
 * Capability and complexity are deliberately independent. A tiny screen change in a
 * huge application can require project tools without becoming PROJECT_COMPLEX. Heavy
 * routing is reserved for genuinely broad work: architecture, migrations, multi-file
 * changes, full builds, or whole-project operations.
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
        val technicalContext = hasTechnicalContext(text)
        val uiScope = containsAny(text, UI_SCOPE_TERMS)
        val executionContext = technicalContext || uiScope || toolsRequired

        val explicitHeavyScope = containsAny(text, COMPLEX_EXECUTION_TERMS)
        val strongBugSignal = containsAny(text, STRONG_BUG_TERMS)
        val contextualBugSignal = executionContext && containsAny(text, CONTEXTUAL_BUG_TERMS)
        val codeEditSignal = executionContext && containsAny(text, CODE_EDIT_TERMS)
        val executionTurn = explicitHeavyScope || strongBugSignal || contextualBugSignal || codeEditSignal
        val requiresProjectTools = toolsRequired || (toolsAvailable && executionTurn)

        if (explicitHeavyScope) {
            return AiTaskProfile(
                kind = AiTaskKind.PROJECT_COMPLEX,
                requiresProjectTools = requiresProjectTools,
                complexity = 5,
            )
        }

        if (strongBugSignal || contextualBugSignal) {
            return AiTaskProfile(
                kind = AiTaskKind.BUG_FIX,
                requiresProjectTools = requiresProjectTools,
                complexity = if (containsAny(text, BROAD_SCOPE_TERMS)) 4 else 3,
            )
        }

        if (codeEditSignal) {
            return AiTaskProfile(
                kind = AiTaskKind.CODE_EDIT,
                requiresProjectTools = requiresProjectTools,
                complexity = if (uiScope || containsAny(text, LIGHT_EDIT_TERMS)) 2 else 3,
            )
        }

        // Non-technical factual lookup is different from casual chat. Route it to a
        // knowledge-capable cloud model instead of treating it as local/light chatter.
        if (containsAny(text, FACTUAL_QUERY_TERMS)) {
            return AiTaskProfile(
                kind = AiTaskKind.EXPLANATION,
                requiresProjectTools = false,
                complexity = 2,
            )
        }

        if (technicalContext && containsAny(text, EXPLANATION_TERMS)) {
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

    private fun hasTechnicalContext(text: String): Boolean =
        containsAny(text, TECHNICAL_CONTEXT_TERMS) || looksLikeCode(text)

    private fun looksLikeCode(text: String): Boolean {
        if (text.contains("```")) return true
        if (CODE_SHAPE_REGEXES.any { it.containsMatchIn(text) }) return true

        val punctuationSignals = listOf("()", "{}", "=>", "::", "?.", "!!", "->")
        return punctuationSignals.count(text::contains) >= 2
    }

    private fun containsAny(text: String, terms: Set<String>): Boolean =
        terms.any { term -> text.contains(term) }

    companion object {
        // Arabic routing terms use Unicode escapes so they remain classifier data rather
        // than user-facing Kotlin strings checked by the localization audit.
        private val COMPLEX_EXECUTION_TERMS = setOf(
            "create app", "build app", "new app", "full app", "entire app",
            "build apk", "release apk", "project wide", "whole project",
            "across the project", "across modules", "multi-module", "multi module",
            "architecture migration", "database migration across", "migrate the project",
            "rewrite architecture", "re-architect", "rearchitect",
            "\u0627\u0646\u0634\u0626 \u062a\u0637\u0628\u064a\u0642",
            "\u0623\u0646\u0634\u0626 \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0635\u0646\u0639 \u062a\u0637\u0628\u064a\u0642",
            "\u0633\u0648 \u0644\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0628\u0646\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0628\u0646\u0627\u0621 \u062a\u0637\u0628\u064a\u0642",
            "\u0645\u0634\u0631\u0648\u0639 \u0643\u0627\u0645\u0644",
            "\u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u0643\u0627\u0645\u0644",
            "\u0627\u0644\u0645\u0634\u0631\u0648\u0639 \u0643\u0627\u0645\u0644",
            "\u0643\u0644 \u0627\u0644\u0645\u0634\u0631\u0648\u0639",
            "\u062c\u0645\u064a\u0639 \u0627\u0644\u0645\u0644\u0641\u0627\u062a",
            "\u0623\u0639\u062f \u0647\u064a\u0643\u0644\u0629",
            "\u0627\u0639\u062f \u0647\u064a\u0643\u0644\u0629",
            "\u062a\u0631\u062d\u064a\u0644 \u0642\u0627\u0639\u062f\u0629 \u0627\u0644\u0628\u064a\u0627\u0646\u0627\u062a",
            "\u0627\u0628\u0646 apk", "\u0628\u0646\u0627\u0621 apk",
        )

        private val BROAD_SCOPE_TERMS = setOf(
            "every screen", "all screens", "all modules", "many files", "multiple files",
            "project wide", "whole project", "across the project", "across modules",
            "\u0643\u0644 \u0627\u0644\u0634\u0627\u0634\u0627\u062a",
            "\u062c\u0645\u064a\u0639 \u0627\u0644\u0634\u0627\u0634\u0627\u062a",
            "\u0643\u0644 \u0627\u0644\u0645\u0644\u0641\u0627\u062a",
            "\u062c\u0645\u064a\u0639 \u0627\u0644\u0645\u0644\u0641\u0627\u062a",
            "\u0643\u0644 \u0627\u0644\u0645\u0634\u0631\u0648\u0639",
        )

        private val UI_SCOPE_TERMS = setOf(
            "screen", "page", "button", "card", "dialog", "sheet", "toolbar", "navbar",
            "layout", "padding", "margin", "icon", "label", "text field", "textfield",
            "\u0634\u0627\u0634\u0629", "\u0627\u0644\u0634\u0627\u0634\u0629",
            "\u0635\u0641\u062d\u0629", "\u0627\u0644\u0635\u0641\u062d\u0629",
            "\u0632\u0631", "\u0627\u0644\u0632\u0631", "\u0628\u0637\u0627\u0642\u0629",
            "\u0646\u0627\u0641\u0630\u0629", "\u0648\u0627\u062c\u0647\u0629",
            "\u0627\u0644\u0648\u0627\u062c\u0647\u0629", "\u0623\u064a\u0642\u0648\u0646\u0629",
            "\u0627\u064a\u0642\u0648\u0646\u0629", "\u0644\u0648\u0646",
        )

        private val LIGHT_EDIT_TERMS = setOf(
            "rename", "move", "align", "center", "resize", "spacing", "font", "color",
            "hide", "show", "remove", "delete button", "add button",
            "\u063a\u064a\u0631 \u0627\u0644\u0644\u0648\u0646", "\u063a\u064a\u0651\u0631 \u0627\u0644\u0644\u0648\u0646",
            "\u062d\u0631\u0643", "\u062d\u0631\u0651\u0643", "\u0648\u0633\u0637", "\u0648\u0633\u0651\u0637",
            "\u0635\u063a\u0631", "\u0635\u063a\u0651\u0631", "\u0643\u0628\u0631", "\u0643\u0628\u0651\u0631",
            "\u0627\u062d\u0630\u0641", "\u0623\u0636\u0641 \u0632\u0631", "\u0627\u0636\u0641 \u0632\u0631",
        )

        private val STRONG_BUG_TERMS = setOf(
            "stacktrace", "stack trace", "exception", "compile error", "compiler error",
            "build failed", "build failure", "segfault", "nullpointerexception",
            "illegalstateexception", "anr", "fatal exception", "crash loop",
            "\u0643\u0631\u0627\u0634", "\u0627\u0633\u062a\u062b\u0646\u0627\u0621",
            "\u062e\u0637\u0623 \u062a\u062c\u0645\u064a\u0639", "\u0641\u0634\u0644 \u0627\u0644\u0628\u0646\u0627\u0621",
        )

        private val CONTEXTUAL_BUG_TERMS = setOf(
            "bug", "crash", "error", "failed", "failure", "broken", "not working",
            "doesn't work", "does not work", "fix", "repair",
            "\u062e\u0637\u0623", "\u0627\u062e\u0637\u0627\u0621", "\u0623\u062e\u0637\u0627\u0621",
            "\u062a\u0639\u0637\u0644", "\u0641\u0634\u0644", "\u0627\u0635\u0644\u062d", "\u0623\u0635\u0644\u062d",
            "\u0645\u0634\u0643\u0644\u0629", "\u0644\u0627 \u064a\u0639\u0645\u0644", "\u0645\u0627 \u064a\u0639\u0645\u0644",
            "\u064a\u0639\u0644\u0642", "\u0628\u0637\u064a\u0621", "\u0628\u0637\u064a\u0626",
            "\u062d\u0644 \u0627\u0644\u0645\u0634\u0643\u0644\u0629",
        )

        private val CODE_EDIT_TERMS = setOf(
            "edit", "change", "modify", "refactor", "implement", "add feature", "update",
            "rename", "move", "resize", "align", "remove", "delete", "add",
            "\u0639\u062f\u0644", "\u062a\u0639\u062f\u064a\u0644", "\u063a\u064a\u0631", "\u063a\u064a\u0651\u0631",
            "\u0627\u0636\u0641", "\u0623\u0636\u0641", "\u0646\u0641\u0630", "\u0646\u0641\u0651\u0630",
            "\u0627\u062d\u0630\u0641", "\u062d\u0631\u0643", "\u062d\u0631\u0651\u0643", "\u0648\u0633\u0637", "\u0648\u0633\u0651\u0637",
            "\u0635\u063a\u0631", "\u0635\u063a\u0651\u0631", "\u0643\u0628\u0631", "\u0643\u0628\u0651\u0631",
        )

        private val EXPLANATION_TERMS = setOf(
            "explain", "summarize", "what does", "how does", "review this",
            "\u0627\u0634\u0631\u062d", "\u0644\u062e\u0635", "\u0645\u0644\u062e\u0635",
            "\u0648\u0634 \u064a\u0639\u0646\u064a", "\u0643\u064a\u0641 \u064a\u0639\u0645\u0644",
            "\u0641\u0633\u0631", "\u0641\u0633\u0651\u0631",
        )

        private val FACTUAL_QUERY_TERMS = setOf(
            "who is ", "who was ", "when did ", "when was ", "what year ",
            "where is ", "where was ", "how many ", "how much ", "what is ", "what are ",
            "\u0645\u0646 \u0647\u0648", "\u0645\u0646 \u0647\u064a", "\u0645\u062a\u0649",
            "\u0623\u064a\u0646", "\u0627\u064a\u0646", "\u0643\u0645 ",
            "\u0645\u0627 \u0647\u0648", "\u0645\u0627 \u0647\u064a", "\u0648\u0634 \u0647\u0648", "\u0648\u0634 \u0647\u064a",
        )

        private val TECHNICAL_CONTEXT_TERMS = setOf(
            "android", "kotlin", "java", "jetpack", "compose", "gradle", "adb", "apk",
            "ios", "swift", "swiftui", "uikit", "xcode", "objective-c", "macos",
            "windows", "winui", "wpf", ".net", "c#", "c++", "cpp", "powershell",
            "javascript", "typescript", "react", "next.js", "nextjs", "vue", "angular",
            "flutter", "dart", "react native", "node.js", "nodejs", "python", "django",
            "fastapi", "golang", "rust", "php", "laravel", "spring", "ktor",
            "sql", "postgres", "postgresql", "mysql", "sqlite", "supabase", "firebase",
            "api", "endpoint", "http", "https", "json", "xml", "graphql", "websocket",
            "oauth", "authentication", "authorization", "jwt", "token", "cookie",
            "github", "git ", "repository", "repo", "codebase", "source code", "code",
            "compiler", "compile", "runtime", "dependency", "dependencies", "sdk", "ndk",
            "framework", "library", "package", "module", "function", "method", "class",
            "interface", "coroutine", "thread", "async", "await", "promise", "callback",
            "database", "migration", "schema", "query", "server", "backend", "frontend",
            "docker", "kubernetes", "ci/cd", "pipeline", "build", "lint", "unit test",
            "stack trace", "stacktrace", "exception", "debugger", "debug", "logcat",
            "\u0627\u0646\u062f\u0631\u0648\u064a\u062f", "\u0643\u0648\u062a\u0644\u0646", "\u062c\u0627\u0641\u0627",
            "\u0633\u0648\u064a\u0641\u062a", "\u0627\u064a\u0641\u0648\u0646", "\u0622\u064a\u0641\u0648\u0646",
            "\u0648\u064a\u0646\u062f\u0648\u0632", "\u0628\u0631\u0645\u062c\u0629", "\u0643\u0648\u062f", "\u0627\u0644\u0643\u0648\u062f",
            "\u062a\u0637\u0628\u064a\u0642", "\u0627\u0644\u062a\u0637\u0628\u064a\u0642", "\u0645\u0634\u0631\u0648\u0639",
            "\u0627\u0644\u0645\u0634\u0631\u0648\u0639", "\u0645\u0633\u062a\u0648\u062f\u0639", "\u0627\u0644\u0645\u0633\u062a\u0648\u062f\u0639",
            "\u062f\u0627\u0644\u0629", "\u0643\u0644\u0627\u0633", "\u0642\u0627\u0639\u062f\u0629 \u0628\u064a\u0627\u0646\u0627\u062a",
            "\u062e\u0627\u062f\u0645", "\u0633\u064a\u0631\u0641\u0631", "\u0627\u0644\u0628\u0646\u0627\u0621",
        )

        private val CODE_SHAPE_REGEXES = listOf(
            Regex("\\b(fun|class|interface|object|val|var|const|let|def|async|await)\\s+[a-zA-Z_][a-zA-Z0-9_]*"),
            Regex("[a-zA-Z_][a-zA-Z0-9_]*\\s*\\([^\\n]{0,120}\\)\\s*\\{"),
            Regex("\\b(import|package|using|namespace|include)\\s+[a-zA-Z0-9_.<>/]+"),
        )
    }
}

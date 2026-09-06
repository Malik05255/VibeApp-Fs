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
 * Fast deterministic classifier used before model selection.
 *
 * It intentionally does not call an LLM: routing must still work when the
 * network is unavailable or every cloud provider is rate-limited.
 *
 * A crucial invariant is that ordinary human language must not accidentally
 * become a software-engineering task. Words such as "problem", "change" or
 * their Arabic equivalents are common in everyday conversation, so they only
 * carry technical meaning when the turn also contains technical context.
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

        // Project-wide work is complex only when the user actually asks for project
        // execution (or the caller explicitly requires tools). Merely mentioning
        // Gradle, authentication or architecture in a question must stay interactive.
        val complex = toolsRequired || containsAny(text, COMPLEX_EXECUTION_TERMS)
        if (complex) {
            return AiTaskProfile(
                kind = AiTaskKind.PROJECT_COMPLEX,
                requiresProjectTools = toolsRequired || toolsAvailable,
                complexity = 5,
            )
        }

        val strongBugSignal = containsAny(text, STRONG_BUG_TERMS)
        val contextualBugSignal = technicalContext && containsAny(text, CONTEXTUAL_BUG_TERMS)
        if (strongBugSignal || contextualBugSignal) {
            return AiTaskProfile(
                kind = AiTaskKind.BUG_FIX,
                requiresProjectTools = toolsAvailable,
                complexity = 4,
            )
        }

        if (technicalContext && containsAny(text, CODE_EDIT_TERMS)) {
            return AiTaskProfile(
                kind = AiTaskKind.CODE_EDIT,
                requiresProjectTools = toolsAvailable,
                complexity = 3,
            )
        }

        // Stronger cloud/reasoning routes are useful for technical explanation, but
        // ordinary factual questions and everyday explanations intentionally remain
        // LIGHT_CHAT so Mohammed's ready on-device model can answer without a cloud
        // quota. This is what makes normal conversation genuinely local-first.
        if (technicalContext && (
                containsAny(text, EXPLANATION_TERMS) ||
                    containsAny(text, FACTUAL_QUERY_TERMS)
            )
        ) {
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
        // Arabic routing keywords are represented with Unicode escapes so the
        // localization audit can continue to reject user-facing Arabic literals
        // in Kotlin while the language-neutral task classifier still supports
        // Arabic input regardless of the selected UI locale.
        private val COMPLEX_EXECUTION_TERMS = setOf(
            "create app", "build app", "new app", "full app", "entire app",
            "build apk", "release apk", "project wide", "whole project",
            "database migration across", "migrate the project",
            "\u0627\u0646\u0634\u0626 \u062a\u0637\u0628\u064a\u0642",
            "\u0623\u0646\u0634\u0626 \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0635\u0646\u0639 \u062a\u0637\u0628\u064a\u0642",
            "\u0633\u0648 \u0644\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0627\u0628\u0646\u064a \u062a\u0637\u0628\u064a\u0642",
            "\u0628\u0646\u0627\u0621 \u062a\u0637\u0628\u064a\u0642",
            "\u0645\u0634\u0631\u0648\u0639 \u0643\u0627\u0645\u0644",
            "\u0627\u0644\u062a\u0637\u0628\u064a\u0642 \u0643\u0627\u0645\u0644",
            "\u0627\u0644\u0645\u0634\u0631\u0648\u0639 \u0643\u0627\u0645\u0644",
            "\u0627\u0628\u0646 apk",
            "\u0628\u0646\u0627\u0621 apk",
        )

        // These signals are technical by themselves and may safely identify a bug
        // without requiring another context keyword.
        private val STRONG_BUG_TERMS = setOf(
            "stacktrace", "stack trace", "exception", "compile error", "compiler error",
            "build failed", "build failure", "segfault", "nullpointerexception",
            "illegalstateexception", "anr", "fatal exception", "crash loop",
            "\u0643\u0631\u0627\u0634", "\u0627\u0633\u062a\u062b\u0646\u0627\u0621",
            "\u062e\u0637\u0623 \u062a\u062c\u0645\u064a\u0639",
            "\u0641\u0634\u0644 \u0627\u0644\u0628\u0646\u0627\u0621",
        )

        // These words are ambiguous in ordinary language. They become a BUG_FIX
        // signal only when the same turn also contains technical context.
        private val CONTEXTUAL_BUG_TERMS = setOf(
            "bug", "crash", "error", "failed", "failure", "broken", "not working",
            "doesn't work", "does not work", "fix", "repair",
            "\u062e\u0637\u0623", "\u0627\u062e\u0637\u0627\u0621",
            "\u0623\u062e\u0637\u0627\u0621", "\u062a\u0639\u0637\u0644",
            "\u0641\u0634\u0644", "\u0627\u0635\u0644\u062d", "\u0623\u0635\u0644\u062d",
            "\u0645\u0634\u0643\u0644\u0629", "\u0644\u0627 \u064a\u0639\u0645\u0644",
            "\u0645\u0627 \u064a\u0639\u0645\u0644",
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

        private val FACTUAL_QUERY_TERMS = setOf(
            "who is ", "who was ", "when did ", "when was ", "what year ",
            "where is ", "where was ", "how many ", "how much ", "what is ", "what are ",
            "\u0645\u0646 \u0647\u0648", "\u0645\u0646 \u0647\u064a",
            "\u0645\u062a\u0649", "\u0623\u064a\u0646", "\u0627\u064a\u0646", "\u0643\u0645 ",
            "\u0645\u0627 \u0647\u0648", "\u0645\u0627 \u0647\u064a",
            "\u0648\u0634 \u0647\u0648", "\u0648\u0634 \u0647\u064a",
        )

        private val TECHNICAL_CONTEXT_TERMS = setOf(
            "android", "kotlin", "java", "jetpack", "compose", "gradle", "adb", "apk",
            "ios", "swift", "swiftui", "uikit", "xcode", "objective-c", "macos",
            "windows", "winui", "wpf", ".net", "c#", "c++", "cpp", "powershell",
            "javascript", "typescript", "react", "next.js", "nextjs", "vue", "angular",
            "flutter", "dart", "react native", "node.js", "nodejs", "python", "django",
            "fastapi", "go ", "golang", "rust", "php", "laravel", "spring", "ktor",
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
            "\u0627\u0646\u062f\u0631\u0648\u064a\u062f", "\u0643\u0648\u062a\u0644\u0646",
            "\u062c\u0627\u0641\u0627", "\u0633\u0648\u064a\u0641\u062a", "\u0627\u064a\u0641\u0648\u0646",
            "\u0622\u064a\u0641\u0648\u0646", "\u0648\u064a\u0646\u062f\u0648\u0632",
            "\u0628\u0631\u0645\u062c\u0629", "\u0643\u0648\u062f", "\u0627\u0644\u0643\u0648\u062f",
            "\u062a\u0637\u0628\u064a\u0642", "\u0627\u0644\u062a\u0637\u0628\u064a\u0642",
            "\u0645\u0634\u0631\u0648\u0639", "\u0627\u0644\u0645\u0634\u0631\u0648\u0639",
            "\u0645\u0633\u062a\u0648\u062f\u0639", "\u0627\u0644\u0645\u0633\u062a\u0648\u062f\u0639",
            "\u062f\u0627\u0644\u0629", "\u0643\u0644\u0627\u0633", "\u0648\u0627\u062c\u0647\u0629",
            "\u0642\u0627\u0639\u062f\u0629 \u0628\u064a\u0627\u0646\u0627\u062a",
            "\u062e\u0627\u062f\u0645", "\u0633\u064a\u0631\u0641\u0631", "\u0627\u0644\u0628\u0646\u0627\u0621",
        )

        private val CODE_SHAPE_REGEXES = listOf(
            Regex("\\b(fun|class|interface|object|val|var|const|let|def|async|await)\\s+[a-zA-Z_][a-zA-Z0-9_]*"),
            Regex("[a-zA-Z_][a-zA-Z0-9_]*\\s*\\([^\\n]{0,120}\\)\\s*\\{"),
            Regex("\\b(import|package|using|namespace|include)\\s+[a-zA-Z0-9_.<>/]+"),
        )
    }
}

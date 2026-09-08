package com.malik.lmai.feature.agent.loop

import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolChoiceMode
import com.malik.lmai.feature.agent.AgentToolDefinition

internal enum class ChatTurnMode {
    CONVERSATION,
    H_ACTION,
    APP_DISCOVERY,
    APP_EXECUTION,
}

/**
 * Intent router for H.
 *
 * Conversation stays lightweight. Daily assistant actions use only H's safe personal tools.
 * Project mutations and project-aware inspection keep using the heavyweight autonomous loop.
 */
internal object ChatTurnPolicy {

    fun detect(request: AgentModelRequest): ChatTurnMode {
        val latestText = latestUserText(request)
        val directMode = detect(latestText)
        if (directMode != ChatTurnMode.CONVERSATION) return directMode

        val normalized = normalize(latestText)
        return if (
            looksLikeExecutionFollowUp(normalized) &&
            recentConversationHasProjectContext(request)
        ) {
            ChatTurnMode.APP_EXECUTION
        } else {
            ChatTurnMode.CONVERSATION
        }
    }

    fun detect(userText: String): ChatTurnMode {
        val normalized = normalize(userText)
        if (normalized.isBlank()) return ChatTurnMode.CONVERSATION

        if (containsAny(normalized, DISCOVERY_ONLY_PHRASES)) {
            return if (containsAny(normalized, PROJECT_CONTEXT_TERMS)) {
                ChatTurnMode.APP_DISCOVERY
            } else {
                ChatTurnMode.CONVERSATION
            }
        }

        if (containsAny(normalized, STRONG_EXECUTION_PHRASES)) {
            return ChatTurnMode.APP_EXECUTION
        }

        val startsWithExecutionCommand = startsWithCommandStem(normalized)
        val hasProjectContext = containsAny(normalized, PROJECT_CONTEXT_TERMS)
        val hasUiMutationContext = containsAny(normalized, UI_MUTATION_TERMS)

        if (startsWithExecutionCommand && (hasProjectContext || hasUiMutationContext)) {
            return ChatTurnMode.APP_EXECUTION
        }

        if (containsAny(normalized, APP_CREATION_INTENT_PHRASES)) {
            return ChatTurnMode.APP_EXECUTION
        }

        if (containsAny(normalized, DISCOVERY_PHRASES) && hasProjectContext) {
            return ChatTurnMode.APP_DISCOVERY
        }

        if (containsAny(normalized, H_ACTION_PHRASES)) {
            return ChatTurnMode.H_ACTION
        }

        return ChatTurnMode.CONVERSATION
    }

    fun actionTools(tools: List<AgentToolDefinition>): List<AgentToolDefinition> =
        tools.filter { it.name in H_ACTION_TOOL_NAMES }

    fun adapt(request: AgentModelRequest): AgentModelRequest {
        val latestText = latestUserText(request)
        val languageInstruction = languageInstruction(latestText)

        return when (detect(request)) {
            ChatTurnMode.CONVERSATION -> request.copy(
                instructions = appendInstructions(
                    request.instructions,
                    buildString {
                        appendLine("## Conversation mode")
                        appendLine("This is a direct human-style conversation, not a work queue and not a project execution turn.")
                        appendLine(languageInstruction)
                        appendLine("Respond to what the user actually said and keep continuity with the ongoing conversation.")
                        appendLine("Never introduce programming, apps, repositories, debugging, or productivity unless the user raises a technical topic first.")
                        appendLine("The user may chat, joke, tell stories, ask about daily life, or vent for a long time. Stay with that conversation instead of steering it toward work.")
                        appendLine("If the user is venting or sharing something personal, respond to the situation first. Do not jump into checklists or fixes unless the user wants a solution.")
                        appendLine("Use natural sentence rhythm and match the user's level of formality and conversational Arabic register when clear.")
                        appendLine("For greetings and small talk, answer naturally. Do not repeatedly introduce yourself or advertise capabilities.")
                        appendLine("A technical question that asks for an explanation is still conversation unless the user asks H to inspect/change/create/fix/build something in the project.")
                        appendLine("Do not call project tools in this mode.")
                        append("Return only the user-facing reply.")
                    },
                ),
                tools = emptyList(),
                policy = request.policy.copy(toolChoiceMode = AgentToolChoiceMode.NONE),
            )

            ChatTurnMode.H_ACTION -> request.copy(
                instructions = appendInstructions(
                    request.instructions,
                    buildString {
                        appendLine("## H personal action mode")
                        appendLine(languageInstruction)
                        appendLine("The user asked H to perform a real everyday assistant action, not project development.")
                        appendLine("Use only the exposed H action tools. Never call build, file, project, UI-inspection, or repository mutation tools in this mode.")
                        appendLine("For reminders use h_reminders and preserve the user's original wording.")
                        appendLine("For WhatsApp actions use peach_whatsapp. Discover Peach tools when necessary and never guess a recipient, conversation, or phone number.")
                        appendLine("For fresh information or recommendations use web_search, then fetch_web_page when verification or page details materially improve the answer.")
                        appendLine("Do not claim you searched all of the internet. State only what you verified from the sources actually checked.")
                        appendLine("Do not invent prices, ratings, opening status, availability, addresses, or tool outcomes.")
                        appendLine("Execute the requested action when enough information is available. Ask one short clarification only when a required detail is genuinely missing.")
                        append("After tool execution, return a concise user-facing result and hide internal tool traces.")
                    },
                ),
                tools = actionTools(request.tools),
                policy = request.policy.copy(
                    maxIterations = minOf(request.policy.maxIterations, H_ACTION_MAX_ITERATIONS),
                    allowParallelToolCalls = false,
                ),
            )

            ChatTurnMode.APP_DISCOVERY -> request.copy(
                instructions = appendInstructions(
                    request.instructions,
                    buildString {
                        appendLine("## App discovery mode")
                        appendLine(languageInstruction)
                        appendLine("The user explicitly wants discussion, planning, comparison, or ideas before execution.")
                        appendLine("Help shape the idea naturally and make useful concrete recommendations.")
                        appendLine("Do not call project tools until the user asks to inspect/implement/build/change/fix the app.")
                        appendLine("Do not repeatedly introduce yourself or advertise capabilities.")
                        append("Return only the useful user-facing discussion.")
                    },
                ),
                tools = emptyList(),
                policy = request.policy.copy(toolChoiceMode = AgentToolChoiceMode.NONE),
            )

            ChatTurnMode.APP_EXECUTION -> request.copy(
                instructions = appendInstructions(
                    request.instructions,
                    buildString {
                        appendLine("## Autonomous project execution mode")
                        appendLine(languageInstruction)
                        appendLine("The user asked for real project-aware work. Execute it with project tools; do not answer with instructions for the user to do manually.")
                        appendLine("Infer short follow-up commands from the recent project context. Do not require magic phrases such as 'build app' or 'modify project'.")
                        appendLine("Do not ask for confirmation when the request is actionable and a reasonable default exists. Ask only for information that is truly blocking.")
                        appendLine("For read-only requests such as summarize/review/inspect, inspect the actual project and answer from it without mutating files or building unnecessarily.")
                        appendLine("For creation or mutation requests use this autonomous loop: inspect the relevant project state -> implement -> review the changed area -> build/test -> diagnose failures -> repair -> rebuild until successful or a genuine external blocker is reached.")
                        appendLine("For creation requests, create the necessary project files rather than merely describing sample code.")
                        appendLine("For modification/debug requests, edit the actual project files rather than only returning a patch or code snippet in chat.")
                        appendLine("After any project-file mutation, run the build pipeline. A build that happened before the latest mutation does not validate the latest state.")
                        appendLine("If a build or project tool fails, inspect the concrete error, fix the cause, and continue the loop. Do not stop at the first failure just to summarize it.")
                        appendLine("Do not claim a requested project mutation is complete until the latest changed state has passed the relevant build. Runtime-verify when the request warrants it.")
                        appendLine("Keep tool traces and internal work hidden from the user. Show only concise user-facing progress/result text.")
                        append("A text-only response is not a valid completion for a requested project mutation.")
                    },
                ),
            )
        }
    }

    private fun latestUserText(request: AgentModelRequest): String =
        request.fullConversation
            .asReversed()
            .firstOrNull { it.role == AgentMessageRole.USER }
            ?.text
            ?: request.conversation
                .asReversed()
                .firstOrNull { it.role == AgentMessageRole.USER }
                ?.text
            ?: ""

    private fun recentConversationHasProjectContext(request: AgentModelRequest): Boolean {
        val source = if (request.fullConversation.isNotEmpty()) {
            request.fullConversation
        } else {
            request.conversation
        }

        val userTexts = source
            .filter { it.role == AgentMessageRole.USER }
            .mapNotNull { it.text }

        return userTexts
            .dropLast(1)
            .takeLast(5)
            .any { previous ->
                val normalized = normalize(previous)
                detect(previous) == ChatTurnMode.APP_EXECUTION ||
                    containsAny(normalized, PROJECT_CONTEXT_TERMS) ||
                    containsAny(normalized, UI_MUTATION_TERMS)
            }
    }

    private fun looksLikeExecutionFollowUp(normalized: String): Boolean {
        if (normalized.isBlank()) return false
        if (containsAny(normalized, FOLLOW_UP_EXECUTION_PHRASES)) return true
        return startsWithCommandStem(normalized)
    }

    private fun startsWithCommandStem(text: String): Boolean {
        val firstToken = text.substringBefore(' ')
        return EXECUTION_COMMAND_STEMS.any { stem ->
            firstToken == stem || (stem.length >= 3 && firstToken.startsWith(stem))
        }
    }

    private fun languageInstruction(text: String): String {
        val containsArabic = text.any { char -> char.code in 0x0600..0x06FF }
        return if (containsArabic) {
            "The user's latest message is Arabic. Reply in natural Arabic and match the user's conversational register when clear, regardless of the app UI language."
        } else {
            "Reply naturally in the same language and conversational register as the user's latest message."
        }
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace("ـ", "")
        .replace(ARABIC_DIACRITICS, "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("[^\\p{L}\\p{N}+#._/-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun appendInstructions(existing: String?, addition: String): String =
        buildString {
            existing
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    append(it.trim())
                    append("\n\n")
                }
            append(addition.trim())
        }

    private fun containsAny(text: String, terms: Set<String>): Boolean =
        terms.any(text::contains)

    private val ARABIC_DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

    private val DISCOVERY_ONLY_PHRASES = setOf(
        "لا تنفذ",
        "لا تنفذ الحين",
        "بدون تنفيذ",
        "فقط فكره",
        "فقط فكرة",
        "خلنا نخطط",
        "خلينا نخطط",
        "ناقش الفكره",
        "ناقش الفكرة",
        "وش رايك",
        "ما رايك",
        "what do you think",
        "do not implement",
        "dont implement",
        "don't implement",
        "no implementation",
        "plan only",
        "brainstorm only",
    ).map(::normalize).toSet()

    private val APP_CREATION_INTENT_PHRASES = setOf(
        "ابي تطبيق",
        "ابغى تطبيق",
        "اريد تطبيق",
        "احتاج تطبيق",
        "سوي لي تطبيق",
        "سو لي تطبيق",
        "ابني لي تطبيق",
        "بناء تطبيق",
        "انشئ تطبيق",
        "اصنع تطبيق",
        "عندي فكرة تطبيق",
        "عندي فكره تطبيق",
        "i want an app",
        "i need an app",
        "build an app",
        "build app",
        "build me an app",
        "create an app",
        "create app",
        "make an app",
        "start building",
    ).map(::normalize).toSet()

    private val STRONG_EXECUTION_PHRASES = setOf(
        "اتصل بالمستودع",
        "انصل بالمستودع",
        "اتصل على المستودع",
        "عدل التطبيق",
        "اصلح التطبيق",
        "طور التطبيق",
        "غير التطبيق",
        "نفذ في المشروع",
        "طبق في المشروع",
        "كمل في المشروع",
        "اصلح المشروع",
        "عدل المشروع",
        "حدث المشروع",
        "ابن المشروع",
        "شغل البناء",
        "اختبر التطبيق",
        "ابن apk",
        "build the project",
        "fix the app",
        "edit the app",
        "update the app",
        "change the app",
        "modify the project",
        "fix the project",
        "update the project",
        "apply this to the project",
        "apply this to the repository",
        "connect to the repository",
        "connect to repo",
        "run the build",
        "build the apk",
    ).map(::normalize).toSet()

    private val DISCOVERY_PHRASES = setOf(
        "ساعدني اخطط",
        "اقترح لي",
        "اعطني افكار",
        "وش افضل طريقه",
        "كيف ممكن نصمم",
        "help me plan",
        "give me ideas",
        "brainstorm",
        "compare approaches",
    ).map(::normalize).toSet()

    private val H_ACTION_PHRASES = setOf(
        "ذكرني",
        "تذكيراتي",
        "التذكيرات",
        "اعرض التذكيرات",
        "ارسل رسالة",
        "ارسل رساله",
        "ابعث رسالة",
        "ابعث رساله",
        "ارسل واتساب",
        "ارسل على واتساب",
        "واتساب",
        "واتس اب",
        "ابحث لي",
        "ابحث عن",
        "دور لي",
        "شوف لي",
        "شيك لي",
        "تحقق لي",
        "افحص لي",
        "remind me",
        "my reminders",
        "send a message",
        "send whatsapp",
        "whatsapp",
        "find me",
        "search for",
        "look up",
        "check for",
    ).map(::normalize).toSet()

    private val FOLLOW_UP_EXECUTION_PHRASES = setOf(
        "كمل",
        "اكمل",
        "نفذ",
        "طبقها",
        "سوها",
        "سويها",
        "نفسه لكن",
        "نفسها لكن",
        "خله كذا",
        "خليها كذا",
        "go ahead",
        "do it",
        "continue",
        "apply it",
    ).map(::normalize).toSet()

    private val EXECUTION_COMMAND_STEMS = setOf(
        "انشئ", "اصنع", "ابن", "ابني", "سوي", "سويها", "سو", "سوها", "سوه", "سووه",
        "صمم", "عدل", "اصلح", "غير", "خل", "خلها", "خله", "خلي", "خليها", "اضف", "ضيف",
        "احذف", "شيل", "ارفع", "نزل", "حرك", "كبر", "صغر", "رتب", "نسق", "طور", "طبق",
        "نفذ", "اربط", "اتصل", "انصل", "اكمل", "كمل", "اختبر", "شغل", "ابدا", "حدث",
        "لخص", "راجع", "افحص", "حلل", "ابحث", "دور",
        "create", "build", "implement", "modify", "repair", "redesign", "apply", "connect",
        "fix", "edit", "update", "change", "add", "remove", "delete", "move", "resize",
        "continue", "run", "test", "install", "develop", "refactor", "summarize", "review",
        "inspect", "analyze", "search",
    ).map(::normalize).toSet()

    private val PROJECT_CONTEXT_TERMS = setOf(
        "تطبيق", "المشروع", "مشروع", "المستودع", "مستودع", "الريبو", "ريبو", "github",
        "الكود", "كود", "ملف", "الملف", "اندرويد", "android", "kotlin", "java", "xml",
        "compose", "gradle", "build", "apk", "api", "database", "قاعدة البيانات",
        "repository", "repo", "project", "app", "application", "codebase", "source",
    ).map(::normalize).toSet()

    private val UI_MUTATION_TERMS = setOf(
        "شاشه", "الشاشه", "واجهة", "الواجهه", "زر", "الزر", "ايقونه", "ايقونة", "الايقونه",
        "لون", "اللون", "خط", "الخط", "صوره", "الصوره", "شعار", "الشعار", "بطاقه", "بطاقة",
        "قائمه", "قائمة", "حقل", "مسافه", "مسافة", "حواف", "دائره", "دائرة", "شفافيه", "شفافية",
        "layout", "screen", "button", "icon", "color", "font", "image", "logo", "card", "menu",
        "field", "spacing", "padding", "margin", "radius", "opacity", "ui", "ux",
    ).map(::normalize).toSet()

    private val H_ACTION_TOOL_NAMES = setOf(
        "h_reminders",
        "peach_whatsapp",
        "web_search",
        "fetch_web_page",
    )

    private const val H_ACTION_MAX_ITERATIONS = 8
}

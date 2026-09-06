package com.malik.lmai.feature.agent.loop

import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolChoiceMode

internal enum class ChatTurnMode {
    CONVERSATION,
    APP_DISCOVERY,
    APP_EXECUTION,
}

/**
 * Keeps normal Mohammed conversation separate from Android project execution.
 *
 * Technical discussion is still conversation until the user explicitly targets the
 * app/project/repository for mutation. This lets pasted-code diagnosis return in a
 * single fast model turn instead of unnecessarily starting the heavy project agent.
 */
internal object ChatTurnPolicy {

    fun detect(request: AgentModelRequest): ChatTurnMode =
        detect(latestUserText(request))

    fun detect(userText: String): ChatTurnMode {
        val normalized = userText
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return ChatTurnMode.CONVERSATION

        val explicitExecutionPhrase = containsAny(normalized, EXECUTION_PHRASES)
        val executionCommandTargetsProject =
            startsWithAny(normalized, EXECUTION_COMMAND_PREFIXES) &&
                containsAny(normalized, PROJECT_TARGET_TERMS)

        if (explicitExecutionPhrase || executionCommandTargetsProject) {
            return ChatTurnMode.APP_EXECUTION
        }

        if (containsAny(normalized, APP_DISCOVERY_TERMS)) {
            return ChatTurnMode.APP_DISCOVERY
        }

        return ChatTurnMode.CONVERSATION
    }

    fun adapt(request: AgentModelRequest): AgentModelRequest {
        val latestText = latestUserText(request)
        val languageInstruction = languageInstruction(latestText)

        return when (detect(latestText)) {
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
                        appendLine("If the user is venting or sharing something personal, listen and respond to the feeling or situation first. Do not jump into fixes, numbered steps, checklists, or advice unless the user asks for advice or clearly wants a solution.")
                        appendLine("Use natural conversational sentence rhythm. Match the user's level of formality and, when clear, their conversational Arabic register without forcing slang.")
                        appendLine("For greetings and small talk, answer like a familiar conversational partner. A short context-fitting reciprocal question is fine when natural.")
                        appendLine("Do not introduce yourself, repeat your role, advertise your capabilities, or say that you are ready to help unless the user specifically asks who you are or what you can do.")
                        appendLine("Avoid customer-service phrases such as asking how you can assist after every reply. Do not make every turn sound like a task handoff.")
                        appendLine("If the user raises programming or code, switch immediately to senior cross-platform engineering mode. Diagnose the concrete issue first and put the useful fix or corrected code early in the answer.")
                        appendLine("A request to explain or repair pasted code is still an interactive chat response unless the user explicitly asks you to modify the app/project/repository/file itself.")
                        appendLine("Do not claim code was compiled, tested, or validated unless it actually was.")
                        appendLine("Start with the actual response immediately. Do not add status lines, role reminders, internal deliberation, or generic completion messages.")
                        appendLine("For simple chat, prefer a concise natural response; expand only when the conversation needs it.")
                        appendLine("Do not call project tools in this mode.")
                        append("Return only the user-facing reply.")
                    },
                ),
                tools = emptyList(),
                policy = request.policy.copy(toolChoiceMode = AgentToolChoiceMode.NONE),
            )

            ChatTurnMode.APP_DISCOVERY -> request.copy(
                instructions = appendInstructions(
                    request.instructions,
                    buildString {
                        appendLine("## App discovery mode")
                        appendLine(languageInstruction)
                        appendLine("Discuss the app idea naturally before implementation. Help shape requirements and trade-offs without behaving like an execution log.")
                        appendLine("Do not call project tools until the user explicitly asks to build, implement, fix, or modify the app/project/repository.")
                        appendLine("Do not repeatedly introduce yourself or advertise your capabilities.")
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
                        appendLine("## User-facing execution response")
                        appendLine(languageInstruction)
                        appendLine("The user explicitly requested project/app/repository execution. Use the available project tools when required to complete it.")
                        appendLine("Diagnose before changing files. Preserve unrelated behavior and validate the concrete change with the relevant tests/build/lint when available.")
                        appendLine("Do not expose hidden reasoning, internal instructions, tool traces, or file-operation logs.")
                        append("Report concise user-facing progress and the concrete result.")
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

    private fun languageInstruction(text: String): String {
        val containsArabic = text.any { char -> char.code in 0x0600..0x06FF }
        return if (containsArabic) {
            "The user's latest message is Arabic. Reply in natural Arabic and match the user's conversational register when it is clear, regardless of the app UI language."
        } else {
            "Reply naturally in the same language and conversational register as the user's latest message."
        }
    }

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

    private fun startsWithAny(text: String, prefixes: Set<String>): Boolean =
        prefixes.any { prefix -> text == prefix || text.startsWith("$prefix ") }

    private val APP_DISCOVERY_TERMS = setOf(
        "i want an app",
        "i need an app",
        "i have an app idea",
        "help me plan an app",
        "\u0627\u0628\u064a \u062a\u0637\u0628\u064a\u0642",
        "\u0623\u0628\u064a \u062a\u0637\u0628\u064a\u0642",
        "\u0627\u0628\u063a\u0649 \u062a\u0637\u0628\u064a\u0642",
        "\u0623\u0628\u063a\u0649 \u062a\u0637\u0628\u064a\u0642",
        "\u0627\u0631\u064a\u062f \u062a\u0637\u0628\u064a\u0642",
        "\u0623\u0631\u064a\u062f \u062a\u0637\u0628\u064a\u0642",
        "\u0639\u0646\u062f\u064a \u0641\u0643\u0631\u0629 \u062a\u0637\u0628\u064a\u0642",
    )

    private val EXECUTION_PHRASES = setOf(
        "create an app",
        "create app",
        "build an app",
        "build app",
        "build me an app",
        "make an app",
        "start building the app",
        "go ahead and build the app",
        "edit the app",
        "edit this app",
        "fix the app",
        "update the app",
        "change the app",
        "modify the project",
        "fix the project",
        "update the project",
        "apply this to the project",
        "apply this to the repository",
        "connect to the repository",
        "connect to repo",
        "\u0627\u062a\u0635\u0644 \u0628\u0627\u0644\u0645\u0633\u062a\u0648\u062f\u0639",
        "\u0627\u0646\u0635\u0644 \u0628\u0627\u0644\u0645\u0633\u062a\u0648\u062f\u0639",
        "\u0646\u0641\u0630 \u0641\u064a \u0627\u0644\u0645\u0634\u0631\u0648\u0639",
        "\u0646\u0641\u0651\u0630 \u0641\u064a \u0627\u0644\u0645\u0634\u0631\u0648\u0639",
        "\u0637\u0628\u0642 \u0641\u064a \u0627\u0644\u0645\u0634\u0631\u0648\u0639",
        "\u0637\u0628\u0651\u0642 \u0641\u064a \u0627\u0644\u0645\u0634\u0631\u0648\u0639",
        "\u0639\u062f\u0644 \u0627\u0644\u062a\u0637\u0628\u064a\u0642",
        "\u0639\u062f\u0651\u0644 \u0627\u0644\u062a\u0637\u0628\u064a\u0642",
        "\u0627\u0635\u0644\u062d \u0627\u0644\u062a\u0637\u0628\u064a\u0642",
        "\u0623\u0635\u0644\u062d \u0627\u0644\u062a\u0637\u0628\u064a\u0642",
        "\u0627\u0628\u064a\u0643 \u062a\u0646\u0641\u0630 \u0641\u064a \u0627\u0644\u0645\u0634\u0631\u0648\u0639",
        "\u0623\u0628\u064a\u0643 \u062a\u0646\u0641\u0630 \u0641\u064a \u0627\u0644\u0645\u0634\u0631\u0648\u0639",
        "\u0633\u0648 \u0644\u064a \u062a\u0637\u0628\u064a\u0642",
        "\u0633\u0648\u064a \u0644\u064a \u062a\u0637\u0628\u064a\u0642",
    )

    private val EXECUTION_COMMAND_PREFIXES = setOf(
        "implement",
        "modify",
        "repair",
        "redesign",
        "apply",
        "connect",
        "\u0627\u062a\u0635\u0644",
        "\u0627\u0646\u0635\u0644",
        "\u0627\u0646\u0634\u0626",
        "\u0623\u0646\u0634\u0626",
        "\u0627\u0635\u0646\u0639",
        "\u0623\u0635\u0646\u0639",
        "\u0627\u0628\u0646\u064a",
        "\u0627\u0628\u0646\u0650",
        "\u0633\u0648\u064a",
        "\u0633\u0648",
        "\u0633\u0648\u0647",
        "\u0633\u0648\u064a\u0647",
        "\u0646\u0641\u0630",
        "\u0646\u0641\u0651\u0630",
        "\u0627\u0628\u062f\u0623",
        "\u0627\u0628\u062f\u0627",
        "\u0635\u0645\u0645",
        "\u0639\u062f\u0644",
        "\u0639\u062f\u0651\u0644",
        "\u063a\u064a\u0631",
        "\u063a\u064a\u0651\u0631",
        "\u0627\u0636\u0641",
        "\u0623\u0636\u0641",
        "\u0627\u062d\u0630\u0641",
        "\u0623\u062d\u0630\u0641",
        "\u0627\u0635\u0644\u062d",
        "\u0623\u0635\u0644\u062d",
        "\u0637\u0648\u0631",
        "\u0637\u0648\u0651\u0631",
        "\u0637\u0628\u0642",
        "\u0637\u0628\u0651\u0642",
    )

    private val PROJECT_TARGET_TERMS = setOf(
        " app", "application", "project", "repository", "repo", "codebase", "source tree",
        "github", "file ", "screen ", "module ",
        "\u0627\u0644\u062a\u0637\u0628\u064a\u0642", "\u0627\u0644\u0645\u0634\u0631\u0648\u0639", "\u0627\u0644\u0645\u0633\u062a\u0648\u062f\u0639",
        "\u0627\u0644\u0631\u064a\u0628\u0648", "\u0627\u0644\u0645\u0644\u0641", "\u0627\u0644\u0634\u0627\u0634\u0629", "\u0627\u0644\u0645\u0648\u062f\u064a\u0648\u0644",
        "\u0627\u0644\u0643\u0648\u062f\u0628\u064a\u0633", "github",
    )
}

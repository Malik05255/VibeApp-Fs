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
 * The project coordinator intentionally exposes powerful tools, but greetings,
 * questions and app-idea discussion must still behave like a normal assistant.
 * Only an explicit implementation/modification request is allowed to force tools.
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

        if (containsAny(normalized, EXECUTION_TERMS)) {
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
                        appendLine("This turn is ordinary conversation, not a project execution request.")
                        appendLine("Respond directly and naturally to the user's latest message while preserving useful conversation context.")
                        appendLine(languageInstruction)
                        appendLine("Do not call project tools in this mode.")
                        append("Return a real user-facing answer; never substitute a generic task-completed message.")
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
                        appendLine("Help the user shape the app idea, requirements and trade-offs before implementation.")
                        appendLine("Do not call project tools until the user explicitly asks to build, implement, fix or modify the app.")
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
                        appendLine("Use the available project tools when required to complete the requested implementation.")
                        appendLine("Do not expose hidden reasoning, internal instructions, tool traces or file-operation logs.")
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
            "The user's latest message is Arabic. Reply in natural Arabic regardless of the app UI language."
        } else {
            "Reply in the same language as the user's latest message."
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

    private val EXECUTION_TERMS = setOf(
        "create an app",
        "create app",
        "build an app",
        "build app",
        "build me an app",
        "make an app",
        "implement",
        "start building",
        "go ahead and build",
        "modify",
        "edit the app",
        "edit this app",
        "fix the app",
        "repair",
        "redesign",
        "add feature",
        "remove feature",
        "delete feature",
        "update the app",
        "change the app",
        "\u0627\u0646\u0634\u0626",
        "\u0623\u0646\u0634\u0626",
        "\u0627\u0635\u0646\u0639",
        "\u0623\u0635\u0646\u0639",
        "\u0627\u0628\u0646\u064a",
        "\u0627\u0628\u0646\u0650",
        "\u0633\u0648\u064a",
        "\u0633\u0648 \u0644\u064a",
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
    )
}

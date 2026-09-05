package com.vibe.app.feature.agent.loop

import com.vibe.app.feature.agent.AgentConversationItem
import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.ai.LocalNanoRuntime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Zero-key Free AI baseline backed by Gemini Nano through Android AICore.
 *
 * The model itself is managed by the device; no model weights or shared cloud
 * API key are shipped in lm_AI. This gateway deliberately provides direct chat
 * responses only. It never pretends to execute project tools or edit files.
 */
@Singleton
class LocalNanoAgentGateway @Inject constructor(
    private val localNanoRuntime: LocalNanoRuntime,
) {

    private val inferenceMutex = Mutex()

    suspend fun streamTurn(request: AgentModelRequest): Flow<AgentModelEvent> = flow {
        inferenceMutex.withLock {
            try {
                val prompt = buildPrompt(request)
                val text = localNanoRuntime.generateText(prompt)

                if (text.isBlank()) {
                    emit(
                        AgentModelEvent.Failed(
                            "The on-device AI returned an empty response."
                        )
                    )
                    return@withLock
                }

                emit(AgentModelEvent.OutputDelta(text))
                emit(AgentModelEvent.Completed(finalText = text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(
                    AgentModelEvent.Failed(
                        e.message?.takeIf { it.isNotBlank() }
                            ?: "The on-device AI is not available on this device."
                    )
                )
            }
        }
    }

    /**
     * Builds a bounded prompt without ever trimming from the front of the system
     * instructions. The previous implementation called takeLast() on the whole
     * prompt, which could silently delete the assistant policy and user-provided
     * instructions when history became large.
     */
    internal fun buildPrompt(request: AgentModelRequest): String {
        val history = request.fullConversation
            .ifEmpty { request.conversation }
            .takeLast(MAX_HISTORY_ITEMS)

        val header = buildString {
            appendLine(
                "You are lm_AI's on-device fallback assistant. Answer the user directly and concisely."
            )
            appendLine(
                "You cannot execute project tools, edit files, build APKs, access the network, or claim that you performed actions."
            )
            appendLine(
                "If the request requires those capabilities, explain what can be done locally and say that a cloud/API provider is needed for execution."
            )

            request.instructions
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { instructions ->
                    appendLine()
                    appendLine("Instructions:")
                    appendLine(instructions.take(MAX_INSTRUCTIONS_CHARS))
                }

            if (request.tools.isNotEmpty()) {
                appendLine()
                appendLine(
                    "Project tools exist in the full agent, but local fallback cannot invoke them. Do not fail solely because tools are requested; answer conversationally instead."
                )
            }
        }

        val footer = "\nRespond to the latest user request."
        val conversationHeader = if (history.isNotEmpty()) "\nConversation:\n" else ""
        val availableHistoryChars = (
            MAX_PROMPT_CHARS - header.length - footer.length - conversationHeader.length
        ).coerceAtLeast(0)

        val selectedLines = ArrayDeque<String>()
        var remaining = availableHistoryChars

        for (item in history.asReversed()) {
            if (remaining <= 0) break

            val text = conversationText(item)
            if (text.isBlank()) continue

            val prefix = "${roleLabel(item.role)}: "
            val maxTextChars = (remaining - prefix.length - 1)
                .coerceAtMost(MAX_ITEM_CHARS)
            if (maxTextChars <= 0) break

            val line = prefix + text.take(maxTextChars) + "\n"
            selectedLines.addFirst(line)
            remaining -= line.length
        }

        return buildString {
            append(header)
            if (selectedLines.isNotEmpty()) {
                append(conversationHeader)
                selectedLines.forEach { line -> append(line) }
            }
            append(footer)
        }.take(MAX_PROMPT_CHARS)
    }

    private fun conversationText(item: AgentConversationItem): String =
        item.text
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: item.payload?.toString().orEmpty()

    private fun roleLabel(role: AgentMessageRole): String = when (role) {
        AgentMessageRole.SYSTEM -> "System"
        AgentMessageRole.USER -> "User"
        AgentMessageRole.ASSISTANT -> "Assistant"
        AgentMessageRole.TOOL -> "Tool"
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 16
        private const val MAX_ITEM_CHARS = 1_500
        private const val MAX_INSTRUCTIONS_CHARS = 2_500
        private const val MAX_PROMPT_CHARS = 14_000
    }
}

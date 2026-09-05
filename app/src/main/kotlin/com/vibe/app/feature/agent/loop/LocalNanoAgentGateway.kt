package com.vibe.app.feature.agent.loop

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.generationConfig
import com.vibe.app.feature.agent.AgentConversationItem
import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Zero-key Free AI baseline backed by Gemini Nano through Android AICore.
 *
 * The model itself is managed by the device; no model weights or shared cloud
 * API key are shipped in lm_AI. This gateway deliberately provides direct chat
 * responses only. It never pretends to execute project tools or edit files.
 *
 * The shared agent loop may mark the first turn as tool-required whenever project
 * tools are registered. Local Nano cannot call those tools, so this gateway
 * intentionally treats that policy as chat-only instead of surfacing an Agent
 * Error. Execution requests are answered with an explanation that a cloud/API
 * provider is required, while normal questions continue to work locally.
 */
@Singleton
class LocalNanoAgentGateway @Inject constructor() {

    private val inferenceMutex = Mutex()

    private val model by lazy {
        Generation.getClient(generationConfig {})
    }

    suspend fun streamTurn(request: AgentModelRequest): Flow<AgentModelEvent> = flow {
        inferenceMutex.withLock {
            try {
                ensureModelReady()

                val prompt = buildPrompt(request)
                val response = model.generateContent(prompt)
                val text = response.candidates
                    .map { it.text }
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()

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

    private suspend fun ensureModelReady() {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> return
            FeatureStatus.UNAVAILABLE -> throw IllegalStateException(
                "Gemini Nano is not available on this device."
            )
            else -> {
                var completed = false

                model.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadFailed -> throw status.e
                        is DownloadStatus.DownloadCompleted -> completed = true
                        else -> Unit
                    }
                }

                if (!completed && model.checkStatus() != FeatureStatus.AVAILABLE) {
                    throw IllegalStateException(
                        "Gemini Nano could not be prepared on this device."
                    )
                }
            }
        }
    }

    private fun buildPrompt(request: AgentModelRequest): String {
        val history = request.fullConversation
            .ifEmpty { request.conversation }
            .takeLast(MAX_HISTORY_ITEMS)

        return buildString {
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

            if (history.isNotEmpty()) {
                appendLine()
                appendLine("Conversation:")
                history.forEach { item ->
                    val text = conversationText(item)
                    if (text.isNotBlank()) {
                        append(roleLabel(item.role))
                        append(": ")
                        appendLine(text.take(MAX_ITEM_CHARS))
                    }
                }
            }

            appendLine()
            append("Respond to the latest user request.")
        }.takeLast(MAX_PROMPT_CHARS)
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

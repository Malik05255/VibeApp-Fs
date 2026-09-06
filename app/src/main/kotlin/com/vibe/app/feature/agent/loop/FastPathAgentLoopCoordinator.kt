package com.vibe.app.feature.agent.loop

import com.vibe.app.data.database.entity.MessageV2
import com.vibe.app.feature.agent.AgentConversationItem
import com.vibe.app.feature.agent.AgentLoopCoordinator
import com.vibe.app.feature.agent.AgentLoopEvent
import com.vibe.app.feature.agent.AgentLoopRequest
import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolChoiceMode
import com.vibe.app.feature.ai.AiTaskClassifier
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Keeps ordinary conversation off the heavyweight Android build-agent path.
 *
 * Greetings, questions and explanations make exactly one model turn with no
 * project tools, no build schemas and no project snapshot preparation. Explicit
 * app/code/repair requests are delegated to the full DefaultAgentLoopCoordinator.
 */
@Singleton
class FastPathAgentLoopCoordinator @Inject constructor(
    private val defaultCoordinator: DefaultAgentLoopCoordinator,
    private val agentModelGateway: AgentModelGateway,
    private val taskClassifier: AiTaskClassifier,
) : AgentLoopCoordinator {

    override suspend fun run(request: AgentLoopRequest): Flow<AgentLoopEvent> {
        val latestUserText = request.userMessages.lastOrNull()?.content.orEmpty()
        val task = taskClassifier.classifyText(
            latestUserText = latestUserText,
            toolsAvailable = request.tools.isNotEmpty(),
            toolsRequired = request.policy.toolChoiceMode == AgentToolChoiceMode.REQUIRED,
        )

        return if (task.requiresProjectTools) {
            defaultCoordinator.run(request)
        } else {
            runConversationFastPath(request)
        }
    }

    private fun runConversationFastPath(request: AgentLoopRequest): Flow<AgentLoopEvent> = flow {
        emit(
            AgentLoopEvent.LoopStarted(
                chatId = request.chatId,
                platformUid = request.platform.uid,
            )
        )
        emit(AgentLoopEvent.ModelTurnStarted(iteration = 1))

        val conversation = buildConversation(request)
        val output = StringBuilder()
        val pendingUiOutput = StringBuilder()
        var emittedAnyOutput = false
        var completed = false
        var completedText: String? = null
        var failureMessage: String? = null

        agentModelGateway.streamTurn(
            AgentModelRequest(
                platform = request.platform,
                diagnosticContext = request.diagnosticContext,
                conversation = conversation,
                fullConversation = conversation,
                instructions = buildFastChatInstructions(request),
                tools = emptyList(),
                policy = request.policy.copy(
                    maxIterations = 1,
                    toolChoiceMode = AgentToolChoiceMode.NONE,
                    allowParallelToolCalls = false,
                ),
                previousResponseId = null,
            )
        ).collect { event ->
            when (event) {
                is AgentModelEvent.OutputDelta -> {
                    output.append(event.delta)
                    pendingUiOutput.append(event.delta)

                    // Show the first provider chunk immediately for responsiveness,
                    // then batch tiny token deltas to avoid rebuilding the whole
                    // Compose chat row dozens of times per second.
                    val shouldFlush = pendingUiOutput.isNotEmpty() &&
                        (!emittedAnyOutput ||
                            pendingUiOutput.length >= STREAM_UI_CHUNK_CHARS ||
                            event.delta.contains('\n'))

                    if (shouldFlush) {
                        val uiDelta = pendingUiOutput.toString()
                        pendingUiOutput.clear()
                        emittedAnyOutput = true
                        emit(
                            AgentLoopEvent.OutputDelta(
                                iteration = 1,
                                delta = uiDelta,
                            )
                        )
                    }
                }

                is AgentModelEvent.Completed -> {
                    completed = true
                    completedText = event.finalText
                }

                is AgentModelEvent.Failed -> {
                    failureMessage = event.message
                }

                // Deliberation stays hidden on the normal chat path. Tool calls
                // are impossible because this request deliberately exposes none.
                is AgentModelEvent.ThinkingDelta,
                is AgentModelEvent.ToolCallReady -> Unit
            }
        }

        if (pendingUiOutput.isNotEmpty()) {
            emit(
                AgentLoopEvent.OutputDelta(
                    iteration = 1,
                    delta = pendingUiOutput.toString(),
                )
            )
            pendingUiOutput.clear()
        }

        val failure = failureMessage
        if (failure != null) {
            emit(
                AgentLoopEvent.LoopFailed(
                    message = failure,
                    iteration = 1,
                )
            )
            return@flow
        }

        if (!completed) {
            emit(
                AgentLoopEvent.LoopFailed(
                    message = "Conversation provider ended without a completion event.",
                    iteration = 1,
                )
            )
            return@flow
        }

        val finalText = output.toString().trim()
            .ifBlank { completedText.orEmpty().trim() }

        if (finalText.isBlank()) {
            emit(
                AgentLoopEvent.LoopFailed(
                    message = "Conversation provider returned an empty response.",
                    iteration = 1,
                )
            )
            return@flow
        }

        emit(
            AgentLoopEvent.LoopCompleted(
                finalText = finalText,
            )
        )
    }

    private fun buildConversation(request: AgentLoopRequest): List<AgentConversationItem> {
        val items = mutableListOf<AgentConversationItem>()

        request.userMessages.forEachIndexed { index, userMessage ->
            items += userMessage.toConversationItem(AgentMessageRole.USER)

            val assistant = request.assistantMessages
                .getOrNull(index)
                .orEmpty()
                .firstOrNull { it.platformType == request.platform.uid && it.content.isNotBlank() }
                ?: request.assistantMessages
                    .getOrNull(index)
                    .orEmpty()
                    .firstOrNull { it.content.isNotBlank() }

            if (assistant != null) {
                items += assistant.toConversationItem(AgentMessageRole.ASSISTANT)
            }
        }

        return items
    }

    private fun MessageV2.toConversationItem(role: AgentMessageRole): AgentConversationItem =
        AgentConversationItem(
            role = role,
            text = content,
            attachments = if (role == AgentMessageRole.USER) files else emptyList(),
        )

    private fun buildFastChatInstructions(request: AgentLoopRequest): String = buildString {
        append(FAST_CHAT_PROMPT)

        request.systemPrompt
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { customPrompt ->
                append("\n\nAdditional user-configured instructions:\n")
                append(
                    customPrompt
                        .replace("VibeApp", "LM_AI", ignoreCase = true)
                        .replace("Vibe App", "LM_AI", ignoreCase = true)
                )
            }
    }

    companion object {
        private const val STREAM_UI_CHUNK_CHARS = 48
        private const val ARABIC_ASSISTANT_NAME =
            "\u0645\u0633\u0627\u0639\u062f \u062d\u0633\u0627\u0646 \u0627\u0644\u0631\u0642\u0645\u064a"

        private val FAST_CHAT_PROMPT = """
            You are Hassan's Digital Assistant inside LM_AI.
            Your user-facing Arabic name is "$ARABIC_ASSISTANT_NAME".
            Never mention VibeApp, Vibe App, internal providers, hidden routing, tools, or chain-of-thought in a user-facing response.
            Answer the user's actual request directly and naturally.
            Use the language of the latest user message: Arabic message -> Arabic response; English message -> English response.
            Continue the existing conversation normally across follow-up messages.
            Do not claim to edit or build an application on this conversation-only path.
            Keep simple greetings and simple questions concise.
        """.trimIndent()
    }
}

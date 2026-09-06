package com.malik.lmai.feature.agent.loop

import com.malik.lmai.data.database.entity.MessageV2
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentLoopCoordinator
import com.malik.lmai.feature.agent.AgentLoopEvent
import com.malik.lmai.feature.agent.AgentLoopRequest
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolChoiceMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Keeps ordinary conversation off the heavyweight Android project-agent path.
 *
 * Casual chat, questions, venting, and app-idea discussion make a single lightweight
 * model turn: no project snapshots, no build schemas, and no project tools. Explicit
 * implementation/modification requests still use the full DefaultAgentLoopCoordinator.
 */
@Singleton
class FastPathAgentLoopCoordinator @Inject constructor(
    private val defaultCoordinator: DefaultAgentLoopCoordinator,
    private val agentModelGateway: AgentModelGateway,
) : AgentLoopCoordinator {

    override suspend fun run(request: AgentLoopRequest): Flow<AgentLoopEvent> {
        val latestUserText = request.userMessages.lastOrNull()?.content.orEmpty()
        return if (ChatTurnPolicy.detect(latestUserText) == ChatTurnMode.APP_EXECUTION) {
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

        val conversation = trimConversation(buildConversation(request))
        val output = StringBuilder()
        val pendingUiOutput = StringBuilder()
        var emittedAnyProviderOutput = false
        var completed = false
        var completedText: String? = null
        var failureMessage: String? = null

        agentModelGateway.streamTurn(
            AgentModelRequest(
                platform = request.platform,
                diagnosticContext = request.diagnosticContext,
                conversation = conversation,
                fullConversation = conversation,
                instructions = request.systemPrompt,
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

                    // Put the first real provider text on screen immediately. After that,
                    // batch tiny token deltas just enough to keep Compose rendering smooth.
                    val shouldFlush = pendingUiOutput.isNotEmpty() &&
                        (!emittedAnyProviderOutput ||
                            pendingUiOutput.length >= STREAM_UI_CHUNK_CHARS ||
                            event.delta.contains('\n'))

                    if (shouldFlush) {
                        val uiDelta = pendingUiOutput.toString()
                        pendingUiOutput.clear()
                        emittedAnyProviderOutput = true
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

                // Thinking is deliberately hidden on the human-conversation path.
                // Tool calls cannot be valid here because no tools are exposed.
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

        failureMessage?.let { failure ->
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

        // Some providers return the whole answer only in Completed.finalText. Never add
        // artificial word-by-word delays after the provider has already finished; emit
        // the missing terminal text immediately so the UI cannot make a fast answer look slow.
        val missingCompletedText = NaturalResponsePacer.missingCompletedText(
            streamedText = output.toString(),
            completedText = completedText,
        )
        if (missingCompletedText.isNotEmpty()) {
            output.append(missingCompletedText)
            emit(
                AgentLoopEvent.OutputDelta(
                    iteration = 1,
                    delta = missingCompletedText,
                )
            )
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

    /**
     * Long conversations can continue for hours without sending an ever-growing payload.
     * Keep the newest conversational context, bounded by both item count and text size.
     */
    private fun trimConversation(items: List<AgentConversationItem>): List<AgentConversationItem> {
        if (items.isEmpty()) return items

        val selected = ArrayDeque<AgentConversationItem>()
        var usedChars = 0

        for (item in items.asReversed()) {
            if (selected.size >= MAX_HISTORY_ITEMS) break
            val itemChars = item.text.orEmpty().length
            if (selected.isNotEmpty() && usedChars + itemChars > MAX_HISTORY_CHARS) break
            selected.addFirst(item)
            usedChars += itemChars
        }

        return selected.toList()
    }

    private fun MessageV2.toConversationItem(role: AgentMessageRole): AgentConversationItem =
        AgentConversationItem(
            role = role,
            text = content,
            attachments = if (role == AgentMessageRole.USER) files else emptyList(),
        )

    companion object {
        private const val STREAM_UI_CHUNK_CHARS = 24
        private const val MAX_HISTORY_ITEMS = 64
        private const val MAX_HISTORY_CHARS = 24_000
    }
}

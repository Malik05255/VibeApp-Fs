package com.malik.lmai.feature.agent.loop

import android.os.SystemClock
import com.malik.lmai.data.dto.qwen.request.QwenChatCompletionRequest
import com.malik.lmai.data.dto.qwen.request.QwenChatMessage
import com.malik.lmai.data.dto.qwen.request.QwenFunctionCall
import com.malik.lmai.data.dto.qwen.request.QwenFunctionDefinition
import com.malik.lmai.data.dto.qwen.request.QwenThinkingParam
import com.malik.lmai.data.dto.qwen.request.QwenTool
import com.malik.lmai.data.dto.qwen.request.QwenToolCall
import com.malik.lmai.data.dto.qwen.request.qwenTextContent
import com.malik.lmai.data.network.OpenAIAPI
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolCall
import com.malik.lmai.feature.agent.AgentToolChoiceMode
import com.malik.lmai.feature.diagnostic.ChatDiagnosticLogger
import com.malik.lmai.feature.diagnostic.ModelExecutionTrace
import com.malik.lmai.feature.diagnostic.ModelRequestDiagnosticContext
import com.malik.lmai.feature.diagnostic.toDiagnosticProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

@Singleton
class DeepSeekChatCompletionsAgentGateway @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val diagnosticLogger: ChatDiagnosticLogger,
) : AgentModelGateway {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun streamTurn(
        request: AgentModelRequest
    ): Flow<AgentModelEvent> = flow {

        openAIAPI.setToken(
            request.platform.token
        )

        openAIAPI.setAPIUrl(
            request.platform.apiUrl.toDeepSeekBaseUrl()
        )

        openAIAPI.setProvider(
            type = request.platform.compatibleType.name,
            customUrl = request.platform.apiUrl
        )

        val trace = ModelExecutionTrace()
        val isReasoning = request.platform.reasoning
        val messages = buildMessages(request)
        trace.markRequestPrepared()

        val requestContext = request.diagnosticContext
            ?.copy(
                platformUid = request.platform.uid
            )
            ?.let { ctx ->
                ModelRequestDiagnosticContext(
                    diagnosticContext = ctx,
                    providerType = request.platform.compatibleType.toDiagnosticProviderType(),
                    apiFamily = "chat_completions",
                    model = request.platform.model,
                    stream = true,
                    reasoningEnabled = isReasoning,
                    estimatedContextTokens = request.estimateContextTokensForDiagnostics(),
                    messageCount = messages.size,
                    toolCount = request.tools.size.takeIf { it > 0 },
                    toolChoiceMode = "auto".takeIf { request.tools.isNotEmpty() },
                    systemPromptPresent = !request.instructions.isNullOrBlank(),
                    systemPromptChars = request.instructions?.length?.takeIf { it > 0 }
                )
            }

        data class ToolCallAccumulator(
            var id: String = "",
            var name: String = "",
            val arguments: StringBuilder = StringBuilder()
        )

        val toolCallAccumulators = mutableMapOf<Int, ToolCallAccumulator>()
        var finishReason: String? = null
        val reasoningBuilder = StringBuilder()
        var streamError: String? = null

        // DeepSeek reasoning models can produce a very high number of tiny SSE
        // deltas. Emitting every tiny delta makes StateFlow + Compose rebuild
        // chat state thousands of times during a single app-generation turn.
        // Coalesce only the UI events; the provider trace and final reasoning
        // content still retain the exact full stream.
        val pendingOutput = StringBuilder()
        val pendingReasoning = StringBuilder()
        var lastUiFlushAt = SystemClock.elapsedRealtime()

        openAIAPI.streamQwenChatCompletion(
            QwenChatCompletionRequest(
                model = request.platform.model,
                messages = messages,
                tools = request.tools
                    .takeIf { it.isNotEmpty() }
                    ?.map { tool ->
                        QwenTool(
                            function = QwenFunctionDefinition(
                                name = tool.name,
                                description = tool.description,
                                parameters = tool.inputSchema.toDeepSeekToolSchema()
                            )
                        )
                    },
                toolChoice = if (request.tools.isNotEmpty()) "auto" else null,
                stream = true,
                thinking = if (isReasoning) QwenThinkingParam(type = "enabled") else null
            ),
            diagnosticContext = requestContext,
            trace = trace
        ).collect { chunk ->
            if (chunk.error != null) {
                streamError = chunk.error.message
                trace.markFailed(
                    chunk.error.type ?: "provider_error",
                    chunk.error.message
                )
                return@collect
            }

            val choice = chunk.choices?.firstOrNull() ?: return@collect
            finishReason = choice.finishReason ?: finishReason

            choice.delta?.content
                ?.takeIf { it.isNotEmpty() }
                ?.let { delta ->
                    trace.markOutput(delta)
                    pendingOutput.append(delta)
                }

            choice.delta?.reasoningContent
                ?.takeIf { it.isNotEmpty() }
                ?.let { delta ->
                    reasoningBuilder.append(delta)
                    pendingReasoning.append(delta)
                }

            choice.delta?.toolCalls?.forEach { deltaToolCall ->
                val acc = toolCallAccumulators.getOrPut(deltaToolCall.index) {
                    ToolCallAccumulator()
                }
                deltaToolCall.id?.let { acc.id = it }
                deltaToolCall.function?.name?.let { acc.name = it }
                deltaToolCall.function?.arguments?.let { acc.arguments.append(it) }
            }

            val now = SystemClock.elapsedRealtime()
            val shouldFlush =
                now - lastUiFlushAt >= STREAM_UI_FLUSH_INTERVAL_MS ||
                    pendingOutput.length >= STREAM_UI_FLUSH_CHARS ||
                    pendingReasoning.length >= STREAM_UI_FLUSH_CHARS

            if (shouldFlush) {
                if (pendingReasoning.isNotEmpty()) {
                    emit(AgentModelEvent.ThinkingDelta(pendingReasoning.toString()))
                    pendingReasoning.setLength(0)
                }
                if (pendingOutput.isNotEmpty()) {
                    emit(AgentModelEvent.OutputDelta(pendingOutput.toString()))
                    pendingOutput.setLength(0)
                }
                lastUiFlushAt = now
            }
        }

        // Never lose the tail of a stream just because it did not reach the
        // size/time threshold before the provider completed.
        if (pendingReasoning.isNotEmpty()) {
            emit(AgentModelEvent.ThinkingDelta(pendingReasoning.toString()))
            pendingReasoning.setLength(0)
        }
        if (pendingOutput.isNotEmpty()) {
            emit(AgentModelEvent.OutputDelta(pendingOutput.toString()))
            pendingOutput.setLength(0)
        }

        streamError?.let { error ->
            if (requestContext != null) {
                diagnosticLogger.logModelResponse(requestContext, trace, success = false)
                diagnosticLogger.logLatencyBreakdown(requestContext, trace)
            }
            emit(AgentModelEvent.Failed(error))
            return@flow
        }

        toolCallAccumulators.entries.sortedBy { it.key }.forEach { (_, acc) ->
            trace.markToolCall()
            val arguments = runCatching {
                json.parseToJsonElement(acc.arguments.toString())
            }.getOrElse {
                buildJsonObject {
                    put("raw", JsonPrimitive(acc.arguments.toString()))
                }
            }

            emit(
                AgentModelEvent.ToolCallReady(
                    AgentToolCall(
                        id = acc.id,
                        name = acc.name,
                        arguments = arguments
                    )
                )
            )
        }

        val reasoningContent = reasoningBuilder.toString().takeIf { it.isNotBlank() }
        reasoningContent?.let { trace.markThinking(it) }

        trace.finishReason = finishReason
        trace.markCompleted(finishReason)

        if (requestContext != null) {
            diagnosticLogger.logModelResponse(requestContext, trace, success = true)
            diagnosticLogger.logLatencyBreakdown(requestContext, trace)
        }

        emit(AgentModelEvent.Completed(reasoningContent = reasoningContent))
    }

    private fun buildMessages(request: AgentModelRequest): List<QwenChatMessage> {
        val messages = mutableListOf<QwenChatMessage>()
        val toolRequired = request.policy.toolChoiceMode == AgentToolChoiceMode.REQUIRED
        val hasTools = request.tools.isNotEmpty()

        val systemContent = buildString {
            request.instructions?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (toolRequired && hasTools) {
                append("\n\n")
                append(TOOL_REQUIRED_INSTRUCTION)
            } else if (hasTools) {
                append("\n\n")
                append(TOOL_ENCOURAGE_INSTRUCTION)
            }
        }.trim()

        if (systemContent.isNotBlank()) {
            messages += QwenChatMessage(
                role = "system",
                content = qwenTextContent(systemContent)
            )
        }

        val lastReasoningIdx = request.fullConversation.indexOfLast {
            it.role == AgentMessageRole.ASSISTANT && !it.reasoningContent.isNullOrBlank()
        }

        request.fullConversation.forEachIndexed { index, item ->
            when (item.role) {
                AgentMessageRole.USER -> messages += QwenChatMessage(
                    role = "user",
                    content = qwenTextContent(item.text.orEmpty())
                )
                AgentMessageRole.ASSISTANT -> {
                    val keepReasoning = index == lastReasoningIdx
                    messages += QwenChatMessage(
                        role = "assistant",
                        content = qwenTextContent(item.text),
                        reasoningContent = if (keepReasoning) item.reasoningContent else null,
                        toolCalls = item.toolCalls?.map { toolCall ->
                            QwenToolCall(
                                id = toolCall.id,
                                function = QwenFunctionCall(
                                    name = toolCall.name,
                                    arguments = toolCall.arguments.toString()
                                )
                            )
                        }?.takeIf { it.isNotEmpty() }
                    )
                }
                AgentMessageRole.TOOL -> messages += QwenChatMessage(
                    role = "tool",
                    content = qwenTextContent(item.payload?.toString() ?: item.text.orEmpty()),
                    toolCallId = item.toolCallId
                )
                AgentMessageRole.SYSTEM -> Unit
            }
        }

        return messages
    }

    companion object {
        private const val STREAM_UI_FLUSH_INTERVAL_MS = 80L
        private const val STREAM_UI_FLUSH_CHARS = 256

        private const val TOOL_REQUIRED_INSTRUCTION =
            """## MANDATORY TOOL USE
You MUST call at least one tool in your response. Do NOT reply with only text.
Analyze the user's request and use the appropriate tools to fulfill it.
Every response MUST include one or more tool calls — a text-only answer is NOT acceptable."""

        private const val TOOL_ENCOURAGE_INSTRUCTION =
            """## IMPORTANT: Continue Using Tools
You have tools available. When the user's request requires reading, writing, or modifying project files, or building the project, you MUST use the appropriate tools instead of describing what to do in text.
Do NOT assume you already know the file contents — always use tools to read and write files."""
    }
}

private fun String.toDeepSeekBaseUrl(): String = trimEnd('/')

private fun kotlinx.serialization.json.JsonElement.toDeepSeekToolSchema(): kotlinx.serialization.json.JsonElement {
    val schemaObject = if (this is kotlinx.serialization.json.JsonObject) this else buildJsonObject {}
    val properties = schemaObject["properties"]?.jsonObject ?: buildJsonObject {}
    val required = schemaObject["required"]

    return buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", properties)
        if (required != null) {
            put("required", required)
        }
        put("additionalProperties", JsonPrimitive(false))
    }
}

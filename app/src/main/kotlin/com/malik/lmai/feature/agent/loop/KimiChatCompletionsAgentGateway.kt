package com.malik.lmai.feature.agent.loop

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.malik.lmai.data.dto.qwen.request.QwenChatCompletionRequest
import com.malik.lmai.data.dto.qwen.request.QwenChatMessage
import com.malik.lmai.data.dto.qwen.request.QwenFunctionDefinition
import com.malik.lmai.data.dto.qwen.request.QwenTool
import com.malik.lmai.data.dto.qwen.request.qwenTextContent
import com.malik.lmai.data.network.OpenAIAPI
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolCall
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

@Singleton
class KimiChatCompletionsAgentGateway @Inject constructor(
    @ApplicationContext private val context: Context,
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

        openAIAPI.setToken(request.platform.token)
        openAIAPI.setAPIUrl(request.platform.apiUrl.toKimiBaseUrl())
        openAIAPI.setProvider(
            type = request.platform.compatibleType.name,
            customUrl = request.platform.apiUrl
        )

        val trace = ModelExecutionTrace()
        val messages = buildMessages(request)
        trace.markRequestPrepared()

        val requestContext = request.diagnosticContext
            ?.copy(platformUid = request.platform.uid)
            ?.let { diagnosticContext ->
                ModelRequestDiagnosticContext(
                    diagnosticContext = diagnosticContext,
                    providerType = request.platform.compatibleType.toDiagnosticProviderType(),
                    apiFamily = "chat_completions",
                    model = request.platform.model,
                    stream = true,
                    reasoningEnabled = request.platform.reasoning,
                    estimatedContextTokens = request.estimateContextTokensForDiagnostics(),
                    messageCount = messages.size,
                    toolCount = request.tools.size.takeIf { it > 0 },
                    toolChoiceMode = "auto".takeIf { request.tools.isNotEmpty() },
                    systemPromptPresent = true,
                    systemPromptChars = request.instructions?.length?.takeIf { it > 0 },
                    hasImages = request.fullConversation.any { it.attachments.isNotEmpty() },
                    imageCount = request.fullConversation.sumOf { it.attachments.size }.takeIf { it > 0 }
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
                                parameters = tool.inputSchema
                            )
                        )
                    },
                toolChoice = if (request.tools.isNotEmpty()) "auto" else null,
                stream = true
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
                    emit(AgentModelEvent.OutputDelta(delta))
                }

            choice.delta?.reasoningContent
                ?.takeIf { it.isNotEmpty() }
                ?.let { delta ->
                    reasoningBuilder.append(delta)
                    emit(AgentModelEvent.ThinkingDelta(delta))
                }

            choice.delta?.toolCalls?.forEach { deltaToolCall ->
                val acc = toolCallAccumulators.getOrPut(deltaToolCall.index) {
                    ToolCallAccumulator()
                }
                deltaToolCall.id?.let { acc.id = it }
                deltaToolCall.function?.name?.let { acc.name = it }
                deltaToolCall.function?.arguments?.let { acc.arguments.append(it) }
            }
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
        
        request.instructions?.takeIf { it.isNotBlank() }?.let {
            messages += QwenChatMessage(
                role = "system",
                content = qwenTextContent(it)
            )
        }

        request.fullConversation.forEach { item ->
            when (item.role) {
                AgentMessageRole.USER -> messages += QwenChatMessage(
                    role = "user",
                    content = qwenTextContent(item.text.orEmpty())
                )
                AgentMessageRole.ASSISTANT -> messages += QwenChatMessage(
                    role = "assistant",
                    content = qwenTextContent(item.text)
                )
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
}

fun String.toKimiBaseUrl(): String {
    val trimmed = this.trim().trimEnd('/')
    return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
}

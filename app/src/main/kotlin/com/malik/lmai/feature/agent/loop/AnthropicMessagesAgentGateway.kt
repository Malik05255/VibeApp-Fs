package com.malik.lmai.feature.agent.loop

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.malik.lmai.data.dto.anthropic.common.ImageContent
import com.malik.lmai.data.dto.anthropic.common.ImageSource
import com.malik.lmai.data.dto.anthropic.common.ImageSourceType
import com.malik.lmai.data.dto.anthropic.common.MediaType
import com.malik.lmai.data.dto.anthropic.common.MessageContent
import com.malik.lmai.data.dto.anthropic.common.MessageRole
import com.malik.lmai.data.dto.anthropic.common.TextContent
import com.malik.lmai.data.dto.anthropic.common.ToolResultContent
import com.malik.lmai.data.dto.anthropic.common.ToolUseContent
import com.malik.lmai.data.dto.anthropic.request.AnthropicTool
import com.malik.lmai.data.dto.anthropic.request.AnthropicToolChoice
import com.malik.lmai.data.dto.anthropic.request.InputMessage
import com.malik.lmai.data.dto.anthropic.request.MessageRequest
import com.malik.lmai.data.dto.anthropic.response.AnthropicResponseChunk
import com.malik.lmai.data.dto.anthropic.response.ContentBlockType
import com.malik.lmai.data.dto.anthropic.response.ContentDeltaResponseChunk
import com.malik.lmai.data.dto.anthropic.response.ContentStartResponseChunk
import com.malik.lmai.data.dto.anthropic.response.ContentStopResponseChunk
import com.malik.lmai.data.dto.anthropic.response.ErrorResponseChunk
import com.malik.lmai.data.dto.anthropic.response.MessageDeltaResponseChunk
import com.malik.lmai.data.dto.anthropic.response.MessageStartResponseChunk
import com.malik.lmai.data.dto.anthropic.response.MessageStopResponseChunk
import com.malik.lmai.data.dto.anthropic.response.StopReason
import com.malik.lmai.data.network.AnthropicAPI
import com.malik.lmai.feature.agent.AgentConversationItem
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
import com.malik.lmai.util.FileUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

@Singleton
class AnthropicMessagesAgentGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val anthropicAPI: AnthropicAPI,
    private val diagnosticLogger: ChatDiagnosticLogger,
) : AgentModelGateway {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    override suspend fun streamTurn(request: AgentModelRequest): Flow<AgentModelEvent> = flow {
        anthropicAPI.setToken(request.platform.token)
        anthropicAPI.setAPIUrl(request.platform.apiUrl)
        val trace = ModelExecutionTrace()

        val messages = buildMessages(request.fullConversation)
        val tools = request.tools
            .takeIf { it.isNotEmpty() }
            ?.map { AnthropicTool(name = it.name, description = it.description, inputSchema = it.inputSchema) }

        val messageRequest = MessageRequest(
            model = request.platform.model,
            messages = messages,
            maxTokens = DEFAULT_MAX_TOKENS,
            stream = true,
            systemPrompt = request.instructions,
            temperature = request.platform.temperature,
            topP = request.platform.topP,
            tools = tools,
            toolChoice = tools?.let { buildToolChoice(request.policy.toolChoiceMode) },
        )
        trace.markRequestPrepared()
        val requestContext = request.diagnosticContext?.copy(platformUid = request.platform.uid)?.let { diagnosticContext ->
            ModelRequestDiagnosticContext(
                diagnosticContext = diagnosticContext,
                providerType = request.platform.compatibleType.toDiagnosticProviderType(),
                apiFamily = "messages",
                model = request.platform.model,
                stream = true,
                reasoningEnabled = request.platform.reasoning,
                estimatedContextTokens = request.estimateContextTokensForDiagnostics(),
                messageCount = messages.size,
                toolCount = request.tools.size.takeIf { it > 0 },
                toolChoiceMode = request.policy.toolChoiceMode.name.lowercase(),
                systemPromptPresent = !request.instructions.isNullOrBlank(),
                systemPromptChars = request.instructions?.length?.takeIf { it > 0 },
            )
        }

        data class ToolUseBlock(val id: String, val name: String, val inputBuilder: StringBuilder)

        val activeToolBlocks = mutableMapOf<Int, ToolUseBlock>()
        var stopReason: StopReason? = null

        anthropicAPI.streamChatMessage(messageRequest, requestContext, trace).collect { chunk: AnthropicResponseChunk ->
            when (chunk) {
                is MessageStartResponseChunk -> {
                    trace.markInputTokens(
                        chunk.message.usage.inputTokens +
                            (chunk.message.usage.cacheCreationInputTokens ?: 0) +
                            (chunk.message.usage.cacheReadInputTokens ?: 0),
                    )
                }

                is ContentStartResponseChunk -> {
                    if (chunk.contentBlock.type == ContentBlockType.TOOL_USE) {
                        val id = requireNotNull(chunk.contentBlock.id) {
                            "tool_use content_block_start missing id"
                        }
                        val name = requireNotNull(chunk.contentBlock.name) {
                            "tool_use content_block_start missing name"
                        }
                        activeToolBlocks[chunk.index] = ToolUseBlock(id, name, StringBuilder())
                    }
                }

                is ContentDeltaResponseChunk -> {
                    when (chunk.delta.type) {
                        ContentBlockType.DELTA -> {
                            chunk.delta.text?.let {
                                trace.markOutput(it)
                                emit(AgentModelEvent.OutputDelta(it))
                            }
                        }

                        ContentBlockType.THINKING_DELTA -> {
                            chunk.delta.thinking?.let {
                                trace.markThinking(it)
                                emit(AgentModelEvent.ThinkingDelta(it))
                            }
                        }

                        ContentBlockType.INPUT_JSON_DELTA -> {
                            activeToolBlocks[chunk.index]?.inputBuilder
                                ?.append(chunk.delta.partialJson.orEmpty())
                        }

                        else -> Unit
                    }
                }

                is ContentStopResponseChunk -> {
                    activeToolBlocks.remove(chunk.index)?.let { block ->
                        val arguments = block.inputBuilder.toString()
                            .takeIf { it.isNotBlank() }
                            ?.let { runCatching { json.parseToJsonElement(it) }.getOrElse { buildJsonObject {} } }
                            ?: buildJsonObject {}
                        emit(
                            AgentModelEvent.ToolCallReady(
                                AgentToolCall(
                                    id = block.id,
                                    name = block.name,
                                    arguments = arguments,
                                ),
                            ),
                        )
                        trace.markToolCall()
                    }
                }

                is MessageDeltaResponseChunk -> {
                    stopReason = chunk.delta.stopReason
                }

                is MessageStopResponseChunk -> {
                    trace.markCompleted(stopReason?.name?.lowercase())
                    emit(AgentModelEvent.Completed())
                }

                is ErrorResponseChunk -> {
                    trace.markFailed("provider_error", chunk.error.message)
                    emit(AgentModelEvent.Failed(chunk.error.message))
                }

                else -> Unit
            }
        }
        if (requestContext != null) {
            diagnosticLogger.logModelResponse(requestContext, trace, trace.errorKind == null)
            diagnosticLogger.logLatencyBreakdown(requestContext, trace)
        }
    }

    private suspend fun buildMessages(conversation: List<AgentConversationItem>): List<InputMessage> {
        val messages = mutableListOf<InputMessage>()
        var i = 0
        while (i < conversation.size) {
            val item = conversation[i]
            when (item.role) {
                AgentMessageRole.SYSTEM -> {
                    i++
                }

                AgentMessageRole.USER -> {
                    messages += InputMessage(
                        role = MessageRole.USER,
                        content = buildUserContent(item),
                    )
                    i++
                }

                AgentMessageRole.ASSISTANT -> {
                    val content = buildList {
                        item.text?.takeIf { it.isNotBlank() }?.let { add(TextContent(it)) }
                        item.toolCalls?.forEach { call ->
                            add(ToolUseContent(id = call.id, name = call.name, input = call.arguments))
                        }
                    }
                    if (content.isNotEmpty()) {
                        messages += InputMessage(role = MessageRole.ASSISTANT, content = content)
                    }
                    i++
                }

                AgentMessageRole.TOOL -> {
                    val toolResultBlocks = buildList {
                        while (i < conversation.size && conversation[i].role == AgentMessageRole.TOOL) {
                            val t = conversation[i]
                            add(
                                ToolResultContent(
                                    toolUseId = requireNotNull(t.toolCallId) {
                                        "TOOL item missing toolCallId"
                                    },
                                    content = t.payload?.toString() ?: t.text.orEmpty(),
                                    isError = null,
                                ),
                            )
                            i++
                        }
                    }
                    messages += InputMessage(role = MessageRole.USER, content = toolResultBlocks)
                }
            }
        }
        return messages
    }

    private fun buildToolChoice(mode: AgentToolChoiceMode): AnthropicToolChoice {
        return when (mode) {
            AgentToolChoiceMode.AUTO -> AnthropicToolChoice(type = "auto")
            AgentToolChoiceMode.REQUIRED -> AnthropicToolChoice(type = "any")
            AgentToolChoiceMode.NONE -> AnthropicToolChoice(type = "none")
        }
    }

    private suspend fun buildUserContent(item: AgentConversationItem): List<MessageContent> {
        val contents = mutableListOf<MessageContent>()
        for (path in item.attachments) {
            val mimeType = FileUtils.getMimeType(context, path) ?: continue
            val mediaType = mimeTypeToMediaType(mimeType) ?: continue
            val base64 = FileUtils.readAndEncodeFile(context, path) ?: continue
            contents.add(ImageContent(source = ImageSource(type = ImageSourceType.BASE64, mediaType = mediaType, data = base64)))
        }
        contents.add(TextContent(item.text.orEmpty()))
        return contents
    }

    private fun mimeTypeToMediaType(mimeType: String): MediaType? = when (mimeType.lowercase()) {
        "image/jpeg" -> MediaType.JPEG
        "image/png" -> MediaType.PNG
        "image/gif" -> MediaType.GIF
        "image/webp" -> MediaType.WEBP
        else -> null
    }

    companion object {
        private const val DEFAULT_MAX_TOKENS = 16000
    }
}

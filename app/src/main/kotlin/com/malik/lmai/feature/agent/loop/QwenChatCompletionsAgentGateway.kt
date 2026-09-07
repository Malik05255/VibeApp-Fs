package com.malik.lmai.feature.agent.loop

import com.malik.lmai.data.dto.qwen.request.QwenChatCompletionRequest
import com.malik.lmai.data.dto.qwen.request.QwenChatMessage
import com.malik.lmai.data.dto.qwen.request.QwenFunctionCall
import com.malik.lmai.data.dto.qwen.request.QwenFunctionDefinition
import com.malik.lmai.data.dto.qwen.request.QwenTool
import com.malik.lmai.data.dto.qwen.request.QwenToolCall
import com.malik.lmai.data.dto.qwen.request.qwenTextContent
import com.malik.lmai.data.model.ClientType
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
import kotlinx.serialization.json.put

@Singleton
class QwenChatCompletionsAgentGateway @Inject constructor(
    private val openAIAPI: OpenAIAPI,
    private val diagnosticLogger: ChatDiagnosticLogger,
) : AgentModelGateway {

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    override suspend fun streamTurn(
        request: AgentModelRequest,
    ): Flow<AgentModelEvent> = flow {

        /*
         * =========================================================
         * PROVIDER CONFIGURATION
         * =========================================================
         */

        openAIAPI.setToken(
            request.platform.token
        )

        openAIAPI.setAPIUrl(
            request.platform.apiUrl
                .toQwenChatCompletionsBaseUrl()
        )

        openAIAPI.setProvider(
            type =
                request.platform.compatibleType.name,

            customUrl =
                request.platform.apiUrl,
        )

        /*
         * NONE:
         * No tools.
         *
         * AUTO:
         * Send tools, but OpenRouter may fall back
         * once to text-only when the model does not
         * support tools.
         *
         * REQUIRED:
         * Tools are mandatory.
         * No text-only fallback is allowed because
         * the Agent cannot create/build an app without tools.
         */
        var includeTools =
            request.shouldSendTools()

        var toolFallbackAttempted =
            false

        data class ToolCallAccumulator(
            var id: String = "",
            var name: String = "",
            val arguments: StringBuilder =
                StringBuilder(),
        )

        val toolCallAccumulators =
            mutableMapOf<Int, ToolCallAccumulator>()

        var finishReason: String? =
            null

        var streamError: String? =
            null

        val reasoningBuilder =
            StringBuilder()

        var trace =
            ModelExecutionTrace()

        var requestContext:
            ModelRequestDiagnosticContext? =
            null

        /*
         * =========================================================
         * REQUEST LOOP
         * =========================================================
         *
         * Normally this executes once.
         *
         * OpenRouter AUTO mode can execute a second
         * time without tools if the selected model
         * explicitly rejects tool calling.
         */
        while (true) {

            toolCallAccumulators.clear()

            finishReason =
                null

            streamError =
                null

            reasoningBuilder.clear()

            trace =
                ModelExecutionTrace()

            trace.markRequestPrepared()

            /*
             * Build the full stateless Chat Completions
             * message history.
             */
            val messages =
                buildMessages(
                    request =
                        request,

                    includeTools =
                        includeTools,
                )

            val effectiveToolChoice =
                request.toQwenToolChoice(
                    includeTools =
                        includeTools
                )

            requestContext =
                buildDiagnosticContext(
                    request =
                        request,

                    messages =
                        messages,

                    includeTools =
                        includeTools,

                    effectiveToolChoice =
                        effectiveToolChoice,
                )

            /*
             * IMPORTANT FIX
             *
             * buildQwenTools() now transforms each
             * Agent tool's schema using the same
             * normalization used by the original
             * LmaiApp project.
             */
            val qwenTools =
                buildQwenTools(
                    request =
                        request,

                    includeTools =
                        includeTools,
                )

            var attemptProducedContent =
                false

            var attemptSawToolCall =
                false

            var retryWithoutTools =
                false

            var stopCurrentAttempt =
                false

            openAIAPI
                .streamQwenChatCompletion(
                    request =
                        QwenChatCompletionRequest(
                            /*
                             * Always preserve exactly the
                             * model selected by the user.
                             */
                            model =
                                request.platform.model,

                            messages =
                                messages,

                            tools =
                                qwenTools,

                            toolChoice =
                                effectiveToolChoice,

                            stream =
                                true,
                        ),

                    diagnosticContext =
                        requestContext,

                    trace =
                        trace,
                )
                .collect { chunk ->

                    if (
                        stopCurrentAttempt
                    ) {
                        return@collect
                    }

                    /*
                     * =================================================
                     * PROVIDER ERROR
                     * =================================================
                     */
                    val providerError =
                        chunk.error

                    if (
                        providerError != null
                    ) {

                        val errorMessage =
                            providerError.message

                        /*
                         * OpenRouter:
                         *
                         * If normal chat uses AUTO tools but
                         * the model has no tool-capable endpoint,
                         * retry exactly once without tools.
                         *
                         * We DO NOT do this for REQUIRED because
                         * app creation requires tool execution.
                         */
                        val canRetryWithoutTools =
                            request.platform.compatibleType ==
                                ClientType.OPEN_ROUTER &&
                                includeTools &&
                                !toolFallbackAttempted &&
                                request.policy.toolChoiceMode ==
                                    AgentToolChoiceMode.AUTO &&
                                !attemptProducedContent &&
                                !attemptSawToolCall &&
                                errorMessage
                                    .isUnsupportedToolError()

                        if (
                            canRetryWithoutTools
                        ) {

                            retryWithoutTools =
                                true

                            stopCurrentAttempt =
                                true

                            return@collect
                        }

                        streamError =
                            errorMessage

                        trace.markFailed(
                            providerError.type
                                ?: "provider_error",

                            errorMessage,
                        )

                        stopCurrentAttempt =
                            true

                        return@collect
                    }

                    val choice =
                        chunk.choices
                            ?.firstOrNull()
                            ?: return@collect

                    finishReason =
                        choice.finishReason
                            ?: finishReason

                    /*
                     * Some providers send streamed delta.
                     *
                     * Some compatible implementations may
                     * put data into message.
                     */
                    val delta =
                        choice.delta

                    val message =
                        choice.message

                    /*
                     * =================================================
                     * TEXT
                     * =================================================
                     */
                    val content =
                        delta?.content
                            ?: message?.content

                    content
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?.let { text ->

                            attemptProducedContent =
                                true

                            trace.markOutput(
                                text
                            )

                            emit(
                                AgentModelEvent.OutputDelta(
                                    text
                                )
                            )
                        }

                    /*
                     * =================================================
                     * REASONING
                     * =================================================
                     */
                    delta
                        ?.reasoningContent
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?.let { reasoning ->

                            attemptProducedContent =
                                true

                            reasoningBuilder
                                .append(
                                    reasoning
                                )

                            emit(
                                AgentModelEvent.ThinkingDelta(
                                    reasoning
                                )
                            )
                        }

                    /*
                     * =================================================
                     * STREAMED TOOL CALLS
                     * =================================================
                     *
                     * Arguments can arrive across several
                     * SSE chunks.
                     *
                     * Collect them by tool-call index and
                     * execute only after the stream finishes.
                     */
                    val toolCalls =
                        delta?.toolCalls
                            ?: message?.toolCalls

                    toolCalls
                        ?.forEach { toolCall ->

                            attemptSawToolCall =
                                true

                            val accumulator =
                                toolCallAccumulators
                                    .getOrPut(
                                        toolCall.index
                                    ) {
                                        ToolCallAccumulator()
                                    }

                            toolCall.id
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?.let { id ->

                                    accumulator.id =
                                        id
                                }

                            toolCall.function
                                ?.name
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?.let { name ->

                                    accumulator.name =
                                        name
                                }

                            toolCall.function
                                ?.arguments
                                ?.let { arguments ->

                                    accumulator.arguments
                                        .append(
                                            arguments
                                        )
                                }
                        }
                }

            /*
             * =========================================================
             * OPTIONAL OPENROUTER FALLBACK
             * =========================================================
             */
            if (
                retryWithoutTools
            ) {

                toolFallbackAttempted =
                    true

                includeTools =
                    false

                continue
            }

            break
        }

        /*
         * =========================================================
         * FINAL PROVIDER FAILURE
         * =========================================================
         */
        streamError
            ?.let { error ->

                requestContext
                    ?.let { context ->

                        diagnosticLogger
                            .logModelResponse(
                                context =
                                    context,

                                trace =
                                    trace,

                                success =
                                    false,
                            )

                        diagnosticLogger
                            .logLatencyBreakdown(
                                context =
                                    context,

                                trace =
                                    trace,
                            )
                    }

                emit(
                    AgentModelEvent.Failed(
                        error
                    )
                )

                return@flow
            }

        /*
         * =========================================================
         * EMIT COMPLETE TOOL CALLS
         * =========================================================
         */
        toolCallAccumulators
            .entries
            .sortedBy {
                it.key
            }
            .forEach { (_, accumulator) ->

                /*
                 * A tool call without a function name
                 * cannot be executed.
                 */
                if (
                    accumulator.name
                        .isBlank()
                ) {
                    return@forEach
                }

                val rawArguments =
                    accumulator.arguments
                        .toString()
                        .trim()

                val arguments =
                    if (
                        rawArguments.isBlank()
                    ) {

                        buildJsonObject {}

                    } else {

                        runCatching {

                            json.parseToJsonElement(
                                rawArguments
                            )

                        }.getOrElse {

                            /*
                             * Preserve malformed raw output
                             * for diagnostics instead of
                             * crashing the whole Agent loop.
                             */
                            buildJsonObject {

                                put(
                                    "raw",
                                    JsonPrimitive(
                                        rawArguments
                                    )
                                )
                            }
                        }
                    }

                trace.markToolCall()

                emit(
                    AgentModelEvent.ToolCallReady(
                        AgentToolCall(
                            id =
                                accumulator.id
                                    .ifBlank {
                                        "call_${System.nanoTime()}"
                                    },

                            name =
                                accumulator.name,

                            arguments =
                                arguments,
                        )
                    )
                )
            }

        /*
         * =========================================================
         * COMPLETE MODEL TURN
         * =========================================================
         */
        val reasoningContent =
            reasoningBuilder
                .toString()
                .takeIf {
                    it.isNotBlank()
                }

        reasoningContent
            ?.let {
                trace.markThinking(
                    it
                )
            }

        trace.finishReason =
            finishReason

        trace.markCompleted(
            finishReason
        )

        requestContext
            ?.let { context ->

                diagnosticLogger
                    .logModelResponse(
                        context =
                            context,

                        trace =
                            trace,

                        success =
                            true,
                    )

                diagnosticLogger
                    .logLatencyBreakdown(
                        context =
                            context,

                        trace =
                            trace,
                    )
            }

        emit(
            AgentModelEvent.Completed(
                reasoningContent =
                    reasoningContent
            )
        )
    }

    /*
     * =============================================================
     * DIAGNOSTICS
     * =============================================================
     */
    private fun buildDiagnosticContext(
        request: AgentModelRequest,
        messages: List<QwenChatMessage>,
        includeTools: Boolean,
        effectiveToolChoice: String?,
    ): ModelRequestDiagnosticContext? {

        return request.diagnosticContext
            ?.copy(
                platformUid =
                    request.platform.uid
            )
            ?.let { diagnosticContext ->

                ModelRequestDiagnosticContext(
                    diagnosticContext =
                        diagnosticContext,

                    providerType =
                        request.platform
                            .compatibleType
                            .toDiagnosticProviderType(),

                    apiFamily =
                        "chat_completions",

                    model =
                        request.platform.model,

                    stream =
                        true,

                    reasoningEnabled =
                        request.platform.reasoning,

                    estimatedContextTokens =
                        request
                            .estimateContextTokensForDiagnostics(),

                    messageCount =
                        messages.size,

                    toolCount =
                        if (
                            includeTools
                        ) {

                            request.tools
                                .size
                                .takeIf {
                                    it > 0
                                }

                        } else {

                            null
                        },

                    toolChoiceMode =
                        effectiveToolChoice,

                    systemPromptPresent =
                        !request.instructions
                            .isNullOrBlank(),

                    systemPromptChars =
                        request.instructions
                            ?.length
                            ?.takeIf {
                                it > 0
                            },
                )
            }
    }

    /*
     * =============================================================
     * TOOL DEFINITIONS
     * =============================================================
     */
    private fun buildQwenTools(
        request: AgentModelRequest,
        includeTools: Boolean,
    ): List<QwenTool>? {

        if (
            !includeTools ||
            request.tools.isEmpty()
        ) {

            return null
        }

        return request.tools
            .map { tool ->

                QwenTool(
                    type =
                        "function",

                    function =
                        QwenFunctionDefinition(
                            name =
                                tool.name,

                            description =
                                tool.description,

                            /*
                             * =================================================
                             * CRITICAL FIX
                             * =================================================
                             *
                             * Do NOT send tool.inputSchema directly.
                             *
                             * The original LmaiApp normalizes it into
                             * an OpenAI-compatible strict object schema.
                             *
                             * This is important for:
                             *
                             * - OpenRouter
                             * - Google AI Studio OpenAI compatibility
                             * - Custom OpenAI-compatible APIs
                             */
                            parameters =
                                tool.inputSchema
                                    .toQwenChatToolSchema(),
                        ),
                )
            }
    }

    /*
     * =============================================================
     * CONVERSATION MESSAGES
     * =============================================================
     */
    private fun buildMessages(
        request: AgentModelRequest,
        includeTools: Boolean,
    ): List<QwenChatMessage> {

        val messages =
            mutableListOf<QwenChatMessage>()

        val toolRequired =
            includeTools &&
                request.policy.toolChoiceMode ==
                    AgentToolChoiceMode.REQUIRED

        val hasTools =
            includeTools &&
                request.tools.isNotEmpty()

        /*
         * =========================================================
         * SYSTEM MESSAGE
         * =========================================================
         */
        val systemContent =
            buildString {

                request.instructions
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        append(
                            it
                        )
                    }

                if (
                    toolRequired &&
                    hasTools
                ) {

                    append(
                        "\n\n"
                    )

                    append(
                        TOOL_REQUIRED_INSTRUCTION
                    )

                } else if (
                    hasTools
                ) {

                    append(
                        "\n\n"
                    )

                    append(
                        TOOL_ENCOURAGE_INSTRUCTION
                    )
                }

            }.trim()

        if (
            systemContent.isNotBlank()
        ) {

            messages +=
                QwenChatMessage(
                    role =
                        "system",

                    content =
                        qwenTextContent(
                            systemContent
                        ),
                )
        }

        /*
         * =========================================================
         * FULL CONVERSATION
         * =========================================================
         *
         * Chat Completions is stateless.
         *
         * Every new model turn receives the complete
         * accumulated conversation including:
         *
         * user
         * assistant
         * assistant tool_calls
         * tool results
         */
        request.fullConversation
            .forEach { item ->

                when (
                    item.role
                ) {

                    /*
                     * USER
                     */
                    AgentMessageRole.USER -> {

                        messages +=
                            QwenChatMessage(
                                role =
                                    "user",

                                content =
                                    qwenTextContent(
                                        item.text
                                            .orEmpty()
                                    ),
                            )
                    }

                    /*
                     * ASSISTANT
                     */
                    AgentMessageRole.ASSISTANT -> {

                        val historicalToolCalls =
                            if (
                                includeTools
                            ) {

                                item.toolCalls
                                    ?.map { toolCall ->

                                        QwenToolCall(
                                            id =
                                                toolCall.id,

                                            function =
                                                QwenFunctionCall(
                                                    name =
                                                        toolCall.name,

                                                    arguments =
                                                        toolCall
                                                            .arguments
                                                            .toString(),
                                                ),
                                        )
                                    }
                                    ?.takeIf {
                                        it.isNotEmpty()
                                    }

                            } else {

                                null
                            }

                        /*
                         * When doing the OpenRouter
                         * text-only fallback, omit old
                         * assistant messages that contained
                         * only tool calls and no text.
                         */
                        val hasAssistantText =
                            !item.text
                                .isNullOrBlank()

                        if (
                            includeTools ||
                            hasAssistantText
                        ) {

                            messages +=
                                QwenChatMessage(
                                    role =
                                        "assistant",

                                    content =
                                        qwenTextContent(
                                            item.text
                                        ),

                                    toolCalls =
                                        historicalToolCalls,
                                )
                        }
                    }

                    /*
                     * TOOL RESULT
                     */
                    AgentMessageRole.TOOL -> {

                        /*
                         * Tool-role messages must be paired
                         * with assistant tool_calls.
                         *
                         * Therefore omit them during a
                         * text-only fallback.
                         */
                        if (
                            includeTools
                        ) {

                            messages +=
                                QwenChatMessage(
                                    role =
                                        "tool",

                                    content =
                                        qwenTextContent(
                                            item.payload
                                                ?.toString()
                                                ?: item.text
                                                    .orEmpty()
                                        ),

                                    toolCallId =
                                        item.toolCallId,
                                )
                        }
                    }

                    /*
                     * SYSTEM conversation items are ignored
                     * here because request.instructions is
                     * already used to build the system prompt.
                     */
                    AgentMessageRole.SYSTEM -> {
                        Unit
                    }
                }
            }

        return messages
    }

    companion object {

        /*
         * The coordinator sets REQUIRED on the first
         * iteration when tools exist.
         *
         * Since not every OpenAI-compatible provider
         * supports literal tool_choice = "required"
         * consistently, the actual API value remains
         * "auto" and the requirement is reinforced
         * through this system instruction.
         */
        private const val TOOL_REQUIRED_INSTRUCTION =
            """
## MANDATORY TOOL USE
You MUST call at least one tool in your response.
Do NOT reply with only text.
Analyze the user's request and use the appropriate tools to fulfill it.
Every response MUST include one or more tool calls.
A text-only answer is NOT acceptable.
"""

        private const val TOOL_ENCOURAGE_INSTRUCTION =
            """
## IMPORTANT: Continue Using Tools
You have tools available.
When the user's request requires reading, writing, or modifying project files,
or building the project, you MUST use the appropriate tools instead of
describing what to do in text.

Do NOT assume you already know the file contents.
Always use tools to read and write files.
"""
    }
}

/*
 * =============================================================
 * SHOULD SEND TOOLS
 * =============================================================
 */
private fun AgentModelRequest.shouldSendTools(): Boolean {

    if (
        tools.isEmpty()
    ) {

        return false
    }

    return policy.toolChoiceMode !=
        AgentToolChoiceMode.NONE
}

/*
 * =============================================================
 * TOOL CHOICE
 * =============================================================
 *
 * Keep the original no-argument function because
 * other existing code/tests may reference it.
 */
internal fun AgentModelRequest.toQwenToolChoice(): String? {

    return toQwenToolChoice(
        includeTools =
            true
    )
}

internal fun AgentModelRequest.toQwenToolChoice(
    includeTools: Boolean,
): String? {

    if (
        !includeTools ||
        tools.isEmpty()
    ) {

        return null
    }

    return when (
        policy.toolChoiceMode
    ) {

        AgentToolChoiceMode.NONE -> {
            "none"
        }

        /*
         * Use "auto" for compatibility.
         *
         * REQUIRED is additionally enforced by the
         * system instruction above.
         */
        AgentToolChoiceMode.AUTO,
        AgentToolChoiceMode.REQUIRED -> {
            "auto"
        }
    }
}

/*
 * =============================================================
 * TOOL SCHEMA NORMALIZATION
 * =============================================================
 *
 * This is the important part restored from
 * the original Skykai521/LmaiApp implementation.
 *
 * Generic AgentTool schemas can contain metadata or
 * top-level fields that compatible Chat Completions
 * providers do not treat identically.
 *
 * Normalize them into:
 *
 * {
 *   "type": "object",
 *   "properties": { ... },
 *   "required": [ ... ],
 *   "additionalProperties": false
 * }
 */
private fun kotlinx.serialization.json.JsonElement
    .toQwenChatToolSchema():
    kotlinx.serialization.json.JsonElement {

    val schemaObject =
        if (
            this is
                kotlinx.serialization.json.JsonObject
        ) {

            this

        } else {

            buildJsonObject {}
        }

    val properties =
        schemaObject[
            "properties"
        ]
            ?.jsonObject
            ?: buildJsonObject {}

    val required =
        schemaObject[
            "required"
        ]

    return buildJsonObject {

        put(
            "type",
            JsonPrimitive(
                "object"
            )
        )

        put(
            "properties",
            properties
        )

        if (
            required != null
        ) {

            put(
                "required",
                required
            )
        }

        /*
         * Matches the original LmaiApp implementation.
         */
        put(
            "additionalProperties",
            JsonPrimitive(
                false
            )
        )
    }
}

/*
 * =============================================================
 * OPENROUTER TOOL-SUPPORT ERROR
 * =============================================================
 */
private fun String.isUnsupportedToolError(): Boolean {

    val normalized =
        lowercase()

    return normalized.contains(
        "no endpoints found that support tool use"
    ) ||
        normalized.contains(
            "no endpoint found that supports tool use"
        ) ||
        normalized.contains(
            "does not support tool use"
        ) ||
        normalized.contains(
            "doesn't support tool use"
        ) ||
        normalized.contains(
            "tool use is not supported"
        ) ||
        normalized.contains(
            "tool calling is not supported"
        ) ||
        normalized.contains(
            "tools are not supported"
        ) ||
        normalized.contains(
            "function calling is not supported"
        )
}

/*
 * =============================================================
 * BASE URL NORMALIZATION
 * =============================================================
 *
 * Google:
 *
 * https://generativelanguage.googleapis.com/v1beta/openai
 *
 * must stay unchanged here.
 *
 * OpenAIAPIImpl later adds:
 *
 * /chat/completions
 *
 * OpenRouter:
 *
 * https://openrouter.ai/api
 *
 * is also kept as its base and OpenAIAPIImpl builds:
 *
 * /v1/chat/completions
 */
private fun String.toQwenChatCompletionsBaseUrl(): String {

    val trimmed =
        trim()
            .trimEnd('/')

    return when {

        /*
         * Legacy Qwen compatible URL migration.
         */
        "/api/v2/apps/protocols/compatible-mode" in
            trimmed -> {

            trimmed.replace(
                "/api/v2/apps/protocols/compatible-mode",
                "/compatible-mode/v1"
            )
        }

        trimmed.endsWith(
            "/compatible-mode/v1"
        ) -> {

            trimmed
        }

        else -> {

            trimmed
        }
    }
}

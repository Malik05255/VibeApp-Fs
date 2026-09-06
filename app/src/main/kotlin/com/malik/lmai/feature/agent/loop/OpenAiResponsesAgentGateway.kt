package com.malik.lmai.feature.agent.loop

import android.content.Context
import com.malik.lmai.data.dto.openai.request.ResponseContentPart
import com.malik.lmai.data.dto.openai.request.ResponseInputContent
import com.malik.lmai.data.dto.openai.request.ResponseInputItem
import com.malik.lmai.data.dto.openai.request.ResponseTool
import com.malik.lmai.data.dto.openai.request.ResponsesRequest
import com.malik.lmai.data.dto.openai.response.OutputItemDoneEvent
import com.malik.lmai.data.dto.openai.response.OutputTextDeltaEvent
import com.malik.lmai.data.dto.openai.response.ReasoningSummaryTextDeltaEvent
import com.malik.lmai.data.dto.openai.response.ResponseCompletedEvent
import com.malik.lmai.data.dto.openai.response.ResponseCreatedEvent
import com.malik.lmai.data.dto.openai.response.ResponseErrorEvent
import com.malik.lmai.data.dto.openai.response.ResponseFailedEvent
import com.malik.lmai.data.network.OpenAIAPI
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolCall
import com.malik.lmai.feature.diagnostic.ChatDiagnosticLogger
import com.malik.lmai.feature.diagnostic.ModelExecutionTrace
import com.malik.lmai.feature.diagnostic.ModelRequestDiagnosticContext
import com.malik.lmai.feature.diagnostic.toDiagnosticProviderType
import com.malik.lmai.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject


@Singleton
class OpenAiResponsesAgentGateway @Inject constructor(
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

        openAIAPI.setToken(
            request.platform.token
        )


        openAIAPI.setAPIUrl(
            request.platform.apiUrl
        )


        openAIAPI.setProvider(
            type = request.platform.compatibleType.name,
            customUrl = request.platform.apiUrl
        )


        val trace =
            ModelExecutionTrace()


        val responseRequest =
            ResponsesRequest(

                model =
                    request.platform.model,

                input =
                    request.conversation.map(
                        ::toResponseInputItem
                    ),

                previousResponseId =
                    request.previousResponseId,

                stream = true,

                instructions =
                    request.instructions,

                tools =
                    request.tools
                        .takeIf {
                            it.isNotEmpty()
                        }
                        ?.map {

                            ResponseTool(

                                name =
                                    it.name,

                                description =
                                    it.description,

                                parameters =
                                    it.inputSchema
                                        .takeUnless {
                                            it.isEmptyJsonObject()
                                        },

                                strict = null
                            )
                        },


                toolChoice =
                    request.policy.toolChoiceMode
                        .name
                        .lowercase()

            )


        trace.markRequestPrepared()


        val requestContext =
            request.diagnosticContext
                ?.copy(
                    platformUid =
                        request.platform.uid
                )
                ?.let {

                    ModelRequestDiagnosticContext(

                        diagnosticContext = it,

                        providerType =
                            request.platform.compatibleType
                                .toDiagnosticProviderType(),

                        apiFamily =
                            "responses",

                        model =
                            request.platform.model,

                        stream = true,

                        reasoningEnabled =
                            request.platform.reasoning,

                        estimatedContextTokens =
                            request.estimateContextTokensForDiagnostics(),

                        messageCount =
                            request.conversation.size,

                        toolCount =
                            request.tools.size
                                .takeIf {
                                    it > 0
                                },

                        toolChoiceMode =
                            request.policy.toolChoiceMode
                                .name
                                .lowercase(),

                        systemPromptPresent =
                            !request.instructions.isNullOrBlank(),

                        systemPromptChars =
                            request.instructions
                                ?.length
                                ?.takeIf {
                                    it > 0
                                }
                    )
                }


        var lastResponseId: String? =
            request.previousResponseId


        openAIAPI.streamResponses(
            responseRequest,
            requestContext,
            trace
        ).collect { event ->

            when(event) {

                is ReasoningSummaryTextDeltaEvent -> {

                    trace.markThinking(
                        event.delta
                    )

                    emit(
                        AgentModelEvent.ThinkingDelta(
                            event.delta
                        )
                    )
                }


                is OutputTextDeltaEvent -> {

                    trace.markOutput(
                        event.delta
                    )

                    emit(
                        AgentModelEvent.OutputDelta(
                            event.delta
                        )
                    )
                }


                is ResponseCreatedEvent -> {

                    lastResponseId =
                        event.response.id
                }


                is ResponseCompletedEvent -> {

                    lastResponseId =
                        event.response.id

                    trace.markCompleted()

                    emit(
                        AgentModelEvent.Completed(
                            responseId = lastResponseId
                        )
                    )
                }


                is OutputItemDoneEvent -> {

                    event.toToolCallOrNull(json)
                        ?.let {

                            trace.markToolCall()

                            emit(
                                AgentModelEvent.ToolCallReady(
                                    it
                                )
                            )
                        }
                }


                is ResponseFailedEvent -> {

                    trace.markFailed(
                        "provider_error",
                        event.response.error?.message
                    )

                    emit(
                        AgentModelEvent.Failed(
                            event.response.error?.message
                                ?: "Responses request failed"
                        )
                    )
                }


                is ResponseErrorEvent -> {

                    trace.markFailed(
                        if (event.code == "network_error")
                            "network_error"
                        else
                            "provider_error",
                        event.message
                    )

                    emit(
                        AgentModelEvent.Failed(
                            event.message
                        )
                    )
                }


                else -> Unit
            }
        }


        requestContext?.let {

            diagnosticLogger.logModelResponse(
                it,
                trace,
                trace.errorKind == null
            )


            diagnosticLogger.logLatencyBreakdown(
                it,
                trace
            )
        }
    }


    private fun toResponseInputItem(
        item: AgentConversationItem
    ): ResponseInputItem {

        return when(item.role) {

            AgentMessageRole.USER ->

                ResponseInputItem.message(
                    role = "user",
                    content = buildUserContent(item)
                )


            AgentMessageRole.ASSISTANT ->

                ResponseInputItem.message(
                    role = "assistant",
                    content =
                        ResponseInputContent.text(
                            item.text.orEmpty()
                        )
                )


            AgentMessageRole.TOOL ->

                ResponseInputItem.functionCallOutput(
                    callId =
                        requireNotNull(item.toolCallId),
                    output =
                        item.payload?.toString()
                            ?: JsonPrimitive(
                                item.text.orEmpty()
                            ).toString()
                )


            AgentMessageRole.SYSTEM ->

                ResponseInputItem.message(
                    role = "user",
                    content =
                        ResponseInputContent.text(
                            item.text.orEmpty()
                        )
                )
        }
    }


    private fun buildUserContent(
        item: AgentConversationItem
    ): ResponseInputContent {

        val images =
            item.attachments.filter {

                FileUtils.isVisionSupportedImage(
                    FileUtils.getMimeType(
                        context,
                        it
                    )
                )
            }


        if(images.isEmpty()) {

            return ResponseInputContent.text(
                item.text.orEmpty()
            )
        }


        val parts =
            buildList {

                images.forEach {

                    val mime =
                        FileUtils.getMimeType(
                            context,
                            it
                        )


                    val base64 =
                        FileUtils.readAndEncodeFile(
                            context,
                            it
                        )
                            ?: return@forEach


                    add(
                        ResponseContentPart.image(
                            "data:$mime;base64,$base64"
                        )
                    )
                }


                add(
                    ResponseContentPart.text(
                        item.text.orEmpty()
                    )
                )
            }


        return ResponseInputContent.parts(parts)
    }
}


private fun OutputItemDoneEvent.toToolCallOrNull(
    json: Json
): AgentToolCall? {

    if(item.type != "function_call") {
        return null
    }


    val arguments =
        item.arguments
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {

                runCatching {
                    json.parseToJsonElement(it)
                }.getOrElse {

                    buildJsonObject {

                        put(
                            "raw",
                            JsonPrimitive(
                                item.arguments
                            )
                        )
                    }
                }
            }
            ?: buildJsonObject {}


    return AgentToolCall(

        id =
            item.callId
                ?: item.id,

        name =
            requireNotNull(item.name),

        arguments =
            arguments
    )
}

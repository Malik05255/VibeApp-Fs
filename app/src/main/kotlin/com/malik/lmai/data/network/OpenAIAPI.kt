package com.malik.lmai.data.network

import com.malik.lmai.data.dto.OpenRouterModel
import com.malik.lmai.data.dto.openai.request.ChatCompletionRequest
import com.malik.lmai.data.dto.openai.request.ResponsesRequest
import com.malik.lmai.data.dto.openai.response.ChatCompletionChunk
import com.malik.lmai.data.dto.openai.response.ResponsesStreamEvent
import com.malik.lmai.data.dto.qwen.request.QwenChatCompletionRequest
import com.malik.lmai.data.dto.qwen.response.QwenChatCompletionResponse
import com.malik.lmai.feature.diagnostic.ModelExecutionTrace
import com.malik.lmai.feature.diagnostic.ModelRequestDiagnosticContext
import kotlinx.coroutines.flow.Flow

interface OpenAIAPI {

    /**
     * تحديث مفتاح API للمزود الحالي.
     *
     * Google AI Studio يستخدم مفتاح API نفسه
     * عبر Authorization: Bearer.
     */
    fun setToken(
        token: String?
    )

    /**
     * تحديث رابط API.
     *
     * يستخدم مع Custom API، ويتم أيضًا استخدامه
     * مع المزودات المتوافقة مع OpenAI.
     */
    fun setAPIUrl(
        url: String
    )

    /**
     * تحديد نوع المزود الحالي.
     *
     * OPEN_ROUTER:
     * OpenRouter API.
     *
     * GOOGLE_AI_STUDIO:
     * Google AI Studio OpenAI-compatible API.
     *
     * CUSTOM:
     * رابط API يحدده المستخدم.
     */
    fun setProvider(
        type: String,
        customUrl: String? = null
    )

    /**
     * جلب قائمة الموديلات المتاحة من OpenRouter.
     *
     * هذه الدالة مخصصة لـ OpenRouter فقط.
     *
     * Google AI Studio لا يستخدم هذه الدالة،
     * لأن Google منفصل عن OpenRouter.
     */
    suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean = false
    ): List<OpenRouterModel>

    /**
     * OpenAI-compatible Chat Completions API.
     *
     * يستخدم مع:
     * - OpenRouter
     * - Google AI Studio
     * - Custom OpenAI-compatible APIs
     */
    fun streamChatCompletion(
        request: ChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext? = null,
        trace: ModelExecutionTrace? = null,
    ): Flow<ChatCompletionChunk>

    /**
     * Qwen / OpenAI-compatible Chat Completions API.
     *
     * يستخدم أيضًا من Google AI Studio في طبقة
     * Agent Gateway الحالية.
     */
    fun streamQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext? = null,
        trace: ModelExecutionTrace? = null,
    ): Flow<ChatCompletionChunk>

    /**
     * Qwen / OpenAI-compatible non-streaming completion.
     *
     * يمكن استخدامه مع المزودات المتوافقة مع
     * OpenAI Chat Completions.
     */
    suspend fun completeQwenChatCompletion(
        request: QwenChatCompletionRequest,
        diagnosticContext: ModelRequestDiagnosticContext? = null,
        trace: ModelExecutionTrace? = null,
    ): QwenChatCompletionResponse

    /**
     * OpenAI Responses API.
     *
     * يستخدم فقط مع المزودات التي تدعم
     * Responses endpoint.
     *
     * Google AI Studio في الإعداد الحالي
     * لا يعتمد على هذه الدالة.
     */
    fun streamResponses(
        request: ResponsesRequest,
        diagnosticContext: ModelRequestDiagnosticContext? = null,
        trace: ModelExecutionTrace? = null,
    ): Flow<ResponsesStreamEvent>
}

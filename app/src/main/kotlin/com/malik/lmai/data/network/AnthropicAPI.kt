package com.malik.lmai.data.network

import com.malik.lmai.data.dto.anthropic.request.MessageRequest
import com.malik.lmai.data.dto.anthropic.response.AnthropicResponseChunk
import com.malik.lmai.feature.diagnostic.ModelExecutionTrace
import com.malik.lmai.feature.diagnostic.ModelRequestDiagnosticContext
import kotlinx.coroutines.flow.Flow

interface AnthropicAPI {

    fun setToken(
        token: String?
    )

    fun setAPIUrl(
        url: String
    )

    fun setProvider(
        type: String,
        customUrl: String? = null
    )

    fun streamChatMessage(
        request: MessageRequest,
        context: ModelRequestDiagnosticContext? = null,
        trace: ModelExecutionTrace
    ): Flow<AnthropicResponseChunk>

}

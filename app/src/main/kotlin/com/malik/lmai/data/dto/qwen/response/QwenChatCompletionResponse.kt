package com.malik.lmai.data.dto.qwen.response

import com.malik.lmai.data.dto.qwen.request.QwenToolCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class QwenChatCompletionResponse(

    @SerialName("id")
    val id: String? = null,


    @SerialName("choices")
    val choices: List<QwenChoice>? = null,


    @SerialName("error")
    val error: QwenErrorDetail? = null

)



@Serializable
data class QwenChoice(

    @SerialName("index")
    val index: Int,


    @SerialName("message")
    val message: QwenAssistantMessage? = null,


    @SerialName("delta")
    val delta: QwenDeltaMessage? = null,


    @SerialName("finish_reason")
    val finishReason: String? = null

)



@Serializable
data class QwenDeltaMessage(

    @SerialName("role")
    val role: String? = null,


    @SerialName("content")
    val content: String? = null,


    @SerialName("reasoning_content")
    val reasoningContent: String? = null,


    @SerialName("tool_calls")
    val toolCalls: List<QwenToolCall>? = null

)



@Serializable
data class QwenAssistantMessage(

    @SerialName("role")
    val role: String? = null,


    @SerialName("content")
    val content: String? = null,


    @SerialName("reasoning_content")
    val reasoningContent: String? = null,


    @SerialName("tool_calls")
    val toolCalls: List<QwenToolCall>? = null

)



@Serializable
data class QwenErrorDetail(

    @SerialName("message")
    val message: String,


    @SerialName("type")
    val type: String? = null,


    @SerialName("code")
    val code: String? = null

)

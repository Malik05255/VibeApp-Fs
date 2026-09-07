package com.malik.lmai.data.dto.openai.request

import com.malik.lmai.data.dto.openai.common.MessageContent
import com.malik.lmai.data.dto.openai.common.Role
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    @SerialName("role")
    val role: Role,

    @SerialName("content")
    val content: List<MessageContent>
)

package com.malik.lmai.data.dto.openai.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    @SerialName("system")
    SYSTEM,

    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT
}

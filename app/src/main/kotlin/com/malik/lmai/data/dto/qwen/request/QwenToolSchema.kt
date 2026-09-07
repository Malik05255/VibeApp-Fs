package com.malik.lmai.data.dto.qwen.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class QwenToolSchema(
    val type: String = "object",
    val properties: Map<String, JsonElement> = emptyMap(),
    val required: List<String> = emptyList()
)

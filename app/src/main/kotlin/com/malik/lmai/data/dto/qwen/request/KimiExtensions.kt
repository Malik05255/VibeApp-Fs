package com.malik.lmai.data.network

import com.malik.lmai.data.dto.qwen.request.QwenToolSchema
import kotlinx.serialization.json.JsonElement

fun String.toKimiBaseUrl(): String {
    val trimmed = this.trim().trimEnd('/')
    return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
}

fun Map<String, Any?>.toKimiToolSchema(): QwenToolSchema {
    @Suppress("UNCHECKED_CAST")
    val propertiesMap = this["properties"] as? Map<String, JsonElement> ?: emptyMap()
    val requiredList = this["required"] as? List<String> ?: emptyList()
    return QwenToolSchema(
        type = "object",
        properties = propertiesMap,
        required = requiredList
    )
}

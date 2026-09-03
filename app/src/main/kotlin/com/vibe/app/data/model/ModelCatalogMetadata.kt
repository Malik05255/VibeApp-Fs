package com.vibe.app.data.model

import com.vibe.app.data.dto.OpenRouterModel

enum class ModelSpeedTier {
    VERY_FAST,
    FAST,
    BALANCED,
    SLOWER,
}

enum class ModelTaskTier {
    SIMPLE,
    MEDIUM,
    COMPLEX,
}

/**
 * Human-friendly estimates for model selection.
 *
 * OpenRouter does not expose a stable per-model speed field in the model object
 * returned to this app, so this is deliberately an estimate based on the model
 * family/name. It is UI guidance only and never affects routing.
 */
val OpenRouterModel.speedTier: ModelSpeedTier
    get() {
        val key = "${id} ${name.orEmpty()}".lowercase()
        return when {
            listOf("flash-lite", "flash lite", "nano", "mini", "small", "haiku", "instant", "fast")
                .any(key::contains) -> ModelSpeedTier.VERY_FAST

            listOf("flash", "turbo", "swift", "air")
                .any(key::contains) -> ModelSpeedTier.FAST

            listOf("opus", "pro", "max", "reasoning", "deepseek-r1", "/r1", "o3", "o4")
                .any(key::contains) -> ModelSpeedTier.SLOWER

            else -> ModelSpeedTier.BALANCED
        }
    }

val OpenRouterModel.taskTier: ModelTaskTier
    get() {
        val key = "${id} ${name.orEmpty()}".lowercase()
        return when {
            listOf("flash-lite", "flash lite", "nano", "mini", "small", "haiku")
                .any(key::contains) -> ModelTaskTier.SIMPLE

            supportsReasoning ||
                listOf("opus", "pro", "max", "reasoning", "deepseek-r1", "/r1", "o3", "o4")
                    .any(key::contains) -> ModelTaskTier.COMPLEX

            else -> ModelTaskTier.MEDIUM
        }
    }

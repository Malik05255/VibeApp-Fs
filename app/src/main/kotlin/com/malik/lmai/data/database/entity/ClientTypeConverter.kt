package com.malik.lmai.data.database.entity

import androidx.room.TypeConverter
import com.malik.lmai.data.model.ClientType

class ClientTypeConverter {

    @TypeConverter
    fun fromClientType(
        type: ClientType?,
    ): String {

        return type?.name
            ?: ClientType.OPEN_ROUTER.name
    }

    @TypeConverter
    fun toClientType(
        value: String?,
    ): ClientType {

        val normalized =
            value
                ?.trim()
                ?.uppercase()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return ClientType.OPEN_ROUTER

        return when (normalized) {

            /*
             * Compatibility with old OpenRouter naming.
             */
            "OPENROUTER",
            "OPEN_ROUTER" ->
                ClientType.OPEN_ROUTER

            /*
             * Compatibility aliases for Google.
             */
            "GOOGLE",
            "GEMINI",
            "GOOGLE_AI_STUDIO" ->
                ClientType.GOOGLE_AI_STUDIO

            /*
             * All current enum values:
             *
             * OPENAI
             * ANTHROPIC
             * QWEN
             * KIMI
             * MINIMAX
             * DEEPSEEK
             * CUSTOM
             *
             * and future values with matching names.
             */
            else ->
                ClientType.entries
                    .firstOrNull { type ->
                        type.name == normalized
                    }
                    ?: ClientType.OPEN_ROUTER
        }
    }
}

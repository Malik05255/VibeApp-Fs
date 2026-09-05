package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType

/**
 * Separates user-managed API providers from lm_AI's hidden free fallback pool.
 *
 * Provider vendor identity (Gemini, Groq, OpenRouter, etc.) is intentionally
 * independent from provider origin. This allows an external Gemini API and an
 * internal/free Gemini route to coexist without sharing enabled state, quota,
 * failure state, model selection, or credentials.
 *
 * The origin is encoded in PlatformV2.provider to avoid a Room schema migration.
 */
enum class AiProviderOrigin {
    INTERNAL_FREE,
    EXTERNAL;

    companion object {
        private const val INTERNAL_PREFIX = "internal:"
        private const val EXTERNAL_PREFIX = "external:"

        fun internalProviderCode(providerId: String): String =
            INTERNAL_PREFIX + normalizeProviderId(providerId)

        fun externalProviderCode(providerId: String): String =
            EXTERNAL_PREFIX + normalizeProviderId(providerId)

        fun baseProviderId(rawProvider: String?): String? {
            val raw = rawProvider?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                ?: return null

            return when {
                raw.startsWith(INTERNAL_PREFIX) -> raw.removePrefix(INTERNAL_PREFIX)
                raw.startsWith(EXTERNAL_PREFIX) -> raw.removePrefix(EXTERNAL_PREFIX)
                else -> raw
            }.takeIf { it.isNotBlank() }
        }

        fun of(platform: PlatformV2): AiProviderOrigin {
            val raw = platform.provider?.trim()?.lowercase().orEmpty()
            return when {
                raw.startsWith(INTERNAL_PREFIX) -> INTERNAL_FREE
                raw.startsWith(EXTERNAL_PREFIX) -> EXTERNAL

                // Google AI Studio and OpenRouter entries are created from the
                // user-facing API setup screen. Treat legacy untagged rows from
                // those transports as external even when the selected model is
                // on a free tier. Hidden lm_AI routes must use internal:... .
                platform.compatibleType == ClientType.GOOGLE_AI_STUDIO -> EXTERNAL
                platform.compatibleType == ClientType.OPEN_ROUTER -> EXTERNAL

                // Backward compatibility for hidden free-provider rows created
                // before explicit origin tagging existed.
                platform.isFree == true -> INTERNAL_FREE
                else -> EXTERNAL
            }
        }

        private fun normalizeProviderId(providerId: String): String =
            providerId.trim().lowercase().removePrefix(INTERNAL_PREFIX).removePrefix(EXTERNAL_PREFIX)
    }
}

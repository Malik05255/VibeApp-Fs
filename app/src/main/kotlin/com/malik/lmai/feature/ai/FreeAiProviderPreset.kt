package com.malik.lmai.feature.ai

/**
 * Lightweight presets for user-managed OpenAI-compatible API providers.
 *
 * A preset may point to a provider that also exists in lm_AI's hidden free
 * fallback pool, but setup-screen entries are always tagged EXTERNAL so the two
 * routes never share credentials, quota, enabled state, or failure state.
 */
enum class FreeAiProviderPreset(
    val code: String,
    val displayName: String,
    val apiUrl: String,
    val apiKeyHelpUrl: String,
) {
    GROQ(
        code = AiProviderOrigin.externalProviderCode("groq"),
        displayName = "Groq",
        apiUrl = "https://api.groq.com/openai/v1",
        apiKeyHelpUrl = "https://console.groq.com/keys",
    ),
    MISTRAL(
        code = AiProviderOrigin.externalProviderCode("mistral"),
        displayName = "Mistral AI",
        apiUrl = "https://api.mistral.ai/v1",
        apiKeyHelpUrl = "https://console.mistral.ai/api-keys",
    ),
    CLOUDFLARE(
        code = AiProviderOrigin.externalProviderCode("cloudflare"),
        displayName = "Cloudflare Workers AI",
        apiUrl = "https://api.cloudflare.com/client/v4/accounts/YOUR_ACCOUNT_ID/ai/v1",
        apiKeyHelpUrl = "https://dash.cloudflare.com/?to=/:account/ai/workers-ai",
    ),
}

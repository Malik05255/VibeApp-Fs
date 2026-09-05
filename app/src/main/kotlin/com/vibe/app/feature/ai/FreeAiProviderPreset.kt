package com.vibe.app.feature.ai

/**
 * Lightweight presets for free-tier/OpenAI-compatible providers.
 *
 * These presets intentionally contain only connection metadata. Models are not
 * pinned here because provider catalogs change frequently; the user can enter a
 * current model ID in the existing model step without requiring an app update.
 */
enum class FreeAiProviderPreset(
    val code: String,
    val displayName: String,
    val apiUrl: String,
    val apiKeyHelpUrl: String,
) {
    GROQ(
        code = "groq",
        displayName = "Groq",
        apiUrl = "https://api.groq.com/openai/v1",
        apiKeyHelpUrl = "https://console.groq.com/keys",
    ),
    MISTRAL(
        code = "mistral",
        displayName = "Mistral AI",
        apiUrl = "https://api.mistral.ai/v1",
        apiKeyHelpUrl = "https://console.mistral.ai/api-keys",
    ),
    CLOUDFLARE(
        code = "cloudflare",
        displayName = "Cloudflare Workers AI",
        apiUrl = "https://api.cloudflare.com/client/v4/accounts/YOUR_ACCOUNT_ID/ai/v1",
        apiKeyHelpUrl = "https://dash.cloudflare.com/?to=/:account/ai/workers-ai",
    ),
}

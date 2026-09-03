package com.vibe.app.data.network

import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.dto.OpenRouterModelsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterModelsAPI @Inject constructor(
    private val client: HttpClient,
) {
    /**
     * Loads the live OpenRouter text-model catalog and keeps only models that
     * currently advertise tool calling. This prevents chat-only, image/audio,
     * stale hard-coded, or otherwise unsuitable entries from being offered to
     * VibeApp's app-building agent.
     */
    suspend fun fetchOpenRouterModels(
        apiKey: String,
        isFreeOnly: Boolean,
    ): List<OpenRouterModel> {
        val response = requestModels(apiKey)

        val compatibleModels = response.data.asSequence()
            .filter { it.supportsTools }

        return if (isFreeOnly) {
            compatibleModels
                .filter { it.pricing?.isFree == true }
                .sortedBy { it.name?.takeIf(String::isNotBlank) ?: it.id }
                .toList()
        } else {
            compatibleModels
                .filter { it.pricing?.isFree == false }
                .sortedWith(
                    compareBy<OpenRouterModel> {
                        it.pricing?.averagePrice ?: Double.MAX_VALUE
                    }.thenBy {
                        it.name?.takeIf(String::isNotBlank) ?: it.id
                    }
                )
                .toList()
        }
    }

    suspend fun getModels(token: String): OpenRouterModelsResponse =
        requestModels(token)

    private suspend fun requestModels(apiKey: String): OpenRouterModelsResponse {
        val normalizedApiKey = normalizeApiKey(apiKey)

        return client.get(OPENROUTER_MODELS_URL) {
            header("Authorization", "Bearer $normalizedApiKey")
            header("HTTP-Referer", APP_REFERER)
            header("X-Title", APP_TITLE)
        }.body()
    }

    private fun normalizeApiKey(rawApiKey: String): String {
        val trimmed = rawApiKey.trim()
        val normalized = if (
            trimmed.startsWith(
                prefix = "Bearer ",
                ignoreCase = true,
            )
        ) {
            trimmed.substring("Bearer ".length).trim()
        } else {
            trimmed
        }

        require(normalized.isNotBlank()) {
            "OpenRouter API key is empty"
        }

        return normalized
    }

    companion object {
        /**
         * Official OpenRouter endpoint with server-side capability filtering.
         * OpenRouter documents both output_modalities=text and
         * supported_parameters=tools for this endpoint.
         */
        private const val OPENROUTER_MODELS_URL =
            "https://openrouter.ai/api/v1/models?output_modalities=text&supported_parameters=tools"

        private const val APP_REFERER = "https://vibe.app"
        private const val APP_TITLE = "Vibe App"
    }
}

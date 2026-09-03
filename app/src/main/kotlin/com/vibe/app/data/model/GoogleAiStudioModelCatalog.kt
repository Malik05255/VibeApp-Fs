package com.vibe.app.data.model

import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.dto.OpenRouterPricing

/**
 * Curated Google AI Studio catalog for the OpenAI-compatible chat endpoint.
 *
 * Prices are the Google Gemini Developer API Standard paid-tier prices published
 * for August 2026, expressed internally as USD per token to match OpenRouterPricing.
 *
 * The free list represents models whose Standard Free Tier is published as free.
 * Actual availability/rate limits can still vary by Google account, region, and quota.
 */
object GoogleAiStudioModelCatalog {

    fun models(isFreeOnly: Boolean): List<OpenRouterModel> =
        if (isFreeOnly) freeModels else paidModels

    private val freeModels = listOf(
        freeModel(
            id = "gemini-3.7-flash",
            name = "Gemini 3.7 Flash",
            description = "Fast, capable Gemini model for agentic workflows and multimodal reasoning.",
            reasoning = true,
        ),
        freeModel(
            id = "gemini-3.5-flash",
            name = "Gemini 3.5 Flash",
            description = "High-intelligence Flash model optimized for speed.",
            reasoning = true,
        ),
        freeModel(
            id = "gemini-3.5-flash-lite",
            name = "Gemini 3.5 Flash-Lite",
            description = "Cost-efficient, high-throughput model for simpler application tasks.",
            reasoning = false,
        ),
        freeModel(
            id = "gemini-3.1-flash-lite",
            name = "Gemini 3.1 Flash-Lite",
            description = "Efficient model for high-volume and straightforward tasks.",
            reasoning = false,
        ),
    )

    private val paidModels = listOf(
        paidModel(
            id = "gemini-3.5-flash-lite",
            name = "Gemini 3.5 Flash-Lite",
            description = "Fast and economical for simple apps and high-volume workloads.",
            inputPerMillion = 0.30,
            outputPerMillion = 2.50,
            reasoning = false,
        ),
        paidModel(
            id = "gemini-3.1-flash-lite",
            name = "Gemini 3.1 Flash-Lite",
            description = "Low-cost option for simple and repetitive application work.",
            inputPerMillion = 0.25,
            outputPerMillion = 1.50,
            reasoning = false,
        ),
        paidModel(
            id = "gemini-3.7-flash",
            name = "Gemini 3.7 Flash",
            description = "Strong balance of speed and capability for medium-to-complex app work.",
            inputPerMillion = 0.75,
            outputPerMillion = 3.75,
            reasoning = true,
        ),
        paidModel(
            id = "gemini-3.5-flash",
            name = "Gemini 3.5 Flash",
            description = "Fast frontier-capability model for complex agentic tasks.",
            inputPerMillion = 1.50,
            outputPerMillion = 9.00,
            reasoning = true,
        ),
        paidModel(
            id = "gemini-3.1-pro-preview",
            name = "Gemini 3.1 Pro Preview",
            description = "Highest-capability option here for difficult coding and long-horizon agent tasks.",
            inputPerMillion = 2.00,
            outputPerMillion = 12.00,
            reasoning = true,
        ),
    )

    private fun freeModel(
        id: String,
        name: String,
        description: String,
        reasoning: Boolean,
    ) = OpenRouterModel(
        id = id,
        name = name,
        description = description,
        contextLength = 1_048_576,
        pricing = OpenRouterPricing(
            prompt = "0",
            completion = "0",
            request = "0",
        ),
        supportedParameters = buildSupportedParameters(reasoning),
    )

    private fun paidModel(
        id: String,
        name: String,
        description: String,
        inputPerMillion: Double,
        outputPerMillion: Double,
        reasoning: Boolean,
    ) = OpenRouterModel(
        id = id,
        name = name,
        description = description,
        contextLength = 1_048_576,
        pricing = OpenRouterPricing(
            prompt = (inputPerMillion / 1_000_000.0).toString(),
            completion = (outputPerMillion / 1_000_000.0).toString(),
            request = "0",
        ),
        supportedParameters = buildSupportedParameters(reasoning),
    )

    private fun buildSupportedParameters(reasoning: Boolean): List<String> =
        buildList {
            add("tools")
            add("tool_choice")
            add("temperature")
            add("top_p")
            add("structured_outputs")
            if (reasoning) add("reasoning")
        }
}

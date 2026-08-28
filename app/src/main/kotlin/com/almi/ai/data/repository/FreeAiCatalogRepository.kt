package com.almi.ai.data.repository

import com.almi.ai.data.network.NetworkClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class FreeAiCatalogRepository @Inject constructor(
    private val networkClient: NetworkClient,
) {
    suspend fun discover(limitPerMedia: Int = MAX_CANDIDATES): Result<FreeAiCatalog> =
        withContext(Dispatchers.IO) {
            runCatching {
                FreeAiCatalog(
                    imageModels = fetchModels(MediaOutput.IMAGE, limitPerMedia),
                    videoModels = fetchModels(MediaOutput.VIDEO, limitPerMedia),
                )
            }
        }

    private suspend fun fetchModels(
        output: MediaOutput,
        limit: Int,
    ): List<FreeAiCandidate> {
        val response = networkClient().get("$MODELS_URL?output_modalities=${output.apiValue}")
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("free_catalog_http_${response.status.value}")
        }

        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        val candidates = buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name").ifBlank { id }
                val architecture = item.optJSONObject("architecture") ?: continue
                val inputModalities = architecture.optJSONArray("input_modalities") ?: JSONArray()
                val outputModalities = architecture.optJSONArray("output_modalities") ?: JSONArray()

                if (!isExplicitlyFree(id, name)) continue
                if (!inputModalities.containsString("image")) continue
                if (!outputModalities.containsString(output.apiValue)) continue

                add(
                    FreeAiCandidate(
                        id = id,
                        name = name,
                        created = item.optLong("created", 0L),
                        qualityScore = qualityScore(id, output),
                    )
                )
            }
        }

        return candidates
            .sortedWith(
                compareByDescending<FreeAiCandidate> { it.qualityScore }
                    .thenByDescending { it.created }
            )
            .take(limit.coerceIn(1, MAX_CANDIDATES))
    }

    private fun isExplicitlyFree(id: String, name: String): Boolean =
        id.endsWith(":free", ignoreCase = true) ||
            name.contains("(free)", ignoreCase = true)

    private fun qualityScore(id: String, output: MediaOutput): Int {
        val value = id.lowercase()
        val orderedFamilies = when (output) {
            MediaOutput.IMAGE -> listOf(
                "gemini", "gpt-image", "seedream", "qwen-image", "flux", "grok", "recraft", "krea"
            )
            MediaOutput.VIDEO -> listOf(
                "wan-3", "wan-2.7", "wan-2.6", "seedance", "veo", "kling", "grok", "hailuo", "runway"
            )
        }
        val index = orderedFamilies.indexOfFirst { value.contains(it) }
        return if (index == -1) 1 else 100 - index * 8
    }

    private fun JSONArray.containsString(value: String): Boolean {
        for (index in 0 until length()) {
            if (optString(index).equals(value, ignoreCase = true)) return true
        }
        return false
    }

    private enum class MediaOutput(val apiValue: String) {
        IMAGE("image"),
        VIDEO("video"),
    }

    companion object {
        const val MAX_CANDIDATES = 30
        private const val MODELS_URL = "https://openrouter.ai/api/v1/models"
    }
}

data class FreeAiCatalog(
    val imageModels: List<FreeAiCandidate>,
    val videoModels: List<FreeAiCandidate>,
)

data class FreeAiCandidate(
    val id: String,
    val name: String,
    val created: Long,
    val qualityScore: Int,
)

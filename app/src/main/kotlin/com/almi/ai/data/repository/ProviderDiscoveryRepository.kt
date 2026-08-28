package com.almi.ai.data.repository

import com.almi.ai.data.network.NetworkClient
import com.almi.ai.data.preferences.ApiKeyVault
import io.ktor.client.request.get
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DiscoveredProvider(
    val id: String,
    val name: String,
    val freeOffer: String,
    val supportsText: Boolean,
    val supportsImage: Boolean,
    val supportsVideo: Boolean,
    val reachable: Boolean,
    val connected: Boolean,
    val integrated: Boolean,
    val score: Int,
)

data class ProviderDiscoveryResult(
    val providers: List<DiscoveredProvider> = emptyList(),
    val checkedAt: Long = System.currentTimeMillis(),
)

/**
 * Discovers known compatible AI API services and verifies that their public API edge is reachable.
 *
 * This intentionally does NOT claim that an arbitrary internet service is "connected" merely
 * because its website responds. Connected means ALMI has a valid credential and a supported
 * runtime adapter. Shipping shared provider secrets inside an APK would be insecure and is never
 * used as a shortcut.
 */
class ProviderDiscoveryRepository @Inject constructor(
    private val networkClient: NetworkClient,
    private val apiKeyVault: ApiKeyVault,
    private val openRouterCatalogRepository: OpenRouterCatalogRepository,
) {
    suspend fun discoverTop(limit: Int = 5): Result<ProviderDiscoveryResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val openRouterKey = apiKeyVault.activeOpenRouterKeys().firstOrNull()?.secret
                val openRouterConnected = openRouterKey?.let {
                    openRouterCatalogRepository.loadKeyStatus(it).getOrNull()?.connected == true
                } == true

                val checked = coroutineScope {
                    REGISTRY.map { entry ->
                        async {
                            val reachable = probe(entry.probeUrl)
                            val connected = entry.id == OPENROUTER_ID && openRouterConnected
                            val availabilityScore = if (reachable) 25 else -40
                            val connectionScore = if (connected) 35 else 0
                            DiscoveredProvider(
                                id = entry.id,
                                name = entry.name,
                                freeOffer = entry.freeOffer,
                                supportsText = entry.supportsText,
                                supportsImage = entry.supportsImage,
                                supportsVideo = entry.supportsVideo,
                                reachable = reachable,
                                connected = connected,
                                integrated = entry.id == OPENROUTER_ID,
                                score = entry.baseScore + availabilityScore + connectionScore,
                            )
                        }
                    }.awaitAll()
                }

                ProviderDiscoveryResult(
                    providers = checked
                        .sortedWith(
                            compareByDescending<DiscoveredProvider> { it.connected }
                                .thenByDescending { it.score }
                                .thenBy { it.name }
                        )
                        .take(limit.coerceIn(1, 5)),
                )
            }
        }

    private suspend fun probe(url: String): Boolean =
        withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            runCatching {
                val response = networkClient().get(url)
                // 401/403 still proves that the provider API is alive; it does not mean connected.
                response.status.value in 200..499
            }.getOrDefault(false)
        } ?: false

    private data class RegistryEntry(
        val id: String,
        val name: String,
        val probeUrl: String,
        val freeOffer: String,
        val supportsText: Boolean,
        val supportsImage: Boolean,
        val supportsVideo: Boolean,
        val baseScore: Int,
    )

    companion object {
        const val OPENROUTER_ID = "openrouter"
        private const val PROBE_TIMEOUT_MS = 4_500L

        private val REGISTRY = listOf(
            RegistryEntry(
                id = OPENROUTER_ID,
                name = "OpenRouter",
                probeUrl = "https://openrouter.ai/api/v1/models/count",
                freeOffer = "Free models + provider fallback",
                supportsText = true,
                supportsImage = true,
                supportsVideo = true,
                baseScore = 100,
            ),
            RegistryEntry(
                id = "huggingface",
                name = "Hugging Face Inference Providers",
                probeUrl = "https://huggingface.co/api/models?limit=1",
                freeOffer = "Monthly starter credits",
                supportsText = true,
                supportsImage = true,
                supportsVideo = true,
                baseScore = 92,
            ),
            RegistryEntry(
                id = "pixazo",
                name = "Pixazo API",
                probeUrl = "https://gateway.pixazo.ai/",
                freeOffer = "Fair-use free image and video REST API",
                supportsText = false,
                supportsImage = true,
                supportsVideo = true,
                baseScore = 88,
            ),
            RegistryEntry(
                id = "cloudflare",
                name = "Cloudflare Workers AI",
                probeUrl = "https://api.cloudflare.com/client/v4/",
                freeOffer = "Daily free allocation",
                supportsText = true,
                supportsImage = true,
                supportsVideo = false,
                baseScore = 84,
            ),
            RegistryEntry(
                id = "google-ai-studio",
                name = "Google AI Studio",
                probeUrl = "https://generativelanguage.googleapis.com/v1beta/models",
                freeOffer = "Free tier on selected Gemini models",
                supportsText = true,
                supportsImage = true,
                supportsVideo = true,
                baseScore = 82,
            ),
        )
    }
}

package com.almi.ai.data.repository

import com.almi.ai.data.network.NetworkClient
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
    val supportsText: Boolean,
    val supportsImage: Boolean,
    val supportsVideo: Boolean,
    val reachable: Boolean,
    val connected: Boolean,
    val integrated: Boolean,
    val requiresPersonalApiKey: Boolean,
    val score: Int,
)

data class ProviderDiscoveryResult(
    val providers: List<DiscoveredProvider> = emptyList(),
    val checkedAt: Long = System.currentTimeMillis(),
) {
    val connectedProvider: DiscoveredProvider?
        get() = providers.firstOrNull { it.connected && it.integrated && !it.requiresPersonalApiKey }
}

/**
 * Discovery for ALMI's "Free AI" mode.
 *
 * Only providers that can be used without the user creating/pasting a personal API key belong
 * here. Connected means ALMI has a tested runtime path for at least one capability.
 *
 * AI Horde exposes anonymous community text access. Although AI Horde also has anonymous image
 * generation, ALMI deliberately does not advertise/use it for virtual try-on: anonymous image
 * jobs are community-hosted and are not an appropriate default route for private body photos.
 */
class ProviderDiscoveryRepository @Inject constructor(
    private val networkClient: NetworkClient,
) {
    suspend fun discoverTop(limit: Int = 5): Result<ProviderDiscoveryResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val checked = coroutineScope {
                    NO_PERSONAL_KEY_REGISTRY.map { entry ->
                        async {
                            val reachable = probe(entry.probeUrl)
                            val connected = reachable && entry.integrated && !entry.requiresPersonalApiKey
                            DiscoveredProvider(
                                id = entry.id,
                                name = entry.name,
                                supportsText = entry.supportsText,
                                supportsImage = entry.supportsImage,
                                supportsVideo = entry.supportsVideo,
                                reachable = reachable,
                                connected = connected,
                                integrated = entry.integrated,
                                requiresPersonalApiKey = entry.requiresPersonalApiKey,
                                score = entry.baseScore + if (connected) 40 else if (reachable) 10 else -50,
                            )
                        }
                    }.awaitAll()
                }

                ProviderDiscoveryResult(
                    providers = checked
                        .filterNot { it.requiresPersonalApiKey }
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
                response.status.value in 200..299
            }.getOrDefault(false)
        } ?: false

    private data class RegistryEntry(
        val id: String,
        val name: String,
        val probeUrl: String,
        val supportsText: Boolean,
        val supportsImage: Boolean,
        val supportsVideo: Boolean,
        val integrated: Boolean,
        val requiresPersonalApiKey: Boolean,
        val baseScore: Int,
    )

    companion object {
        const val AI_HORDE_ID = "ai-horde"
        const val AI_HORDE_ANONYMOUS_KEY = "0000000000"
        const val AI_HORDE_OPENAI_BASE_URL = "https://oai.aihorde.net"
        private const val PROBE_TIMEOUT_MS = 4_500L

        // Add a provider only after verifying both no-personal-key access and an ALMI runtime adapter.
        private val NO_PERSONAL_KEY_REGISTRY = listOf(
            RegistryEntry(
                id = AI_HORDE_ID,
                name = "AI Horde",
                probeUrl = "$AI_HORDE_OPENAI_BASE_URL/heartbeat",
                supportsText = true,
                supportsImage = false,
                supportsVideo = false,
                integrated = true,
                requiresPersonalApiKey = false,
                baseScore = 100,
            ),
        )
    }
}

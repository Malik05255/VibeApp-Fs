package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreeAiRouter @Inject constructor() {

    enum class Provider(
        val id: String,
        val priority: Int,
    ) {
        GEMINI("gemini", 0),
        GROQ("groq", 1),
        MISTRAL("mistral", 2),
        OPENROUTER("openrouter", 3),
        CLOUDFLARE("cloudflare", 4),
        LOCAL("local", 5),
        UNKNOWN("unknown", 99),
    }

    data class Candidate(
        val platform: PlatformV2,
        val provider: Provider,
    )

    fun orderedCandidates(platforms: List<PlatformV2>): List<Candidate> =
        platforms
            .mapNotNull { platform ->
                val provider = detectProvider(platform)
                if (!isFreeCandidate(platform, provider)) return@mapNotNull null
                Candidate(platform = platform, provider = provider)
            }
            .sortedWith(
                compareBy<Candidate> { it.provider.priority }
                    .thenBy { it.platform.name.lowercase() }
            )

    fun selectBest(platforms: List<PlatformV2>): PlatformV2? =
        orderedCandidates(platforms).firstOrNull()?.platform

    fun nextAfter(
        platforms: List<PlatformV2>,
        currentPlatformUid: String,
    ): PlatformV2? {
        val candidates = orderedCandidates(platforms)
        val currentIndex = candidates.indexOfFirst { it.platform.uid == currentPlatformUid }
        if (currentIndex < 0) return candidates.firstOrNull()?.platform
        return candidates.getOrNull(currentIndex + 1)?.platform
    }

    fun detectProvider(platform: PlatformV2): Provider {
        // New setup flows persist an explicit provider code. Treat it as the
        // authoritative identity so a display name or custom URL cannot
        // accidentally reclassify a provider.
        explicitProvider(platform.provider)?.let { return it }

        // Legacy rows may not have provider metadata. Keep a conservative
        // fingerprint fallback so existing installations continue to work.
        val fingerprint = buildString {
            append(platform.name)
            append(' ')
            append(platform.apiUrl)
        }.lowercase()

        return when {
            "gemini" in fingerprint || "googleapis.com" in fingerprint -> Provider.GEMINI
            "groq" in fingerprint -> Provider.GROQ
            "mistral" in fingerprint -> Provider.MISTRAL
            "openrouter" in fingerprint -> Provider.OPENROUTER
            "cloudflare" in fingerprint || "workers.ai" in fingerprint -> Provider.CLOUDFLARE
            "local" in fingerprint || "aicore" in fingerprint || "nano" in fingerprint -> Provider.LOCAL
            else -> Provider.UNKNOWN
        }
    }

    private fun explicitProvider(rawProvider: String?): Provider? {
        val normalized = rawProvider
            ?.trim()
            ?.lowercase()
            ?.replace("_", "")
            ?.replace("-", "")
            ?.replace(" ", "")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return when (normalized) {
            "gemini", "google", "googleaistudio" -> Provider.GEMINI
            "groq" -> Provider.GROQ
            "mistral", "mistralai" -> Provider.MISTRAL
            "openrouter" -> Provider.OPENROUTER
            "cloudflare", "cloudflareworkersai", "workersai" -> Provider.CLOUDFLARE
            "local", "aicore", "nano" -> Provider.LOCAL
            else -> null
        }
    }

    fun isFreeCandidate(
        platform: PlatformV2,
        provider: Provider = detectProvider(platform),
    ): Boolean {
        if (provider == Provider.UNKNOWN) return false
        if (provider == Provider.LOCAL) return true

        // Explicit paid selection always wins. This is important for catalog
        // providers such as OpenRouter where the same API key can access both
        // paid and free models.
        if (platform.isFree == false) return false
        if (platform.isFree == true) return true

        // Legacy rows created before isFree/provider metadata was persisted can
        // still participate when they are a known free-tier provider with a key.
        return provider in setOf(
            Provider.GEMINI,
            Provider.GROQ,
            Provider.MISTRAL,
            Provider.OPENROUTER,
            Provider.CLOUDFLARE,
        ) && !platform.token.isNullOrBlank()
    }
}

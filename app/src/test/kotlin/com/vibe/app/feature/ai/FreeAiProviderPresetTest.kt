package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiProviderPresetTest {

    private val router = FreeAiRouter()

    @Test
    fun `preset provider codes map to deterministic free router providers`() {
        val expected = mapOf(
            FreeAiProviderPreset.GROQ to FreeAiProvider.GROQ,
            FreeAiProviderPreset.MISTRAL to FreeAiProvider.MISTRAL,
            FreeAiProviderPreset.CLOUDFLARE to FreeAiProvider.CLOUDFLARE,
        )

        expected.forEach { (preset, provider) ->
            val platform = PlatformV2(
                name = preset.displayName,
                compatibleType = ClientType.CUSTOM,
                apiUrl = preset.apiUrl,
                token = "key",
                model = "model",
                provider = preset.code,
                isFree = true,
            )

            assertEquals(provider, router.detectProvider(platform))
            assertTrue(router.isFreeCandidate(platform))
        }
    }

    @Test
    fun `preset endpoints are non blank and use https`() {
        FreeAiProviderPreset.entries.forEach { preset ->
            assertTrue(preset.apiUrl.startsWith("https://"))
            assertTrue(preset.apiKeyHelpUrl.startsWith("https://"))
        }
    }
}

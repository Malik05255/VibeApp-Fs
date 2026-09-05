package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiProviderPresetTest {

    private val router = FreeAiRouter()

    @Test
    fun `setup presets keep vendor identity but are external routes`() {
        val expected = mapOf(
            FreeAiProviderPreset.GROQ to FreeAiRouter.Provider.GROQ,
            FreeAiProviderPreset.MISTRAL to FreeAiRouter.Provider.MISTRAL,
            FreeAiProviderPreset.CLOUDFLARE to FreeAiRouter.Provider.CLOUDFLARE,
        )

        expected.forEach { (preset, provider) ->
            val platform = PlatformV2(
                name = preset.displayName,
                compatibleType = ClientType.CUSTOM,
                apiUrl = preset.apiUrl,
                token = "user-key",
                model = "model",
                provider = preset.code,
                isFree = true,
            )

            assertEquals(provider, router.detectProvider(platform))
            assertTrue(router.isExternal(platform))
            assertFalse(router.isFreeCandidate(platform))
        }
    }

    @Test
    fun `preset endpoints are non blank and use https`() {
        FreeAiProviderPreset.values().forEach { preset ->
            assertTrue(preset.code.startsWith("external:"))
            assertTrue(preset.apiUrl.startsWith("https://"))
            assertTrue(preset.apiKeyHelpUrl.startsWith("https://"))
        }
    }
}

package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeAiRouterTest {

    private val router = FreeAiRouter()

    @Test
    fun `orders hidden internal free providers by preferred fallback order`() {
        val platforms = listOf(
            platform(name = "OpenRouter Free", provider = "internal:openrouter", token = "or-key", isFree = true),
            platform(name = "Groq Free", provider = "internal:groq", token = "groq-key", isFree = true),
            platform(name = "Gemini Free", provider = "internal:gemini", token = "gem-key", isFree = true),
        )

        val result = router.orderedCandidates(platforms)

        assertEquals(
            listOf(
                FreeAiRouter.Provider.GEMINI,
                FreeAiRouter.Provider.GROQ,
                FreeAiRouter.Provider.OPENROUTER,
            ),
            result.map { it.provider },
        )
    }

    @Test
    fun `nextAfter advances only through internal free providers`() {
        val gemini = platform(name = "Gemini", provider = "internal:gemini", token = "g", isFree = true)
        val groq = platform(name = "Groq", provider = "internal:groq", token = "q", isFree = true)

        assertEquals(groq.uid, router.nextAfter(listOf(groq, gemini), gemini.uid)?.uid)
        assertNull(router.nextAfter(listOf(gemini, groq), groq.uid))
    }

    @Test
    fun `external and internal provider from same vendor never collide`() {
        val externalGemini = PlatformV2(
            name = "My Google AI Studio",
            compatibleType = ClientType.GOOGLE_AI_STUDIO,
            enabled = true,
            apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            token = "user-key",
            model = "gemini-user-model",
            provider = "external:gemini",
            isFree = true,
        )
        val internalGemini = platform(
            name = "Hidden Gemini",
            provider = "internal:gemini",
            token = "internal-key",
            isFree = true,
        )

        assertEquals(FreeAiRouter.Provider.GEMINI, router.detectProvider(externalGemini))
        assertEquals(FreeAiRouter.Provider.GEMINI, router.detectProvider(internalGemini))
        assertTrue(router.isExternal(externalGemini))
        assertFalse(router.isFreeCandidate(externalGemini))
        assertTrue(router.isInternalFree(internalGemini))
        assertTrue(router.isFreeCandidate(internalGemini))
    }

    @Test
    fun `legacy google ai studio free tier remains external`() {
        val legacyExternalGemini = PlatformV2(
            name = "Google AI Studio",
            compatibleType = ClientType.GOOGLE_AI_STUDIO,
            apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            token = "user-key",
            model = "gemini-model",
            provider = "gemini",
            isFree = true,
        )

        assertTrue(router.isExternal(legacyExternalGemini))
        assertFalse(router.isFreeCandidate(legacyExternalGemini))
    }

    @Test
    fun `unknown external platform is not a free candidate`() {
        val paid = platform(
            name = "Private endpoint",
            provider = "external:custom",
            token = "private-key",
            isFree = false,
        )

        assertEquals(emptyList<FreeAiRouter.Candidate>(), router.orderedCandidates(listOf(paid)))
    }

    @Test
    fun `explicit provider code wins over misleading display name and url`() {
        val explicitGroq = PlatformV2(
            name = "Gemini-looking custom name",
            compatibleType = ClientType.CUSTOM,
            apiUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            token = "key",
            model = "model",
            provider = "external:groq",
            isFree = true,
        )

        assertEquals(
            FreeAiRouter.Provider.GROQ,
            router.detectProvider(explicitGroq),
        )
        assertFalse(router.isFreeCandidate(explicitGroq))
    }

    private fun platform(
        name: String,
        provider: String,
        token: String?,
        isFree: Boolean,
    ) = PlatformV2(
        name = name,
        compatibleType = ClientType.CUSTOM,
        apiUrl = "https://example.test/v1",
        token = token,
        model = "test-model",
        provider = provider,
        isFree = isFree,
    )
}

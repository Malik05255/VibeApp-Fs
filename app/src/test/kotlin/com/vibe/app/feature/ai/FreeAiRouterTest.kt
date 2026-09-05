package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FreeAiRouterTest {

    private val router = FreeAiRouter()

    @Test
    fun `orders configured free providers by preferred fallback order`() {
        val platforms = listOf(
            platform(name = "OpenRouter Free", provider = "openrouter", token = "or-key", isFree = true),
            platform(name = "Groq Free", provider = "groq", token = "groq-key", isFree = true),
            platform(name = "Gemini Free", provider = "gemini", token = "gem-key", isFree = true),
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
    fun `nextAfter advances to next configured provider`() {
        val gemini = platform(name = "Gemini", provider = "gemini", token = "g", isFree = true)
        val groq = platform(name = "Groq", provider = "groq", token = "q", isFree = true)

        assertEquals(groq.uid, router.nextAfter(listOf(groq, gemini), gemini.uid)?.uid)
        assertNull(router.nextAfter(listOf(gemini, groq), groq.uid))
    }

    @Test
    fun `unknown paid platform is not a free candidate`() {
        val paid = platform(
            name = "Private endpoint",
            provider = "custom",
            token = "private-key",
            isFree = false,
        )

        assertEquals(emptyList<FreeAiRouter.Candidate>(), router.orderedCandidates(listOf(paid)))
    }

    private fun platform(
        name: String,
        provider: String,
        token: String?,
        isFree: Boolean,
    ) = PlatformV2(
        name = name,
        compatibleType = ClientType.OPENAI,
        apiUrl = "https://example.test/v1",
        token = token,
        model = "test-model",
        provider = provider,
        isFree = isFree,
    )
}

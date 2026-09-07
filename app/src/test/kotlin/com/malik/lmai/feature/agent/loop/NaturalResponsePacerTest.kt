package com.malik.lmai.feature.agent.loop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalResponsePacerTest {

    @Test
    fun `completed-only answer is recovered when nothing streamed`() {
        assertEquals(
            "يعرف عادل إمام كممثل مصري بارز.",
            NaturalResponsePacer.missingCompletedText(
                streamedText = "",
                completedText = "يعرف عادل إمام كممثل مصري بارز.",
            ),
        )
    }

    @Test
    fun `completed payload does not duplicate already streamed text`() {
        assertEquals(
            " اليوم؟",
            NaturalResponsePacer.missingCompletedText(
                streamedText = "كيف أساعدك",
                completedText = "كيف أساعدك اليوم؟",
            ),
        )
    }

    @Test
    fun `natural chunks preserve exact response`() {
        val response = "هذا رد عربي طويل نسبيًا حتى يظهر للمستخدم بشكل انسيابي بدل ظهوره دفعة واحدة."
        val chunks = NaturalResponsePacer.chunks(response, maxChunkChars = 18)

        assertTrue(chunks.size > 1)
        assertEquals(response, chunks.joinToString(separator = ""))
    }
}

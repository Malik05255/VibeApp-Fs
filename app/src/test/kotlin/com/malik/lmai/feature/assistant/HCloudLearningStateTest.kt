package com.malik.lmai.feature.assistant

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HCloudLearningStateTest {

    @Test
    fun `merge is monotonic and never lets stale state reduce learning`() {
        val local = state(
            first = 200L,
            last = 900L,
            turns = 9L,
            directness = 4,
            tags = mapOf("android" to 3),
        )
        val cloud = state(
            first = 100L,
            last = 800L,
            turns = 12L,
            directness = 2,
            tags = mapOf("android" to 2, "github" to 5),
        )

        val merged = local.merge(cloud)

        assertEquals(100L, merged.firstMetAtMs)
        assertEquals(900L, merged.lastInteractionAtMs)
        assertEquals(12L, merged.turnCount)
        assertEquals(4, merged.directnessScore)
        assertEquals(3, merged.interestTags["android"])
        assertEquals(5, merged.interestTags["github"])
    }

    @Test
    fun `baseline json carries aggregates only and filters unknown tags`() {
        val baseline = state(
            first = 100L,
            last = 200L,
            turns = 7L,
            directness = 3,
            tags = mapOf("android" to 4, "private-topic" to 99),
        ).toBaselineJson()

        assertTrue("first_met_at_ms" in baseline)
        assertTrue("interest_tags" in baseline)
        assertFalse("memories" in baseline)
        assertFalse("prompt" in baseline)
        assertFalse("response" in baseline)
        assertFalse(baseline.toString().contains("private-topic"))
    }

    @Test
    fun `cloud json parser accepts only bounded portable fields`() {
        val parsed = HCloudLearningState.fromCloudJson(
            buildJsonObject {
                put("firstMetAtMs", 100L)
                put("lastInteractionAtMs", 300L)
                put("turnCount", 9L)
                put("directnessScore", 99)
                put("interactionSamples", 10L)
                put("interestTags", buildJsonObject {
                    put("github", 4)
                    put("unknown", 80)
                })
                put("rawConversation", "must be ignored")
            }
        )

        assertNotNull(parsed)
        assertEquals(20, parsed!!.directnessScore)
        assertEquals(4, parsed.interestTags["github"])
        assertFalse("unknown" in parsed.interestTags)
    }

    private fun state(
        first: Long,
        last: Long,
        turns: Long,
        directness: Int,
        tags: Map<String, Int>,
    ) = HCloudLearningState(
        firstMetAtMs = first,
        lastInteractionAtMs = last,
        turnCount = turns,
        directnessScore = directness,
        technicalDepthScore = 0,
        programmingInterestScore = 0,
        solutionBreadthScore = 0,
        arabicPreferenceScore = 0,
        concisePreferenceScore = 0,
        codeReplacementPreferenceScore = 0,
        interactionSamples = turns,
        interestTags = tags,
    )
}

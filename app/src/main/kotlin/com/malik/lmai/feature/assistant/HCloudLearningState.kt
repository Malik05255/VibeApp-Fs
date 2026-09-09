package com.malik.lmai.feature.assistant

import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Portable H-owned learning state.
 *
 * This intentionally contains only bounded aggregate signals. It must never contain raw
 * conversation text, prompts, model responses, attachment data, credentials, or provider
 * history. That keeps H's durable learning independent from every external model.
 */
data class HCloudLearningState(
    val firstMetAtMs: Long,
    val lastInteractionAtMs: Long,
    val turnCount: Long,
    val directnessScore: Int,
    val technicalDepthScore: Int,
    val programmingInterestScore: Int,
    val solutionBreadthScore: Int,
    val arabicPreferenceScore: Int,
    val concisePreferenceScore: Int,
    val codeReplacementPreferenceScore: Int,
    val interactionSamples: Long,
    val interestTags: Map<String, Int>,
) {
    fun toBaselineJson(): JsonObject = buildJsonObject {
        put("first_met_at_ms", firstMetAtMs.coerceAtLeast(1L))
        put("last_interaction_at_ms", lastInteractionAtMs.coerceAtLeast(firstMetAtMs.coerceAtLeast(1L)))
        put("turn_count", turnCount.coerceAtLeast(0L))
        put("directness_score", directnessScore.coerceIn(0, 20))
        put("technical_depth_score", technicalDepthScore.coerceIn(0, 20))
        put("programming_interest_score", programmingInterestScore.coerceIn(0, 20))
        put("solution_breadth_score", solutionBreadthScore.coerceIn(0, 20))
        put("arabic_preference_score", arabicPreferenceScore.coerceIn(0, 20))
        put("concise_preference_score", concisePreferenceScore.coerceIn(0, 20))
        put("code_replacement_preference_score", codeReplacementPreferenceScore.coerceIn(0, 20))
        put("interaction_samples", interactionSamples.coerceAtLeast(0L))
        put("interest_tags", buildJsonObject {
            interestTags
                .filterKeys(ALLOWED_INTEREST_TAGS::contains)
                .forEach { (tag, score) ->
                    if (score > 0) put(tag, score.coerceAtMost(1_000_000))
                }
        })
    }

    fun merge(other: HCloudLearningState): HCloudLearningState = HCloudLearningState(
        firstMetAtMs = minPositive(firstMetAtMs, other.firstMetAtMs),
        lastInteractionAtMs = max(lastInteractionAtMs, other.lastInteractionAtMs),
        turnCount = max(turnCount, other.turnCount),
        directnessScore = max(directnessScore, other.directnessScore).coerceIn(0, 20),
        technicalDepthScore = max(technicalDepthScore, other.technicalDepthScore).coerceIn(0, 20),
        programmingInterestScore = max(programmingInterestScore, other.programmingInterestScore).coerceIn(0, 20),
        solutionBreadthScore = max(solutionBreadthScore, other.solutionBreadthScore).coerceIn(0, 20),
        arabicPreferenceScore = max(arabicPreferenceScore, other.arabicPreferenceScore).coerceIn(0, 20),
        concisePreferenceScore = max(concisePreferenceScore, other.concisePreferenceScore).coerceIn(0, 20),
        codeReplacementPreferenceScore = max(
            codeReplacementPreferenceScore,
            other.codeReplacementPreferenceScore,
        ).coerceIn(0, 20),
        interactionSamples = max(interactionSamples, other.interactionSamples),
        interestTags = (interestTags.keys + other.interestTags.keys)
            .filter(ALLOWED_INTEREST_TAGS::contains)
            .associateWith { tag ->
                max(interestTags[tag] ?: 0, other.interestTags[tag] ?: 0)
                    .coerceIn(0, 1_000_000)
            }
            .filterValues { it > 0 },
    )

    fun syncSignature(): String = buildString {
        append(firstMetAtMs)
        append('|').append(lastInteractionAtMs)
        append('|').append(turnCount)
        append('|').append(directnessScore)
        append('|').append(technicalDepthScore)
        append('|').append(programmingInterestScore)
        append('|').append(solutionBreadthScore)
        append('|').append(arabicPreferenceScore)
        append('|').append(concisePreferenceScore)
        append('|').append(codeReplacementPreferenceScore)
        append('|').append(interactionSamples)
        interestTags.toSortedMap().forEach { (tag, score) ->
            append('|').append(tag).append('=').append(score)
        }
    }

    companion object {
        val ALLOWED_INTEREST_TAGS = setOf(
            "programming",
            "android",
            "github",
            "authentication",
            "ai",
            "ui-ux",
            "cloud",
        )

        fun fromCloudJson(value: JsonObject?): HCloudLearningState? {
            if (value == null) return null
            val first = value.long("firstMetAtMs") ?: return null
            val last = value.long("lastInteractionAtMs") ?: return null
            if (first <= 0L || last <= 0L) return null

            val tags = runCatching { value["interestTags"]?.jsonObject }
                .getOrNull()
                .orEmpty()
                .mapNotNull { (tag, rawValue) ->
                    if (tag !in ALLOWED_INTEREST_TAGS) return@mapNotNull null
                    val score = runCatching { rawValue.jsonPrimitive.intOrNull }.getOrNull()
                        ?.coerceIn(0, 1_000_000)
                        ?: return@mapNotNull null
                    if (score <= 0) null else tag to score
                }
                .toMap()

            return HCloudLearningState(
                firstMetAtMs = min(first, last),
                lastInteractionAtMs = max(first, last),
                turnCount = value.long("turnCount")?.coerceAtLeast(0L) ?: 0L,
                directnessScore = value.int("directnessScore").coerceIn(0, 20),
                technicalDepthScore = value.int("technicalDepthScore").coerceIn(0, 20),
                programmingInterestScore = value.int("programmingInterestScore").coerceIn(0, 20),
                solutionBreadthScore = value.int("solutionBreadthScore").coerceIn(0, 20),
                arabicPreferenceScore = value.int("arabicPreferenceScore").coerceIn(0, 20),
                concisePreferenceScore = value.int("concisePreferenceScore").coerceIn(0, 20),
                codeReplacementPreferenceScore = value.int("codeReplacementPreferenceScore").coerceIn(0, 20),
                interactionSamples = value.long("interactionSamples")?.coerceAtLeast(0L) ?: 0L,
                interestTags = tags,
            )
        }

        private fun JsonObject.int(key: String): Int =
            runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull() ?: 0

        private fun JsonObject.long(key: String): Long? =
            runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()

        private fun minPositive(a: Long, b: Long): Long = when {
            a <= 0L -> b
            b <= 0L -> a
            else -> min(a, b)
        }
    }
}

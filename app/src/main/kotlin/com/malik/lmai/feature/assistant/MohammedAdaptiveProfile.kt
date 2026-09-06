package com.malik.lmai.feature.assistant

import java.time.Duration
import java.time.Instant
import kotlin.math.max

/**
 * Mohammed has one global birth moment shared by every installation.
 * A friend installing the app later therefore gets Mohammed at the same global age,
 * while that friend's private relationship history still starts empty.
 */
object MohammedGlobalAge {
    // 2026-09-06T00:00:00Z — first public lm_AI Mohammed generation.
    private const val CORE_BIRTH_EPOCH_MS = 1788652800000L

    fun age(nowMs: Long = System.currentTimeMillis()): MohammedAge {
        val elapsedMs = max(0L, nowMs - CORE_BIRTH_EPOCH_MS)
        val duration = Duration.ofMillis(elapsedMs)
        val days = duration.toDays()
        val hours = duration.minusDays(days).toHours()
        return MohammedAge(
            birthEpochMs = CORE_BIRTH_EPOCH_MS,
            ageDays = days,
            ageHoursRemainder = hours,
        )
    }

    fun birthIsoUtc(): String = Instant.ofEpochMilli(CORE_BIRTH_EPOCH_MS).toString()
}

data class MohammedAge(
    val birthEpochMs: Long,
    val ageDays: Long,
    val ageHoursRemainder: Long,
) {
    val compactLabel: String
        get() = when {
            ageDays <= 0L -> "0 days"
            ageDays == 1L -> "1 day"
            else -> "$ageDays days"
        }
}

data class MohammedAdaptiveProfile(
    val directnessScore: Int = 0,
    val technicalDepthScore: Int = 0,
    val programmingInterestScore: Int = 0,
    val solutionBreadthScore: Int = 0,
    val arabicPreferenceScore: Int = 0,
    val concisePreferenceScore: Int = 0,
    val codeReplacementPreferenceScore: Int = 0,
    val interactionSamples: Long = 0L,
    val interestTags: Map<String, Int> = emptyMap(),
) {
    fun instructions(): String = buildString {
        append("Adaptive style learned locally for this owner: ")
        if (directnessScore >= 3) append("be direct; ")
        if (technicalDepthScore >= 3) append("prefer technical depth and concrete implementation details; ")
        if (programmingInterestScore >= 3) append("treat programming and software engineering as high-priority expertise; ")
        if (solutionBreadthScore >= 3) append("offer more than one viable solution when useful; ")
        if (arabicPreferenceScore >= 3) append("default to Arabic unless the task benefits from English code/terms; ")
        if (concisePreferenceScore >= 3) append("avoid unnecessary explanation; ")
        if (codeReplacementPreferenceScore >= 2) {
            append("when code is returned after external editing, compare it with the prior version, explain material changes, and replace the relevant project file automatically when project tools are available; ")
        }
        if (interestTags.isNotEmpty()) {
            append("recurring interests: ")
            append(
                interestTags.entries
                    .sortedByDescending { it.value }
                    .take(6)
                    .joinToString(", ") { it.key }
            )
            append("; ")
        }
    }.trim()
}

object MohammedAdaptiveLearner {
    private val programmingMarkers = listOf(
        "code", "kotlin", "java", "android", "gradle", "github", "repository", "repo",
        "برمجة", "كود", "مستودع", "جت هب", "github", "تطبيق", "apk", "خطأ", "bug",
    )
    private val technicalMarkers = listOf(
        "api", "oauth", "sha-1", "database", "sql", "supabase", "server", "cloud", "ci",
        "تقني", "تقنية", "سيرفر", "قاعدة", "خوارزم", "توقيع", "مفتاح", "نموذج",
    )
    private val directMarkers = listOf(
        "نفذ", "اصلح", "اتصل", "كمل", "بدون رجوع", "مباشرة", "لا تسأل", "نفّذ",
    )
    private val broadSolutionMarkers = listOf(
        "حلول اكثر", "حلول أكثر", "اكثر من حل", "بدائل", "options", "alternatives",
    )
    private val conciseMarkers = listOf(
        "مختصر", "باختصار", "بدون كثر كلام", "مباشر", "concise", "short",
    )
    private val replacementMarkers = listOf(
        "استبدله", "استبدال", "replace", "الكود المعدل", "الكود المعدّل", "قارنه", "قارن",
    )

    fun learn(
        previous: MohammedAdaptiveProfile,
        rawText: String,
    ): MohammedAdaptiveProfile {
        val text = MohammedMemoryPolicy.semanticUserText(rawText)
        if (text.isBlank()) return previous
        val normalized = text.lowercase()

        val arabicChars = text.count { it in '\u0600'..'\u06FF' }
        val latinChars = text.count { it in 'A'..'Z' || it in 'a'..'z' }

        val interests = previous.interestTags.toMutableMap()
        detectInterestTags(normalized).forEach { tag ->
            interests[tag] = (interests[tag] ?: 0) + 1
        }

        return previous.copy(
            directnessScore = bump(previous.directnessScore, directMarkers.any(normalized::contains)),
            technicalDepthScore = bump(previous.technicalDepthScore, technicalMarkers.any(normalized::contains)),
            programmingInterestScore = bump(previous.programmingInterestScore, programmingMarkers.any(normalized::contains)),
            solutionBreadthScore = bump(previous.solutionBreadthScore, broadSolutionMarkers.any(normalized::contains)),
            arabicPreferenceScore = bump(previous.arabicPreferenceScore, arabicChars > latinChars),
            concisePreferenceScore = bump(previous.concisePreferenceScore, conciseMarkers.any(normalized::contains)),
            codeReplacementPreferenceScore = bump(
                previous.codeReplacementPreferenceScore,
                replacementMarkers.any(normalized::contains),
            ),
            interactionSamples = previous.interactionSamples + 1L,
            interestTags = interests
                .entries
                .sortedByDescending { it.value }
                .take(12)
                .associate { it.key to it.value },
        )
    }

    private fun bump(value: Int, matched: Boolean): Int =
        if (matched) (value + 1).coerceAtMost(20) else value

    private fun detectInterestTags(text: String): Set<String> = buildSet {
        if (programmingMarkers.any(text::contains)) add("programming")
        if ("android" in text || "apk" in text || "kotlin" in text) add("android")
        if ("github" in text || "مستودع" in text) add("github")
        if ("oauth" in text || "تسجيل الدخول" in text) add("authentication")
        if ("ai" in text || "ذكاء" in text || "نموذج" in text) add("ai")
        if ("تصميم" in text || "واجهة" in text || "ui" in text || "ux" in text) add("ui-ux")
        if ("cloud" in text || "سحابة" in text || "سيرفر" in text) add("cloud")
    }
}

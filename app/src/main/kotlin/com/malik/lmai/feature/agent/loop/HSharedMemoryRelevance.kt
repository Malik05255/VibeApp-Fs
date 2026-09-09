package com.malik.lmai.feature.agent.loop

/** Lightweight retrieval over the bounded H Cloud memory snapshot. */
internal data class HSharedMemoryCandidate(
    val body: String,
    val category: String? = null,
)

internal object HSharedMemoryRelevance {
    private val tokenRegex = Regex("[\\p{L}\\p{N}]{2,}")
    private val arabicDiacritics = Regex("[\\u064B-\\u065F\\u0670]")

    private val stopWords = setOf(
        "انا", "انت", "انتي", "هو", "هي", "هم", "من", "عن", "في", "على", "الى", "الي",
        "وش", "ايش", "ما", "هل", "كيف", "وين", "عندي", "عندك", "لي", "ابي", "ابغى", "بدي",
        "تذكر", "تتذكر", "حفظت", "المحفوظ", "ذاكرتك", "ذاكرة",
        "the", "a", "an", "and", "or", "is", "are", "was", "were", "to", "of", "in", "on",
        "for", "with", "what", "do", "you", "my", "me", "about", "remember", "saved", "memory",
    )

    fun select(
        query: String,
        candidates: List<HSharedMemoryCandidate>,
        limit: Int,
    ): List<HSharedMemoryCandidate> {
        if (limit <= 0 || candidates.isEmpty()) return emptyList()
        val distinct = candidates.distinctBy { normalize(it.body) }
        val queryNormalized = normalize(query)
        val queryTokens = tokens(queryNormalized)
        if (queryTokens.isEmpty()) return distinct.take(limit)

        val categoryCues = categoryCues(queryNormalized)
        val scored = distinct.mapIndexed { index, candidate ->
            val memoryNormalized = normalize(candidate.body)
            val memoryTokens = tokens(memoryNormalized)
            val overlap = queryTokens.count { it in memoryTokens }
            val category = candidate.category?.trim()?.lowercase().orEmpty()
            val categoryBoost = if (category in categoryCues) 70 else 0
            val phraseBoost = when {
                queryNormalized.length >= 4 && memoryNormalized.contains(queryNormalized) -> 120
                memoryNormalized.length >= 4 && queryNormalized.contains(memoryNormalized) -> 80
                else -> 0
            }
            val score = overlap * 100 + categoryBoost + phraseBoost
            Ranked(candidate, index, score)
        }

        val relevant = scored
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.originalIndex })
            .map { it.candidate }

        if (relevant.isEmpty()) return distinct.take(limit)

        // Put relevant memories first, then use the cloud's recency order as context fallback.
        val selectedBodies = relevant.mapTo(hashSetOf()) { normalize(it.body) }
        return (relevant + distinct.filter { normalize(it.body) !in selectedBodies })
            .take(limit)
    }

    private fun tokens(value: String): Set<String> = tokenRegex
        .findAll(value)
        .map { it.value }
        .filterNot { it in stopWords }
        .toSet()

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(arabicDiacritics, "")
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun categoryCues(query: String): Set<String> = buildSet {
        if (listOf("فكره", "فكرة", "افكار", "أفكار", "idea", "ideas").any(query::contains)) add("idea")
        if (listOf("افضل", "أفضل", "احب", "أحب", "اكره", "أكره", "prefer", "like", "dislike").any(query::contains)) {
            add("preference")
        }
        if (listOf("اسمي", "اسمه", "اسمي", "من انا", "identity", "name").any(query::contains)) add("identity")
        if (listOf("زوج", "زوجه", "زوجة", "ابني", "ابنتي", "امي", "أمي", "relationship", "wife", "husband", "son", "daughter").any(query::contains)) {
            add("relationship")
        }
        if (listOf("ملاحظه", "ملاحظة", "note", "notes").any(query::contains)) add("note")
    }

    private data class Ranked(
        val candidate: HSharedMemoryCandidate,
        val originalIndex: Int,
        val score: Int,
    )
}

package com.malik.lmai.feature.agent.loop

import org.junit.Assert.assertEquals
import org.junit.Test

class HSharedMemoryRelevanceTest {

    @Test
    fun `older matching memory outranks unrelated recent memories`() {
        val memories = listOf(
            HSharedMemoryCandidate("أفضل القهوة بدون سكر", "preference"),
            HSharedMemoryCandidate("مشروعي القادم عن متجر عسل", "idea"),
            HSharedMemoryCandidate("اسم زوجتي سارة", "relationship"),
        )

        val selected = HSharedMemoryRelevance.select(
            query = "وش اسم زوجتي؟",
            candidates = memories,
            limit = 3,
        )

        assertEquals("اسم زوجتي سارة", selected.first().body)
    }

    @Test
    fun `category cue promotes saved ideas even without exact wording overlap`() {
        val memories = listOf(
            HSharedMemoryCandidate("أفضل الردود المختصرة", "preference"),
            HSharedMemoryCandidate("مشروع سباكة متنقل", "idea"),
        )

        val selected = HSharedMemoryRelevance.select(
            query = "وش أفكاري المحفوظة؟",
            candidates = memories,
            limit = 2,
        )

        assertEquals("مشروع سباكة متنقل", selected.first().body)
    }

    @Test
    fun `query with no useful match falls back to cloud recency order`() {
        val memories = listOf(
            HSharedMemoryCandidate("الأحدث", "general"),
            HSharedMemoryCandidate("الأقدم", "general"),
        )

        val selected = HSharedMemoryRelevance.select(
            query = "مرحبا كيف الحال",
            candidates = memories,
            limit = 2,
        )

        assertEquals(listOf("الأحدث", "الأقدم"), selected.map { it.body })
    }

    @Test
    fun `duplicate memory bodies are injected only once`() {
        val memories = listOf(
            HSharedMemoryCandidate("أحب السفر", "preference"),
            HSharedMemoryCandidate("أحب السفر", "general"),
            HSharedMemoryCandidate("أفضل جدة", "preference"),
        )

        val selected = HSharedMemoryRelevance.select(
            query = "وش أحب؟",
            candidates = memories,
            limit = 3,
        )

        assertEquals(2, selected.size)
        assertEquals(1, selected.count { it.body == "أحب السفر" })
    }
}

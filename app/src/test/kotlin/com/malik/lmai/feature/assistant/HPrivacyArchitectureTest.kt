package com.malik.lmai.feature.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HPrivacyArchitectureTest {

    @Test
    fun ownerStorageKeysAreCryptographicallySeparated() {
        val first = HOwnerScope.storageKey("google:first-user")
        val second = HOwnerScope.storageKey("google:second-user")

        assertNotEquals(first, second)
        assertFalse(first.contains("first-user"))
        assertFalse(second.contains("second-user"))
    }

    @Test
    fun contextContainsOnlyMemoriesPassedForCurrentOwner() {
        val identity = HIdentity(
            releaseName = "2.1.0",
            generation = 20100L,
        )
        val ownerA = HRelationshipState(
            firstMetAtMs = 1L,
            lastInteractionAtMs = 2L,
            turnCount = 8L,
            memories = listOf(HMemory("أحب القهوة بدون سكر", 2L)),
        )
        val ownerBSecret = "أفضل الشاي بالنعناع"

        val prompt = HContextBuilder.build(
            identity = identity,
            relationship = ownerA,
            userDisplayName = "User A",
            currentAttachmentCount = 0,
        )

        assertTrue(prompt.contains("أحب القهوة بدون سكر"))
        assertFalse(prompt.contains(ownerBSecret))
        assertTrue(prompt.contains("Release 2.1.0, generation 20100"))
        assertTrue(prompt.contains("Global H age"))
        assertTrue(prompt.contains("private and independent from every other user"))
    }

    @Test
    fun globalAgeBirthIsSharedAndDoesNotDependOnOwner() {
        assertEqualsForAge(
            HGlobalAge.age(1788652800000L),
            expectedDays = 0L,
        )
        assertEqualsForAge(
            HGlobalAge.age(1788652800000L + 31L * 24L * 60L * 60L * 1000L),
            expectedDays = 31L,
        )
    }

    @Test
    fun sensitiveCredentialsAreNeverLearned() {
        val compactCard = "1".repeat(16)
        val spacedCard = List(4) { "1".repeat(4) }.joinToString(" ")
        val arabicCard = List(4) { "١".repeat(4) }.joinToString("-")

        assertTrue(HMemoryPolicy.candidate("تذكر أن كلمة المرور هي abc123") == null)
        assertTrue(HMemoryPolicy.candidate("احفظ رمز التحقق 123456") == null)
        assertTrue(HMemoryPolicy.candidate("remember my card $compactCard") == null)
        assertTrue(HMemoryPolicy.candidate("remember my card $spacedCard") == null)
        assertTrue(HMemoryPolicy.candidate("احفظ رقم البطاقة $arabicCard") == null)
        assertTrue(HMemoryPolicy.candidate("تذكر أن رمز سري هو 1234") == null)
    }

    @Test
    fun explicitPreferenceCanBecomePrivateMemory() {
        val memory = HMemoryPolicy.candidate("تذكر أني أفضل الردود المختصرة")
        assertTrue(memory?.contains("أفضل الردود المختصرة") == true)
    }

    @Test
    fun doNotRememberDirectiveWinsOverMemoryMarkers() {
        val memory = HMemoryPolicy.candidate("لا تحفظ هذا: أنا أفضل مكان معين")
        assertTrue(memory == null)
    }

    @Test
    fun syntheticSystemTurnsDoNotAdvanceRelationship() {
        assertFalse(HMemoryPolicy.isRealUserTurn("[System] summarize the work"))
        assertFalse(HMemoryPolicy.isRealUserTurn("[Previous Turn Summary] old turn"))
        assertTrue(HMemoryPolicy.isRealUserTurn("مرحبا المساعد الشخصي H"))
    }

    private fun assertEqualsForAge(age: HAge, expectedDays: Long) {
        assertTrue(age.ageDays == expectedDays)
        assertTrue(age.birthEpochMs == 1788652800000L)
    }
}

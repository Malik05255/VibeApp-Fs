package com.malik.lmai.feature.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MohammedPrivacyArchitectureTest {

    @Test
    fun ownerStorageKeysAreCryptographicallySeparated() {
        val first = MohammedOwnerScope.storageKey("google:first-user")
        val second = MohammedOwnerScope.storageKey("google:second-user")

        assertNotEquals(first, second)
        assertFalse(first.contains("first-user"))
        assertFalse(second.contains("second-user"))
    }

    @Test
    fun contextContainsOnlyMemoriesPassedForCurrentOwner() {
        val identity = MohammedIdentity(
            releaseName = "2.1.0",
            generation = 20100L,
        )
        val ownerA = MohammedRelationshipState(
            firstMetAtMs = 1L,
            lastInteractionAtMs = 2L,
            turnCount = 8L,
            memories = listOf(MohammedMemory("أحب القهوة بدون سكر", 2L)),
        )
        val ownerBSecret = "أفضل الشاي بالنعناع"

        val prompt = MohammedContextBuilder.build(
            identity = identity,
            relationship = ownerA,
            userDisplayName = "User A",
            currentAttachmentCount = 0,
        )

        assertTrue(prompt.contains("أحب القهوة بدون سكر"))
        assertFalse(prompt.contains(ownerBSecret))
        assertTrue(prompt.contains("Release 2.1.0, generation 20100"))
        assertTrue(prompt.contains("Global Mohammed age"))
        assertTrue(prompt.contains("private and independent from every other user"))
    }

    @Test
    fun globalAgeBirthIsSharedAndDoesNotDependOnOwner() {
        assertEqualsForAge(
            MohammedGlobalAge.age(1788652800000L),
            expectedDays = 0L,
        )
        assertEqualsForAge(
            MohammedGlobalAge.age(1788652800000L + 31L * 24L * 60L * 60L * 1000L),
            expectedDays = 31L,
        )
    }

    @Test
    fun sensitiveCredentialsAreNeverLearned() {
        val compactCard = "1".repeat(16)
        val spacedCard = List(4) { "1".repeat(4) }.joinToString(" ")
        val arabicCard = List(4) { "١".repeat(4) }.joinToString("-")

        assertTrue(MohammedMemoryPolicy.candidate("تذكر أن كلمة المرور هي abc123") == null)
        assertTrue(MohammedMemoryPolicy.candidate("احفظ رمز التحقق 123456") == null)
        assertTrue(MohammedMemoryPolicy.candidate("remember my card $compactCard") == null)
        assertTrue(MohammedMemoryPolicy.candidate("remember my card $spacedCard") == null)
        assertTrue(MohammedMemoryPolicy.candidate("احفظ رقم البطاقة $arabicCard") == null)
        assertTrue(MohammedMemoryPolicy.candidate("تذكر أن رمز سري هو 1234") == null)
    }

    @Test
    fun explicitPreferenceCanBecomePrivateMemory() {
        val memory = MohammedMemoryPolicy.candidate("تذكر أني أفضل الردود المختصرة")
        assertTrue(memory?.contains("أفضل الردود المختصرة") == true)
    }

    @Test
    fun doNotRememberDirectiveWinsOverMemoryMarkers() {
        val memory = MohammedMemoryPolicy.candidate("لا تحفظ هذا: أنا أفضل مكان معين")
        assertTrue(memory == null)
    }

    @Test
    fun syntheticSystemTurnsDoNotAdvanceRelationship() {
        assertFalse(MohammedMemoryPolicy.isRealUserTurn("[System] summarize the work"))
        assertFalse(MohammedMemoryPolicy.isRealUserTurn("[Previous Turn Summary] old turn"))
        assertTrue(MohammedMemoryPolicy.isRealUserTurn("مرحبا محمد"))
    }

    private fun assertEqualsForAge(age: MohammedAge, expectedDays: Long) {
        assertTrue(age.ageDays == expectedDays)
        assertTrue(age.birthEpochMs == 1788652800000L)
    }
}

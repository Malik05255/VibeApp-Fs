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
        assertTrue(prompt.contains("release 2.1.0, generation 20100"))
    }

    @Test
    fun sensitiveCredentialsAreNeverLearned() {
        assertTrue(MohammedMemoryPolicy.candidate("تذكر أن كلمة المرور هي abc123") == null)
        assertTrue(MohammedMemoryPolicy.candidate("احفظ رمز التحقق 123456") == null)
        assertTrue(MohammedMemoryPolicy.candidate("remember my card 4111111111111111") == null)
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
}

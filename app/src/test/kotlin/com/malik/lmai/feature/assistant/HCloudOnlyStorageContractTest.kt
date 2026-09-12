package com.malik.lmai.feature.assistant

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architecture regression guard for H's Android storage boundary.
 *
 * Durable H personal state belongs to H Cloud. Android may keep process-memory working
 * state and the minimum OS scheduling identifiers needed to wake a reminder, but it must
 * not regain a second durable copy of H memory, learning, reminder bodies, or location
 * scope data.
 */
class HCloudOnlyStorageContractTest {

    @Test
    fun `assistant memory and learning stay process memory only`() {
        val source = source("feature/assistant/HAssistantContext.kt")

        assertTrue(source.contains("sessionStates"))
        assertFalse(source.contains("KEY_STATE_JSON"))
        assertFalse(source.contains(".putString("))
        assertFalse(source.contains(".putLong("))
        assertFalse(source.contains(".putBoolean("))
    }

    @Test
    fun `location scope has no durable android store`() {
        val source = source("feature/reminder/HLocationScopeStore.kt")

        assertTrue(source.contains("sessionConfigs"))
        assertFalse(source.contains("SharedPreferences"))
        assertFalse(source.contains("getSharedPreferences"))
        assertFalse(source.contains("h_location_scope_v1"))
    }

    @Test
    fun `reminder content is never read from room by runtime paths`() {
        val paths = listOf(
            "feature/reminder/HReminderRepository.kt",
            "feature/reminder/HReminderCloudSync.kt",
            "feature/reminder/HReminderCloudRuntime.kt",
            "feature/reminder/HTimeReminderWorker.kt",
            "feature/reminder/HGeofenceReceiver.kt",
            "feature/reminder/HReminderBootReceiver.kt",
        )

        paths.forEach { path ->
            val source = source(path)
            assertFalse("$path must not depend on HReminderDatabase", source.contains("HReminderDatabase"))
        }

        assertTrue(source("feature/reminder/HReminderRepository.kt").contains("deleteDatabase(LEGACY_REMINDER_DATABASE)"))
        assertTrue(source("feature/reminder/HReminderCloudRuntime.kt").contains("cloud.reminderSync"))
        assertTrue(source("feature/reminder/HTimeReminderWorker.kt").contains("HReminderCloudRuntime"))
        assertTrue(source("feature/reminder/HGeofenceReceiver.kt").contains("HReminderCloudRuntime"))
        assertTrue(source("feature/reminder/HReminderBootReceiver.kt").contains("HReminderCloudRuntime"))
    }

    @Test
    fun `work manager persists only reminder id`() {
        val scheduler = source("feature/reminder/HReminderScheduler.kt")
        val worker = source("feature/reminder/HTimeReminderWorker.kt")

        assertEquals(1, scheduler.countOccurrences(".putString("))
        assertTrue(scheduler.contains(".putString(HTimeReminderWorker.KEY_REMINDER_ID, reminderId)"))
        assertTrue(worker.contains("const val KEY_REMINDER_ID = \"h_reminder_id\""))

        listOf("title", "originalText", "interpretedText", "personName").forEach { field ->
            assertFalse("WorkManager must not persist reminder field: $field", scheduler.contains(".putString(\"$field\""))
        }
    }

    @Test
    fun `geofence registration carries reminder id rather than reminder body`() {
        val scheduler = source("feature/reminder/HReminderScheduler.kt")

        assertTrue(scheduler.contains(".setRequestId(reminderId)"))
        assertFalse(scheduler.contains("putExtra(\"title\""))
        assertFalse(scheduler.contains("putExtra(\"interpretedText\""))
        assertFalse(scheduler.contains("putExtra(\"originalText\""))
    }

    private fun source(relativePath: String): String {
        val root = repositoryRoot()
        val file = File(root, "app/src/main/kotlin/com/malik/lmai/$relativePath")
        check(file.isFile) { "Missing source file: ${file.path}" }
        return file.readText()
    }

    private fun repositoryRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = current ?: return@repeat
            if (File(candidate, "app/src/main/kotlin").isDirectory) return candidate
            current = candidate.parentFile
        }
        error("Could not resolve repository root from ${System.getProperty("user.dir")}")
    }

    private fun String.countOccurrences(needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var offset = 0
        while (true) {
            val index = indexOf(needle, offset)
            if (index < 0) return count
            count += 1
            offset = index + needle.length
        }
    }
}

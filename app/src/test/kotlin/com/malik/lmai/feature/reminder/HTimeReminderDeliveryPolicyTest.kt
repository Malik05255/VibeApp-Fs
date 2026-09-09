package com.malik.lmai.feature.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HTimeReminderDeliveryPolicyTest {
    @Test
    fun oneShotReminderBecomesCompletedAfterTrigger() {
        val scheduledAt = 1_000_000L
        val deliveredAt = 1_000_500L
        val reminder = reminder(scheduledAtMs = scheduledAt)

        val updated = HTimeReminderDeliveryPolicy.afterTrigger(reminder, deliveredAt)

        assertEquals(HReminderStatus.COMPLETED, updated.status)
        assertEquals(deliveredAt, updated.completedAtMs)
        assertEquals(scheduledAt, updated.scheduledAtMs)
    }

    @Test
    fun dailyReminderAdvancesAndStaysActive() {
        val scheduledAt = 1_000_000L
        val deliveredAt = 1_000_500L
        val reminder = reminder(
            scheduledAtMs = scheduledAt,
            recurrenceRule = "DAILY",
        )

        val updated = HTimeReminderDeliveryPolicy.afterTrigger(reminder, deliveredAt)

        assertEquals(HReminderStatus.ACTIVE, updated.status)
        assertEquals(scheduledAt + 24L * 60L * 60L * 1000L, updated.scheduledAtMs)
        assertNull(updated.completedAtMs)
        assertEquals(deliveredAt, updated.updatedAtMs)
    }

    @Test
    fun weeklyReminderAdvancesSevenDays() {
        val scheduledAt = 1_000_000L
        val reminder = reminder(
            scheduledAtMs = scheduledAt,
            recurrenceRule = "weekly",
        )

        val updated = HTimeReminderDeliveryPolicy.afterTrigger(reminder, 1_000_500L)

        assertEquals(scheduledAt + 7L * 24L * 60L * 60L * 1000L, updated.scheduledAtMs)
        assertEquals(HReminderStatus.ACTIVE, updated.status)
    }

    private fun reminder(
        scheduledAtMs: Long,
        recurrenceRule: String? = null,
    ) = HReminder(
        id = "reminder-1",
        ownerKey = "google:test-owner",
        title = "اختبار",
        originalText = "ذكرني",
        interpretedText = "تذكير اختبار",
        type = if (recurrenceRule == null) HReminderType.TIME else HReminderType.RECURRING,
        status = HReminderStatus.ACTIVE,
        source = HReminderSource.APP_CHAT,
        domain = HReminderDomain.PERSONAL,
        createdAtMs = 900_000L,
        updatedAtMs = 900_000L,
        scheduledAtMs = scheduledAtMs,
        recurrenceRule = recurrenceRule,
    )
}

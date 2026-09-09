package com.malik.lmai.feature.reminder

/** Pure lifecycle policy for a time reminder after its scheduled trigger fires. */
internal object HTimeReminderDeliveryPolicy {
    fun afterTrigger(reminder: HReminder, nowMs: Long): HReminder {
        val next = nextOccurrence(reminder)
        return if (next != null) {
            reminder.copy(
                scheduledAtMs = next,
                updatedAtMs = nowMs,
            )
        } else {
            reminder.copy(
                status = HReminderStatus.COMPLETED,
                updatedAtMs = nowMs,
                completedAtMs = nowMs,
            )
        }
    }

    private fun nextOccurrence(reminder: HReminder): Long? {
        val current = reminder.scheduledAtMs ?: return null
        return when (reminder.recurrenceRule?.uppercase()) {
            "DAILY" -> current + 24L * 60L * 60L * 1000L
            "WEEKLY" -> current + 7L * 24L * 60L * 60L * 1000L
            else -> null
        }
    }
}

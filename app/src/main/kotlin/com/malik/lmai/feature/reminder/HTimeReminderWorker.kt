package com.malik.lmai.feature.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Executes a scheduled H reminder without a durable Android reminder database. */
class HTimeReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val runtime = HReminderCloudRuntime(applicationContext)
        val reminder = runtime.get(id) ?: return Result.retry()
        if (!reminder.isPersonal || !reminder.isOpen || reminder.source == HReminderSource.WHATSAPP) {
            return Result.success()
        }

        HReminderNotifier.show(applicationContext, reminder)

        val next = nextOccurrence(reminder)
        if (next != null) {
            val updated = reminder.copy(
                scheduledAtMs = next,
                updatedAtMs = System.currentTimeMillis(),
            )
            if (!runtime.push(updated)) return Result.retry()
            HReminderScheduler(applicationContext).schedule(updated)
        }
        return Result.success()
    }

    private fun nextOccurrence(reminder: HReminder): Long? {
        val current = reminder.scheduledAtMs ?: return null
        return when (reminder.recurrenceRule?.uppercase()) {
            "DAILY" -> current + 24L * 60L * 60L * 1000L
            "WEEKLY" -> current + 7L * 24L * 60L * 60L * 1000L
            else -> null
        }
    }

    companion object {
        const val KEY_REMINDER_ID = "h_reminder_id"
    }
}

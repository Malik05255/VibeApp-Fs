package com.malik.lmai.feature.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.malik.lmai.feature.reminder.db.HReminderDatabase

class HTimeReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        val dao = HReminderDatabase.get(applicationContext).reminderDao()
        val entity = dao.getById(id) ?: return Result.success()
        val reminder = entity.toDomain()
        if (!reminder.isPersonal || !reminder.isOpen) return Result.success()

        HReminderNotifier.show(applicationContext, reminder)

        val next = nextOccurrence(reminder)
        if (next != null) {
            val updated = reminder.copy(
                scheduledAtMs = next,
                updatedAtMs = System.currentTimeMillis(),
            )
            dao.upsert(com.malik.lmai.feature.reminder.db.HReminderEntity.fromDomain(updated))
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

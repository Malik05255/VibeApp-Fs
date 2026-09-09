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

        val updated = HTimeReminderDeliveryPolicy.afterTrigger(
            reminder = reminder,
            nowMs = System.currentTimeMillis(),
        )

        // H Cloud is authoritative. Commit the next lifecycle state before emitting the
        // device-side notification so a retry/reboot cannot revive an already-fired reminder.
        if (!runtime.push(updated)) return Result.retry()

        if (updated.isOpen && updated.scheduledAtMs != reminder.scheduledAtMs) {
            HReminderScheduler(applicationContext).schedule(updated)
        }

        HReminderNotifier.show(applicationContext, reminder)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "h_reminder_id"
    }
}

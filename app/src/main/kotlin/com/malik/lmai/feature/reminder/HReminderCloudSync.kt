package com.malik.lmai.feature.reminder

import com.malik.lmai.feature.assistant.HCloudLinkClient
import com.malik.lmai.feature.assistant.HOwnerIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reconciles Android's execution registrations with H's Google-account-bound cloud state.
 *
 * The cloud is authoritative. Android does not keep a durable reminder-content database;
 * WorkManager/geofences may retain only the minimum reminder id and scheduling metadata
 * required by the OS. Reminder text and lifecycle state are fetched from H Cloud.
 */
@Singleton
class HReminderCloudSync @Inject constructor(
    private val ownerIdentity: HOwnerIdentity,
    private val cloud: HCloudLinkClient,
    private val scheduler: HReminderScheduler,
) {
    suspend fun push(reminder: HReminder): Boolean {
        val response = cloud.reminderSync(
            action = "upsert",
            extra = buildJsonObject {
                put("reminder", HReminderCloudCodec.toCloudJson(reminder))
            },
        )
        return response.ok
    }

    suspend fun setStatus(id: String, status: HReminderStatus): Boolean =
        cloud.reminderSync(
            action = "set_status",
            extra = buildJsonObject {
                put("id", id)
                put("status", status.name)
            },
        ).ok

    suspend fun delete(id: String): Boolean =
        cloud.reminderSync(
            action = "delete",
            extra = buildJsonObject { put("id", id) },
        ).ok

    suspend fun pull(): List<HReminder>? {
        val response = cloud.reminderSync("pull")
        if (!response.ok) return null

        val reminders = HReminderCloudCodec.parseRows(
            body = response.body,
            ownerKey = ownerIdentity.currentOwnerKey(),
        )

        reminders.forEach { reminder ->
            if (reminder.shouldExecuteOnDevice()) {
                scheduler.schedule(reminder)
            } else {
                scheduler.cancel(reminder.id)
            }
        }

        return reminders.filter { it.status != HReminderStatus.CANCELLED }
    }

    suspend fun syncFromCloud(): HReminderSyncResult {
        val reminders = pull() ?: return HReminderSyncResult(cloudAvailable = false)
        return HReminderSyncResult(
            cloudAvailable = true,
            downloaded = reminders.size,
            reminders = reminders,
        )
    }

    private fun HReminder.shouldExecuteOnDevice(): Boolean =
        source != HReminderSource.WHATSAPP &&
            isPersonal &&
            isOpen
}

data class HReminderSyncResult(
    val cloudAvailable: Boolean,
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val removed: Int = 0,
    val reminders: List<HReminder> = emptyList(),
)

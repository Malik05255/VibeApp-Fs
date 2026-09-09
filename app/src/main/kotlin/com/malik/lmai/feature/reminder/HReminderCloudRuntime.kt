package com.malik.lmai.feature.reminder

import android.content.Context
import com.malik.lmai.feature.assistant.HCloudLinkClient
import com.malik.lmai.feature.assistant.HOwnerIdentity
import com.malik.lmai.presentation.ui.auth.GoogleIdTokenProvider
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Minimal non-Hilt runtime used by Android workers/receivers.
 * Reminder contents are fetched from the Google-account-bound H Cloud when needed; only
 * reminder ids are allowed to remain in Android scheduling metadata.
 */
internal class HReminderCloudRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val ownerIdentity = HOwnerIdentity(appContext)
    private val cloud = HCloudLinkClient(GoogleIdTokenProvider(appContext))

    suspend fun pull(): List<HReminder>? {
        val response = cloud.reminderSync("pull")
        if (!response.ok) return null
        return HReminderCloudCodec.parseRows(response.body, ownerIdentity.currentOwnerKey())
            .filter { it.status != HReminderStatus.CANCELLED }
    }

    suspend fun get(id: String): HReminder? = pull()?.firstOrNull { it.id == id }

    suspend fun push(reminder: HReminder): Boolean = cloud.reminderSync(
        action = "upsert",
        extra = buildJsonObject {
            put("reminder", HReminderCloudCodec.toCloudJson(reminder))
        },
    ).ok

    suspend fun setStatus(id: String, status: HReminderStatus): Boolean = cloud.reminderSync(
        action = "set_status",
        extra = buildJsonObject {
            put("id", id)
            put("status", status.name)
        },
    ).ok
}

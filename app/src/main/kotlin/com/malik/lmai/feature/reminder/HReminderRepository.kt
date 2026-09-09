package com.malik.lmai.feature.reminder

import android.content.Context
import com.malik.lmai.feature.assistant.HOwnerIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * H reminder repository with cloud-authoritative durable state.
 *
 * Android holds a process-memory view only. Reminder bodies are written to and restored from
 * the signed-in Google owner's H Cloud. The former Room database is deleted on startup.
 */
@Singleton
class HReminderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ownerIdentity: HOwnerIdentity,
    private val scheduler: HReminderScheduler,
    private val cloudSync: HReminderCloudSync,
) {
    private val reminders = MutableStateFlow<List<HReminder>>(emptyList())
    private var cachedOwnerKey: String? = null

    init {
        purgeLegacyReminderDatabase()
    }

    fun observePersonal(): Flow<List<HReminder>> = reminders.map { list ->
        val ownerKey = ownerIdentity.currentOwnerKey()
        list.filter { it.ownerKey == ownerKey && it.isPersonal }
    }

    suspend fun list(domain: HReminderDomain? = null): List<HReminder> {
        val all = refreshFromCloudOrMemory()
        val ownerKey = ownerIdentity.currentOwnerKey()
        return all.filter { it.ownerKey == ownerKey && (domain == null || it.domain == domain) }
    }

    suspend fun get(id: String): HReminder? {
        val ownerKey = ownerIdentity.currentOwnerKey()
        return refreshFromCloudOrMemory().firstOrNull { it.id == id && it.ownerKey == ownerKey }
    }

    suspend fun create(
        title: String,
        originalText: String,
        interpretedText: String,
        type: HReminderType,
        source: HReminderSource = HReminderSource.APP_CHAT,
        domain: HReminderDomain = HReminderDomain.PERSONAL,
        scheduledAtMs: Long? = null,
        recurrenceRule: String? = null,
        personName: String? = null,
        location: HReminderLocation? = null,
    ): HReminder {
        val now = System.currentTimeMillis()
        val reminder = HReminder(
            id = UUID.randomUUID().toString(),
            ownerKey = ownerIdentity.currentOwnerKey(),
            title = title.trim().ifBlank { interpretedText.trim().take(80).ifBlank { "تذكير من H" } },
            originalText = originalText.trim(),
            interpretedText = interpretedText.trim(),
            type = type,
            status = HReminderStatus.ACTIVE,
            source = source,
            domain = domain,
            createdAtMs = now,
            updatedAtMs = now,
            scheduledAtMs = scheduledAtMs,
            recurrenceRule = recurrenceRule,
            personName = personName?.trim()?.takeIf { it.isNotBlank() },
            location = location,
        )

        requireCloudWrite(cloudSync.push(reminder))
        updateMemory(reminder)
        scheduleForDevice(reminder)
        return reminder
    }

    suspend fun update(reminder: HReminder): Boolean {
        val ownerKey = ownerIdentity.currentOwnerKey()
        if (reminder.ownerKey != ownerKey) return false
        val updated = reminder.copy(updatedAtMs = System.currentTimeMillis())
        if (!cloudSync.push(updated)) return false
        updateMemory(updated)
        scheduleForDevice(updated)
        return true
    }

    suspend fun delete(id: String): Boolean {
        val existing = get(id) ?: return false
        if (existing.ownerKey != ownerIdentity.currentOwnerKey()) return false
        if (!cloudSync.delete(id)) return false
        reminders.value = reminders.value.filterNot { it.id == id }
        scheduler.cancel(id)
        return true
    }

    suspend fun setStatus(id: String, status: HReminderStatus): Boolean {
        val existing = get(id) ?: return false
        if (existing.ownerKey != ownerIdentity.currentOwnerKey()) return false
        if (!cloudSync.setStatus(id, status)) return false

        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = status,
            updatedAtMs = now,
            completedAtMs = if (status == HReminderStatus.COMPLETED) now else existing.completedAtMs,
        )
        updateMemory(updated)
        scheduleForDevice(updated)
        return true
    }

    suspend fun syncFromCloud(): HReminderSyncResult {
        ensureOwnerScope()
        val result = cloudSync.syncFromCloud()
        if (result.cloudAvailable) reminders.value = result.reminders
        return result
    }

    suspend fun rescheduleAll() {
        val result = syncFromCloud()
        if (!result.cloudAvailable) return
        result.reminders
            .filter { it.shouldExecuteOnDevice() }
            .forEach(scheduler::schedule)
    }

    private suspend fun refreshFromCloudOrMemory(): List<HReminder> {
        ensureOwnerScope()
        val remote = cloudSync.pull()
        if (remote != null) reminders.value = remote
        return reminders.value
    }

    private fun ensureOwnerScope() {
        val current = ownerIdentity.currentOwnerKey()
        if (cachedOwnerKey != current) {
            cachedOwnerKey = current
            reminders.value = emptyList()
        }
    }

    private fun updateMemory(reminder: HReminder) {
        ensureOwnerScope()
        reminders.value = (reminders.value.filterNot { it.id == reminder.id } + reminder)
            .sortedByDescending(HReminder::updatedAtMs)
    }

    private fun scheduleForDevice(reminder: HReminder) {
        if (reminder.shouldExecuteOnDevice()) scheduler.schedule(reminder)
        else scheduler.cancel(reminder.id)
    }

    private fun HReminder.shouldExecuteOnDevice(): Boolean =
        source != HReminderSource.WHATSAPP && isPersonal && isOpen

    private fun requireCloudWrite(ok: Boolean) {
        if (!ok) {
            throw IllegalStateException(
                "تعذر حفظ تذكير H في مساحة الحساب السحابية. تأكد من تسجيل الدخول بحساب Google والاتصال بالإنترنت."
            )
        }
    }

    private fun purgeLegacyReminderDatabase() {
        runCatching { context.deleteDatabase(LEGACY_REMINDER_DATABASE) }
    }

    companion object {
        private const val LEGACY_REMINDER_DATABASE = "h_personal_reminders.db"
    }
}

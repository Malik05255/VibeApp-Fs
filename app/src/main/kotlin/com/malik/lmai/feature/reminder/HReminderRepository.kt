package com.malik.lmai.feature.reminder

import android.content.Context
import com.malik.lmai.feature.assistant.HOwnerIdentity
import com.malik.lmai.feature.reminder.db.HReminderDatabase
import com.malik.lmai.feature.reminder.db.HReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class HReminderRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val ownerIdentity: HOwnerIdentity,
    private val scheduler: HReminderScheduler,
    private val cloudSync: HReminderCloudSync,
) {
    private val dao = HReminderDatabase.get(context).reminderDao()

    fun observePersonal(): Flow<List<HReminder>> {
        val ownerKey = ownerIdentity.currentOwnerKey()
        return dao.observePersonal(ownerKey).map { list -> list.map(HReminderEntity::toDomain) }
    }

    suspend fun list(domain: HReminderDomain? = null): List<HReminder> {
        val ownerKey = ownerIdentity.currentOwnerKey()
        return dao.getAllForOwner(ownerKey)
            .map(HReminderEntity::toDomain)
            .filter { domain == null || it.domain == domain }
    }

    suspend fun get(id: String): HReminder? {
        val ownerKey = ownerIdentity.currentOwnerKey()
        return dao.getById(id)?.takeIf { it.ownerKey == ownerKey }?.toDomain()
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
        dao.upsert(HReminderEntity.fromDomain(reminder))
        scheduler.schedule(reminder)
        // Local execution must never depend on network availability. Cloud sync is best effort.
        runCatching { cloudSync.push(reminder) }
        return reminder
    }

    suspend fun update(reminder: HReminder): Boolean {
        val ownerKey = ownerIdentity.currentOwnerKey()
        if (reminder.ownerKey != ownerKey) return false
        val updated = reminder.copy(updatedAtMs = System.currentTimeMillis())
        dao.upsert(HReminderEntity.fromDomain(updated))
        scheduler.schedule(updated)
        runCatching { cloudSync.push(updated) }
        return true
    }

    suspend fun delete(id: String): Boolean {
        val ownerKey = ownerIdentity.currentOwnerKey()
        val reminder = dao.getById(id) ?: return false
        if (reminder.ownerKey != ownerKey) return false
        scheduler.cancel(id)
        dao.deleteById(ownerKey, id)
        runCatching { cloudSync.delete(id) }
        return true
    }

    suspend fun setStatus(id: String, status: HReminderStatus): Boolean {
        val ownerKey = ownerIdentity.currentOwnerKey()
        val reminder = dao.getById(id) ?: return false
        if (reminder.ownerKey != ownerKey) return false
        val now = System.currentTimeMillis()
        dao.updateStatus(
            ownerKey = ownerKey,
            id = id,
            status = status.name,
            updatedAtMs = now,
            completedAtMs = if (status == HReminderStatus.COMPLETED) now else null,
        )
        if (status == HReminderStatus.ACTIVE || status == HReminderStatus.DEFERRED) {
            scheduler.schedule(reminder.toDomain().copy(status = status, updatedAtMs = now))
        } else {
            scheduler.cancel(id)
        }
        runCatching { cloudSync.setStatus(id, status) }
        return true
    }

    suspend fun syncFromCloud(): HReminderSyncResult = cloudSync.syncFromCloud()

    suspend fun rescheduleAll() {
        runCatching { syncFromCloud() }
        val ownerKey = ownerIdentity.currentOwnerKey()
        dao.getAllForOwner(ownerKey)
            .map(HReminderEntity::toDomain)
            .filter { it.isPersonal && it.isOpen && it.source != HReminderSource.WHATSAPP }
            .forEach(scheduler::schedule)
    }
}

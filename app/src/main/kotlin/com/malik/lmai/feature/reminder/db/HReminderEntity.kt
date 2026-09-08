package com.malik.lmai.feature.reminder.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.malik.lmai.feature.reminder.HLocationTriggerMode
import com.malik.lmai.feature.reminder.HReminder
import com.malik.lmai.feature.reminder.HReminderDomain
import com.malik.lmai.feature.reminder.HReminderLocation
import com.malik.lmai.feature.reminder.HReminderSource
import com.malik.lmai.feature.reminder.HReminderStatus
import com.malik.lmai.feature.reminder.HReminderType

@Entity(
    tableName = "h_reminders",
    indices = [
        Index(value = ["ownerKey", "domain", "status"]),
        Index(value = ["scheduledAtMs"]),
    ],
)
data class HReminderEntity(
    @PrimaryKey val id: String,
    val ownerKey: String,
    val title: String,
    val originalText: String,
    val interpretedText: String,
    val type: String,
    val status: String,
    val source: String,
    val domain: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val scheduledAtMs: Long?,
    val recurrenceRule: String?,
    val personName: String?,
    val placeNameAr: String?,
    val placeAddressAr: String?,
    val placeId: String?,
    val latitude: Double?,
    val longitude: Double?,
    val radiusMeters: Float?,
    val dwellMinutes: Int?,
    val locationTriggerMode: String?,
    val cooldownUntilMs: Long?,
    val completedAtMs: Long?,
) {
    fun toDomain(): HReminder = HReminder(
        id = id,
        ownerKey = ownerKey,
        title = title,
        originalText = originalText,
        interpretedText = interpretedText,
        type = enumValueOrDefault(type, HReminderType.CONTEXTUAL),
        status = enumValueOrDefault(status, HReminderStatus.ACTIVE),
        source = enumValueOrDefault(source, HReminderSource.APP_CHAT),
        domain = enumValueOrDefault(domain, HReminderDomain.PERSONAL),
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        scheduledAtMs = scheduledAtMs,
        recurrenceRule = recurrenceRule,
        personName = personName,
        location = if (latitude != null && longitude != null && !placeNameAr.isNullOrBlank()) {
            HReminderLocation(
                placeNameAr = placeNameAr,
                addressAr = placeAddressAr,
                placeId = placeId,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: 180f,
                dwellMinutes = dwellMinutes ?: 1,
                triggerMode = enumValueOrDefault(locationTriggerMode, HLocationTriggerMode.DWELL),
            )
        } else null,
        cooldownUntilMs = cooldownUntilMs,
        completedAtMs = completedAtMs,
    )

    companion object {
        fun fromDomain(reminder: HReminder): HReminderEntity = HReminderEntity(
            id = reminder.id,
            ownerKey = reminder.ownerKey,
            title = reminder.title,
            originalText = reminder.originalText,
            interpretedText = reminder.interpretedText,
            type = reminder.type.name,
            status = reminder.status.name,
            source = reminder.source.name,
            domain = reminder.domain.name,
            createdAtMs = reminder.createdAtMs,
            updatedAtMs = reminder.updatedAtMs,
            scheduledAtMs = reminder.scheduledAtMs,
            recurrenceRule = reminder.recurrenceRule,
            personName = reminder.personName,
            placeNameAr = reminder.location?.placeNameAr,
            placeAddressAr = reminder.location?.addressAr,
            placeId = reminder.location?.placeId,
            latitude = reminder.location?.latitude,
            longitude = reminder.location?.longitude,
            radiusMeters = reminder.location?.radiusMeters,
            dwellMinutes = reminder.location?.dwellMinutes,
            locationTriggerMode = reminder.location?.triggerMode?.name,
            cooldownUntilMs = reminder.cooldownUntilMs,
            completedAtMs = reminder.completedAtMs,
        )
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
    runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)

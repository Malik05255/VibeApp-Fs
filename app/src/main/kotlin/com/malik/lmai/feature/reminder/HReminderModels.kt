package com.malik.lmai.feature.reminder

enum class HReminderType {
    TIME,
    LOCATION,
    PERSON,
    RECURRING,
    CONTEXTUAL,
}

enum class HReminderStatus {
    ACTIVE,
    DEFERRED,
    COMPLETED,
    DISABLED,
    CANCELLED,
}

enum class HReminderSource {
    APP_CHAT,
    WHATSAPP,
    MANUAL,
    IMPORTED,
}

enum class HReminderDomain {
    PERSONAL,
    PROGRAMMING,
}

enum class HLocationTriggerMode {
    ARRIVE,
    DWELL,
    DEPART,
    NEARBY,
}

data class HReminderLocation(
    val placeNameAr: String,
    val addressAr: String? = null,
    val placeId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 180f,
    val dwellMinutes: Int = 1,
    val triggerMode: HLocationTriggerMode = HLocationTriggerMode.DWELL,
)

data class HReminder(
    val id: String,
    val ownerKey: String,
    val title: String,
    val originalText: String,
    val interpretedText: String,
    val type: HReminderType,
    val status: HReminderStatus = HReminderStatus.ACTIVE,
    val source: HReminderSource = HReminderSource.APP_CHAT,
    val domain: HReminderDomain = HReminderDomain.PERSONAL,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val scheduledAtMs: Long? = null,
    val recurrenceRule: String? = null,
    val personName: String? = null,
    val location: HReminderLocation? = null,
    val cooldownUntilMs: Long? = null,
    val completedAtMs: Long? = null,
) {
    val isPersonal: Boolean get() = domain == HReminderDomain.PERSONAL
    val isLocationBased: Boolean get() = location != null
    val isOpen: Boolean get() = status == HReminderStatus.ACTIVE || status == HReminderStatus.DEFERRED
}

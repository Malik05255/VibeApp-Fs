package com.malik.lmai.feature.reminder

import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject

/** Pure H Cloud reminder wire codec shared by UI, workers, and geofence delivery. */
internal object HReminderCloudCodec {
    fun toCloudJson(reminder: HReminder): JsonObject = buildJsonObject {
        put("id", reminder.id)
        put("title", reminder.title)
        put("original_text", reminder.originalText)
        put("interpreted_text", reminder.interpretedText)
        put("type", reminder.type.name)
        put("status", reminder.status.name)
        put("source", reminder.source.name)
        put("domain", reminder.domain.name)
        reminder.scheduledAtMs?.let { put("scheduled_at", Instant.ofEpochMilli(it).toString()) }
        reminder.recurrenceRule?.let { put("recurrence_rule", it) }
        reminder.personName?.let { put("person_name", it) }
        reminder.cooldownUntilMs?.let { put("cooldown_until", Instant.ofEpochMilli(it).toString()) }
        reminder.completedAtMs?.let { put("completed_at", Instant.ofEpochMilli(it).toString()) }
        put("updated_at", Instant.ofEpochMilli(reminder.updatedAtMs).toString())
        reminder.location?.let { item ->
            put("location", buildJsonObject {
                put("place_name_ar", item.placeNameAr)
                item.addressAr?.let { put("address_ar", it) }
                item.placeId?.let { put("place_id", it) }
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("radius_meters", item.radiusMeters)
                put("dwell_minutes", item.dwellMinutes)
                put("trigger_mode", item.triggerMode.name)
            })
        }
    }

    fun parseRows(body: JsonObject, ownerKey: String): List<HReminder> {
        val rows = body["reminders"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { (it as? JsonObject)?.toReminder(ownerKey) }
    }

    private fun JsonObject.toReminder(ownerKey: String): HReminder? {
        val id = string("id") ?: return null
        val interpreted = string("interpreted_text") ?: string("body") ?: return null
        val updatedAt = instantMs("updated_at") ?: return null
        val type = enumOrDefault(
            string("reminder_type") ?: string("type"),
            HReminderType.CONTEXTUAL,
        )
        val status = enumOrDefault(
            string("lifecycle_status") ?: string("status"),
            legacyLifecycle(string("status")),
        )
        val source = enumOrDefault(string("source"), HReminderSource.WHATSAPP)
        val domain = enumOrDefault(string("domain"), HReminderDomain.PERSONAL)
        val location = (this["location"] as? JsonObject)?.toLocation()

        return HReminder(
            id = id,
            ownerKey = ownerKey,
            title = string("title") ?: interpreted.take(80),
            originalText = string("original_text") ?: interpreted,
            interpretedText = interpreted,
            type = type,
            status = status,
            source = source,
            domain = domain,
            createdAtMs = instantMs("created_at") ?: updatedAt,
            updatedAtMs = updatedAt,
            scheduledAtMs = instantMs("due_at") ?: instantMs("scheduled_at"),
            recurrenceRule = string("recurrence_rule"),
            personName = string("person_name"),
            location = location,
            cooldownUntilMs = instantMs("cooldown_until"),
            completedAtMs = instantMs("completed_at"),
        )
    }

    private fun JsonObject.toLocation(): HReminderLocation? {
        val placeName = string("place_name_ar") ?: return null
        val latitude = number("latitude") ?: return null
        val longitude = number("longitude") ?: return null
        return HReminderLocation(
            placeNameAr = placeName,
            addressAr = string("address_ar"),
            placeId = string("place_id"),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = number("radius_meters")?.toFloat() ?: 180f,
            dwellMinutes = int("dwell_minutes") ?: 1,
            triggerMode = enumOrDefault(string("trigger_mode"), HLocationTriggerMode.DWELL),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.instantMs(key: String): Long? = string(key)?.let { value ->
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty().uppercase()) }.getOrDefault(fallback)

    private fun legacyLifecycle(deliveryStatus: String?): HReminderStatus = when (deliveryStatus) {
        "sent" -> HReminderStatus.COMPLETED
        "cancelled" -> HReminderStatus.CANCELLED
        else -> HReminderStatus.ACTIVE
    }
}

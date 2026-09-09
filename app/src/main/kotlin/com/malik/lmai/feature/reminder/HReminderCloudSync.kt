package com.malik.lmai.feature.reminder

import android.content.Context
import com.malik.lmai.feature.assistant.HCloudLinkClient
import com.malik.lmai.feature.assistant.HOwnerIdentity
import com.malik.lmai.feature.reminder.db.HReminderDatabase
import com.malik.lmai.feature.reminder.db.HReminderEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Reconciles the device reminder cache/executor with H's account-bound cloud state.
 *
 * Cloud rows are portable across reinstall/device changes. Room remains a local execution
 * cache because Android must own WorkManager/geofence delivery. WhatsApp-origin reminders
 * are visible in the app but are not scheduled again on-device, avoiding duplicate delivery.
 */
@Singleton
class HReminderCloudSync @Inject constructor(
    @ApplicationContext context: Context,
    private val ownerIdentity: HOwnerIdentity,
    private val cloud: HCloudLinkClient,
    private val scheduler: HReminderScheduler,
) {
    private val dao = HReminderDatabase.get(context).reminderDao()

    suspend fun push(reminder: HReminder): Boolean {
        val response = cloud.reminderSync(
            action = "upsert",
            extra = buildJsonObject { put("reminder", reminder.toCloudJson()) },
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

    suspend fun syncFromCloud(): HReminderSyncResult {
        val response = cloud.reminderSync("pull")
        if (!response.ok) return HReminderSyncResult(cloudAvailable = false)
        val rows = response.body["reminders"] as? JsonArray ?: return HReminderSyncResult(cloudAvailable = true)
        val ownerKey = ownerIdentity.currentOwnerKey()
        var downloaded = 0
        var uploaded = 0
        var removed = 0

        for (element in rows) {
            val remoteObject = element as? JsonObject ?: continue
            val remote = remoteObject.toReminder(ownerKey) ?: continue
            val local = dao.getById(remote.id)?.takeIf { it.ownerKey == ownerKey }?.toDomain()

            if (remote.status == HReminderStatus.CANCELLED) {
                if (local != null && remote.updatedAtMs >= local.updatedAtMs) {
                    scheduler.cancel(remote.id)
                    dao.deleteById(ownerKey, remote.id)
                    removed += 1
                }
                continue
            }

            if (local != null && local.updatedAtMs > remote.updatedAtMs + CLOCK_SKEW_MS) {
                if (push(local)) uploaded += 1
                continue
            }

            dao.upsert(HReminderEntity.fromDomain(remote))
            if (remote.shouldExecuteOnDevice()) {
                scheduler.schedule(remote)
            } else {
                scheduler.cancel(remote.id)
            }
            downloaded += 1
        }

        return HReminderSyncResult(
            cloudAvailable = true,
            downloaded = downloaded,
            uploaded = uploaded,
            removed = removed,
        )
    }

    private fun HReminder.shouldExecuteOnDevice(): Boolean =
        source != HReminderSource.WHATSAPP &&
            (status == HReminderStatus.ACTIVE || status == HReminderStatus.DEFERRED)

    private fun HReminder.toCloudJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("original_text", originalText)
        put("interpreted_text", interpretedText)
        put("type", type.name)
        put("status", status.name)
        put("source", source.name)
        put("domain", domain.name)
        scheduledAtMs?.let { put("scheduled_at", Instant.ofEpochMilli(it).toString()) }
        recurrenceRule?.let { put("recurrence_rule", it) }
        personName?.let { put("person_name", it) }
        cooldownUntilMs?.let { put("cooldown_until", Instant.ofEpochMilli(it).toString()) }
        completedAtMs?.let { put("completed_at", Instant.ofEpochMilli(it).toString()) }
        put("updated_at", Instant.ofEpochMilli(updatedAtMs).toString())
        location?.let { item ->
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

    private fun JsonObject.toReminder(ownerKey: String): HReminder? {
        val id = string("id") ?: return null
        val body = string("interpreted_text") ?: string("body") ?: return null
        val remoteUpdated = instantMs("updated_at") ?: return null
        val type = enumOrDefault(string("reminder_type"), HReminderType.CONTEXTUAL)
        val status = enumOrDefault(string("lifecycle_status"), legacyLifecycle(string("status")))
        val source = enumOrDefault(string("source"), HReminderSource.WHATSAPP)
        val domain = enumOrDefault(string("domain"), HReminderDomain.PERSONAL)
        val locationObject = this["location"] as? JsonObject
        val location = locationObject?.toLocation()

        return HReminder(
            id = id,
            ownerKey = ownerKey,
            title = string("title") ?: body.take(80),
            originalText = string("original_text") ?: body,
            interpretedText = body,
            type = type,
            status = status,
            source = source,
            domain = domain,
            createdAtMs = instantMs("created_at") ?: remoteUpdated,
            updatedAtMs = remoteUpdated,
            scheduledAtMs = instantMs("due_at"),
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

    companion object {
        private const val CLOCK_SKEW_MS = 2_000L
    }
}

data class HReminderSyncResult(
    val cloudAvailable: Boolean,
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val removed: Int = 0,
)

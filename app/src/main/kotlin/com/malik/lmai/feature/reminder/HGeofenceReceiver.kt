package com.malik.lmai.feature.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Geofence delivery backed only by H Cloud reminder content. */
class HGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val runtime = HReminderCloudRuntime(context)
                val now = System.currentTimeMillis()
                event.triggeringGeofences.orEmpty().forEach { geofence ->
                    val reminder = runtime.get(geofence.requestId) ?: return@forEach
                    if (!reminder.isPersonal || !reminder.isOpen || reminder.source == HReminderSource.WHATSAPP) {
                        return@forEach
                    }
                    if ((reminder.cooldownUntilMs ?: 0L) > now) return@forEach
                    if (!matchesTransition(reminder, event.geofenceTransition)) return@forEach

                    HReminderNotifier.show(context, reminder)
                    // The cooldown is part of the cloud reminder, not an Android database.
                    runtime.push(
                        reminder.copy(
                            cooldownUntilMs = now + TRIP_COOLDOWN_MS,
                            updatedAtMs = now,
                        )
                    )
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun matchesTransition(reminder: HReminder, transition: Int): Boolean = when (
        reminder.location?.triggerMode
    ) {
        HLocationTriggerMode.ARRIVE,
        HLocationTriggerMode.NEARBY -> transition == Geofence.GEOFENCE_TRANSITION_ENTER
        HLocationTriggerMode.DEPART -> transition == Geofence.GEOFENCE_TRANSITION_EXIT
        HLocationTriggerMode.DWELL -> transition == Geofence.GEOFENCE_TRANSITION_DWELL
        null -> false
    }

    companion object {
        private const val TRIP_COOLDOWN_MS = 6L * 60L * 60L * 1000L
    }
}

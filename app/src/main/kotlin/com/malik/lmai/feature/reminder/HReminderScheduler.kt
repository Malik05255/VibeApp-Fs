package com.malik.lmai.feature.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HReminderScheduler @Inject constructor(
    private val context: Context,
) {
    constructor(context: Context) : this(context.applicationContext)

    fun schedule(reminder: HReminder) {
        cancel(reminder.id)
        if (!reminder.isPersonal || !reminder.isOpen) return
        reminder.scheduledAtMs?.let { scheduleTime(reminder.id, it) }
        reminder.location?.let { scheduleLocation(reminder.id, it) }
    }

    fun cancel(reminderId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(reminderId))
        runCatching {
            LocationServices.getGeofencingClient(context).removeGeofences(listOf(reminderId))
        }
    }

    private fun scheduleTime(reminderId: String, scheduledAtMs: Long) {
        val delay = (scheduledAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val input = Data.Builder()
            .putString(HTimeReminderWorker.KEY_REMINDER_ID, reminderId)
            .build()
        val work = OneTimeWorkRequestBuilder<HTimeReminderWorker>()
            .setInputData(input)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(reminderId),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    private fun scheduleLocation(reminderId: String, location: HReminderLocation) {
        if (!hasLocationPermission()) return

        val transitions = when (location.triggerMode) {
            HLocationTriggerMode.ARRIVE,
            HLocationTriggerMode.NEARBY -> Geofence.GEOFENCE_TRANSITION_ENTER
            HLocationTriggerMode.DEPART -> Geofence.GEOFENCE_TRANSITION_EXIT
            HLocationTriggerMode.DWELL -> Geofence.GEOFENCE_TRANSITION_DWELL
        }

        val geofenceBuilder = Geofence.Builder()
            .setRequestId(reminderId)
            .setCircularRegion(location.latitude, location.longitude, location.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions)

        if (location.triggerMode == HLocationTriggerMode.DWELL) {
            geofenceBuilder.setLoiteringDelay(location.dwellMinutes.coerceAtLeast(1) * 60_000)
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                if (location.triggerMode == HLocationTriggerMode.DWELL) 0
                else GeofencingRequest.INITIAL_TRIGGER_ENTER
            )
            .addGeofence(geofenceBuilder.build())
            .build()

        try {
            LocationServices.getGeofencingClient(context)
                .addGeofences(request, geofencePendingIntent())
        } catch (_: SecurityException) {
            // UI keeps the reminder saved; scheduling is retried once permission is granted.
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, HGeofenceReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 9104, intent, flags)
    }

    companion object {
        private fun workName(id: String) = "h-reminder-time-$id"
    }
}

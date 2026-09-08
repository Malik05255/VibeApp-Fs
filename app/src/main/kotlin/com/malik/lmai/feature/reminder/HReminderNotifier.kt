package com.malik.lmai.feature.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.malik.lmai.R

object HReminderNotifier {
    private const val CHANNEL_ID = "h_personal_reminders"

    fun show(context: Context, reminder: HReminder) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                reminder.id.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val text = buildString {
            append(reminder.title)
            reminder.location?.placeNameAr?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_h_ai)
            .setContentTitle("H يذكرك")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.interpretedText.ifBlank { text }))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        NotificationManagerCompat.from(context).notify(reminder.id.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تذكيرات H",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "التذكيرات الشخصية الزمنية والمكانية من المساعد الشخصي H"
        }
        manager.createNotificationChannel(channel)
    }
}

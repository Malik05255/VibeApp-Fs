package com.almi.ai.update

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.almi.ai.BuildConfig
import com.almi.ai.R
import com.almi.ai.ui.MainActivity

/**
 * One-shot Android notification surface for automatic update discovery.
 *
 * Automatic update checks never open an in-app modal. A release is surfaced once as a real Android
 * notification for the exact (releaseId, installedVersionCode) pair. The installed version is part
 * of the token deliberately: after a signed rollback build is installed, the same logical release
 * may be announced once again with its rollback-aware Skip semantics. A newer releaseId always
 * replaces the older notification because all update notifications use one stable notification ID.
 */
internal object AlmiUpdateNotifier {
    const val EXTRA_OPEN_UPDATE = "com.almi.ai.extra.OPEN_UPDATE"

    private const val PREFS = "almi_update_notification_v1"
    private const val KEY_LAST_TOKEN = "last_notified_token"
    private const val KEY_PERMISSION_PROMPTED = "permission_prompted"
    private const val CHANNEL_ID = "almi_updates"
    private const val NOTIFICATION_ID = 12001

    fun notifyOnce(context: Context, release: AlmiRelease, language: String): Boolean {
        val app = context.applicationContext
        if (!canPostNotifications(app)) return false

        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = "${release.releaseId}:${BuildConfig.VERSION_CODE}"
        if (prefs.getString(KEY_LAST_TOKEN, null) == token) return false

        createChannel(app)
        val openIntent = Intent(app, MainActivity::class.java).apply {
            action = ACTION_OPEN_UPDATE
            putExtra(EXTRA_OPEN_UPDATE, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (language == "ar") release.titleAr else release.titleEn
        val note = if (language == "ar") release.notesAr else release.notesEn
        val detail = if (language == "ar") {
            "$note • الإصدار ${release.versionName}"
        } else {
            "$note • Version ${release.versionName}"
        }
        val actionLabel = if (language == "ar") "عرض التحديث" else "View update"

        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_almi_update)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stat_almi_update, actionLabel, pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
            prefs.edit().putString(KEY_LAST_TOKEN, token).apply()
            true
        }.getOrDefault(false)
    }

    fun shouldRequestPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_PERMISSION_PROMPTED, false)
    }

    fun markPermissionPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSION_PROMPTED, true)
            .apply()
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ALMI Updates",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "New ALMI releases and update availability"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    private const val ACTION_OPEN_UPDATE = "com.almi.ai.action.OPEN_UPDATE"
}

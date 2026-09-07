package com.malik.lmai.feature.agent.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.malik.lmai.R
import com.malik.lmai.presentation.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannels() {
        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING,
            context.getString(R.string.notification_channel_ongoing_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_ongoing_description)
            setShowBadge(false)
        }

        val resultChannel = NotificationChannel(
            CHANNEL_RESULT,
            context.getString(R.string.notification_channel_result_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_result_description)
        }

        notificationManager.createNotificationChannels(listOf(ongoingChannel, resultChannel))
    }

    fun buildOngoingNotification(activeCount: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val cancelIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_CANCEL_ALL,
            Intent(context, AgentForegroundService::class.java).apply {
                action = ACTION_CANCEL_ALL
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = if (activeCount == 1) {
            context.getString(R.string.notification_task_running_single)
        } else {
            context.getString(R.string.notification_tasks_running, activeCount)
        }

        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_agent_working))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.notification_cancel_all),
                cancelIntent,
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showCompletionNotification(chatId: Int, projectName: String?, success: Boolean) {
        // A successful assistant reply is not notification-worthy by itself. Long
        // project execution already has a low-priority foreground notification;
        // result notifications are reserved for failures that need user attention.
        if (success) return
        if (!canPostNotifications()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            chatId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_NAVIGATE_CHAT_ID, chatId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val displayName = projectName ?: context.getString(R.string.notification_default_project)

        val title = context.getString(R.string.notification_task_failed)
        val text = context.getString(R.string.notification_task_failed_text, displayName)

        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        safeNotify(RESULT_NOTIFICATION_BASE_ID + chatId, notification)
    }

    fun updateOngoingNotification(activeCount: Int) {
        if (!canPostNotifications()) return
        safeNotify(ONGOING_NOTIFICATION_ID, buildOngoingNotification(activeCount))
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun safeNotify(notificationId: Int, notification: Notification) {
        runCatching {
            notificationManager.notify(notificationId, notification)
        }
    }

    /**
     * Cancel all result notifications (the "Task Completed" / "Task Failed" ones).
     * Called when the app returns to the foreground so stale results don't pile up.
     */
    fun cancelAllResultNotifications() {
        runCatching {
            notificationManager.activeNotifications.forEach { sbn ->
                if (sbn.id >= RESULT_NOTIFICATION_BASE_ID) {
                    notificationManager.cancel(sbn.id)
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ONGOING = "agent_ongoing"
        const val CHANNEL_RESULT = "agent_result"
        const val ONGOING_NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_BASE_ID = 2000
        const val ACTION_CANCEL_ALL = "com.malik.lmai.ACTION_CANCEL_ALL_AGENT_SESSIONS"
        const val EXTRA_NAVIGATE_CHAT_ID = "navigate_chat_id"
        private const val REQUEST_CODE_CANCEL_ALL = 100
    }
}

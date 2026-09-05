package com.vibe.app.feature.agent.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject
    lateinit var sessionManager: AgentSessionManager

    @Inject
    lateinit var notificationHelper: AgentNotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        observeSessions()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AgentNotificationHelper.ACTION_CANCEL_ALL -> {
                sessionManager.stopAllSessions()
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        }

        val activeCount = sessionManager.sessions.value.size
        val notification = notificationHelper.buildOngoingNotification(activeCount.coerceAtLeast(1))

        try {
            // Promote first. Android 13+ allows foreground-service startup while the
            // notification permission is still undecided, but optional notify() calls
            // must not race the permission dialog before this service is foreground.
            ServiceCompat.startForeground(
                this,
                AgentNotificationHelper.ONGOING_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
            foregroundStarted = true
        } catch (e: RuntimeException) {
            foregroundStarted = false
            Log.e(TAG, "Unable to promote agent service to foreground", e)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        foregroundStarted = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeSessions() {
        serviceScope.launch {
            sessionManager.hasActiveSessions.collect { hasActive ->
                if (!hasActive) {
                    foregroundStarted = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (foregroundStarted) {
                    // Avoid notify() from the StateFlow's immediate onCreate emission.
                    val count = sessionManager.sessions.value.size
                    notificationHelper.updateOngoingNotification(count)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AgentForegroundService"

        fun start(context: Context): Boolean {
            val intent = Intent(context, AgentForegroundService::class.java)
            return try {
                context.startForegroundService(intent)
                true
            } catch (e: RuntimeException) {
                Log.e(TAG, "Unable to start agent foreground service", e)
                false
            }
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = AgentNotificationHelper.ACTION_CANCEL_ALL
            }
            runCatching { context.startService(intent) }
        }
    }
}

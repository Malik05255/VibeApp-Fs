package com.vibe.app.feature.agent.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
    private var isObservingSessions = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AgentNotificationHelper.ACTION_CANCEL_ALL) {
            sessionManager.stopAllSessions()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val activeCount = sessionManager.sessions.value.size
        if (activeCount == 0) {
            // A foreground service must never outlive the session that requested it.
            // This also protects against a delayed service start after a session was cancelled.
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val foregroundStarted = runCatching {
            val notification = notificationHelper.buildOngoingNotification(activeCount)
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
        }.onFailure { error ->
            Log.e(TAG, "Unable to promote agent service to foreground", error)
        }.isSuccess

        if (!foregroundStarted) {
            // Never let an OEM/Android foreground-service restriction crash the app.
            // The agent session itself is owned by AgentSessionManager and can still
            // report its provider error back to the chat instead of killing the process.
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        observeSessionsAfterForegroundStart()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeSessionsAfterForegroundStart() {
        if (isObservingSessions) return
        isObservingSessions = true

        serviceScope.launch {
            // Observe the session map directly. The old implementation observed a second,
            // asynchronously-derived boolean which could still be false while a newly-created
            // session already existed, causing the service to stop before startForeground().
            sessionManager.sessions.collect { sessions ->
                if (sessions.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    runCatching {
                        notificationHelper.updateOngoingNotification(sessions.size)
                    }.onFailure { error ->
                        Log.w(TAG, "Unable to refresh agent notification", error)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "AgentForegroundService"

        fun start(context: Context) {
            // Chat submission can happen at the same moment Android shows the runtime
            // notification permission UI. On Android 13+ do not start a foreground service
            // until POST_NOTIFICATIONS is actually granted. The agent session itself is
            // owned by AgentSessionManager, so skipping this auxiliary service must never
            // block the in-app response.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationPermissionGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!notificationPermissionGranted) {
                    Log.i(TAG, "Skipping foreground service until notification permission is granted")
                    return
                }
            }

            val intent = Intent(context, AgentForegroundService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { error ->
                // Some Android/OEM builds reject foreground-service launches depending on
                // app state or notification policy. This must not terminate a chat session.
                Log.e(TAG, "Foreground service launch rejected", error)
            }
        }

        fun cancelAll(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = AgentNotificationHelper.ACTION_CANCEL_ALL
            }
            runCatching {
                context.startService(intent)
            }.onFailure { error ->
                Log.w(TAG, "Unable to deliver cancel-all action to service", error)
            }
        }
    }
}

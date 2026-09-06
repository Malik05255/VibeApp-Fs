package com.malik.lmai.feature.agent.service

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
import com.malik.lmai.feature.agent.loop.ChatTurnMode
import com.malik.lmai.feature.agent.loop.ChatTurnPolicy
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

        val activeCount = importantSessionCount(sessionManager.sessions.value)
        if (activeCount == 0) {
            // Ordinary chat is not foreground-service work. Stop immediately so a
            // greeting or normal reply never creates an ongoing notification.
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
            sessionManager.sessions.collect { sessions ->
                val activeCount = importantSessionCount(sessions)
                if (activeCount == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    runCatching {
                        notificationHelper.updateOngoingNotification(activeCount)
                    }.onFailure { error ->
                        Log.w(TAG, "Unable to refresh agent notification", error)
                    }
                }
            }
        }
    }

    private fun importantSessionCount(sessions: Map<Int, AgentSession>): Int =
        sessions.values.count { session ->
            if (session.projectId.isNullOrBlank()) return@count false

            val latestUserText = sessionManager
                .getMessageState(session.chatId)
                ?.value
                ?.userMessages
                ?.lastOrNull()
                ?.content
                .orEmpty()

            ChatTurnPolicy.detect(latestUserText) == ChatTurnMode.APP_EXECUTION
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

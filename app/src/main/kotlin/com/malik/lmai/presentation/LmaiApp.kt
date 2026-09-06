package com.malik.lmai.presentation

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.malik.lmai.data.preferences.AppText
import com.malik.lmai.data.preferences.LanguageManager
import com.malik.lmai.feature.agent.service.AgentNotificationHelper
import com.malik.lmai.feature.ai.FreeAiBootstrapper
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class LmaiApp : Application() {
    // TODO Delete when https://github.com/google/dagger/issues/3601 is resolved.
    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var languageManager: LanguageManager

    @Inject
    lateinit var notificationHelper: AgentNotificationHelper

    @Inject
    lateinit var freeAiBootstrapper: FreeAiBootstrapper

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        AppText.initialize(this)

        runCatching {
            languageManager.applyStoredLanguage()
        }

        runCatching {
            notificationHelper.createChannels()
        }

        // Keep app startup lightweight. Mohammed's built-in cloud routes are prepared
        // immediately, while the optional 500+ MiB local model is prepared only when
        // the runtime actually needs an offline fallback. Do not download or initialize
        // MediaPipe inference during normal application startup.
        appScope.launch {
            runCatching {
                freeAiBootstrapper.ensureReady()
            }
        }

        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    runCatching {
                        notificationHelper.cancelAllResultNotifications()
                    }
                }
            })
        }
    }
}

package com.vibe.app.presentation

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vibe.app.data.preferences.AppText
import com.vibe.app.data.preferences.LanguageManager
import com.vibe.app.feature.agent.service.AgentNotificationHelper
import com.vibe.app.feature.ai.FreeAiBootstrapper
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VibeApp : Application() {
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

        // Apply the persisted app locale before any user-facing work starts.
        runCatching {
            languageManager.applyStoredLanguage()
        }

        runCatching {
            notificationHelper.createChannels()
        }

        // Free AI must be usable without first visiting settings. Provision the
        // hidden zero-key local route immediately; external APIs still take
        // priority and keep it on standby when explicitly enabled.
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
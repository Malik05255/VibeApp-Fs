package com.vibe.app.presentation

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vibe.app.data.preferences.AppText
import com.vibe.app.data.preferences.LanguageManager
import com.vibe.app.feature.agent.service.AgentNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

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

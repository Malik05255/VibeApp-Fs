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
import com.malik.lmai.feature.ai.HLocalModelManager
import com.malik.lmai.feature.ai.HMediaPipeAgentGateway
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

    @Inject
    lateinit var hLocalModelManager: HLocalModelManager

    @Inject
    lateinit var hMediaPipeAgentGateway: HMediaPipeAgentGateway

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

        // Prepare Mohammed's one-time local model on any available connection. If it
        // was already verified from a previous launch, prewarm the inference engine in
        // the background so the first ordinary chat turn avoids model-load latency.
        appScope.launch {
            runCatching {
                freeAiBootstrapper.ensureReady()
            }
            runCatching {
                hLocalModelManager.scheduleBackgroundDownload()
            }
            runCatching {
                hMediaPipeAgentGateway.warmUp()
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

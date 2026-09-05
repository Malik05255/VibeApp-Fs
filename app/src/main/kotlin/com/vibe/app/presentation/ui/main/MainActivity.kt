package com.vibe.app.presentation.ui.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.vibe.app.BuildConfig
import com.vibe.app.data.preferences.LanguageManager
import com.vibe.app.feature.agent.service.AgentNotificationHelper
import com.vibe.app.presentation.common.AuthenticatedAppRoot
import com.vibe.app.presentation.common.LocalDynamicTheme
import com.vibe.app.presentation.common.LocalThemeMode
import com.vibe.app.presentation.common.ThemeSettingProvider
import com.vibe.app.presentation.theme.CleanVibeTheme
import com.vibe.app.presentation.ui.update.ForcedUpdateScreen
import com.vibe.app.presentation.ui.update.UpdateAvailableDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var languageManager: LanguageManager

    @Inject
    lateinit var notificationHelper: AgentNotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                !mainViewModel.isReady.value
            }
        }

        super.onCreate(savedInstanceState)

        runCatching { languageManager.applyStoredLanguage() }
        runCatching { enableEdgeToEdge() }
        runCatching {
            window.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        setContent {
            val navController = rememberNavController()
            val updateState by mainViewModel.updateState.collectAsState()
            val lifecycleOwner = LocalLifecycleOwner.current

            LaunchedEffect(Unit) {
                runCatching { notificationHelper.createChannels() }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        mainViewModel.checkForUpdate()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            ThemeSettingProvider {
                CleanVibeTheme(
                    dynamicTheme = LocalDynamicTheme.current,
                    themeMode = LocalThemeMode.current
                ) {
                    val manifest = updateState.available
                    val updateRequired = manifest?.let {
                        it.mandatory || BuildConfig.VERSION_CODE < it.minimumVersionCode
                    } == true

                    if (updateRequired) {
                        ForcedUpdateScreen(
                            state = updateState,
                            onUpdate = mainViewModel::installUpdate,
                            onRetry = { mainViewModel.checkForUpdate(force = true) },
                        )
                    } else {
                        AuthenticatedAppRoot(navController = navController)
                        if (manifest != null) {
                            UpdateAvailableDialog(
                                state = updateState,
                                onUpdate = mainViewModel::installUpdate,
                                onDismiss = mainViewModel::dismissOptionalUpdate,
                            )
                        }
                    }
                }
            }
        }
    }
}

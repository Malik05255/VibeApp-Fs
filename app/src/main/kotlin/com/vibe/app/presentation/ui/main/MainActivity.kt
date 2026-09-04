package com.vibe.app.presentation.ui.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.vibe.app.data.preferences.LanguageManager
import com.vibe.app.feature.agent.service.AgentNotificationHelper
import com.vibe.app.presentation.common.AuthenticatedAppRoot
import com.vibe.app.presentation.common.LocalDynamicTheme
import com.vibe.app.presentation.common.LocalThemeMode
import com.vibe.app.presentation.common.ThemeSettingProvider
import com.vibe.app.presentation.theme.CleanVibeTheme
import com.vibe.app.presentation.ui.update.ForcedUpdateScreen
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
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }

        setContent {
            val navController = rememberNavController()
            val updateState by mainViewModel.updateState.collectAsState()

            LaunchedEffect(Unit) {
                runCatching { notificationHelper.createChannels() }
            }

            ThemeSettingProvider {
                CleanVibeTheme(
                    dynamicTheme = LocalDynamicTheme.current,
                    themeMode = LocalThemeMode.current
                ) {
                    val manifest = updateState.available
                    if (manifest != null && manifest.mandatory) {
                        ForcedUpdateScreen(
                            state = updateState,
                            onUpdate = mainViewModel::installRequiredUpdate,
                            onRetry = mainViewModel::checkForUpdate,
                        )
                    } else {
                        AuthenticatedAppRoot(navController = navController)
                    }
                }
            }
        }
    }
}

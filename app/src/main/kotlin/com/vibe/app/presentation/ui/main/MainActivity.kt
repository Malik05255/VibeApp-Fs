package com.vibe.app.presentation.ui.main

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.vibe.app.data.preferences.LanguageManager
import com.vibe.app.feature.agent.service.AgentNotificationHelper
import com.vibe.app.presentation.common.AuthenticatedAppRoot
import com.vibe.app.presentation.common.LocalDynamicTheme
import com.vibe.app.presentation.common.LocalThemeMode
import com.vibe.app.presentation.common.ThemeSettingProvider
import com.vibe.app.presentation.theme.CleanVibeTheme
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

        runCatching { enableEdgeToEdge() }
        runCatching {
            window.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
        }

        setContent {
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                runCatching { notificationHelper.createChannels() }
            }

            ThemeSettingProvider {
                CleanVibeTheme(
                    dynamicTheme = LocalDynamicTheme.current,
                    themeMode = LocalThemeMode.current
                ) {
                    AuthenticatedAppRoot(navController = navController)
                }
            }
        }
    }
}

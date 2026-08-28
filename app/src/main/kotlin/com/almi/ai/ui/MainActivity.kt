package com.almi.ai.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.almi.ai.ui.settings.AiSettingsScreen
import com.almi.ai.ui.settings.SettingsScreen
import com.almi.ai.ui.settings.SettingsViewModel
import com.almi.ai.ui.theme.AlmiTheme
import com.almi.ai.ui.tryon.TryOnScreen
import com.almi.ai.ui.tryon.TryOnViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val tryOnViewModel: TryOnViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val language by settingsViewModel.language.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val tryOnState by tryOnViewModel.uiState.collectAsState()
            var page by rememberSaveable { mutableStateOf(AppPage.TRY_ON) }
            val layoutDirection = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

            // App-level history for Android's hardware/gesture back:
            // result -> studio (preserve inputs), settings -> studio, AI Hub handles its own nested
            // history. Only the actual studio root falls through to Android's normal app exit.
            BackHandler(enabled = page == AppPage.TRY_ON && tryOnState.generatedImage != null) {
                tryOnViewModel.returnToStudio()
            }
            BackHandler(enabled = page == AppPage.SETTINGS) {
                page = AppPage.TRY_ON
            }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                AlmiTheme(themeMode = themeMode) {
                    when (page) {
                        AppPage.TRY_ON -> TryOnScreen(
                            viewModel = tryOnViewModel,
                            onOpenSettings = { page = AppPage.SETTINGS },
                        )

                        AppPage.SETTINGS -> SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { page = AppPage.TRY_ON },
                            onOpenAiSettings = { page = AppPage.AI_SETTINGS },
                        )

                        AppPage.AI_SETTINGS -> AiSettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { page = AppPage.SETTINGS },
                        )
                    }
                }
            }
        }
    }
}

private enum class AppPage {
    TRY_ON,
    SETTINGS,
    AI_SETTINGS,
}

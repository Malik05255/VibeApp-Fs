package com.almi.ai.ui

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.almi.ai.data.preferences.BodyProfileStore
import com.almi.ai.ui.components.DimensionBackdrop
import com.almi.ai.ui.components.DimensionBottomBar
import com.almi.ai.ui.components.DimensionDestination
import com.almi.ai.ui.onboarding.AlmiV7OnboardingScreen
import com.almi.ai.ui.settings.AiCenterScreen
import com.almi.ai.ui.settings.SettingsHubScreen
import com.almi.ai.ui.settings.SettingsViewModel
import com.almi.ai.ui.theme.AlmiTheme
import com.almi.ai.ui.tryon.FittingRoomScreen
import com.almi.ai.ui.tryon.TryOnViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val tryOnViewModel: TryOnViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var bodyProfileStore: BodyProfileStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val language by settingsViewModel.language.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val tryOnState by tryOnViewModel.uiState.collectAsState()
            val onboardingComplete by bodyProfileStore.onboardingComplete.collectAsState()
            val bodyProfile by bodyProfileStore.profile.collectAsState()
            var page by rememberSaveable { mutableStateOf(AppPage.HOME) }
            var homeRootKey by remember { mutableIntStateOf(0) }
            var aiRootKey by remember { mutableIntStateOf(0) }
            var settingsRootKey by remember { mutableIntStateOf(0) }
            var lastRootBackAt by remember { mutableLongStateOf(0L) }
            val layoutDirection = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

            fun openHomeRoot() {
                tryOnViewModel.returnToStudio()
                homeRootKey++
                page = AppPage.HOME
            }

            fun openAiRoot() {
                aiRootKey++
                page = AppPage.AI
            }

            fun openSettingsRoot() {
                settingsRootKey++
                page = AppPage.SETTINGS
            }

            BackHandler(
                enabled = onboardingComplete && page == AppPage.HOME && tryOnState.generatedImage != null,
            ) {
                tryOnViewModel.returnToStudio()
                homeRootKey++
            }
            BackHandler(enabled = onboardingComplete && page != AppPage.HOME) {
                openHomeRoot()
            }
            BackHandler(
                enabled = onboardingComplete && page == AppPage.HOME && tryOnState.generatedImage == null,
            ) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastRootBackAt <= EXIT_CONFIRM_WINDOW_MS) {
                    finish()
                } else {
                    lastRootBackAt = now
                    Toast.makeText(
                        this@MainActivity,
                        if (language == "ar") "اضغط رجوع مرة أخرى للخروج" else "Press back again to exit",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                AlmiTheme(themeMode = themeMode) {
                    if (!onboardingComplete) {
                        AlmiV7OnboardingScreen(
                            language = language,
                            profile = bodyProfile,
                            onLanguageChange = settingsViewModel::setLanguage,
                            onJourneyMode = bodyProfileStore::setJourneyMode,
                            onHeightChanged = bodyProfileStore::setHeightInches,
                            onWeightChanged = bodyProfileStore::setWeightPounds,
                            onMeasurementChanged = bodyProfileStore::setMeasurement,
                            onMeasurementCleared = bodyProfileStore::clearMeasurement,
                            onComplete = bodyProfileStore::completeOnboarding,
                        )
                    } else {
                        DimensionBackdrop {
                            Scaffold(
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    DimensionBottomBar(
                                        selected = when (page) {
                                            AppPage.HOME -> DimensionDestination.HOME
                                            AppPage.AI -> DimensionDestination.AI
                                            AppPage.SETTINGS -> DimensionDestination.SETTINGS
                                        },
                                        language = language,
                                        onHome = ::openHomeRoot,
                                        onAi = ::openAiRoot,
                                        onSettings = ::openSettingsRoot,
                                    )
                                },
                            ) { padding ->
                                Box(modifier = Modifier.padding(padding)) {
                                    AnimatedContent(
                                        targetState = page,
                                        transitionSpec = {
                                            (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.985f)) togetherWith
                                                (fadeOut(tween(130)) + scaleOut(tween(160), targetScale = 0.992f))
                                        },
                                        label = "almi-root-transition",
                                    ) { destination ->
                                        when (destination) {
                                            AppPage.HOME -> key(homeRootKey) {
                                                FittingRoomScreen(
                                                    viewModel = tryOnViewModel,
                                                    language = language,
                                                    onOpenAi = ::openAiRoot,
                                                )
                                            }

                                            AppPage.AI -> key(aiRootKey) {
                                                AiCenterScreen(
                                                    viewModel = settingsViewModel,
                                                    language = language,
                                                )
                                            }

                                            AppPage.SETTINGS -> key(settingsRootKey) {
                                                SettingsHubScreen(
                                                    viewModel = settingsViewModel,
                                                    language = language,
                                                    onOpenAi = ::openAiRoot,
                                                    onOpenBodyLab = bodyProfileStore::reopenBodyLab,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXIT_CONFIRM_WINDOW_MS = 2_000L
    }
}

private enum class AppPage {
    HOME,
    AI,
    SETTINGS,
}

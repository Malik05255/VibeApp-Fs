package com.almi.ai.ui

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AvatarAppearanceStore
import com.almi.ai.data.preferences.BodyProfileStore
import com.almi.ai.data.preferences.JourneyMode
import com.almi.ai.ui.avatar.AvatarDesignerScreen
import com.almi.ai.ui.body.RealHuman3DBodyScreen
import com.almi.ai.ui.onboarding.AlmiV7OnboardingScreen
import com.almi.ai.ui.settings.AiCenterScreen
import com.almi.ai.ui.settings.SettingsHubScreen
import com.almi.ai.ui.settings.SettingsViewModel
import com.almi.ai.ui.theme.AlmiTheme
import com.almi.ai.ui.tryon.AlmiV7StudioScreen
import com.almi.ai.ui.tryon.TryOnViewModel
import com.almi.ai.ui.v7.AlmiV7Backdrop
import com.almi.ai.ui.v7.AlmiV7BottomDock
import com.almi.ai.ui.v7.AlmiV7Destination
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val tryOnViewModel: TryOnViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject lateinit var bodyProfileStore: BodyProfileStore
    @Inject lateinit var avatarAppearanceStore: AvatarAppearanceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val language by settingsViewModel.language.collectAsState()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val tryOnState by tryOnViewModel.uiState.collectAsState()
            val onboardingComplete by bodyProfileStore.onboardingComplete.collectAsState()
            val journeyMode by bodyProfileStore.journeyMode.collectAsState()
            val bodyProfile by bodyProfileStore.profile.collectAsState()
            val digitalTwinSnapshotUri by bodyProfileStore.digitalTwinSnapshotUri.collectAsState()
            val avatarAppearance by avatarAppearanceStore.appearance.collectAsState()
            val avatarLooks by avatarAppearanceStore.savedLooks.collectAsState()
            var page by rememberSaveable { mutableStateOf(AppPage.STUDIO) }
            var lastRootBackAt by remember { mutableLongStateOf(0L) }
            val layoutDirection = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

            LaunchedEffect(onboardingComplete, journeyMode, digitalTwinSnapshotUri) {
                if (
                    onboardingComplete &&
                    journeyMode == JourneyMode.AVATAR &&
                    tryOnViewModel.uiState.value.personImage == null
                ) {
                    digitalTwinSnapshotUri?.let(tryOnViewModel::setPersonImage)
                }
            }

            fun openStudio() {
                tryOnViewModel.returnToStudio()
                page = AppPage.STUDIO
            }
            fun openAi() { page = AppPage.AI }
            fun openSettings() { page = AppPage.SETTINGS }
            fun openAvatar() { page = AppPage.AVATAR }
            fun openBody() { page = AppPage.BODY }

            BackHandler(
                enabled = onboardingComplete && page == AppPage.STUDIO && tryOnState.generatedImage != null,
            ) {
                tryOnViewModel.returnToStudio()
            }
            BackHandler(enabled = onboardingComplete && page in setOf(AppPage.BODY, AppPage.AVATAR)) {
                page = AppPage.SETTINGS
            }
            BackHandler(enabled = onboardingComplete && page in setOf(AppPage.AI, AppPage.SETTINGS)) {
                page = AppPage.STUDIO
            }
            BackHandler(
                enabled = onboardingComplete && page == AppPage.STUDIO && tryOnState.generatedImage == null,
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
                            avatarAppearance = avatarAppearance,
                            digitalTwinSnapshotUri = digitalTwinSnapshotUri,
                            onLanguageChange = settingsViewModel::setLanguage,
                            onJourneyMode = bodyProfileStore::setJourneyMode,
                            onHeightChanged = bodyProfileStore::setHeightInches,
                            onWeightChanged = bodyProfileStore::setWeightPounds,
                            onMeasurementChanged = bodyProfileStore::setMeasurement,
                            onMeasurementCleared = bodyProfileStore::clearMeasurement,
                            onDigitalTwinSnapshot = bodyProfileStore::setDigitalTwinSnapshotUri,
                            onAvatarPresentation = avatarAppearanceStore::setPresentation,
                            onAvatarHair = avatarAppearanceStore::setHairVariant,
                            onAvatarHairColor = avatarAppearanceStore::setHairColor,
                            onAvatarSkinColor = avatarAppearanceStore::setSkinColor,
                            onAvatarAccessories = avatarAppearanceStore::setAccessoriesVariant,
                            onAvatarFacialHair = avatarAppearanceStore::setFacialHairVariant,
                            onAvatarEyes = avatarAppearanceStore::setEyesVariant,
                            onAvatarEyebrows = avatarAppearanceStore::setEyebrowsVariant,
                            onAvatarMouth = avatarAppearanceStore::setMouthVariant,
                            onAvatarRandomize = avatarAppearanceStore::randomizeIdentity,
                            onComplete = bodyProfileStore::completeOnboarding,
                        )
                    } else {
                        AlmiV7Backdrop {
                            Scaffold(
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    if (page != AppPage.BODY) {
                                        AlmiV7BottomDock(
                                            selected = when (page) {
                                                AppPage.STUDIO -> AlmiV7Destination.STUDIO
                                                AppPage.AI -> AlmiV7Destination.AI
                                                AppPage.SETTINGS, AppPage.AVATAR, AppPage.BODY -> AlmiV7Destination.SETTINGS
                                            },
                                            language = language,
                                            onStudio = ::openStudio,
                                            onAi = ::openAi,
                                            onSettings = ::openSettings,
                                        )
                                    }
                                },
                            ) { padding ->
                                val contentPadding = if (page == AppPage.BODY) PaddingValues(0.dp) else padding
                                Box(modifier = Modifier.padding(contentPadding)) {
                                    Crossfade(
                                        targetState = page,
                                        animationSpec = tween(160),
                                        label = "almi-v9-root",
                                    ) { destination ->
                                        when (destination) {
                                            AppPage.STUDIO -> AlmiV7StudioScreen(
                                                viewModel = tryOnViewModel,
                                                language = language,
                                                onOpenAi = ::openAi,
                                            )
                                            AppPage.AI -> AiCenterScreen(
                                                viewModel = settingsViewModel,
                                                language = language,
                                            )
                                            AppPage.SETTINGS -> SettingsHubScreen(
                                                viewModel = settingsViewModel,
                                                language = language,
                                                onOpenAi = ::openAi,
                                                onOpenBodyLab = ::openBody,
                                                onOpenAvatar = ::openAvatar,
                                            )
                                            AppPage.AVATAR -> AvatarDesignerScreen(
                                                language = language,
                                                appearance = avatarAppearance,
                                                savedLooks = avatarLooks,
                                                bodyProfile = bodyProfile,
                                                digitalTwinSnapshotUri = digitalTwinSnapshotUri,
                                                onPresentation = avatarAppearanceStore::setPresentation,
                                                onHair = avatarAppearanceStore::setHairVariant,
                                                onHairColor = avatarAppearanceStore::setHairColor,
                                                onSkinColor = avatarAppearanceStore::setSkinColor,
                                                onAccessories = avatarAppearanceStore::setAccessoriesVariant,
                                                onFacialHair = avatarAppearanceStore::setFacialHairVariant,
                                                onEyes = avatarAppearanceStore::setEyesVariant,
                                                onEyebrows = avatarAppearanceStore::setEyebrowsVariant,
                                                onMouth = avatarAppearanceStore::setMouthVariant,
                                                onPreset = avatarAppearanceStore::applyPreset,
                                                onSaveLook = avatarAppearanceStore::saveLook,
                                                onApplyLook = avatarAppearanceStore::applyLook,
                                                onRandomize = avatarAppearanceStore::randomizeIdentity,
                                                onComplete = ::openSettings,
                                            )
                                            AppPage.BODY -> RealHuman3DBodyScreen(
                                                language = language,
                                                profile = bodyProfile,
                                                onHeightChanged = bodyProfileStore::setHeightInches,
                                                onWeightChanged = bodyProfileStore::setWeightPounds,
                                                onMeasurementChanged = bodyProfileStore::setMeasurement,
                                                onMeasurementCleared = bodyProfileStore::clearMeasurement,
                                                onSnapshotReady = bodyProfileStore::setDigitalTwinSnapshotUri,
                                                onComplete = ::openSettings,
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

    companion object {
        private const val EXIT_CONFIRM_WINDOW_MS = 2_000L
    }
}

private enum class AppPage { STUDIO, AI, SETTINGS, AVATAR, BODY }

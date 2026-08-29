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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import com.almi.ai.ui.tryon.TryOnViewModel
import com.almi.ai.ui.tryon.V11MirrorScreen
import com.almi.ai.ui.v11.V11Destination
import com.almi.ai.ui.v11.V11Dock
import com.almi.ai.ui.v11.V11Stage
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
            var page by rememberSaveable { mutableStateOf(AppPage.MIRROR) }
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

            fun openMirror() {
                tryOnViewModel.returnToStudio()
                page = AppPage.MIRROR
            }
            fun openAvatar() { page = AppPage.AVATAR }
            fun openAi() { page = AppPage.AI }
            fun openControl() { page = AppPage.CONTROL }
            fun openBody() { page = AppPage.BODY }
            fun useAvatar() {
                digitalTwinSnapshotUri?.let(tryOnViewModel::setPersonImage)
                page = AppPage.MIRROR
            }

            BackHandler(enabled = onboardingComplete && page == AppPage.MIRROR && tryOnState.generatedImage != null) {
                tryOnViewModel.returnToStudio()
            }
            BackHandler(enabled = onboardingComplete && page == AppPage.BODY) { page = AppPage.CONTROL }
            BackHandler(enabled = onboardingComplete && page in setOf(AppPage.AVATAR, AppPage.AI, AppPage.CONTROL)) { page = AppPage.MIRROR }
            BackHandler(enabled = onboardingComplete && page == AppPage.MIRROR && tryOnState.generatedImage == null) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastRootBackAt <= EXIT_CONFIRM_WINDOW_MS) finish()
                else {
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
                        V11Stage {
                            Scaffold(
                                containerColor = Color.Transparent,
                                bottomBar = {
                                    if (page != AppPage.BODY) {
                                        V11Dock(
                                            selected = when (page) {
                                                AppPage.MIRROR -> V11Destination.MIRROR
                                                AppPage.AVATAR -> V11Destination.AVATAR
                                                AppPage.AI -> V11Destination.AI
                                                AppPage.CONTROL, AppPage.BODY -> V11Destination.CONTROL
                                            },
                                            language = language,
                                            onMirror = ::openMirror,
                                            onAvatar = ::openAvatar,
                                            onAi = ::openAi,
                                            onControl = ::openControl,
                                        )
                                    }
                                },
                            ) { padding ->
                                val contentPadding = if (page == AppPage.BODY) PaddingValues(0.dp) else padding
                                Box(Modifier.padding(contentPadding)) {
                                    Crossfade(page, animationSpec = tween(140), label = "almi-v11-root") { destination ->
                                        when (destination) {
                                            AppPage.MIRROR -> V11MirrorScreen(
                                                viewModel = tryOnViewModel,
                                                language = language,
                                                onOpenAi = ::openAi,
                                                onOpenAvatar = ::openAvatar,
                                            )
                                            AppPage.AVATAR -> AvatarDesignerScreen(
                                                language = language,
                                                appearance = avatarAppearance,
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
                                                onRandomize = avatarAppearanceStore::randomizeIdentity,
                                                onComplete = ::useAvatar,
                                            )
                                            AppPage.AI -> AiCenterScreen(viewModel = settingsViewModel, language = language)
                                            AppPage.CONTROL -> SettingsHubScreen(
                                                viewModel = settingsViewModel,
                                                language = language,
                                                onOpenAi = ::openAi,
                                                onOpenBodyLab = ::openBody,
                                                onOpenAvatar = ::openAvatar,
                                            )
                                            AppPage.BODY -> RealHuman3DBodyScreen(
                                                language = language,
                                                profile = bodyProfile,
                                                onHeightChanged = bodyProfileStore::setHeightInches,
                                                onWeightChanged = bodyProfileStore::setWeightPounds,
                                                onMeasurementChanged = bodyProfileStore::setMeasurement,
                                                onMeasurementCleared = bodyProfileStore::clearMeasurement,
                                                onSnapshotReady = bodyProfileStore::setDigitalTwinSnapshotUri,
                                                onComplete = ::openControl,
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

    companion object { private const val EXIT_CONFIRM_WINDOW_MS = 2_000L }
}

private enum class AppPage { MIRROR, AVATAR, AI, CONTROL, BODY }

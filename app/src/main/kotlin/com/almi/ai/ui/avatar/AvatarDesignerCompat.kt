package com.almi.ai.ui.avatar

import androidx.compose.runtime.Composable
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile

/**
 * Compatibility overload for the first-run flow. The full Control Room entry adds presets and
 * persistent Look slots; onboarding keeps the same Filament character-select experience without
 * forcing those secondary controls into the first-use journey.
 */
@Composable
fun AvatarDesignerScreen(
    language: String,
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    digitalTwinSnapshotUri: String?,
    onPresentation: (AvatarPresentation) -> Unit,
    onHair: (String) -> Unit,
    onHairColor: (String) -> Unit,
    onSkinColor: (String) -> Unit,
    onAccessories: (String) -> Unit,
    onFacialHair: (String) -> Unit,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
    onRandomize: () -> Unit,
    onComplete: () -> Unit,
) {
    AvatarDesignerScreen(
        language = language,
        appearance = appearance,
        savedLooks = emptyMap(),
        bodyProfile = bodyProfile,
        digitalTwinSnapshotUri = digitalTwinSnapshotUri,
        onPresentation = onPresentation,
        onHair = onHair,
        onHairColor = onHairColor,
        onSkinColor = onSkinColor,
        onAccessories = onAccessories,
        onFacialHair = onFacialHair,
        onEyes = onEyes,
        onEyebrows = onEyebrows,
        onMouth = onMouth,
        onPreset = {},
        onSaveLook = {},
        onApplyLook = {},
        onRandomize = onRandomize,
        onComplete = onComplete,
    )
}

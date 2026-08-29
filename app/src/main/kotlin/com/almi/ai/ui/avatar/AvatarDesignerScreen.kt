package com.almi.ai.ui.avatar

import androidx.compose.runtime.Composable
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile

/** Stable entry point retained for onboarding/settings; v11 owns the actual experience. */
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
    V11AvatarScreen(
        language = language,
        appearance = appearance,
        bodyProfile = bodyProfile,
        digitalTwinSnapshotUri = digitalTwinSnapshotUri,
        onPresentation = onPresentation,
        onHair = onHair,
        onHairColor = onHairColor,
        onSkinColor = onSkinColor,
        onEyes = onEyes,
        onEyebrows = onEyebrows,
        onMouth = onMouth,
        onComplete = onComplete,
    )
}

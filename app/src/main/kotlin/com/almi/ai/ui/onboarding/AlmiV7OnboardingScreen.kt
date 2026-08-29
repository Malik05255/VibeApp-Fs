package com.almi.ai.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.JourneyMode
import com.almi.ai.ui.avatar.AvatarDesignerScreen
import com.almi.ai.ui.body.RealHuman3DBodyScreen

private enum class V7IntroStage { LANGUAGE, JOURNEY, DIGITAL_HUMAN, AVATAR, PHOTO }

@Composable
fun AlmiV7OnboardingScreen(
    language: String,
    profile: BodyProfile,
    avatarAppearance: AvatarAppearance,
    digitalTwinSnapshotUri: String?,
    onLanguageChange: (String) -> Unit,
    onJourneyMode: (JourneyMode) -> Unit,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onDigitalTwinSnapshot: (String) -> Unit,
    onAvatarPresentation: (AvatarPresentation) -> Unit,
    onAvatarHair: (String) -> Unit,
    onAvatarHairColor: (String) -> Unit,
    onAvatarSkinColor: (String) -> Unit,
    onAvatarAccessories: (String) -> Unit,
    onAvatarFacialHair: (String) -> Unit,
    onAvatarEyes: (String) -> Unit,
    onAvatarEyebrows: (String) -> Unit,
    onAvatarMouth: (String) -> Unit,
    onAvatarRandomize: () -> Unit,
    onComplete: () -> Unit,
) {
    var stageName by rememberSaveable { mutableStateOf(V7IntroStage.LANGUAGE.name) }
    val stage = runCatching { V7IntroStage.valueOf(stageName) }.getOrDefault(V7IntroStage.LANGUAGE)

    BackHandler(enabled = stage != V7IntroStage.LANGUAGE) {
        stageName = when (stage) {
            V7IntroStage.LANGUAGE -> V7IntroStage.LANGUAGE.name
            V7IntroStage.JOURNEY -> V7IntroStage.LANGUAGE.name
            V7IntroStage.DIGITAL_HUMAN, V7IntroStage.PHOTO -> V7IntroStage.JOURNEY.name
            V7IntroStage.AVATAR -> V7IntroStage.DIGITAL_HUMAN.name
        }
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
        label = "almi-intro-minimal",
    ) { current ->
        when (current) {
            V7IntroStage.LANGUAGE -> LanguageScreen(
                onArabic = {
                    onLanguageChange("ar")
                    stageName = V7IntroStage.JOURNEY.name
                },
                onEnglish = {
                    onLanguageChange("en")
                    stageName = V7IntroStage.JOURNEY.name
                },
            )

            V7IntroStage.JOURNEY -> JourneyScreen(
                language = language,
                onTwin = {
                    onJourneyMode(JourneyMode.AVATAR)
                    stageName = V7IntroStage.DIGITAL_HUMAN.name
                },
                onPhoto = {
                    onJourneyMode(JourneyMode.PHOTO)
                    stageName = V7IntroStage.PHOTO.name
                },
            )

            V7IntroStage.DIGITAL_HUMAN -> RealHuman3DBodyScreen(
                language = language,
                profile = profile,
                onHeightChanged = onHeightChanged,
                onWeightChanged = onWeightChanged,
                onMeasurementChanged = onMeasurementChanged,
                onMeasurementCleared = onMeasurementCleared,
                onSnapshotReady = onDigitalTwinSnapshot,
                onComplete = { stageName = V7IntroStage.AVATAR.name },
            )

            V7IntroStage.AVATAR -> AvatarDesignerScreen(
                language = language,
                appearance = avatarAppearance,
                bodyProfile = profile,
                digitalTwinSnapshotUri = digitalTwinSnapshotUri,
                onPresentation = onAvatarPresentation,
                onHair = onAvatarHair,
                onHairColor = onAvatarHairColor,
                onSkinColor = onAvatarSkinColor,
                onAccessories = onAvatarAccessories,
                onFacialHair = onAvatarFacialHair,
                onEyes = onAvatarEyes,
                onEyebrows = onAvatarEyebrows,
                onMouth = onAvatarMouth,
                onRandomize = onAvatarRandomize,
                onComplete = onComplete,
            )

            V7IntroStage.PHOTO -> PhotoScreen(language, onComplete)
        }
    }
}

@Composable
private fun LanguageScreen(
    onArabic: () -> Unit,
    onEnglish: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(scheme.background).padding(24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "ALMI",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Wear it before you own it.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "اختر لغتك  ·  Choose your language",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Button(onClick = onArabic, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("العربية", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onEnglish, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("English", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun JourneyScreen(
    language: String,
    onTwin: () -> Unit,
    onPhoto: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().background(scheme.background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "ALMI",
            color = scheme.tertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            tr(language, "كيف تريد أن تبدأ؟", "How do you want to start?"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            tr(language, "مساران فقط. يمكنك تغيير اختيارك لاحقًا.", "Two paths only. You can change this later."),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(10.dp))

        PathCard(
            index = "01",
            title = tr(language, "ابنِ جسمي", "Build my body"),
            description = tr(language, "قياسات تفاعلية على مجسم 360° ثم أفاتار بسيط.", "Interactive measurements on a 360° body, then a simple avatar."),
            selectedStyle = true,
            onClick = onTwin,
        )
        PathCard(
            index = "02",
            title = tr(language, "استخدم صورتي", "Use my photo"),
            description = tr(language, "تجاوز القياسات وابدأ بصورة كاملة للجسم.", "Skip measurements and start with a full-body photo."),
            selectedStyle = false,
            onClick = onPhoto,
        )
    }
}

@Composable
private fun PathCard(
    index: String,
    title: String,
    description: String,
    selectedStyle: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (selectedStyle) scheme.primary else scheme.surface,
        border = if (selectedStyle) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                index,
                color = if (selectedStyle) scheme.onPrimary.copy(alpha = 0.55f) else scheme.tertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                title,
                color = if (selectedStyle) scheme.onPrimary else scheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                description,
                color = if (selectedStyle) scheme.onPrimary.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "→",
                color = if (selectedStyle) scheme.onPrimary else scheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun PhotoScreen(language: String, onComplete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().background(scheme.background).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(22.dp))
        Text("ALMI / PHOTO", color = scheme.tertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(
            tr(language, "صورة واحدة واضحة تكفي", "One clear photo is enough"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            tr(language, "الجسم كامل، إضاءة متساوية، وقفة طبيعية، وبدون فلاتر.", "Full body, even light, natural stance and no filters."),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PhotoRule("01", tr(language, "من الرأس إلى القدم", "Head to toe"))
                PhotoRule("02", tr(language, "خلفية هادئة", "Clean background"))
                PhotoRule("03", tr(language, "لا فلاتر أو عدسة واسعة", "No filters or wide lens"))
            }
        }

        Spacer(Modifier.weight(1f))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Text(tr(language, "فتح الاستوديو", "Open Studio"), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PhotoRule(code: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(code, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en

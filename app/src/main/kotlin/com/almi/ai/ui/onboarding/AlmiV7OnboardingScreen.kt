package com.almi.ai.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.JourneyMode
import com.almi.ai.ui.body.RealHuman3DBodyScreen

private enum class V7IntroStage { LANGUAGE, JOURNEY, DIGITAL_HUMAN, PHOTO }

@Composable
fun AlmiV7OnboardingScreen(
    language: String,
    profile: BodyProfile,
    onLanguageChange: (String) -> Unit,
    onJourneyMode: (JourneyMode) -> Unit,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onDigitalTwinSnapshot: (String) -> Unit,
    onComplete: () -> Unit,
) {
    var stageName by rememberSaveable { mutableStateOf(V7IntroStage.LANGUAGE.name) }
    val stage = runCatching { V7IntroStage.valueOf(stageName) }.getOrDefault(V7IntroStage.LANGUAGE)

    BackHandler(enabled = stage != V7IntroStage.LANGUAGE) {
        stageName = when (stage) {
            V7IntroStage.LANGUAGE -> V7IntroStage.LANGUAGE.name
            V7IntroStage.JOURNEY -> V7IntroStage.LANGUAGE.name
            V7IntroStage.DIGITAL_HUMAN, V7IntroStage.PHOTO -> V7IntroStage.JOURNEY.name
        }
    }

    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInHorizontally(tween(360)) { it / 9 }) togetherWith
                (fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { -it / 10 })
        },
        label = "almi-v7-intro",
    ) { current ->
        when (current) {
            V7IntroStage.LANGUAGE -> LanguageMoment(
                onArabic = {
                    onLanguageChange("ar")
                    stageName = V7IntroStage.JOURNEY.name
                },
                onEnglish = {
                    onLanguageChange("en")
                    stageName = V7IntroStage.JOURNEY.name
                },
            )

            V7IntroStage.JOURNEY -> JourneyMoment(
                language = language,
                onDigitalHuman = {
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
                onComplete = onComplete,
            )

            V7IntroStage.PHOTO -> PhotoMoment(language = language, onComplete = onComplete)
        }
    }
}

@Composable
private fun LanguageMoment(onArabic: () -> Unit, onEnglish: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(scheme.background).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Surface(shape = CircleShape, color = scheme.onBackground) {
                Text(
                    "A",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    color = scheme.background,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
            }
            Text("ALMI", style = MaterialTheme.typography.labelLarge, color = scheme.error, fontWeight = FontWeight.Black)
            Text("Choose your language\nاختر لغتك", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, fontWeight = FontWeight.Black)
            Text(
                "This choice changes the entire experience and can be changed later.\nيمكنك تغيير اللغة لاحقًا من الإعدادات.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onArabic, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("العربية", fontWeight = FontWeight.Black) }
            OutlinedButton(onClick = onEnglish, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("English", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun JourneyMoment(language: String, onDigitalHuman: () -> Unit, onPhoto: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ALMI / PERSONAL TWIN", style = MaterialTheme.typography.labelLarge, color = scheme.error, fontWeight = FontWeight.Black)
        Text(tr(language, "كيف تريد أن نكمل رحلتك؟", "How should your ALMI journey begin?"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(
            tr(language, "أنشئ توأمًا رقميًا ثلاثي الأبعاد بقياساتك، أو استخدم صورتك الشخصية مباشرة. يمكنك التبديل لاحقًا.", "Build a measurement-aware 3D digital twin, or start directly with your own photo. You can switch later."),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        JourneyChoice(
            eyebrow = "REAL 3D / FILAMENT",
            title = tr(language, "توأمي الرقمي", "My digital twin"),
            description = tr(language, "مجسم إنسان حقيقي ثلاثي الأبعاد يتغير مع طولك ووزنك وقياساتك، ثم يُحفظ كمرجع لتجربة المقاسات.", "A real 3D human that changes with your height, weight and measurements, then becomes the body reference for size simulation."),
            action = tr(language, "ابدأ بناء جسمي", "Build my body"),
            emphasized = true,
            onClick = onDigitalHuman,
        )

        JourneyChoice(
            eyebrow = "PHOTO / DIRECT",
            title = tr(language, "صورتي الشخصية", "My personal photo"),
            description = tr(language, "ابدأ بصورة واضحة للجسم وانتقل مباشرة إلى استوديو تجربة الملابس بالذكاء الاصطناعي.", "Start with a clear full-body photo and go directly to the AI try-on studio."),
            action = tr(language, "استخدم صورتي", "Use my photo"),
            emphasized = false,
            onClick = onPhoto,
        )
    }
}

@Composable
private fun JourneyChoice(
    eyebrow: String,
    title: String,
    description: String,
    action: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = if (emphasized) scheme.onBackground else scheme.surface,
        border = if (emphasized) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = if (emphasized) scheme.errorContainer else scheme.error, fontWeight = FontWeight.Black)
            Text(title, style = MaterialTheme.typography.headlineMedium, color = if (emphasized) scheme.background else scheme.onSurface, fontWeight = FontWeight.Black)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = if (emphasized) scheme.background.copy(alpha = 0.74f) else scheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("$action  →", style = MaterialTheme.typography.titleMedium, color = if (emphasized) scheme.background else scheme.primary, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PhotoMoment(language: String, onComplete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().background(scheme.background).padding(horizontal = 20.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ALMI / PHOTO", style = MaterialTheme.typography.labelLarge, color = scheme.error, fontWeight = FontWeight.Black)
        Text(tr(language, "صورتك هي نقطة البداية", "Your photo is the starting point"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(
            tr(language, "داخل الاستوديو ستختار صورة كاملة وواضحة للجسم. استخدم وقفة طبيعية وإضاءة متساوية، ثم أضف قطعة الملابس أو رابط المنتج.", "Inside the studio, choose a clear full-body photo. Use a natural stance and even lighting, then add a garment image or product link."),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), color = scheme.surface, border = BorderStroke(1.dp, scheme.outlineVariant)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PhotoRule("01", tr(language, "الجسم كامل من الرأس إلى القدم", "Full body from head to toe"))
                PhotoRule("02", tr(language, "خلفية واضحة وإضاءة متساوية", "Clean background and even lighting"))
                PhotoRule("03", tr(language, "وقفة طبيعية والذراعان بعيدتان قليلًا", "Natural stance with arms slightly away"))
                PhotoRule("04", tr(language, "لا فلاتر ولا تشويه للعدسة", "No filters or lens distortion"))
            }
        }

        Spacer(Modifier.weight(1f))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Text(tr(language, "فتح استوديو ALMI", "Open ALMI Studio"), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PhotoRule(code: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en

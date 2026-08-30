package com.almi.ai.ui.v12

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.JourneyMode

private enum class RefOnboardingStep { LANGUAGE, IDENTITY, AVATAR }

@Composable
internal fun V12ReferenceOnboardingScreen(
    language: String,
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    digitalTwinSnapshotUri: String?,
    onLanguageChange: (String) -> Unit,
    onJourneyMode: (JourneyMode) -> Unit,
    onAvatarPresentation: (AvatarPresentation) -> Unit,
    onAvatarHair: (String) -> Unit,
    onAvatarHairColor: (String) -> Unit,
    onAvatarSkinColor: (String) -> Unit,
    onAvatarEyes: (String) -> Unit,
    onAvatarEyebrows: (String) -> Unit,
    onAvatarMouth: (String) -> Unit,
    onComplete: () -> Unit,
) {
    var stepName by rememberSaveable { mutableStateOf(RefOnboardingStep.LANGUAGE.name) }
    val step = runCatching { RefOnboardingStep.valueOf(stepName) }.getOrDefault(RefOnboardingStep.LANGUAGE)

    when (step) {
        RefOnboardingStep.LANGUAGE -> RefLanguageScreen(
            language = language,
            onPick = {
                onLanguageChange(it)
                stepName = RefOnboardingStep.IDENTITY.name
            },
        )
        RefOnboardingStep.IDENTITY -> RefIdentityScreen(
            language = language,
            onAvatar = {
                onJourneyMode(JourneyMode.AVATAR)
                stepName = RefOnboardingStep.AVATAR.name
            },
            onPhoto = {
                onJourneyMode(JourneyMode.PHOTO)
                onComplete()
            },
            onBack = { stepName = RefOnboardingStep.LANGUAGE.name },
        )
        RefOnboardingStep.AVATAR -> V12ReferenceAvatarScreen(
            language = language,
            appearance = appearance,
            bodyProfile = bodyProfile,
            digitalTwinSnapshotUri = digitalTwinSnapshotUri,
            onPresentation = onAvatarPresentation,
            onHair = onAvatarHair,
            onHairColor = onAvatarHairColor,
            onSkinColor = onAvatarSkinColor,
            onEyes = onAvatarEyes,
            onEyebrows = onAvatarEyebrows,
            onMouth = onAvatarMouth,
            onBack = { stepName = RefOnboardingStep.IDENTITY.name },
            onComplete = onComplete,
        )
    }
}

@Composable
private fun RefLanguageScreen(language: String, onPick: (String) -> Unit) {
    RefOnboardingBackdrop {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALMI / FILAMENT", color = Color(0xFF79B2DD), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("ALMI 12", color = Color(0xFF173657), fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text(
                if (language == "ar") "اختر لغتك" else "Choose your language",
                modifier = Modifier.padding(top = 8.dp),
                color = Color(0xFF173657),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 22.dp).fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = Color.White.copy(alpha = .82f),
            border = BorderStroke(1.dp, Color(0xFF82C9F3).copy(alpha = .30f)),
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RefLanguageOption("العربية", "AR", Color(0xFF4FBFF2), language == "ar") { onPick("ar") }
                RefLanguageOption("English", "EN", Color(0xFFFF86AA), language != "ar") { onPick("en") }
            }
        }

        Text(
            if (language == "ar") "تقدر تغيّرها لاحقًا من الإعدادات" else "You can change this later",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
            color = Color(0xFF173657).copy(alpha = .55f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun RefLanguageOption(title: String, code: String, accent: Color, active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(82.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = if (active) accent.copy(alpha = .14f) else Color(0xFFF7FBFE),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = if (active) .75f else .25f)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, color = Color(0xFF173657), fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(code, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            }
            Surface(Modifier.size(42.dp), CircleShape, color = accent) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (active) "✓" else "›", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RefIdentityScreen(language: String, onAvatar: () -> Unit, onPhoto: () -> Unit, onBack: () -> Unit) {
    RefOnboardingBackdrop {
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clickable(onClick = onBack),
            shape = RoundedCornerShape(24.dp),
            color = Color.White.copy(alpha = .82f),
            border = BorderStroke(1.dp, Color(0xFF4FBFF2).copy(alpha = .28f)),
        ) {
            Text("‹", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Color(0xFF173657), fontSize = 28.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALMI / IDENTITY", color = Color(0xFF79B2DD), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                if (language == "ar") "ابدأ بطريقتك" else "START YOUR WAY",
                color = Color(0xFF173657),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RefIdentityCard(
                title = if (language == "ar") "أنشئ شخصية" else "CREATE AVATAR",
                subtitle = if (language == "ar") "اختر ذكر أو أنثى ثم خصص الشكل" else "Choose a character, then customize",
                glyph = "✦",
                accent = Color(0xFF4FBFF2),
                onClick = onAvatar,
            )
            RefIdentityCard(
                title = if (language == "ar") "استخدم صورتك" else "USE YOUR PHOTO",
                subtitle = if (language == "ar") "ابدأ من صورتك مباشرة" else "Start directly from your image",
                glyph = "◉",
                accent = Color(0xFFFF86AA),
                onClick = onPhoto,
            )
        }
    }
}

@Composable
private fun RefIdentityCard(title: String, subtitle: String, glyph: String, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(126.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = .86f),
        border = BorderStroke(1.5.dp, accent.copy(alpha = .35f)),
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(Modifier.size(66.dp), CircleShape, color = accent.copy(alpha = .15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(glyph, color = accent, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
            }
            Column {
                Text(title, color = Color(0xFF173657), fontSize = 21.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color(0xFF173657).copy(alpha = .60f), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun RefOnboardingBackdrop(content: @Composable Box.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFEAF7FF), Color(0xFFFBFDFF), Color(0xFFF2FAFF))))
            .statusBarsPadding(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val grid = Color(0xFFB7D9EF).copy(alpha = .20f)
            val step = 44f
            var x = 0f
            while (x <= size.width) {
                drawLine(grid, Offset(x, size.height * .18f), Offset(x, size.height * .88f), 1f)
                x += step
            }
            var y = size.height * .18f
            while (y <= size.height * .88f) {
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
            drawCircle(Color(0xFF4FBFF2).copy(alpha = .08f), size.minDimension * .55f, Offset(size.width * .16f, size.height * .42f))
            drawCircle(Color(0xFFFF86AA).copy(alpha = .07f), size.minDimension * .46f, Offset(size.width * .90f, size.height * .63f))
        }
        content()
    }
}

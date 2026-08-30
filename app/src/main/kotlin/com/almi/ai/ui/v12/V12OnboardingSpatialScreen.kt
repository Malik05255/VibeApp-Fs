package com.almi.ai.ui.v12

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.JourneyMode

private enum class SpatialOnboardingStep { LANGUAGE, IDENTITY, AVATAR }

private val AwakeInk = Color(0xFF173A60)
private val AwakeBlue = Color(0xFF57BFFF)
private val AwakePink = Color(0xFFFF8FB8)
private val AwakeMint = Color(0xFF57D8C1)
private val AwakeViolet = Color(0xFFA58AFF)

@Composable
internal fun V12OnboardingSpatialScreen(
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
    var stepName by rememberSaveable { mutableStateOf(SpatialOnboardingStep.LANGUAGE.name) }
    val step = runCatching { SpatialOnboardingStep.valueOf(stepName) }
        .getOrDefault(SpatialOnboardingStep.LANGUAGE)

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(tween(360), initialScale = .94f)) togetherWith
                (fadeOut(tween(180)) + scaleOut(tween(230), targetScale = 1.04f))
        },
        label = "v12-awaken-flow",
    ) { destination ->
        when (destination) {
            SpatialOnboardingStep.LANGUAGE -> AwakeLanguageScene(
                language = language,
                onPick = {
                    onLanguageChange(it)
                    stepName = SpatialOnboardingStep.IDENTITY.name
                },
            )

            SpatialOnboardingStep.IDENTITY -> AwakeIdentityScene(
                language = language,
                onAvatar = {
                    onJourneyMode(JourneyMode.AVATAR)
                    stepName = SpatialOnboardingStep.AVATAR.name
                },
                onPhoto = {
                    onJourneyMode(JourneyMode.PHOTO)
                    onComplete()
                },
                onBack = { stepName = SpatialOnboardingStep.LANGUAGE.name },
            )

            SpatialOnboardingStep.AVATAR -> V12AvatarDigitalLabScreen(
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
                onBack = { stepName = SpatialOnboardingStep.IDENTITY.name },
                onComplete = onComplete,
            )
        }
    }
}

@Composable
private fun AwakeLanguageScene(language: String, onPick: (String) -> Unit) {
    val pulse by rememberInfiniteTransition(label = "awake-language")
        .animateFloat(
            initialValue = .95f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
            label = "awake-language-pulse",
        )

    AwakeBackdrop {
        Column(Modifier.align(Alignment.TopStart).padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text("ALMI", color = AwakeInk, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.8).sp)
            Text("12 / AWAKEN", color = AwakeBlue, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        }

        AwakeCore(
            modifier = Modifier.align(Alignment.Center).offset(y = (-74).dp).scale(pulse),
            accent = AwakeBlue,
            secondary = AwakeViolet,
            label = "12",
        )

        Text(
            if (language == "ar") "اختر اللغة التي سيتشكل حولها العالم" else "CHOOSE THE LANGUAGE YOUR WORLD FORMS AROUND",
            modifier = Modifier.align(Alignment.Center).offset(y = 82.dp).padding(horizontal = 40.dp),
            color = AwakeInk,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        AwakeOrbit(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = 70.dp),
            code = "AR",
            title = "العربية",
            accent = AwakeMint,
            active = language == "ar",
        ) { onPick("ar") }
        AwakeOrbit(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 70.dp),
            code = "EN",
            title = "English",
            accent = AwakePink,
            active = language != "ar",
        ) { onPick("en") }
    }
}

@Composable
private fun AwakeIdentityScene(
    language: String,
    onAvatar: () -> Unit,
    onPhoto: () -> Unit,
    onBack: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "awake-identity")
        .animateFloat(
            initialValue = .98f,
            targetValue = 1.055f,
            animationSpec = infiniteRepeatable(tween(1450), RepeatMode.Reverse),
            label = "awake-identity-pulse",
        )

    AwakeBackdrop {
        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ALMI / IDENTITY FIELD", color = AwakeViolet, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(if (language == "ar") "كيف تريد أن تعيش داخل ALMI؟" else "HOW DO YOU WANT TO EXIST IN ALMI?", color = AwakeInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Surface(
                modifier = Modifier.size(47.dp).clickable(onClick = onBack),
                shape = CircleShape,
                color = Color(0xEDFFFFFF),
                border = BorderStroke(1.dp, AwakeBlue.copy(alpha = .30f)),
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(V12GlyphType.BACK, AwakeInk, Modifier.size(20.dp))
                }
            }
        }

        AwakeCore(
            modifier = Modifier.align(Alignment.Center).offset(y = (-60).dp).scale(pulse),
            accent = AwakePink,
            secondary = AwakeBlue,
            label = "YOU",
        )

        AwakeIdentityOrbit(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 76.dp),
            title = if (language == "ar") "شخصية حيّة" else "LIVE IDENTITY",
            subtitle = "3D • MOTION • BODY",
            accent = AwakePink,
            glyph = V12GlyphType.AVATAR,
            onClick = onAvatar,
        )
        AwakeIdentityOrbit(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 76.dp),
            title = if (language == "ar") "صورتي" else "MY PHOTO",
            subtitle = "CAMERA • DIRECT FIT",
            accent = AwakeBlue,
            glyph = V12GlyphType.CAMERA,
            onClick = onPhoto,
        )
    }
}

@Composable
private fun AwakeBackdrop(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFEAF8FF),
                        Color(0xFFF6F2FF),
                        Color(0xFFFFF4F9),
                        Color(0xFFF0FFFA),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(AwakeBlue.copy(alpha = .10f), size.minDimension * .62f, Offset(size.width * .02f, size.height * .15f))
            drawCircle(AwakePink.copy(alpha = .085f), size.minDimension * .55f, Offset(size.width * .97f, size.height * .67f))
            drawCircle(AwakeMint.copy(alpha = .07f), size.minDimension * .42f, Offset(size.width * .28f, size.height * .94f))
            drawCircle(AwakeViolet.copy(alpha = .08f), size.minDimension * .34f, Offset(size.width * .69f, size.height * .36f), style = Stroke(1.25f))
        }
        content()
    }
}

@Composable
private fun AwakeCore(
    modifier: Modifier,
    accent: Color,
    secondary: Color,
    label: String,
) {
    Box(modifier.size(184.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(accent.copy(alpha = .08f), size.minDimension * .50f)
            drawCircle(secondary.copy(alpha = .13f), size.minDimension * .39f, style = Stroke(2f))
            drawCircle(accent.copy(alpha = .16f), size.minDimension * .30f)
            drawCircle(Color.White.copy(alpha = .94f), size.minDimension * .22f)
            drawCircle(accent.copy(alpha = .38f), size.minDimension * .22f, style = Stroke(1.5f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = AwakeInk, fontSize = if (label == "YOU") 14.sp else 25.sp, fontWeight = FontWeight.Black)
            Text("ALMI CORE", color = accent, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.05.sp)
        }
    }
}

@Composable
private fun AwakeOrbit(
    modifier: Modifier,
    code: String,
    title: String,
    accent: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(140.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .19f) else Color(0xECFFFFFF),
        border = BorderStroke(1.6.dp, accent.copy(alpha = if (active) .90f else .42f)),
        shadowElevation = if (active) 17.dp else 8.dp,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(code, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
            Text(title, modifier = Modifier.padding(top = 5.dp), color = AwakeInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(if (active) "ACTIVE" else "ENTER", modifier = Modifier.padding(top = 7.dp), color = AwakeInk.copy(alpha = .34f), fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        }
    }
}

@Composable
private fun AwakeIdentityOrbit(
    modifier: Modifier,
    title: String,
    subtitle: String,
    accent: Color,
    glyph: V12GlyphType,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(156.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xEDFFFFFF),
        border = BorderStroke(1.5.dp, accent.copy(alpha = .48f)),
        shadowElevation = 17.dp,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = accent.copy(alpha = .13f)) {
                V12Glyph(glyph, accent, Modifier.padding(10.dp).size(29.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, color = AwakeInk, fontSize = 12.5.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(subtitle, modifier = Modifier.padding(top = 3.dp), color = AwakeInk.copy(alpha = .36f), fontSize = 6.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = .45.sp)
        }
    }
}

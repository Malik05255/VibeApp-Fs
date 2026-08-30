package com.almi.ai.ui.v12

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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

private enum class V12OnboardingStep { LANGUAGE, IDENTITY, AVATAR }

private val PortalInk = Color(0xFF18395D)
private val PortalBlue = Color(0xFF5DBEFF)
private val PortalPink = Color(0xFFFF8FB8)
private val PortalMint = Color(0xFF58D6C2)
private val PortalViolet = Color(0xFFA58BFF)

@Composable
internal fun V12OnboardingScreen(
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
    var step by rememberSaveable { mutableStateOf(V12OnboardingStep.LANGUAGE) }

    when (step) {
        V12OnboardingStep.LANGUAGE -> LanguagePortal(
            language = language,
            onPick = {
                onLanguageChange(it)
                step = V12OnboardingStep.IDENTITY
            },
        )

        V12OnboardingStep.IDENTITY -> IdentityPortal(
            language = language,
            onAvatar = {
                onJourneyMode(JourneyMode.AVATAR)
                step = V12OnboardingStep.AVATAR
            },
            onPhoto = {
                onJourneyMode(JourneyMode.PHOTO)
                onComplete()
            },
            onBack = { step = V12OnboardingStep.LANGUAGE },
        )

        V12OnboardingStep.AVATAR -> V12AvatarScreen(
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
            onBack = { step = V12OnboardingStep.IDENTITY },
            onComplete = onComplete,
        )
    }
}

@Composable
private fun PortalBackground(content: @Composable Box.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFEAF8FF),
                        Color(0xFFF4F1FF),
                        Color(0xFFFFF4F9),
                        Color(0xFFF1FFFB),
                    ),
                ),
            )
            .statusBarsPadding(),
        content = content,
    )
}

@Composable
private fun LanguagePortal(
    language: String,
    onPick: (String) -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "language-core")
        .animateFloat(
            initialValue = .96f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
            label = "language-core-pulse",
        )

    PortalBackground {
        PortalAtmosphere()

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text("ALMI", color = PortalInk, fontSize = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.8).sp)
            Text("V12 / AWAKEN", color = PortalBlue, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.Center).offset(y = (-36).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LuminousCore(
                modifier = Modifier.scale(pulse),
                accent = PortalBlue,
                secondary = PortalViolet,
                label = "12",
            )
            Spacer(Modifier.height(30.dp))
            Text(
                if (language == "ar") "ابدأ من لغتك" else "CHOOSE YOUR VOICE",
                color = PortalInk,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                if (language == "ar") "المشهد سيتكيّف فورًا معك" else "THE WHOLE WORLD ADAPTS INSTANTLY",
                modifier = Modifier.padding(top = 6.dp),
                color = PortalInk.copy(alpha = .46f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .7.sp,
            )
        }

        OrbitChoice(
            modifier = Modifier.align(Alignment.CenterStart).offset(x = 28.dp, y = 184.dp),
            code = "AR",
            title = "العربية",
            accent = PortalMint,
            active = language == "ar",
            onClick = { onPick("ar") },
        )
        OrbitChoice(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-28).dp, y = 184.dp),
            code = "EN",
            title = "English",
            accent = PortalPink,
            active = language != "ar",
            onClick = { onPick("en") },
        )

        Text(
            "TAP A LANGUAGE • ALMI WILL REORIENT THE ENTIRE EXPERIENCE",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp, start = 26.dp, end = 26.dp),
            color = PortalInk.copy(alpha = .34f),
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = .75.sp,
        )
    }
}

@Composable
private fun IdentityPortal(
    language: String,
    onAvatar: () -> Unit,
    onPhoto: () -> Unit,
    onBack: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "identity-core")
        .animateFloat(
            initialValue = .98f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
            label = "identity-core-pulse",
        )

    PortalBackground {
        PortalAtmosphere()

        Row(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ALMI / IDENTITY FIELD", color = PortalViolet, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(if (language == "ar") "كيف تريد أن تظهر؟" else "HOW SHOULD ALMI SEE YOU?", color = PortalInk, fontSize = 23.sp, fontWeight = FontWeight.Black)
            }
            Surface(
                modifier = Modifier.size(46.dp).clickable(onClick = onBack),
                shape = CircleShape,
                color = Color(0xEFFFFFFF),
                border = BorderStroke(1.dp, PortalBlue.copy(alpha = .25f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(V12GlyphType.BACK, PortalInk, Modifier.size(20.dp))
                }
            }
        }

        LuminousCore(
            modifier = Modifier.align(Alignment.Center).offset(y = (-34).dp).scale(pulse),
            accent = PortalPink,
            secondary = PortalBlue,
            label = "YOU",
        )

        IdentityOrbit(
            modifier = Modifier.align(Alignment.CenterStart).offset(x = 22.dp, y = 184.dp),
            accent = PortalPink,
            glyph = V12GlyphType.AVATAR,
            title = if (language == "ar") "شخصية حيّة" else "LIVE AVATAR",
            subtitle = if (language == "ar") "3D • حركة • قياسات" else "3D • MOTION • BODY",
            onClick = onAvatar,
        )
        IdentityOrbit(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-22).dp, y = 184.dp),
            accent = PortalBlue,
            glyph = V12GlyphType.CAMERA,
            title = if (language == "ar") "صورتي" else "MY PHOTO",
            subtitle = if (language == "ar") "كاميرا • FIT مباشر" else "CAMERA • DIRECT FIT",
            onClick = onPhoto,
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (language == "ar") "لا توجد طريقة صحيحة أو خاطئة" else "NO WRONG PATH",
                color = PortalInk.copy(alpha = .58f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = .8.sp,
            )
            Text(
                if (language == "ar") "يمكنك التبديل لاحقًا دون فقد بياناتك" else "SWITCH LATER WITHOUT LOSING YOUR BODY OR AI DATA",
                modifier = Modifier.padding(top = 4.dp),
                color = PortalInk.copy(alpha = .34f),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .55.sp,
            )
        }
    }
}

@Composable
private fun PortalAtmosphere() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(PortalBlue.copy(alpha = .10f), size.minDimension * .60f, Offset(size.width * .04f, size.height * .16f))
        drawCircle(PortalPink.copy(alpha = .09f), size.minDimension * .55f, Offset(size.width * .96f, size.height * .64f))
        drawCircle(PortalMint.copy(alpha = .07f), size.minDimension * .46f, Offset(size.width * .30f, size.height * .92f))
        drawCircle(PortalViolet.copy(alpha = .09f), size.minDimension * .32f, Offset(size.width * .70f, size.height * .35f), style = Stroke(1.2f))
        drawCircle(PortalBlue.copy(alpha = .10f), size.minDimension * .42f, Offset(size.width * .50f, size.height * .48f), style = Stroke(1.1f))
    }
}

@Composable
private fun LuminousCore(
    modifier: Modifier = Modifier,
    accent: Color,
    secondary: Color,
    label: String,
) {
    Box(modifier = modifier.size(178.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(accent.copy(alpha = .09f), radius = size.minDimension * .50f)
            drawCircle(secondary.copy(alpha = .12f), radius = size.minDimension * .39f, style = Stroke(2f))
            drawCircle(accent.copy(alpha = .16f), radius = size.minDimension * .30f)
            drawCircle(Color.White.copy(alpha = .92f), radius = size.minDimension * .22f)
            drawCircle(accent.copy(alpha = .38f), radius = size.minDimension * .22f, style = Stroke(1.5f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = PortalInk, fontSize = if (label == "YOU") 15.sp else 25.sp, fontWeight = FontWeight.Black, letterSpacing = .5.sp)
            Text("ALMI CORE", color = accent, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        }
    }
}

@Composable
private fun OrbitChoice(
    modifier: Modifier,
    code: String,
    title: String,
    accent: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(136.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .20f) else Color(0xEFFFFFFF),
        border = BorderStroke(1.5.dp, if (active) accent else accent.copy(alpha = .38f)),
        shadowElevation = if (active) 17.dp else 8.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(code, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text(title, modifier = Modifier.padding(top = 5.dp), color = PortalInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(if (active) "ACTIVE" else "ENTER", modifier = Modifier.padding(top = 7.dp), color = PortalInk.copy(alpha = .36f), fontSize = 6.5.sp, fontWeight = FontWeight.Bold, letterSpacing = .9.sp)
        }
    }
}

@Composable
private fun IdentityOrbit(
    modifier: Modifier,
    accent: Color,
    glyph: V12GlyphType,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(154.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xEFFFFFFF),
        border = BorderStroke(1.5.dp, accent.copy(alpha = .50f)),
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = .14f)) {
                V12Glyph(glyph, accent, Modifier.padding(10.dp).size(30.dp))
            }
            Text(title, modifier = Modifier.padding(top = 8.dp), color = PortalInk, fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(subtitle, modifier = Modifier.padding(top = 3.dp), color = PortalInk.copy(alpha = .38f), fontSize = 6.8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = .45.sp)
        }
    }
}

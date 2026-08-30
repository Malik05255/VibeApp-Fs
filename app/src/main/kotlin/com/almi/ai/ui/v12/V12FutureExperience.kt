package com.almi.ai.ui.v12

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.JourneyMode

private val FutureInk = Color(0xFF123657)
private val FutureBlue = Color(0xFF43BAF5)
private val FutureBlueDeep = Color(0xFF3198F2)
private val FutureCyan = Color(0xFF55E4F2)
private val FuturePink = Color(0xFFFF7CA9)
private val FutureMint = Color(0xFF55D9C4)
private val FutureViolet = Color(0xFF9C8EFF)
private val FutureGlass = Color(0xEFFFFFFF)

private enum class FutureAvatarStage { CHOOSE, EDIT }
private enum class FutureOnboardingStep { LANGUAGE, PATH, AVATAR }

@Composable
internal fun V12FutureAvatarScreen(
    language: String,
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    digitalTwinSnapshotUri: String?,
    onPresentation: (AvatarPresentation) -> Unit,
    onHair: (String) -> Unit,
    onHairColor: (String) -> Unit,
    onSkinColor: (String) -> Unit,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    var stageName by rememberSaveable { mutableStateOf(FutureAvatarStage.CHOOSE.name) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val stage = runCatching { FutureAvatarStage.valueOf(stageName) }.getOrDefault(FutureAvatarStage.CHOOSE)
    val selected = selectedName?.let { runCatching { AvatarPresentation.valueOf(it) }.getOrNull() }

    when (stage) {
        FutureAvatarStage.CHOOSE -> FutureIdentityChooser(
            language = language,
            selected = selected,
            onSelect = { selectedName = it.name },
            onBack = onBack,
            onNext = {
                selected?.let(onPresentation)
                stageName = FutureAvatarStage.EDIT.name
            },
        )

        FutureAvatarStage.EDIT -> V12AvatarDigitalLabScreen(
            language = language,
            appearance = appearance.copy(presentation = selected ?: appearance.presentation),
            bodyProfile = bodyProfile,
            digitalTwinSnapshotUri = digitalTwinSnapshotUri,
            onPresentation = onPresentation,
            onHair = onHair,
            onHairColor = onHairColor,
            onSkinColor = onSkinColor,
            onEyes = onEyes,
            onEyebrows = onEyebrows,
            onMouth = onMouth,
            onBack = { stageName = FutureAvatarStage.CHOOSE.name },
            onComplete = onComplete,
        )
    }
}

@Composable
private fun FutureIdentityChooser(
    language: String,
    selected: AvatarPresentation?,
    onSelect: (AvatarPresentation) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "future-identity-pulse")
        .animateFloat(
            initialValue = .94f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
            label = "future-identity-pulse-value",
        )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE9F8FF),
                        Color(0xFFF8FCFF),
                        Color(0xFFFFFBFD),
                        Color(0xFFEEFAFF),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        FutureLivingField()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.clickable(onClick = onBack),
                shape = RoundedCornerShape(24.dp),
                color = FutureGlass,
                border = BorderStroke(1.dp, FutureBlue.copy(alpha = .24f)),
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("‹", color = FutureInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(if (language == "ar") "رجوع" else "BACK", color = FutureInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = .66f),
                border = BorderStroke(1.dp, FutureCyan.copy(alpha = .25f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .graphicsLayer(scaleX = pulse, scaleY = pulse)
                            .background(FutureMint, CircleShape),
                    )
                    Text("IDENTITY LINK", color = FutureInk.copy(alpha = .64f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALMI // HUMAN ID", color = FutureBlueDeep, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                if (language == "ar") "اختر هويتك الرقمية" else "CHOOSE YOUR DIGITAL IDENTITY",
                color = FutureInk,
                fontSize = 29.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                if (language == "ar") "هذه ليست صورة حساب — هذه نسختك داخل ALMI" else "Not a profile picture — your living ALMI twin",
                modifier = Modifier.padding(top = 6.dp),
                color = FutureInk.copy(alpha = .53f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }

        val side = 16.dp
        val gap = 12.dp
        val width = (maxWidth - side * 2 - gap) / 2
        val height = (maxHeight * .62f).coerceAtMost(590.dp)

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 26.dp)
                .padding(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            FutureIdentityCard(
                width = width,
                height = height,
                presentation = AvatarPresentation.MASCULINE,
                title = if (language == "ar") "ذكر" else "MALE",
                accent = FutureBlue,
                selected = selected == AvatarPresentation.MASCULINE,
            ) { onSelect(AvatarPresentation.MASCULINE) }

            FutureIdentityCard(
                width = width,
                height = height,
                presentation = AvatarPresentation.FEMININE,
                title = if (language == "ar") "أنثى" else "FEMALE",
                accent = FuturePink,
                selected = selected == AvatarPresentation.FEMININE,
            ) { onSelect(AvatarPresentation.FEMININE) }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 17.dp)
                .fillMaxWidth()
                .height(66.dp)
                .clickable(enabled = selected != null, onClick = onNext),
            shape = RoundedCornerShape(31.dp),
            color = if (selected == null) Color(0xFFDDE7EF) else FutureBlueDeep,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .88f)),
            shadowElevation = if (selected == null) 6.dp else 18.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (language == "ar") "فعّل نسختي" else "ACTIVATE MY TWIN",
                    color = if (selected == null) FutureInk.copy(alpha = .33f) else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .2.sp,
                )
                Text(
                    "→",
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
                    color = if (selected == null) FutureInk.copy(alpha = .22f) else Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun FutureIdentityCard(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    presentation: AvatarPresentation,
    title: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1.025f else .985f, tween(260), label = "future-card-scale")
    val lift by animateFloatAsState(if (selected) -9f else 0f, tween(260), label = "future-card-lift")

    Surface(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer(scaleX = scale, scaleY = scale, translationY = lift)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = .62f),
        border = BorderStroke(if (selected) 2.2.dp else 1.1.dp, accent.copy(alpha = if (selected) .92f else .30f)),
        shadowElevation = if (selected) 22.dp else 11.dp,
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(
                    if (presentation == AvatarPresentation.FEMININE) {
                        listOf(Color(0xFFFFF5FA), Color(0xFFFFEAF3), Color(0xFFFFF9FC))
                    } else {
                        listOf(Color(0xFFF2FCFF), Color(0xFFE3F7FF), Color(0xFFF8FEFF))
                    },
                ),
            ),
        ) {
            FutureCardField(accent, selected)

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).fillMaxWidth(.76f).height(50.dp),
                shape = RoundedCornerShape(22.dp),
                color = accent,
                shadowElevation = 14.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }

            FutureHumanHero(
                presentation = presentation,
                accent = accent,
                modifier = Modifier.fillMaxSize().padding(top = 70.dp, bottom = 32.dp, start = 5.dp, end = 5.dp),
            )

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = .72f),
                border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(if (selected) FutureMint else accent.copy(alpha = .45f), CircleShape))
                    Text(if (selected) "LINKED" else "AVAILABLE", color = FutureInk.copy(alpha = .58f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
                }
            }
        }
    }
}

@Composable
private fun FutureHumanHero(
    presentation: AvatarPresentation,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w * .5f
        val feminine = presentation == AvatarPresentation.FEMININE
        val skin = if (feminine) Color(0xFFF2C6B1) else Color(0xFFE5B596)
        val skinShade = if (feminine) Color(0xFFDCA38C) else Color(0xFFCA8C70)
        val hair = if (feminine) Color(0xFF47302E) else Color(0xFF26262C)
        val hairHi = if (feminine) Color(0xFF72504A) else Color(0xFF50505A)
        val white = Color(0xFFFCFDFE)
        val clothShade = Color(0xFFE5EBF1)
        val ink = Color(0xFF283443)

        drawCircle(
            brush = Brush.radialGradient(listOf(accent.copy(alpha = .20f), Color.Transparent)),
            radius = w * .58f,
            center = Offset(cx, h * .42f),
        )
        drawCircle(
            color = Color.White.copy(alpha = .72f),
            radius = w * .36f,
            center = Offset(cx, h * .42f),
        )

        // Legs.
        val legTop = h * .60f
        val legBottom = h * .94f
        val legDx = w * if (feminine) .075f else .09f
        val legWidth = w * if (feminine) .088f else .105f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(skin, skinShade)),
            topLeft = Offset(cx - legDx - legWidth / 2, legTop),
            size = Size(legWidth, legBottom - legTop),
            cornerRadius = CornerRadius(legWidth * .45f),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(skin, skinShade)),
            topLeft = Offset(cx + legDx - legWidth / 2, legTop),
            size = Size(legWidth, legBottom - legTop),
            cornerRadius = CornerRadius(legWidth * .45f),
        )
        drawOval(skinShade, Offset(cx - legDx - legWidth * .60f, legBottom - h * .012f), Size(legWidth * 1.25f, h * .025f))
        drawOval(skinShade, Offset(cx + legDx - legWidth * .60f, legBottom - h * .012f), Size(legWidth * 1.25f, h * .025f))

        // Shorts around the knee, matching ALMI base outfit.
        val shortsTop = h * .53f
        val shortsHeight = h * .15f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(white, clothShade)),
            topLeft = Offset(cx - w * .22f, shortsTop),
            size = Size(w * .44f, shortsHeight),
            cornerRadius = CornerRadius(w * .06f),
        )
        drawLine(Color(0xFFD5DDE6), Offset(cx, shortsTop + h * .02f), Offset(cx, shortsTop + shortsHeight), strokeWidth = 1.5f)

        // Torso and fitted sleeveless top.
        val shoulderY = h * .30f
        val waistY = h * .56f
        val shoulderHalf = w * if (feminine) .19f else .225f
        val waistHalf = w * if (feminine) .135f else .16f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(white, clothShade)),
            topLeft = Offset(cx - shoulderHalf, shoulderY),
            size = Size(shoulderHalf * 2f, waistY - shoulderY),
            cornerRadius = CornerRadius(w * .09f),
        )
        drawRoundRect(
            color = skin,
            topLeft = Offset(cx - w * .042f, h * .235f),
            size = Size(w * .084f, h * .105f),
            cornerRadius = CornerRadius(w * .03f),
        )

        // Arms.
        val armWidth = w * if (feminine) .065f else .082f
        val armTop = h * .325f
        val armHeight = h * .30f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(skin, skinShade)),
            topLeft = Offset(cx - shoulderHalf - armWidth * .68f, armTop),
            size = Size(armWidth, armHeight),
            cornerRadius = CornerRadius(armWidth * .48f),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(skin, skinShade)),
            topLeft = Offset(cx + shoulderHalf - armWidth * .32f, armTop),
            size = Size(armWidth, armHeight),
            cornerRadius = CornerRadius(armWidth * .48f),
        )

        // Head.
        val faceW = w * if (feminine) .23f else .25f
        val faceH = h * .135f
        val faceTop = h * .13f
        if (feminine) {
            drawOval(hair, Offset(cx - faceW * .69f, faceTop - h * .018f), Size(faceW * 1.38f, faceH * 1.82f))
        }
        drawOval(
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = .20f), skin, skinShade)),
            topLeft = Offset(cx - faceW / 2, faceTop),
            size = Size(faceW, faceH),
        )

        // Hair cap and volume.
        drawOval(
            brush = Brush.verticalGradient(listOf(hairHi, hair)),
            topLeft = Offset(cx - faceW * .59f, faceTop - faceH * .27f),
            size = Size(faceW * 1.18f, faceH * .70f),
        )
        if (!feminine) {
            repeat(5) { index ->
                val px = cx - faceW * .42f + index * faceW * .20f
                drawCircle(hairHi.copy(alpha = .78f), faceW * .14f, Offset(px, faceTop - faceH * (.07f + (index % 2) * .09f)))
            }
        }

        // Face details.
        val eyeY = faceTop + faceH * .53f
        val eyeDx = faceW * .22f
        listOf(cx - eyeDx, cx + eyeDx).forEach { ex ->
            drawOval(Color.White, Offset(ex - faceW * .075f, eyeY - faceH * .044f), Size(faceW * .15f, faceH * .09f))
            drawCircle(Color(0xFF695148), faceW * .027f, Offset(ex, eyeY))
            drawCircle(ink, faceW * .012f, Offset(ex, eyeY))
            drawCircle(Color.White, faceW * .005f, Offset(ex - faceW * .008f, eyeY - faceH * .010f))
        }
        drawLine(ink, Offset(cx - eyeDx - faceW * .07f, eyeY - faceH * .085f), Offset(cx - eyeDx + faceW * .07f, eyeY - faceH * .095f), 1.7f)
        drawLine(ink, Offset(cx + eyeDx - faceW * .07f, eyeY - faceH * .095f), Offset(cx + eyeDx + faceW * .07f, eyeY - faceH * .085f), 1.7f)
        drawLine(skinShade, Offset(cx, eyeY + faceH * .03f), Offset(cx - faceW * .012f, eyeY + faceH * .16f), 1.4f)
        drawLine(Color(0xFFB56467), Offset(cx - faceW * .055f, faceTop + faceH * .78f), Offset(cx + faceW * .055f, faceTop + faceH * .78f), 1.7f)

        // Futuristic edge glints.
        drawLine(accent.copy(alpha = .35f), Offset(cx - shoulderHalf, shoulderY + h * .02f), Offset(cx - waistHalf, waistY - h * .01f), 2f)
        drawLine(accent.copy(alpha = .35f), Offset(cx + shoulderHalf, shoulderY + h * .02f), Offset(cx + waistHalf, waistY - h * .01f), 2f)
    }
}

@Composable
private fun FutureLivingField() {
    val sweep by rememberInfiniteTransition(label = "future-field-sweep")
        .animateFloat(
            initialValue = -.12f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(3600), RepeatMode.Restart),
            label = "future-field-sweep-value",
        )

    Canvas(Modifier.fillMaxSize()) {
        val grid = Color(0xFF8ACCEB).copy(alpha = .15f)
        val step = 46f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .17f), Offset(x, size.height * .88f), 1f)
            x += step
        }
        var y = size.height * .17f
        while (y <= size.height * .88f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }

        drawCircle(FutureBlue.copy(alpha = .08f), size.minDimension * .56f, Offset(size.width * .12f, size.height * .44f))
        drawCircle(FuturePink.copy(alpha = .07f), size.minDimension * .48f, Offset(size.width * .90f, size.height * .60f))
        drawCircle(FutureViolet.copy(alpha = .045f), size.minDimension * .33f, Offset(size.width * .53f, size.height * .24f))

        val beamY = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, FutureCyan.copy(alpha = .11f), Color.White.copy(alpha = .26f), FutureCyan.copy(alpha = .08f), Color.Transparent),
                startY = beamY - 80f,
                endY = beamY + 80f,
            ),
            topLeft = Offset(0f, beamY - 80f),
            size = Size(size.width, 160f),
        )
        drawLine(FutureCyan.copy(alpha = .38f), Offset(0f, beamY), Offset(size.width, beamY), 1.2f)
    }
}

@Composable
private fun FutureCardField(accent: Color, selected: Boolean) {
    val sweep by rememberInfiniteTransition(label = "future-card-sweep")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (selected) 1700 else 2800), RepeatMode.Restart),
            label = "future-card-sweep-value",
        )

    Canvas(Modifier.fillMaxSize()) {
        val beamY = size.height * sweep
        drawCircle(accent.copy(alpha = if (selected) .13f else .07f), size.width * .58f, Offset(size.width * .48f, size.height * .50f))
        drawLine(accent.copy(alpha = .16f), Offset(size.width * .12f, size.height * .18f), Offset(size.width * .88f, size.height * .18f), 1f)
        drawLine(accent.copy(alpha = .13f), Offset(size.width * .12f, size.height * .84f), Offset(size.width * .88f, size.height * .84f), 1f)
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = .12f), Color.Transparent), beamY - 40f, beamY + 40f),
            topLeft = Offset(0f, beamY - 40f),
            size = Size(size.width, 80f),
        )
        repeat(4) { i ->
            val px = size.width * (.16f + i * .21f)
            val py = size.height * (.24f + (i % 3) * .18f)
            drawCircle(Color.White.copy(alpha = .86f), 3.2f, Offset(px, py))
            drawLine(Color.White.copy(alpha = .50f), Offset(px - 7f, py), Offset(px + 7f, py), 1.2f)
            drawLine(Color.White.copy(alpha = .50f), Offset(px, py - 7f), Offset(px, py + 7f), 1.2f)
        }
    }
}

@Composable
internal fun V12FutureBodyScreen(
    language: String,
    profile: BodyProfile,
    presentation: AvatarPresentation,
    onPresentation: (AvatarPresentation) -> Unit,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        V12ReferenceBodyScreen(
            language = language,
            profile = profile,
            presentation = presentation,
            onPresentation = onPresentation,
            onHeightChanged = onHeightChanged,
            onWeightChanged = onWeightChanged,
            onMeasurementChanged = onMeasurementChanged,
            onMeasurementCleared = onMeasurementCleared,
            onBack = onBack,
        )
        FutureAnatomyScanOverlay(language = language)
    }
}

@Composable
private fun FutureAnatomyScanOverlay(language: String) {
    val sweep by rememberInfiniteTransition(label = "future-anatomy-sweep")
        .animateFloat(
            initialValue = .15f,
            targetValue = .87f,
            animationSpec = infiniteRepeatable(tween(2900), RepeatMode.Reverse),
            label = "future-anatomy-sweep-value",
        )
    val pulse by rememberInfiniteTransition(label = "future-anatomy-pulse")
        .animateFloat(
            initialValue = .55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "future-anatomy-pulse-value",
        )

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val beamY = size.height * sweep
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, FutureCyan.copy(alpha = .05f), FutureCyan.copy(alpha = .14f), Color.White.copy(alpha = .16f), FutureCyan.copy(alpha = .05f), Color.Transparent),
                    beamY - 95f,
                    beamY + 95f,
                ),
                topLeft = Offset(0f, beamY - 95f),
                size = Size(size.width, 190f),
            )
            drawLine(FutureCyan.copy(alpha = .52f), Offset(size.width * .08f, beamY), Offset(size.width * .92f, beamY), 1.3f)

            // Subtle volumetric rings keep the body field feeling spatial without blocking touch.
            repeat(4) { index ->
                val radius = size.minDimension * (.20f + index * .10f)
                drawCircle(
                    color = FutureBlue.copy(alpha = .025f + index * .008f),
                    radius = radius,
                    center = Offset(size.width * .5f, size.height * .49f),
                )
            }

            // Tiny scanner ticks at the edges.
            repeat(7) { index ->
                val y = size.height * (.22f + index * .085f)
                drawLine(FutureBlueDeep.copy(alpha = .22f), Offset(size.width * .035f, y), Offset(size.width * .075f, y), 2f)
                drawLine(FutureBlueDeep.copy(alpha = .22f), Offset(size.width * .925f, y), Offset(size.width * .965f, y), 2f)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 92.dp, start = 15.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = .64f),
            border = BorderStroke(1.dp, FutureCyan.copy(alpha = .24f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .graphicsLayer(alpha = pulse)
                        .background(FutureMint, CircleShape),
                )
                Text(
                    if (language == "ar") "مسح تشريحي حي" else "LIVE ANATOMY SCAN",
                    color = FutureInk.copy(alpha = .62f),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .7.sp,
                )
            }
        }
    }
}

@Composable
internal fun V12FutureOnboardingScreen(
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
    var stepName by rememberSaveable { mutableStateOf(FutureOnboardingStep.LANGUAGE.name) }
    val step = runCatching { FutureOnboardingStep.valueOf(stepName) }.getOrDefault(FutureOnboardingStep.LANGUAGE)

    when (step) {
        FutureOnboardingStep.LANGUAGE -> FutureLanguagePortal(
            language = language,
            onPick = {
                onLanguageChange(it)
                stepName = FutureOnboardingStep.PATH.name
            },
        )

        FutureOnboardingStep.PATH -> FuturePathPortal(
            language = language,
            onBack = { stepName = FutureOnboardingStep.LANGUAGE.name },
            onAvatar = {
                onJourneyMode(JourneyMode.AVATAR)
                stepName = FutureOnboardingStep.AVATAR.name
            },
            onPhoto = {
                onJourneyMode(JourneyMode.PHOTO)
                onComplete()
            },
        )

        FutureOnboardingStep.AVATAR -> V12FutureAvatarScreen(
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
            onBack = { stepName = FutureOnboardingStep.PATH.name },
            onComplete = onComplete,
        )
    }
}

@Composable
private fun FutureLanguagePortal(language: String, onPick: (String) -> Unit) {
    val pulse by rememberInfiniteTransition(label = "future-language-pulse")
        .animateFloat(
            initialValue = .92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "future-language-pulse-value",
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE8F8FF), Color(0xFFFBFDFF), Color(0xFFF5FAFF))))
            .statusBarsPadding(),
    ) {
        FutureLivingField()

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier
                    .size(92.dp)
                    .graphicsLayer(scaleX = pulse, scaleY = pulse),
                shape = CircleShape,
                color = Color.White.copy(alpha = .80f),
                border = BorderStroke(1.5.dp, FutureCyan.copy(alpha = .38f)),
                shadowElevation = 18.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("A", color = FutureBlueDeep, fontSize = 44.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("ALMI // 12", color = FutureBlueDeep, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
            Text(
                if (language == "ar") "اختر لغة عالمك" else "CHOOSE YOUR WORLD LANGUAGE",
                modifier = Modifier.padding(top = 5.dp),
                color = FutureInk,
                fontSize = 29.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp).offset(y = 75.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            FuturePortalChoice("العربية", "AR", FutureBlue, language == "ar") { onPick("ar") }
            FuturePortalChoice("English", "EN", FuturePink, language != "ar") { onPick("en") }
        }

        Text(
            "HUMAN INTERFACE / ZERO DARK MODE",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            color = FutureInk.copy(alpha = .32f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
        )
    }
}

@Composable
private fun FuturePortalChoice(title: String, code: String, accent: Color, active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(82.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .78f),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = if (active) .72f else .25f)),
        shadowElevation = if (active) 16.dp else 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, color = FutureInk, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(code, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            }
            Surface(Modifier.size(44.dp), CircleShape, color = accent.copy(alpha = .14f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (active) "✓" else "→", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun FuturePathPortal(
    language: String,
    onBack: () -> Unit,
    onAvatar: () -> Unit,
    onPhoto: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFEAF8FF), Color(0xFFFCFDFF), Color(0xFFFFF8FC))))
            .statusBarsPadding(),
    ) {
        FutureLivingField()

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clickable(onClick = onBack),
            shape = RoundedCornerShape(23.dp),
            color = FutureGlass,
            border = BorderStroke(1.dp, FutureBlue.copy(alpha = .25f)),
        ) {
            Text("‹", modifier = Modifier.padding(horizontal = 17.dp, vertical = 8.dp), color = FutureInk, fontSize = 27.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALMI // ENTRY VECTOR", color = FutureBlueDeep, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
            Text(
                if (language == "ar") "كيف تريد الدخول؟" else "HOW DO YOU WANT TO ENTER?",
                modifier = Modifier.padding(top = 5.dp),
                color = FutureInk,
                fontSize = 29.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp).offset(y = 46.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FutureEntryCard(
                title = if (language == "ar") "أنشئ نسختك الرقمية" else "BUILD YOUR DIGITAL TWIN",
                subtitle = if (language == "ar") "هوية، جسم، قياسات وتخصيص ثلاثي الأبعاد" else "Identity, body, measurements and 3D customization",
                code = "TWIN",
                accent = FutureBlue,
                onClick = onAvatar,
            )
            FutureEntryCard(
                title = if (language == "ar") "ابدأ من صورتك" else "START FROM YOUR PHOTO",
                subtitle = if (language == "ar") "ادخل مباشرة إلى تجربة الملابس بالذكاء الاصطناعي" else "Jump directly into AI try-on",
                code = "PHOTO",
                accent = FuturePink,
                onClick = onPhoto,
            )
        }
    }
}

@Composable
private fun FutureEntryCard(
    title: String,
    subtitle: String,
    code: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(132.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(31.dp),
        color = Color.White.copy(alpha = .80f),
        border = BorderStroke(1.4.dp, accent.copy(alpha = .32f)),
        shadowElevation = 13.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Surface(
                modifier = Modifier.size(69.dp),
                shape = RoundedCornerShape(24.dp),
                color = accent.copy(alpha = .14f),
                border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(code.take(1), color = accent, fontSize = 29.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = FutureInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = FutureInk.copy(alpha = .56f), fontSize = 10.5.sp, lineHeight = 15.sp)
            }
            Text("→", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

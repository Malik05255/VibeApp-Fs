package com.almi.ai.ui.v12

import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile

private enum class RefAvatarStage { CHOOSE, EDIT }
private enum class RefAvatarTab { SKIN, HAIR, COLOR, FACE }
private enum class RefAvatarLoad { LOADING, READY, ERROR }

private val RefInk = Color(0xFF173657)
private val RefBlue = Color(0xFF48B8F2)
private val RefBlueStrong = Color(0xFF3E9FF3)
private val RefPink = Color(0xFFFF789D)
private val RefMint = Color(0xFF55D5C4)
private val RefViolet = Color(0xFF9C8DF4)
private val RefIce = Color(0xFFF2FAFF)

@Composable
internal fun V12ReferenceAvatarScreen(
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
    var stageName by rememberSaveable { mutableStateOf(RefAvatarStage.CHOOSE.name) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val stage = runCatching { RefAvatarStage.valueOf(stageName) }.getOrDefault(RefAvatarStage.CHOOSE)
    val selected = selectedName?.let { runCatching { AvatarPresentation.valueOf(it) }.getOrNull() }

    when (stage) {
        RefAvatarStage.CHOOSE -> ReferenceGenderChooser(
            language = language,
            selected = selected,
            onSelect = { selectedName = it.name },
            onBack = onBack,
            onNext = {
                selected?.let(onPresentation)
                stageName = RefAvatarStage.EDIT.name
            },
        )

        RefAvatarStage.EDIT -> ReferenceDigitalEditor(
            language = language,
            presentation = selected ?: appearance.presentation,
            appearance = appearance,
            bodyProfile = bodyProfile,
            digitalTwinSnapshotUri = digitalTwinSnapshotUri,
            onHair = onHair,
            onHairColor = onHairColor,
            onSkinColor = onSkinColor,
            onEyes = onEyes,
            onEyebrows = onEyebrows,
            onMouth = onMouth,
            onBack = { stageName = RefAvatarStage.CHOOSE.name },
            onComplete = onComplete,
        )
    }
}

@Composable
private fun ReferenceGenderChooser(
    language: String,
    selected: AvatarPresentation?,
    onSelect: (AvatarPresentation) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF7FF), Color(0xFFF8FCFF), Color(0xFFF1FAFF)),
                ),
            )
            .statusBarsPadding(),
    ) {
        ReferenceGridBackdrop()

        ReferenceHeader(
            language = language,
            title = if (language == "ar") "قياسات جسمك" else "YOUR BODY",
            onBack = onBack,
        )

        val horizontalMargin = 18.dp
        val gap = 12.dp
        val cardWidth = (maxWidth - horizontalMargin * 2 - gap) / 2
        val cardHeight = (maxHeight * .66f).coerceAtMost(610.dp)

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 18.dp)
                .padding(horizontal = horizontalMargin),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            ReferenceGenderCard(
                width = cardWidth,
                height = cardHeight,
                presentation = AvatarPresentation.MASCULINE,
                title = if (language == "ar") "ذكر" else "MALE",
                accent = RefBlue,
                selected = selected == AvatarPresentation.MASCULINE,
                onClick = { onSelect(AvatarPresentation.MASCULINE) },
            )
            ReferenceGenderCard(
                width = cardWidth,
                height = cardHeight,
                presentation = AvatarPresentation.FEMININE,
                title = if (language == "ar") "أنثى" else "FEMALE",
                accent = RefPink,
                selected = selected == AvatarPresentation.FEMININE,
                onClick = { onSelect(AvatarPresentation.FEMININE) },
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 18.dp)
                .fillMaxWidth()
                .height(66.dp)
                .clickable(enabled = selected != null, onClick = onNext),
            shape = RoundedCornerShape(30.dp),
            color = if (selected == null) Color(0xFFDDE6EF) else RefBlueStrong,
            border = BorderStroke(1.dp, Color.White.copy(alpha = .86f)),
            shadowElevation = if (selected == null) 5.dp else 15.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (language == "ar") "التالي" else "Next",
                    color = if (selected == null) RefInk.copy(alpha = .34f) else Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "›",
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
                    color = if (selected == null) RefInk.copy(alpha = .24f) else Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
    }
}

@Composable
private fun ReferenceHeader(language: String, title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.TopStart).clickable(onClick = onBack),
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = .78f),
            border = BorderStroke(1.dp, RefBlue.copy(alpha = .26f)),
            shadowElevation = 9.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (language == "ar") "تم" else "Done", color = RefInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("✓", color = RefInk, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALMI / FILAMENT", color = Color(0xFF7CB5DF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .9.sp)
            Text(title, color = RefInk, fontSize = 31.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Surface(Modifier.width(48.dp).height(5.dp), RoundedCornerShape(99.dp), color = RefBlueStrong) {}
                Surface(Modifier.width(62.dp).height(5.dp), RoundedCornerShape(99.dp), color = Color(0xFFDDEFFC)) {}
                Surface(Modifier.width(42.dp).height(5.dp), RoundedCornerShape(99.dp), color = Color(0xFFE8F3FA)) {}
            }
        }
    }
}

@Composable
private fun ReferenceGenderCard(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    presentation: AvatarPresentation,
    title: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(width).height(height).clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .58f),
        border = BorderStroke(if (selected) 2.5.dp else 1.2.dp, accent.copy(alpha = if (selected) .90f else .34f)),
        shadowElevation = if (selected) 18.dp else 10.dp,
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(
                    if (presentation == AvatarPresentation.FEMININE) {
                        listOf(Color(0xFFFFF6FA), Color(0xFFFFEEF4), Color(0xFFFFF9FB))
                    } else {
                        listOf(Color(0xFFF2FCFF), Color(0xFFE7F8FF), Color(0xFFF6FDFF))
                    },
                ),
            ),
        ) {
            ReferenceCardSparkles(accent)
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp).fillMaxWidth(.72f).height(54.dp),
                shape = RoundedCornerShape(24.dp),
                color = accent,
                shadowElevation = 13.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
            ReferenceCharacterArt(
                presentation = presentation,
                modifier = Modifier.fillMaxSize().padding(top = 80.dp, bottom = 18.dp, start = 5.dp, end = 5.dp),
            )
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(39.dp),
                shape = CircleShape,
                color = accent.copy(alpha = .13f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (presentation == AvatarPresentation.FEMININE) "♀" else "♂",
                        color = accent,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceCharacterArt(presentation: AvatarPresentation, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w * .5f
        val skin = if (presentation == AvatarPresentation.FEMININE) Color(0xFFF2C3AD) else Color(0xFFE6B394)
        val skinShade = if (presentation == AvatarPresentation.FEMININE) Color(0xFFDFA48E) else Color(0xFFCE8C70)
        val hair = if (presentation == AvatarPresentation.FEMININE) Color(0xFF4C2C29) else Color(0xFF27242A)
        val hairHi = if (presentation == AvatarPresentation.FEMININE) Color(0xFF744740) else Color(0xFF4A4650)
        val ink = Color(0xFF2B3340)
        val white = Color(0xFFFCFCFD)
        val clothShade = Color(0xFFE8ECF2)

        drawOval(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = .95f), Color.Transparent)),
            topLeft = Offset(w * .11f, h * .05f),
            size = Size(w * .78f, h * .87f),
        )

        if (presentation == AvatarPresentation.FEMININE) {
            drawOval(color = hair, topLeft = Offset(cx + w * .03f, h * .08f), size = Size(w * .30f, h * .30f))
            drawCircle(hairHi, radius = w * .09f, center = Offset(cx + w * .10f, h * .11f))
        }

        val faceW = if (presentation == AvatarPresentation.FEMININE) w * .26f else w * .28f
        val faceH = h * .15f
        val faceTop = h * .13f
        drawOval(
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f), skin, skinShade)),
            topLeft = Offset(cx - faceW / 2, faceTop),
            size = Size(faceW, faceH),
        )

        val hairFront = Path().apply {
            moveTo(cx - faceW * .55f, faceTop + faceH * .28f)
            cubicTo(cx - faceW * .48f, faceTop - faceH * .27f, cx + faceW * .35f, faceTop - faceH * .28f, cx + faceW * .55f, faceTop + faceH * .25f)
            cubicTo(cx + faceW * .20f, faceTop + faceH * .08f, cx + faceW * .04f, faceTop + faceH * .20f, cx - faceW * .06f, faceTop + faceH * .28f)
            cubicTo(cx - faceW * .20f, faceTop + faceH * .13f, cx - faceW * .35f, faceTop + faceH * .10f, cx - faceW * .55f, faceTop + faceH * .28f)
            close()
        }
        drawPath(hairFront, brush = Brush.verticalGradient(listOf(hairHi, hair)))

        val eyeY = faceTop + faceH * .52f
        val eyeDx = faceW * .22f
        listOf(cx - eyeDx, cx + eyeDx).forEach { ex ->
            drawOval(Color.White, Offset(ex - faceW * .09f, eyeY - faceH * .055f), Size(faceW * .18f, faceH * .10f))
            drawCircle(Color(0xFF5C433B), radius = faceW * .035f, center = Offset(ex, eyeY))
            drawCircle(ink, radius = faceW * .016f, center = Offset(ex, eyeY))
        }
        drawLine(ink, Offset(cx - eyeDx - faceW * .08f, eyeY - faceH * .10f), Offset(cx - eyeDx + faceW * .08f, eyeY - faceH * .11f), 2.2f)
        drawLine(ink, Offset(cx + eyeDx - faceW * .08f, eyeY - faceH * .11f), Offset(cx + eyeDx + faceW * .08f, eyeY - faceH * .10f), 2.2f)
        drawLine(skinShade, Offset(cx, eyeY + faceH * .04f), Offset(cx - faceW * .015f, eyeY + faceH * .20f), 2f)
        drawLine(Color(0xFF9D5D60), Offset(cx - faceW * .07f, faceTop + faceH * .79f), Offset(cx + faceW * .07f, faceTop + faceH * .79f), 2f)

        drawRoundRect(skin, Offset(cx - w * .035f, faceTop + faceH * .88f), Size(w * .07f, h * .06f), CornerRadius(w * .02f))

        if (presentation == AvatarPresentation.MASCULINE) {
            val shoulderY = h * .30f
            val torso = Path().apply {
                moveTo(cx - w * .24f, shoulderY)
                cubicTo(cx - w * .17f, shoulderY - h * .025f, cx - w * .09f, shoulderY - h * .03f, cx - w * .055f, shoulderY - h * .018f)
                lineTo(cx + w * .055f, shoulderY - h * .018f)
                cubicTo(cx + w * .09f, shoulderY - h * .03f, cx + w * .17f, shoulderY - h * .025f, cx + w * .24f, shoulderY)
                lineTo(cx + w * .18f, h * .58f)
                lineTo(cx - w * .18f, h * .58f)
                close()
            }
            drawPath(torso, brush = Brush.verticalGradient(listOf(white, clothShade)))
            drawPath(torso, color = Color(0xFFBCC7D4), style = Stroke(1.7f))

            drawRoundRect(skin, Offset(cx - w * .29f, shoulderY + h * .04f), Size(w * .085f, h * .28f), CornerRadius(w * .04f))
            drawRoundRect(skin, Offset(cx + w * .205f, shoulderY + h * .04f), Size(w * .085f, h * .28f), CornerRadius(w * .04f))

            drawRoundRect(brush = Brush.verticalGradient(listOf(white, clothShade)), topLeft = Offset(cx - w * .18f, h * .57f), size = Size(w * .17f, h * .20f), cornerRadius = CornerRadius(w * .025f))
            drawRoundRect(brush = Brush.verticalGradient(listOf(white, clothShade)), topLeft = Offset(cx + w * .01f, h * .57f), size = Size(w * .17f, h * .20f), cornerRadius = CornerRadius(w * .025f))

            drawRoundRect(skin, Offset(cx - w * .145f, h * .75f), Size(w * .085f, h * .20f), CornerRadius(w * .04f))
            drawRoundRect(skin, Offset(cx + w * .06f, h * .75f), Size(w * .085f, h * .20f), CornerRadius(w * .04f))
            drawOval(skinShade, Offset(cx - w * .16f, h * .925f), Size(w * .13f, h * .035f))
            drawOval(skinShade, Offset(cx + w * .03f, h * .925f), Size(w * .13f, h * .035f))
        } else {
            val dressTop = h * .30f
            val dress = Path().apply {
                moveTo(cx - w * .20f, dressTop)
                cubicTo(cx - w * .15f, dressTop - h * .02f, cx - w * .07f, dressTop - h * .03f, cx, dressTop - h * .02f)
                cubicTo(cx + w * .07f, dressTop - h * .03f, cx + w * .15f, dressTop - h * .02f, cx + w * .20f, dressTop)
                lineTo(cx + w * .24f, h * .77f)
                cubicTo(cx + w * .12f, h * .80f, cx - w * .12f, h * .80f, cx - w * .24f, h * .77f)
                close()
            }
            drawPath(dress, brush = Brush.verticalGradient(listOf(white, Color(0xFFF5F6F9), clothShade)))
            drawPath(dress, color = Color(0xFFBCC7D4), style = Stroke(1.5f))
            drawRoundRect(brush = Brush.verticalGradient(listOf(white, clothShade)), topLeft = Offset(cx - w * .30f, dressTop + h * .02f), size = Size(w * .10f, h * .30f), cornerRadius = CornerRadius(w * .04f))
            drawRoundRect(brush = Brush.verticalGradient(listOf(white, clothShade)), topLeft = Offset(cx + w * .20f, dressTop + h * .02f), size = Size(w * .10f, h * .30f), cornerRadius = CornerRadius(w * .04f))
            drawRoundRect(skin, Offset(cx - w * .27f, dressTop + h * .28f), Size(w * .065f, h * .10f), CornerRadius(w * .03f))
            drawRoundRect(skin, Offset(cx + w * .205f, dressTop + h * .28f), Size(w * .065f, h * .10f), CornerRadius(w * .03f))
            drawRoundRect(skin, Offset(cx - w * .12f, h * .77f), Size(w * .075f, h * .18f), CornerRadius(w * .04f))
            drawRoundRect(skin, Offset(cx + w * .045f, h * .77f), Size(w * .075f, h * .18f), CornerRadius(w * .04f))
            drawOval(skinShade, Offset(cx - w * .14f, h * .925f), Size(w * .12f, h * .032f))
            drawOval(skinShade, Offset(cx + w * .02f, h * .925f), Size(w * .12f, h * .032f))
        }
    }
}

@Composable
private fun ReferenceDigitalEditor(
    language: String,
    presentation: AvatarPresentation,
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    digitalTwinSnapshotUri: String?,
    onHair: (String) -> Unit,
    onHairColor: (String) -> Unit,
    onSkinColor: (String) -> Unit,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    var tabName by rememberSaveable { mutableStateOf(RefAvatarTab.SKIN.name) }
    var load by remember { mutableStateOf(RefAvatarLoad.LOADING) }
    var runtime by remember { mutableStateOf<V12DigitalHumanRuntime?>(null) }
    val tab = runCatching { RefAvatarTab.valueOf(tabName) }.getOrDefault(RefAvatarTab.SKIN)
    val live = appearance.copy(presentation = presentation)
    val accent = if (presentation == AvatarPresentation.FEMININE) RefPink else RefBlue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFEAF8FF), Color(0xFFFAFDFF), Color(0xFFFFF7FB))))
            .statusBarsPadding(),
    ) {
        ReferenceGridBackdrop()
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).also { surface ->
                    V12DigitalHumanRuntime(
                        context = context,
                        surfaceView = surface,
                        initialPresentation = presentation,
                        initialAppearance = live,
                        onReady = { surface.post { load = RefAvatarLoad.READY } },
                        onFailure = { surface.post { load = RefAvatarLoad.ERROR } },
                    ).also {
                        runtime = it
                        it.initialize()
                        it.start()
                    }
                }
            },
            update = { runtime?.update(presentation, live) },
        )
        DisposableEffect(Unit) {
            onDispose {
                runtime?.destroy()
                runtime = null
            }
        }

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = .16f), Color.Transparent, Color.Transparent, Color(0xFFF1F9FF).copy(alpha = .40f))),
            ),
        )

        ReferenceHeader(
            language = language,
            title = if (language == "ar") "شخصيتك" else "YOUR AVATAR",
            onBack = onBack,
        )

        if (digitalTwinSnapshotUri != null || bodyProfile.hasExplicitHeight) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 98.dp, end = 16.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White.copy(alpha = .82f),
                border = BorderStroke(1.dp, RefMint.copy(alpha = .38f)),
            ) {
                Text("BODY SYNC", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = RefMint, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
        }

        if (load == RefAvatarLoad.LOADING) {
            Surface(
                modifier = Modifier.align(Alignment.Center).size(76.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = .82f),
                border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 18.dp).fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = Color.White.copy(alpha = .91f),
            border = BorderStroke(1.dp, accent.copy(alpha = .26f)),
            shadowElevation = 14.dp,
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    RefTabButton(if (language == "ar") "البشرة" else "SKIN", tab == RefAvatarTab.SKIN, RefMint) { tabName = RefAvatarTab.SKIN.name }
                    RefTabButton(if (language == "ar") "الشعر" else "HAIR", tab == RefAvatarTab.HAIR, accent) { tabName = RefAvatarTab.HAIR.name }
                    RefTabButton(if (language == "ar") "اللون" else "COLOR", tab == RefAvatarTab.COLOR, RefViolet) { tabName = RefAvatarTab.COLOR.name }
                    RefTabButton(if (language == "ar") "الوجه" else "FACE", tab == RefAvatarTab.FACE, RefPink) { tabName = RefAvatarTab.FACE.name }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    when (tab) {
                        RefAvatarTab.SKIN -> listOf("F3D0BA", "E4B58F", "CF936B", "B97752", "8E583D", "603A2D").forEach { value ->
                            RefColorOption(value, live.skinColor.equals(value, true), RefMint) { onSkinColor(value) }
                        }
                        RefAvatarTab.HAIR -> listOf(
                            "bald" to (if (language == "ar") "بدون" else "BALD"),
                            "shortFlat" to (if (language == "ar") "قصير" else "SHORT"),
                            "shortCurly" to (if (language == "ar") "كيرلي" else "CURL"),
                            "bob" to "BOB",
                            "longButNotTooLong" to (if (language == "ar") "طويل" else "LONG"),
                        ).forEach { (value, label) -> RefTextOption(label, live.hairVariant == value, accent) { onHair(value) } }
                        RefAvatarTab.COLOR -> listOf("151210", "281916", "4D3025", "774227", "A46C3E", "D0B184").forEach { value ->
                            RefColorOption(value, live.hairColor.equals(value, true), RefViolet) { onHairColor(value) }
                        }
                        RefAvatarTab.FACE -> {
                            RefTextOption("NATURAL", live.eyesVariant == "default", RefBlue) { onEyes("default") }
                            RefTextOption("WIDE", live.eyesVariant == "wide", RefMint) { onEyes("wide") }
                            RefTextOption("SHARP", live.eyesVariant == "sharp", RefViolet) { onEyes("sharp") }
                            RefTextOption("BROW", live.eyebrowsVariant == "defined", accent) { onEyebrows("defined") }
                            RefTextOption("SMILE", live.mouthVariant == "smile", RefPink) { onMouth("smile") }
                            RefTextOption("LIPS", live.mouthVariant == "full", RefPink) { onMouth("full") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(54.dp).clickable(enabled = load == RefAvatarLoad.READY, onClick = onComplete),
                    shape = RoundedCornerShape(24.dp),
                    color = if (load == RefAvatarLoad.READY) RefBlueStrong else Color(0xFFDDE6EF),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (language == "ar") "اعتماد الشخصية" else "SAVE AVATAR", color = if (load == RefAvatarLoad.READY) Color.White else RefInk.copy(alpha = .35f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RefTabButton(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (active) accent else Color(0xFFF5F9FC),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = if (active) Color.White else RefInk, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RefColorOption(hex: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val fill = runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color.Gray)
    Surface(
        modifier = Modifier.size(if (active) 48.dp else 42.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = fill,
        border = BorderStroke(if (active) 4.dp else 2.dp, if (active) accent else Color.White),
        shadowElevation = if (active) 8.dp else 2.dp,
    ) {}
}

@Composable
private fun RefTextOption(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (active) accent else Color(0xFFF5F9FC),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp), color = if (active) Color.White else RefInk, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReferenceGridBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val grid = Color(0xFFB7D9EF).copy(alpha = .24f)
        val step = 42f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .17f), Offset(x, size.height * .82f), 1f)
            x += step
        }
        var y = size.height * .17f
        while (y <= size.height * .82f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(RefBlue.copy(alpha = .08f), radius = size.minDimension * .55f, center = Offset(size.width * .18f, size.height * .40f))
        drawCircle(RefPink.copy(alpha = .07f), radius = size.minDimension * .48f, center = Offset(size.width * .90f, size.height * .55f))
    }
}

@Composable
private fun ReferenceCardSparkles(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        listOf(.18f to .20f, .83f to .16f, .15f to .56f, .87f to .73f).forEach { (x, y) ->
            drawCircle(accent.copy(alpha = .22f), radius = 5f, center = Offset(size.width * x, size.height * y))
            drawLine(Color.White, Offset(size.width * x - 10f, size.height * y), Offset(size.width * x + 10f, size.height * y), 2f)
            drawLine(Color.White, Offset(size.width * x, size.height * y - 10f), Offset(size.width * x, size.height * y + 10f), 2f)
        }
    }
}

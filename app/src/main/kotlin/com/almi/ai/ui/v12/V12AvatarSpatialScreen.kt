package com.almi.ai.ui.v12

import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private enum class SpatialAvatarMode { CHOOSE, EDIT }
private enum class SpatialAvatarLens { SKIN, HAIR, COLOR, FACE }

private val AvatarInk = Color(0xFF173A60)
private val AvatarBlue = Color(0xFF52BCFA)
private val AvatarPink = Color(0xFFFF88B0)
private val AvatarMint = Color(0xFF59D7C3)
private val AvatarViolet = Color(0xFFA78BFA)

/**
 * Spatial v12 identity experience.
 *
 * The choice stage is one real Filament world with both PBR humans. There are no gender cards.
 * The editor keeps all existing appearance persistence functional while replacing side rails and
 * option sheets with orbit controls over the live character stage.
 */
@Composable
internal fun V12AvatarSpatialScreen(
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
    var modeName by rememberSaveable { mutableStateOf(SpatialAvatarMode.CHOOSE.name) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var lensName by rememberSaveable { mutableStateOf(SpatialAvatarLens.SKIN.name) }
    var duoRuntime by remember { mutableStateOf<V12AvatarDuoRuntime?>(null) }
    var editorRuntime by remember { mutableStateOf<V12AvatarRuntime?>(null) }
    val mode = runCatching { SpatialAvatarMode.valueOf(modeName) }.getOrDefault(SpatialAvatarMode.CHOOSE)
    val lens = runCatching { SpatialAvatarLens.valueOf(lensName) }.getOrDefault(SpatialAvatarLens.SKIN)
    val selected = selectedName?.let { runCatching { AvatarPresentation.valueOf(it) }.getOrNull() }

    LaunchedEffect(selected, duoRuntime, mode) {
        if (mode == SpatialAvatarMode.CHOOSE) duoRuntime?.select(selected)
    }
    LaunchedEffect(mode, selected, editorRuntime) {
        if (mode == SpatialAvatarMode.EDIT && selected != null) {
            onPresentation(selected)
            editorRuntime?.start()
            editorRuntime?.faceFront()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF8FF), Color(0xFFF8F4FF), Color(0xFFFFF6FA)),
                ),
            )
            .statusBarsPadding(),
    ) {
        AvatarAtmosphere()

        when (mode) {
            SpatialAvatarMode.CHOOSE -> {
                DuoViewport(
                    modifier = Modifier.fillMaxSize(),
                    onRuntime = { duoRuntime = it },
                )

                AvatarHeader(
                    language = language,
                    subtitle = "ALMI / IDENTITY FIELD",
                    title = if (language == "ar") "اختر من سيبقى" else "CHOOSE WHO STAYS",
                    onBack = onBack,
                )

                // Transparent interaction fields: user taps the actual character rather than a card.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight(.72f)
                        .fillMaxWidth(.50f)
                        .clickable {
                            selectedName = AvatarPresentation.MASCULINE.name
                            duoRuntime?.select(AvatarPresentation.MASCULINE)
                        },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(.72f)
                        .fillMaxWidth(.50f)
                        .clickable {
                            selectedName = AvatarPresentation.FEMININE.name
                            duoRuntime?.select(AvatarPresentation.FEMININE)
                        },
                )

                FloatingIdentityTag(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 116.dp),
                    title = if (language == "ar") "ذكر" else "MALE",
                    accent = AvatarBlue,
                    selected = selected == AvatarPresentation.MASCULINE,
                ) {
                    selectedName = AvatarPresentation.MASCULINE.name
                    duoRuntime?.select(AvatarPresentation.MASCULINE)
                }
                FloatingIdentityTag(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 116.dp),
                    title = if (language == "ar") "أنثى" else "FEMALE",
                    accent = AvatarPink,
                    selected = selected == AvatarPresentation.FEMININE,
                ) {
                    selectedName = AvatarPresentation.FEMININE.name
                    duoRuntime?.select(AvatarPresentation.FEMININE)
                }

                AnimatedVisibility(
                    visible = selected != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn() + scaleIn(initialScale = .84f),
                    exit = fadeOut() + scaleOut(targetScale = .84f),
                ) {
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 22.dp)
                            .size(92.dp)
                            .clickable {
                                selected?.let(onPresentation)
                                modeName = SpatialAvatarMode.EDIT.name
                            },
                        shape = CircleShape,
                        color = (if (selected == AvatarPresentation.FEMININE) AvatarPink else AvatarBlue).copy(alpha = .94f),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = .86f)),
                        shadowElevation = 18.dp,
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("↗", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Light)
                            Text(if (language == "ar") "ادخل" else "ENTER", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                        }
                    }
                }
            }

            SpatialAvatarMode.EDIT -> {
                val presentation = selected ?: appearance.presentation
                val accent = if (presentation == AvatarPresentation.FEMININE) AvatarPink else AvatarBlue

                EditorViewport(
                    presentation = presentation,
                    appearance = appearance.copy(presentation = presentation),
                    modifier = Modifier.fillMaxSize(),
                    onRuntime = { editorRuntime = it },
                )
                AvatarEditorWash(accent)

                AvatarHeader(
                    language = language,
                    subtitle = "ALMI / AVATAR LAB",
                    title = if (language == "ar") "هويتك تتحرك" else "YOUR LIVING IDENTITY",
                    onBack = {
                        editorRuntime?.stop()
                        editorRuntime = null
                        modeName = SpatialAvatarMode.CHOOSE.name
                        duoRuntime?.resetSelection()
                    },
                )

                if (digitalTwinSnapshotUri != null || bodyProfile.hasExplicitHeight) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 74.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = .84f),
                        border = BorderStroke(1.dp, AvatarMint.copy(alpha = .38f)),
                    ) {
                        Text(
                            "BODY SYNC • LIVE",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            color = AvatarMint,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = .9.sp,
                        )
                    }
                }

                AvatarLensOrb(
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = 12.dp, y = (-112).dp),
                    label = if (language == "ar") "البشرة" else "SKIN",
                    glyph = V12GlyphType.AVATAR,
                    accent = AvatarMint,
                    active = lens == SpatialAvatarLens.SKIN,
                ) { lensName = SpatialAvatarLens.SKIN.name }
                AvatarLensOrb(
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-12).dp, y = (-78).dp),
                    label = if (language == "ar") "الشعر" else "HAIR",
                    glyph = V12GlyphType.FIT,
                    accent = accent,
                    active = lens == SpatialAvatarLens.HAIR,
                ) { lensName = SpatialAvatarLens.HAIR.name }
                AvatarLensOrb(
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = 18.dp, y = 96.dp),
                    label = if (language == "ar") "اللون" else "COLOR",
                    glyph = V12GlyphType.THEME,
                    accent = AvatarViolet,
                    active = lens == SpatialAvatarLens.COLOR,
                ) { lensName = SpatialAvatarLens.COLOR.name }
                AvatarLensOrb(
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-18).dp, y = 118.dp),
                    label = if (language == "ar") "الوجه" else "FACE",
                    glyph = V12GlyphType.DETAIL,
                    accent = AvatarPink,
                    active = lens == SpatialAvatarLens.FACE,
                ) { lensName = SpatialAvatarLens.FACE.name }

                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 78.dp, end = 14.dp).size(54.dp).clickable {
                        editorRuntime?.playTurntable()
                    },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = .88f),
                    border = BorderStroke(1.dp, accent.copy(alpha = .35f)),
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        V12Glyph(V12GlyphType.TURN, accent, Modifier.size(23.dp))
                    }
                }

                AvatarOptionOrbit(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp),
                    lens = lens,
                    language = language,
                    appearance = appearance,
                    accent = accent,
                    onSkinColor = onSkinColor,
                    onHair = onHair,
                    onHairColor = onHairColor,
                    onEyes = onEyes,
                    onEyebrows = onEyebrows,
                    onMouth = onMouth,
                )

                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).size(82.dp).clickable(onClick = onComplete),
                    shape = CircleShape,
                    color = accent.copy(alpha = .94f),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = .88f)),
                    shadowElevation = 18.dp,
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("✓", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                        Text(if (language == "ar") "اعتماد" else "LIVE", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DuoViewport(modifier: Modifier, onRuntime: (V12AvatarDuoRuntime) -> Unit) {
    var runtime by remember { mutableStateOf<V12AvatarDuoRuntime?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runtime?.destroy()
            runtime = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).also { surface ->
                V12AvatarDuoRuntime(context, surface).also {
                    runtime = it
                    onRuntime(it)
                    it.initialize()
                    it.start()
                }
            }
        },
    )
}

@Composable
private fun EditorViewport(
    presentation: AvatarPresentation,
    appearance: AvatarAppearance,
    modifier: Modifier,
    onRuntime: (V12AvatarRuntime) -> Unit,
) {
    var runtime by remember(presentation) { mutableStateOf<V12AvatarRuntime?>(null) }
    DisposableEffect(presentation) {
        onDispose {
            runtime?.stop()
            runtime = null
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).also { surface ->
                V12AvatarRuntime(context, surface, presentation, appearance).also {
                    runtime = it
                    onRuntime(it)
                    it.initialize()
                    it.start()
                }
            }
        },
        update = { runtime?.update(presentation, appearance) },
    )
}

@Composable
private fun AvatarAtmosphere() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(AvatarBlue.copy(alpha = .09f), size.minDimension * .58f, androidx.compose.ui.geometry.Offset(size.width * .04f, size.height * .22f))
        drawCircle(AvatarPink.copy(alpha = .08f), size.minDimension * .54f, androidx.compose.ui.geometry.Offset(size.width * .94f, size.height * .64f))
        drawCircle(AvatarViolet.copy(alpha = .07f), size.minDimension * .36f, androidx.compose.ui.geometry.Offset(size.width * .55f, size.height * .42f), style = Stroke(1.4f))
    }
}

@Composable
private fun AvatarEditorWash(accent: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, accent.copy(alpha = .055f), Color.White.copy(alpha = .15f)),
                ),
            ),
    )
}

@Composable
private fun AvatarHeader(language: String, subtitle: String, title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(subtitle, color = AvatarBlue, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
            Text(title, color = AvatarInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Surface(
            modifier = Modifier.size(47.dp).clickable(onClick = onBack),
            shape = CircleShape,
            color = Color(0xEFFFFFFF),
            border = BorderStroke(1.dp, AvatarBlue.copy(alpha = .30f)),
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                V12Glyph(V12GlyphType.BACK, AvatarInk, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FloatingIdentityTag(
    modifier: Modifier,
    title: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent.copy(alpha = .94f) else Color(0xE8FFFFFF),
        border = BorderStroke(if (selected) 2.dp else 1.dp, accent.copy(alpha = if (selected) .95f else .42f)),
        shadowElevation = if (selected) 13.dp else 7.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = if (selected) Color.White else accent) {}
            Spacer(Modifier.width(7.dp))
            Text(title, color = if (selected) Color.White else AvatarInk, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
        }
    }
}

@Composable
private fun AvatarLensOrb(
    modifier: Modifier,
    label: String,
    glyph: V12GlyphType,
    accent: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(if (active) 78.dp else 64.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .88f) else Color(0xE8FFFFFF),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = .50f)),
        shadowElevation = if (active) 15.dp else 7.dp,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            V12Glyph(glyph, if (active) Color.White else accent, Modifier.size(if (active) 24.dp else 20.dp))
            Text(label, modifier = Modifier.padding(top = 4.dp), color = if (active) Color.White else AvatarInk, fontSize = 6.8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AvatarOptionOrbit(
    modifier: Modifier,
    lens: SpatialAvatarLens,
    language: String,
    appearance: AvatarAppearance,
    accent: Color,
    onSkinColor: (String) -> Unit,
    onHair: (String) -> Unit,
    onHairColor: (String) -> Unit,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (lens) {
            SpatialAvatarLens.SKIN -> {
                listOf("F3D0BA", "E4B58F", "CF936B", "B97752", "8E583D", "603A2D").forEach { value ->
                    AvatarColorOrb(value, appearance.skinColor.equals(value, true), AvatarMint) { onSkinColor(value) }
                }
            }
            SpatialAvatarLens.HAIR -> {
                listOf(
                    "bald" to (if (language == "ar") "بدون" else "BALD"),
                    "shortFlat" to (if (language == "ar") "قصير" else "SHORT"),
                    "shortCurly" to (if (language == "ar") "كيرلي" else "CURLY"),
                    "bob" to "BOB",
                    "longButNotTooLong" to (if (language == "ar") "طويل" else "LONG"),
                ).forEach { (value, label) ->
                    AvatarTextOrb(label, appearance.hairVariant == value, accent) { onHair(value) }
                }
            }
            SpatialAvatarLens.COLOR -> {
                listOf("151210", "281916", "4D3025", "774227", "A46C3E", "D0B184").forEach { value ->
                    AvatarColorOrb(value, appearance.hairColor.equals(value, true), AvatarViolet) { onHairColor(value) }
                }
            }
            SpatialAvatarLens.FACE -> {
                listOf(
                    Triple("NATURAL", AvatarBlue) { onEyes("default") },
                    Triple("WIDE", AvatarMint) { onEyes("wide") },
                    Triple("SHARP", AvatarViolet) { onEyes("sharp") },
                    Triple("BROW", accent) { onEyebrows("defined") },
                    Triple("SMILE", AvatarPink) { onMouth("smile") },
                    Triple("LIPS", AvatarPink) { onMouth("full") },
                ).forEach { (label, color, action) ->
                    AvatarTextOrb(label, false, color, action)
                }
            }
        }
    }
}

@Composable
private fun AvatarColorOrb(hex: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val fill = runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color.Gray)
    Surface(
        modifier = Modifier.size(if (active) 58.dp else 48.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = fill,
        border = BorderStroke(if (active) 4.dp else 2.dp, if (active) accent else Color.White.copy(alpha = .90f)),
        shadowElevation = if (active) 12.dp else 5.dp,
    ) {}
}

@Composable
private fun AvatarTextOrb(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(if (active) 70.dp else 60.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .94f) else Color(0xEFFFFFFF),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = .45f)),
        shadowElevation = if (active) 11.dp else 5.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (active) Color.White else AvatarInk, fontSize = 7.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

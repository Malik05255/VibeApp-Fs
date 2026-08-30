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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile

private enum class DigitalAvatarMode { CHOOSE, EDIT }
private enum class DigitalAvatarLens { SKIN, HAIR, COLOR, FACE }
private enum class DigitalAvatarState { LOADING, READY, ERROR }

private val DigitalInk = Color(0xFF15395F)
private val DigitalBlue = Color(0xFF55BEFA)
private val DigitalPink = Color(0xFFFF8FB5)
private val DigitalMint = Color(0xFF55D4C0)
private val DigitalViolet = Color(0xFFA58BFA)
private val DigitalIce = Color(0xFFF4FBFF)

/**
 * Final v12 avatar path: real dual-character selection followed by the high-detail Vitruvian
 * body + FACS head + rigged hair editor. HM08 is intentionally not referenced anywhere here.
 */
@Composable
internal fun V12AvatarDigitalLabScreen(
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
    var modeName by rememberSaveable { mutableStateOf(DigitalAvatarMode.CHOOSE.name) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var lensName by rememberSaveable { mutableStateOf(DigitalAvatarLens.SKIN.name) }
    var duoRuntime by remember { mutableStateOf<V12AvatarDuoRuntime?>(null) }
    var digitalRuntime by remember { mutableStateOf<V12DigitalHumanRuntime?>(null) }
    var digitalState by remember { mutableStateOf(DigitalAvatarState.LOADING) }

    val mode = runCatching { DigitalAvatarMode.valueOf(modeName) }.getOrDefault(DigitalAvatarMode.CHOOSE)
    val lens = runCatching { DigitalAvatarLens.valueOf(lensName) }.getOrDefault(DigitalAvatarLens.SKIN)
    val selected = selectedName?.let { runCatching { AvatarPresentation.valueOf(it) }.getOrNull() }

    LaunchedEffect(selected, duoRuntime, mode) {
        if (mode == DigitalAvatarMode.CHOOSE) duoRuntime?.select(selected)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE8F8FF),
                        Color(0xFFF8F4FF),
                        Color(0xFFFFF5FA),
                        Color(0xFFF0FFFB),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        DigitalAtmosphere()

        when (mode) {
            DigitalAvatarMode.CHOOSE -> {
                DigitalDuoViewport(
                    modifier = Modifier.fillMaxSize(),
                    onRuntime = { duoRuntime = it },
                )

                DigitalHeader(
                    language = language,
                    code = "ALMI / LIVING IDENTITY",
                    title = if (language == "ar") "اختر الشخصية" else "CHOOSE YOUR HUMAN",
                    onBack = onBack,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight(.70f)
                        .fillMaxWidth(.50f)
                        .clickable {
                            selectedName = AvatarPresentation.MASCULINE.name
                            duoRuntime?.select(AvatarPresentation.MASCULINE)
                        },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(.70f)
                        .fillMaxWidth(.50f)
                        .clickable {
                            selectedName = AvatarPresentation.FEMININE.name
                            duoRuntime?.select(AvatarPresentation.FEMININE)
                        },
                )

                DigitalIdentityPill(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 25.dp, bottom = 112.dp),
                    title = if (language == "ar") "ذكر" else "MALE",
                    accent = DigitalBlue,
                    selected = selected == AvatarPresentation.MASCULINE,
                ) {
                    selectedName = AvatarPresentation.MASCULINE.name
                    duoRuntime?.select(AvatarPresentation.MASCULINE)
                }
                DigitalIdentityPill(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 25.dp, bottom = 112.dp),
                    title = if (language == "ar") "أنثى" else "FEMALE",
                    accent = DigitalPink,
                    selected = selected == AvatarPresentation.FEMININE,
                ) {
                    selectedName = AvatarPresentation.FEMININE.name
                    duoRuntime?.select(AvatarPresentation.FEMININE)
                }

                AnimatedVisibility(
                    visible = selected != null,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn() + scaleIn(initialScale = .82f),
                    exit = fadeOut() + scaleOut(targetScale = .82f),
                ) {
                    val accent = if (selected == AvatarPresentation.FEMININE) DigitalPink else DigitalBlue
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .size(94.dp)
                            .clickable {
                                selected?.let(onPresentation)
                                digitalState = DigitalAvatarState.LOADING
                                modeName = DigitalAvatarMode.EDIT.name
                            },
                        shape = CircleShape,
                        color = accent.copy(alpha = .95f),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = .90f)),
                        shadowElevation = 20.dp,
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("↗", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Light)
                            Text(
                                if (language == "ar") "ادخل" else "ENTER",
                                color = Color.White,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = .9.sp,
                            )
                        }
                    }
                }
            }

            DigitalAvatarMode.EDIT -> {
                val presentation = selected ?: appearance.presentation
                val accent = if (presentation == AvatarPresentation.FEMININE) DigitalPink else DigitalBlue
                val liveAppearance = appearance.copy(presentation = presentation)

                DigitalHumanViewport(
                    presentation = presentation,
                    appearance = liveAppearance,
                    modifier = Modifier.fillMaxSize(),
                    onRuntime = { digitalRuntime = it },
                    onState = { digitalState = it },
                )
                DigitalEditWash(accent)

                DigitalHeader(
                    language = language,
                    code = "ALMI / DIGITAL HUMAN LAB",
                    title = if (language == "ar") "إنسانك الرقمي" else "YOUR DIGITAL HUMAN",
                    onBack = {
                        digitalRuntime?.destroy()
                        digitalRuntime = null
                        digitalState = DigitalAvatarState.LOADING
                        modeName = DigitalAvatarMode.CHOOSE.name
                        duoRuntime?.resetSelection()
                    },
                )

                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 73.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = .88f),
                    border = BorderStroke(1.dp, accent.copy(alpha = .30f)),
                    shadowElevation = 7.dp,
                ) {
                    Text(
                        when (digitalState) {
                            DigitalAvatarState.LOADING -> if (language == "ar") "تحميل DIGITAL HUMAN…" else "DIGITAL HUMAN • LOADING"
                            DigitalAvatarState.READY -> "PBR • FACS • SKELETON • LIVE"
                            DigitalAvatarState.ERROR -> if (language == "ar") "تعذر تحميل الإنسان الرقمي" else "DIGITAL HUMAN FAILED"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = when (digitalState) {
                            DigitalAvatarState.ERROR -> Color(0xFFE25572)
                            else -> accent
                        },
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = .75.sp,
                    )
                }

                if (digitalState == DigitalAvatarState.LOADING) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).size(84.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = .78f),
                        border = BorderStroke(1.dp, accent.copy(alpha = .24f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(31.dp))
                        }
                    }
                }

                if (digitalTwinSnapshotUri != null || bodyProfile.hasExplicitHeight) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 78.dp, start = 14.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = DigitalMint.copy(alpha = .13f),
                        border = BorderStroke(1.dp, DigitalMint.copy(alpha = .40f)),
                    ) {
                        Text(
                            "BODY SYNC",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = DigitalMint,
                            fontSize = 6.8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = .8.sp,
                        )
                    }
                }

                DigitalLensOrb(
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = 10.dp, y = (-118).dp),
                    label = if (language == "ar") "البشرة" else "SKIN",
                    glyph = V12GlyphType.AVATAR,
                    accent = DigitalMint,
                    active = lens == DigitalAvatarLens.SKIN,
                ) { lensName = DigitalAvatarLens.SKIN.name }
                DigitalLensOrb(
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-10).dp, y = (-78).dp),
                    label = if (language == "ar") "الشعر" else "HAIR",
                    glyph = V12GlyphType.FIT,
                    accent = accent,
                    active = lens == DigitalAvatarLens.HAIR,
                ) { lensName = DigitalAvatarLens.HAIR.name }
                DigitalLensOrb(
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = 16.dp, y = 92.dp),
                    label = if (language == "ar") "اللون" else "COLOR",
                    glyph = V12GlyphType.THEME,
                    accent = DigitalViolet,
                    active = lens == DigitalAvatarLens.COLOR,
                ) { lensName = DigitalAvatarLens.COLOR.name }
                DigitalLensOrb(
                    modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-16).dp, y = 118.dp),
                    label = if (language == "ar") "الوجه" else "FACE",
                    glyph = V12GlyphType.DETAIL,
                    accent = DigitalPink,
                    active = lens == DigitalAvatarLens.FACE,
                ) { lensName = DigitalAvatarLens.FACE.name }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 78.dp, end = 14.dp)
                        .size(54.dp)
                        .clickable(enabled = digitalState == DigitalAvatarState.READY) {
                            digitalRuntime?.playTurntable()
                        },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = .90f),
                    border = BorderStroke(1.dp, accent.copy(alpha = .34f)),
                    shadowElevation = 9.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        V12Glyph(V12GlyphType.TURN, accent, Modifier.size(23.dp))
                    }
                }

                DigitalOptionOrbit(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp),
                    lens = lens,
                    language = language,
                    appearance = liveAppearance,
                    accent = accent,
                    onSkinColor = onSkinColor,
                    onHair = onHair,
                    onHairColor = onHairColor,
                    onEyes = onEyes,
                    onEyebrows = onEyebrows,
                    onMouth = onMouth,
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .size(84.dp)
                        .clickable(enabled = digitalState == DigitalAvatarState.READY) {
                            onPresentation(presentation)
                            onComplete()
                        },
                    shape = CircleShape,
                    color = if (digitalState == DigitalAvatarState.READY) accent.copy(alpha = .95f) else Color.White.copy(alpha = .82f),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = .90f)),
                    shadowElevation = 18.dp,
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("✓", color = if (digitalState == DigitalAvatarState.READY) Color.White else DigitalInk.copy(alpha = .35f), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (language == "ar") "اعتماد" else "LIVE",
                            color = if (digitalState == DigitalAvatarState.READY) Color.White else DigitalInk.copy(alpha = .35f),
                            fontSize = 7.2.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = .7.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DigitalDuoViewport(modifier: Modifier, onRuntime: (V12AvatarDuoRuntime) -> Unit) {
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
private fun DigitalHumanViewport(
    presentation: AvatarPresentation,
    appearance: AvatarAppearance,
    modifier: Modifier,
    onRuntime: (V12DigitalHumanRuntime) -> Unit,
    onState: (DigitalAvatarState) -> Unit,
) {
    var runtime by remember { mutableStateOf<V12DigitalHumanRuntime?>(null) }
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
                V12DigitalHumanRuntime(
                    context = context,
                    surfaceView = surface,
                    initialPresentation = presentation,
                    initialAppearance = appearance,
                    onReady = { onState(DigitalAvatarState.READY) },
                    onFailure = { onState(DigitalAvatarState.ERROR) },
                ).also {
                    runtime = it
                    onRuntime(it)
                    it.initialize()
                    it.start()
                }
            }
        },
        update = {
            runtime?.update(presentation, appearance)
        },
    )
}

@Composable
private fun DigitalAtmosphere() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(DigitalBlue.copy(alpha = .10f), size.minDimension * .58f, Offset(size.width * .04f, size.height * .20f))
        drawCircle(DigitalPink.copy(alpha = .09f), size.minDimension * .54f, Offset(size.width * .96f, size.height * .66f))
        drawCircle(DigitalMint.copy(alpha = .07f), size.minDimension * .47f, Offset(size.width * .28f, size.height * .93f))
        drawCircle(DigitalViolet.copy(alpha = .08f), size.minDimension * .36f, Offset(size.width * .60f, size.height * .40f), style = Stroke(1.3f))
    }
}

@Composable
private fun DigitalEditWash(accent: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Transparent, accent.copy(alpha = .045f), DigitalIce.copy(alpha = .24f)),
                ),
            ),
    )
}

@Composable
private fun DigitalHeader(language: String, code: String, title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(code, color = DigitalBlue, fontSize = 8.2.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
            Text(title, color = DigitalInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Surface(
            modifier = Modifier.size(47.dp).clickable(onClick = onBack),
            shape = CircleShape,
            color = Color(0xEEFFFFFF),
            border = BorderStroke(1.dp, DigitalBlue.copy(alpha = .30f)),
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                V12Glyph(V12GlyphType.BACK, DigitalInk, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DigitalIdentityPill(
    modifier: Modifier,
    title: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent.copy(alpha = .95f) else Color(0xEAFFFFFF),
        border = BorderStroke(if (selected) 2.dp else 1.dp, accent.copy(alpha = if (selected) .95f else .42f)),
        shadowElevation = if (selected) 14.dp else 7.dp,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(8.dp), CircleShape, color = if (selected) Color.White else accent) {}
            Spacer(Modifier.width(7.dp))
            Text(title, color = if (selected) Color.White else DigitalInk, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
        }
    }
}

@Composable
private fun DigitalLensOrb(
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
        color = if (active) accent.copy(alpha = .90f) else Color(0xECFFFFFF),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = .52f)),
        shadowElevation = if (active) 16.dp else 7.dp,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            V12Glyph(glyph, if (active) Color.White else accent, Modifier.size(if (active) 24.dp else 20.dp))
            Text(label, modifier = Modifier.padding(top = 4.dp), color = if (active) Color.White else DigitalInk, fontSize = 6.8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DigitalOptionOrbit(
    modifier: Modifier,
    lens: DigitalAvatarLens,
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
            DigitalAvatarLens.SKIN -> {
                listOf("F3D0BA", "E4B58F", "CF936B", "B97752", "8E583D", "603A2D").forEach { value ->
                    DigitalColorOrb(value, appearance.skinColor.equals(value, true), DigitalMint) { onSkinColor(value) }
                }
            }
            DigitalAvatarLens.HAIR -> {
                listOf(
                    "bald" to (if (language == "ar") "بدون" else "BALD"),
                    "shortFlat" to (if (language == "ar") "قصير" else "SHORT"),
                    "shortCurly" to (if (language == "ar") "كيرلي" else "CURL"),
                    "bob" to "BOB",
                    "longButNotTooLong" to (if (language == "ar") "طويل" else "LONG"),
                ).forEach { (value, label) ->
                    DigitalTextOrb(label, appearance.hairVariant == value, accent) { onHair(value) }
                }
            }
            DigitalAvatarLens.COLOR -> {
                listOf("151210", "281916", "4D3025", "774227", "A46C3E", "D0B184").forEach { value ->
                    DigitalColorOrb(value, appearance.hairColor.equals(value, true), DigitalViolet) { onHairColor(value) }
                }
            }
            DigitalAvatarLens.FACE -> {
                DigitalTextOrb("NATURAL", appearance.eyesVariant == "default", DigitalBlue) { onEyes("default") }
                DigitalTextOrb("WIDE", appearance.eyesVariant == "wide", DigitalMint) { onEyes("wide") }
                DigitalTextOrb("SHARP", appearance.eyesVariant == "sharp", DigitalViolet) { onEyes("sharp") }
                DigitalTextOrb("BROW", appearance.eyebrowsVariant == "defined", accent) { onEyebrows("defined") }
                DigitalTextOrb("SMILE", appearance.mouthVariant == "smile", DigitalPink) { onMouth("smile") }
                DigitalTextOrb("LIPS", appearance.mouthVariant == "full", DigitalPink) { onMouth("full") }
            }
        }
    }
}

@Composable
private fun DigitalColorOrb(hex: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    val fill = runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color.Gray)
    Surface(
        modifier = Modifier.size(if (active) 58.dp else 48.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = fill,
        border = BorderStroke(if (active) 4.dp else 2.dp, if (active) accent else Color.White.copy(alpha = .92f)),
        shadowElevation = if (active) 12.dp else 5.dp,
    ) {}
}

@Composable
private fun DigitalTextOrb(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(if (active) 70.dp else 60.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .95f) else Color(0xEEFFFFFF),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = .46f)),
        shadowElevation = if (active) 11.dp else 5.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (active) Color.White else DigitalInk, fontSize = 7.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

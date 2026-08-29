package com.almi.ai.ui.v12

import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile
import kotlinx.coroutines.delay

private enum class AvatarRail { SKIN, HAIR, COLOR, FACE }

@Composable
internal fun V12AvatarScreen(
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
    val p = V12Palettes.Avatar
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPresentation = selected?.let { runCatching { AvatarPresentation.valueOf(it) }.getOrNull() }
    var rail by rememberSaveable { mutableStateOf(AvatarRail.SKIN) }
    var maleRuntime by remember { mutableStateOf<V12AvatarRuntime?>(null) }
    var femaleRuntime by remember { mutableStateOf<V12AvatarRuntime?>(null) }
    var controls by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPresentation) {
        controls = false
        when (selectedPresentation) {
            null -> {
                maleRuntime?.start(); femaleRuntime?.start()
                maleRuntime?.faceFront(); femaleRuntime?.faceFront()
            }
            AvatarPresentation.MASCULINE -> {
                onPresentation(AvatarPresentation.MASCULINE)
                maleRuntime?.start(); femaleRuntime?.start()
                maleRuntime?.playWalkIn(fromRight = false, durationMs = 760L)
                delay(620)
                femaleRuntime?.stop()
                controls = true
            }
            AvatarPresentation.FEMININE -> {
                onPresentation(AvatarPresentation.FEMININE)
                maleRuntime?.start(); femaleRuntime?.start()
                femaleRuntime?.playWalkIn(fromRight = true, durationMs = 760L)
                delay(620)
                maleRuntime?.stop()
                controls = true
            }
        }
    }

    Box(Modifier.fillMaxSize().background(p.background).statusBarsPadding()) {
        // Ambient geometry, deliberately static: visual depth without battery-cost animation.
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF171322), Color(0xFF0D0B12)))))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ALMI / IDENTITY LAB", color = p.signal, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                Text(
                    if (selectedPresentation == null) (if (language == "ar") "اختر الشخصية" else "CHOOSE A BODY") else (if (language == "ar") "ابنِ هويتك" else "BUILD IDENTITY"),
                    color = p.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-.5).sp,
                )
            }
            V12BackControl(p, if (language == "ar") "العوالم" else "WORLDS", onBack)
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(top = 76.dp, bottom = if (controls) 96.dp else 14.dp)) {
            val maleOffset by animateDpAsState(
                targetValue = when (selectedPresentation) {
                    null -> -(maxWidth * .22f)
                    AvatarPresentation.MASCULINE -> 0.dp
                    AvatarPresentation.FEMININE -> -(maxWidth * 1.05f)
                },
                animationSpec = tween(560),
                label = "male-runway",
            )
            val femaleOffset by animateDpAsState(
                targetValue = when (selectedPresentation) {
                    null -> maxWidth * .22f
                    AvatarPresentation.FEMININE -> 0.dp
                    AvatarPresentation.MASCULINE -> maxWidth * 1.05f
                },
                animationSpec = tween(560),
                label = "female-runway",
            )
            val width = if (selectedPresentation == null) maxWidth * .56f else maxWidth * .82f

            AvatarViewport(
                presentation = AvatarPresentation.MASCULINE,
                appearance = appearance.copy(presentation = AvatarPresentation.MASCULINE),
                modifier = Modifier.align(Alignment.Center).offset(x = maleOffset).width(width).fillMaxSize(.92f),
                onRuntime = { maleRuntime = it },
            )
            AvatarViewport(
                presentation = AvatarPresentation.FEMININE,
                appearance = appearance.copy(presentation = AvatarPresentation.FEMININE),
                modifier = Modifier.align(Alignment.Center).offset(x = femaleOffset).width(width).fillMaxSize(.92f),
                onRuntime = { femaleRuntime = it },
            )

            if (selectedPresentation == null) {
                // Split-runway labels: no conventional radio buttons.
                RunwayChoice(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 18.dp).width(maxWidth * .42f),
                    code = "A",
                    title = if (language == "ar") "ذكر" else "MALE",
                    p = p,
                ) { selected = AvatarPresentation.MASCULINE.name }
                RunwayChoice(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp).width(maxWidth * .42f),
                    code = "B",
                    title = if (language == "ar") "أنثى" else "FEMALE",
                    p = p,
                ) { selected = AvatarPresentation.FEMININE.name }

                Box(Modifier.align(Alignment.Center).width(1.dp).fillMaxSize(.72f).background(p.edge.copy(alpha = .55f)))
            }
        }

        AnimatedVisibility(visible = controls, modifier = Modifier.align(Alignment.CenterStart), enter = fadeIn(), exit = fadeOut()) {
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 130.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                RailNode("SKIN", V12GlyphType.AVATAR, rail == AvatarRail.SKIN, p) { rail = AvatarRail.SKIN }
                RailNode("HAIR", V12GlyphType.FIT, rail == AvatarRail.HAIR, p) { rail = AvatarRail.HAIR }
                RailNode("COLOR", V12GlyphType.THEME, rail == AvatarRail.COLOR, p) { rail = AvatarRail.COLOR }
                RailNode("FACE", V12GlyphType.DETAIL, rail == AvatarRail.FACE, p) { rail = AvatarRail.FACE }
            }
        }

        AnimatedVisibility(visible = controls, modifier = Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
            Column(
                modifier = Modifier.padding(end = 9.dp, top = 130.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundAction("360", V12GlyphType.TURN, p) {
                    if (selectedPresentation == AvatarPresentation.MASCULINE) maleRuntime?.playTurntable() else femaleRuntime?.playTurntable()
                }
                RoundAction(if (language == "ar") "بدّل" else "SWAP", V12GlyphType.RESET, p) {
                    controls = false
                    selected = null
                }
            }
        }

        AnimatedVisibility(visible = controls, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 10.dp),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 12.dp, bottomEnd = 32.dp, bottomStart = 12.dp),
                color = p.panel.copy(alpha = .96f),
                border = BorderStroke(1.dp, p.edge),
            ) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        when (rail) {
                            AvatarRail.SKIN -> ColorChoices(listOf("F3D0BA", "E4B58F", "CF936B", "B97752", "8E583D", "603A2D"), appearance.skinColor, p, onSkinColor)
                            AvatarRail.HAIR -> TextChoices(
                                listOf(
                                    "bald" to (if (language == "ar") "بدون" else "BALD"),
                                    "shortFlat" to (if (language == "ar") "قصير" else "SHORT"),
                                    "shortCurly" to (if (language == "ar") "كيرلي" else "CURLY"),
                                    "bob" to "BOB",
                                    "longButNotTooLong" to (if (language == "ar") "طويل" else "LONG"),
                                ),
                                appearance.hairVariant,
                                p,
                                onHair,
                            )
                            AvatarRail.COLOR -> ColorChoices(listOf("151210", "281916", "4D3025", "774227", "A46C3E", "D0B184"), appearance.hairColor, p, onHairColor)
                            AvatarRail.FACE -> TextChoices(
                                listOf(
                                    "eyes:default" to (if (language == "ar") "طبيعي" else "NATURAL"),
                                    "eyes:wide" to (if (language == "ar") "عين واسعة" else "WIDE"),
                                    "eyes:sharp" to (if (language == "ar") "نظرة حادة" else "SHARP"),
                                    "brow:defined" to (if (language == "ar") "حاجب محدد" else "BROW"),
                                    "mouth:smile" to (if (language == "ar") "ابتسامة" else "SMILE"),
                                    "mouth:full" to (if (language == "ar") "شفاه" else "LIPS"),
                                ),
                                currentFaceKey(appearance),
                                p,
                            ) { key ->
                                when {
                                    key.startsWith("eyes:") -> onEyes(key.substringAfter(':'))
                                    key.startsWith("brow:") -> onEyebrows(key.substringAfter(':'))
                                    key.startsWith("mouth:") -> onMouth(key.substringAfter(':'))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.height(56.dp).width(72.dp).clickable(onClick = onComplete),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 8.dp),
                        color = p.signal,
                    ) {
                        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                            Text("✓", color = p.signalInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text(if (language == "ar") "اعتماد" else "USE", color = p.signalInk, fontSize = 7.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        if (controls && (digitalTwinSnapshotUri != null || bodyProfile.hasExplicitHeight)) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp),
                shape = RoundedCornerShape(999.dp),
                color = p.signal.copy(alpha = .13f),
                border = BorderStroke(1.dp, p.signal.copy(alpha = .30f)),
            ) {
                Text("BODY SYNC / ON", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = p.signal, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun AvatarViewport(
    presentation: AvatarPresentation,
    appearance: AvatarAppearance,
    modifier: Modifier,
    onRuntime: (V12AvatarRuntime) -> Unit,
) {
    var runtime by remember(presentation) { mutableStateOf<V12AvatarRuntime?>(null) }
    DisposableEffect(presentation) { onDispose { runtime?.stop() } }
    Box(modifier.clip(RoundedCornerShape(36.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun RunwayChoice(modifier: Modifier, code: String, title: String, p: V12Palette, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(74.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 9.dp, bottomEnd = 28.dp, bottomStart = 9.dp),
        color = p.panel.copy(alpha = .84f),
        border = BorderStroke(1.dp, p.edge),
    ) {
        Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(38.dp), CircleShape, color = p.signal) { Box(contentAlignment = Alignment.Center) { Text(code, color = p.signalInk, fontSize = 11.sp, fontWeight = FontWeight.Black) } }
            Spacer(Modifier.width(10.dp))
            Text(title, color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RailNode(label: String, glyph: V12GlyphType, active: Boolean, p: V12Palette, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(width = 58.dp, height = if (active) 68.dp else 52.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(topStart = 9.dp, topEnd = 24.dp, bottomEnd = 9.dp, bottomStart = 24.dp),
        color = if (active) p.signal else p.panel.copy(alpha = .86f),
        border = BorderStroke(1.dp, if (active) p.signal else p.edge),
    ) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            V12Glyph(glyph, if (active) p.signalInk else p.muted, Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (active) p.signalInk else p.muted, fontSize = 6.8.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
        }
    }
}

@Composable
private fun RoundAction(label: String, glyph: V12GlyphType, p: V12Palette, onClick: () -> Unit) {
    Surface(Modifier.size(54.dp).clickable(onClick = onClick), CircleShape, color = p.panel.copy(alpha = .88f), border = BorderStroke(1.dp, p.edge)) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            V12Glyph(glyph, p.signal, Modifier.size(18.dp))
            Text(label, color = p.muted, fontSize = 6.5.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ColorChoices(values: List<String>, current: String, p: V12Palette, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            Surface(
                modifier = Modifier.size(if (current.equals(value, true)) 48.dp else 40.dp).clickable { onSelect(value) },
                shape = CircleShape,
                color = runCatching { Color(android.graphics.Color.parseColor("#$value")) }.getOrDefault(Color.Gray),
                border = BorderStroke(if (current.equals(value, true)) 3.dp else 1.dp, if (current.equals(value, true)) p.signal else p.edge),
            ) {}
        }
    }
}

@Composable
private fun TextChoices(options: List<Pair<String, String>>, current: String, p: V12Palette, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { (value, label) ->
            val active = current == value
            Surface(
                modifier = Modifier.height(42.dp).clickable { onSelect(value) },
                shape = RoundedCornerShape(if (active) 21.dp else 11.dp),
                color = if (active) p.signal else p.background,
                border = BorderStroke(1.dp, if (active) p.signal else p.edge),
            ) {
                Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = if (active) p.signalInk else p.ink, fontSize = 7.8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun currentFaceKey(appearance: AvatarAppearance): String = when {
    appearance.eyesVariant != "default" -> "eyes:${appearance.eyesVariant}"
    appearance.eyebrowsVariant != "default" -> "brow:${appearance.eyebrowsVariant}"
    else -> "mouth:${appearance.mouthVariant}"
}

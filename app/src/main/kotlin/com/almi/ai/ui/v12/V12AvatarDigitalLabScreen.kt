package com.almi.ai.ui.v12

import android.view.SurfaceView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile

private enum class FutureAvatarLens { SKIN, HAIR, COLOR, FACE }
private enum class FutureAvatarRenderState { LOADING, READY, ERROR }

private val LabInk = Color(0xFF143654)
private val LabBlue = Color(0xFF43B9F3)
private val LabPink = Color(0xFFFF7FA8)
private val LabMint = Color(0xFF55D8C4)
private val LabViolet = Color(0xFF9A8CFF)
private val LabGlass = Color(0xF2FFFFFF)

/**
 * High-fidelity avatar editor shown after the polished Hero gender selection.
 *
 * There is deliberately no second gender chooser here. The selected Digital Human immediately
 * fills the stage and customization lives in one bottom control dock instead of floating orbs.
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
    var lensName by rememberSaveable { mutableStateOf(FutureAvatarLens.SKIN.name) }
    var runtime by remember { mutableStateOf<V12DigitalHumanRuntime?>(null) }
    var renderState by remember { mutableStateOf(FutureAvatarRenderState.LOADING) }
    val lens = runCatching { FutureAvatarLens.valueOf(lensName) }.getOrDefault(FutureAvatarLens.SKIN)
    val accent = if (appearance.presentation == AvatarPresentation.FEMININE) LabPink else LabBlue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE8F8FF),
                        Color(0xFFF8FCFF),
                        Color(0xFFFFF9FC),
                        Color(0xFFF1FBFF),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        AvatarFutureField(accent)

        DigitalHumanViewportV2(
            presentation = appearance.presentation,
            appearance = appearance,
            modifier = Modifier.fillMaxSize(),
            onRuntime = { runtime = it },
            onState = { renderState = it },
        )
        AvatarStudioWash(accent)
        AvatarStudioScan(accent = accent, active = renderState == FutureAvatarRenderState.READY)

        AvatarStudioHeader(
            language = language,
            state = renderState,
            accent = accent,
            bodySynced = digitalTwinSnapshotUri != null || bodyProfile.hasExplicitHeight,
            onBack = onBack,
            onTurn = { runtime?.playTurntable() },
        )

        if (renderState == FutureAvatarRenderState.LOADING) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(188.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = .88f),
                border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
                shadowElevation = 14.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (language == "ar") "بناء الإنسان الرقمي" else "BUILDING DIGITAL HUMAN",
                        color = LabInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(10.dp))
                    AvatarLoadingRail(accent)
                }
            }
        }

        if (renderState == FutureAvatarRenderState.ERROR) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = .94f),
                border = BorderStroke(1.dp, Color(0xFFFF6680).copy(alpha = .42f)),
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (language == "ar") "تعذر تشغيل الإنسان الرقمي" else "DIGITAL HUMAN FAILED",
                        color = Color(0xFFE94E6B),
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (language == "ar") "ارجع ثم حاول مرة أخرى" else "Go back and try again",
                        modifier = Modifier.padding(top = 5.dp),
                        color = LabInk.copy(alpha = .55f),
                        fontSize = 10.sp,
                    )
                }
            }
        }

        AvatarControlDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            language = language,
            lens = lens,
            appearance = appearance,
            accent = accent,
            enabled = renderState == FutureAvatarRenderState.READY,
            onLens = { lensName = it.name },
            onSkinColor = onSkinColor,
            onHair = onHair,
            onHairColor = onHairColor,
            onEyes = onEyes,
            onEyebrows = onEyebrows,
            onMouth = onMouth,
            onComplete = {
                onPresentation(appearance.presentation)
                onComplete()
            },
        )
    }
}

@Composable
private fun DigitalHumanViewportV2(
    presentation: AvatarPresentation,
    appearance: AvatarAppearance,
    modifier: Modifier,
    onRuntime: (V12DigitalHumanRuntime) -> Unit,
    onState: (FutureAvatarRenderState) -> Unit,
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
                    onReady = { onState(FutureAvatarRenderState.READY) },
                    onFailure = { onState(FutureAvatarRenderState.ERROR) },
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
private fun AvatarStudioHeader(
    language: String,
    state: FutureAvatarRenderState,
    accent: Color,
    bodySynced: Boolean,
    onBack: () -> Unit,
    onTurn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.clickable(onClick = onBack),
                shape = RoundedCornerShape(20.dp),
                color = LabGlass,
                border = BorderStroke(1.dp, LabBlue.copy(alpha = .24f)),
                shadowElevation = 7.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("‹", color = LabInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (language == "ar") "رجوع" else "BACK",
                        color = LabInk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "ALMI / DIGITAL HUMAN",
                    color = LabBlue,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.15.sp,
                )
                Text(
                    if (language == "ar") "اصنع نسختك" else "BUILD YOUR TWIN",
                    color = LabInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Surface(
                modifier = Modifier.clickable(enabled = state == FutureAvatarRenderState.READY, onClick = onTurn),
                shape = RoundedCornerShape(20.dp),
                color = LabGlass,
                border = BorderStroke(1.dp, accent.copy(alpha = .34f)),
                shadowElevation = 7.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("360°", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (language == "ar") "دوران" else "TURN",
                        color = LabInk.copy(alpha = .55f),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AvatarStatusChip(
                text = when (state) {
                    FutureAvatarRenderState.LOADING -> "PBR / LOADING"
                    FutureAvatarRenderState.READY -> "PBR / FACS / LIVE"
                    FutureAvatarRenderState.ERROR -> "RENDER / ERROR"
                },
                accent = if (state == FutureAvatarRenderState.ERROR) Color(0xFFE94E6B) else accent,
            )
            if (bodySynced) AvatarStatusChip("BODY / SYNC", LabMint)
        }
    }
}

@Composable
private fun AvatarStatusChip(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = .72f),
        border = BorderStroke(1.dp, accent.copy(alpha = .24f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = accent,
            fontSize = 6.8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = .65.sp,
        )
    }
}

@Composable
private fun AvatarControlDock(
    modifier: Modifier,
    language: String,
    lens: FutureAvatarLens,
    appearance: AvatarAppearance,
    accent: Color,
    enabled: Boolean,
    onLens: (FutureAvatarLens) -> Unit,
    onSkinColor: (String) -> Unit,
    onHair: (String) -> Unit,
    onHairColor: (String) -> Unit,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
    onComplete: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.White.copy(alpha = .93f),
        border = BorderStroke(1.2.dp, accent.copy(alpha = .28f)),
        shadowElevation = 20.dp,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AvatarDockTab(
                    modifier = Modifier.weight(1f),
                    label = if (language == "ar") "البشرة" else "SKIN",
                    active = lens == FutureAvatarLens.SKIN,
                    accent = LabMint,
                ) { onLens(FutureAvatarLens.SKIN) }
                AvatarDockTab(
                    modifier = Modifier.weight(1f),
                    label = if (language == "ar") "الشعر" else "HAIR",
                    active = lens == FutureAvatarLens.HAIR,
                    accent = accent,
                ) { onLens(FutureAvatarLens.HAIR) }
                AvatarDockTab(
                    modifier = Modifier.weight(1f),
                    label = if (language == "ar") "اللون" else "COLOR",
                    active = lens == FutureAvatarLens.COLOR,
                    accent = LabViolet,
                ) { onLens(FutureAvatarLens.COLOR) }
                AvatarDockTab(
                    modifier = Modifier.weight(1f),
                    label = if (language == "ar") "الوجه" else "FACE",
                    active = lens == FutureAvatarLens.FACE,
                    accent = LabPink,
                ) { onLens(FutureAvatarLens.FACE) }
            }

            Spacer(Modifier.height(9.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (lens) {
                    FutureAvatarLens.SKIN -> {
                        listOf("F3D0BA", "E4B58F", "CF936B", "B97752", "8E583D", "603A2D").forEach { value ->
                            AvatarColorTile(
                                hex = value,
                                active = appearance.skinColor.equals(value, true),
                                accent = LabMint,
                                enabled = enabled,
                            ) { onSkinColor(value) }
                        }
                    }
                    FutureAvatarLens.HAIR -> {
                        listOf(
                            "bald" to (if (language == "ar") "بدون" else "BALD"),
                            "shortFlat" to (if (language == "ar") "قصير" else "SHORT"),
                            "shortCurly" to (if (language == "ar") "كيرلي" else "CURL"),
                            "bob" to "BOB",
                            "longButNotTooLong" to (if (language == "ar") "طويل" else "LONG"),
                        ).forEach { (value, label) ->
                            AvatarOptionTile(
                                label = label,
                                active = appearance.hairVariant == value,
                                accent = accent,
                                enabled = enabled,
                            ) { onHair(value) }
                        }
                    }
                    FutureAvatarLens.COLOR -> {
                        listOf("151210", "281916", "4D3025", "774227", "A46C3E", "D0B184").forEach { value ->
                            AvatarColorTile(
                                hex = value,
                                active = appearance.hairColor.equals(value, true),
                                accent = LabViolet,
                                enabled = enabled,
                            ) { onHairColor(value) }
                        }
                    }
                    FutureAvatarLens.FACE -> {
                        AvatarOptionTile("NATURAL", appearance.eyesVariant == "default", LabBlue, enabled) { onEyes("default") }
                        AvatarOptionTile("WIDE", appearance.eyesVariant == "wide", LabMint, enabled) { onEyes("wide") }
                        AvatarOptionTile("SHARP", appearance.eyesVariant == "sharp", LabViolet, enabled) { onEyes("sharp") }
                        AvatarOptionTile("BROW", appearance.eyebrowsVariant == "defined", accent, enabled) { onEyebrows("defined") }
                        AvatarOptionTile("SMILE", appearance.mouthVariant == "smile", LabPink, enabled) { onMouth("smile") }
                        AvatarOptionTile("LIPS", appearance.mouthVariant == "full", LabPink, enabled) { onMouth("full") }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable(enabled = enabled, onClick = onComplete),
                shape = RoundedCornerShape(20.dp),
                color = if (enabled) accent else Color(0xFFE3EBF1),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .90f)),
                shadowElevation = if (enabled) 11.dp else 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            if (language == "ar") "اعتماد النسخة" else "ACTIVATE DIGITAL TWIN",
                            color = if (enabled) Color.White else LabInk.copy(alpha = .32f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "LIVE ID / READY",
                            color = if (enabled) Color.White.copy(alpha = .72f) else LabInk.copy(alpha = .22f),
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = .8.sp,
                        )
                    }
                    Text(
                        "✓",
                        color = if (enabled) Color.White else LabInk.copy(alpha = .25f),
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarDockTab(
    modifier: Modifier,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (active) accent.copy(alpha = .15f) else Color(0xFFF5F9FC),
        border = BorderStroke(if (active) 1.5.dp else 1.dp, accent.copy(alpha = if (active) .62f else .18f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (active) accent else LabInk.copy(alpha = .48f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AvatarColorTile(
    hex: String,
    active: Boolean,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val fill = runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrDefault(Color.Gray)
    Surface(
        modifier = Modifier
            .width(58.dp)
            .height(46.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = fill,
        border = BorderStroke(if (active) 3.dp else 1.5.dp, if (active) accent else Color.White),
        shadowElevation = if (active) 8.dp else 2.dp,
    ) {
        if (active) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun AvatarOptionTile(
    label: String,
    active: Boolean,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(82.dp)
            .height(46.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (active) accent else Color(0xFFF5F9FC),
        border = BorderStroke(1.2.dp, accent.copy(alpha = if (active) .80f else .25f)),
        shadowElevation = if (active) 7.dp else 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (active) Color.White else LabInk,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AvatarLoadingRail(accent: Color) {
    val progress by rememberInfiniteTransition(label = "avatar-load-rail")
        .animateFloat(
            initialValue = .15f,
            targetValue = .92f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "avatar-load-rail-value",
        )

    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(Color(0xFFE0EEF7), RoundedCornerShape(99.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(5.dp)
                .background(accent, RoundedCornerShape(99.dp)),
        )
    }
}

@Composable
private fun AvatarFutureField(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val grid = LabBlue.copy(alpha = .09f)
        val step = 52f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .15f), Offset(x, size.height * .90f), 1f)
            x += step
        }
        var y = size.height * .15f
        while (y <= size.height * .90f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(accent.copy(alpha = .075f), size.minDimension * .52f, Offset(size.width * .50f, size.height * .47f))
        drawCircle(LabMint.copy(alpha = .045f), size.minDimension * .32f, Offset(size.width * .18f, size.height * .70f))
    }
}

@Composable
private fun AvatarStudioWash(accent: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = .05f),
                        Color.Transparent,
                        Color.Transparent,
                        accent.copy(alpha = .035f),
                        Color(0xFFF1FBFF).copy(alpha = .16f),
                    ),
                ),
            ),
    )
}

@Composable
private fun AvatarStudioScan(accent: Color, active: Boolean) {
    val sweep by rememberInfiniteTransition(label = "avatar-studio-scan")
        .animateFloat(
            initialValue = .18f,
            targetValue = .76f,
            animationSpec = infiniteRepeatable(tween(if (active) 3200 else 5200), RepeatMode.Reverse),
            label = "avatar-studio-scan-value",
        )

    Canvas(Modifier.fillMaxSize().graphicsLayer(alpha = if (active) 1f else .45f)) {
        val y = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, accent.copy(alpha = .045f), Color.White.copy(alpha = .13f), accent.copy(alpha = .045f), Color.Transparent),
                startY = y - 72f,
                endY = y + 72f,
            ),
            topLeft = Offset(0f, y - 72f),
            size = Size(size.width, 144f),
        )
        drawLine(accent.copy(alpha = .28f), Offset(size.width * .10f, y), Offset(size.width * .90f, y), 1.1f)
    }
}

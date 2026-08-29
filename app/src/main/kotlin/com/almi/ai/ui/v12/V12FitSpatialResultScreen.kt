package com.almi.ai.ui.v12

import android.net.Uri
import android.widget.VideoView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.almi.ai.data.repository.MotionDirection
import com.almi.ai.data.repository.VideoGenerationStatus
import com.almi.ai.ui.tryon.FitPressure
import com.almi.ai.ui.tryon.TryOnViewModel

private val MirrorInk = Color(0xFF173A60)
private val MirrorBlue = Color(0xFF56BFFE)
private val MirrorPink = Color(0xFFFF8FB7)
private val MirrorMint = Color(0xFF53D8C1)
private val MirrorViolet = Color(0xFFA48AFA)
private val MirrorGlass = Color(0xEFFFFFFF)

@Composable
internal fun V12FitSpatialResultScreen(
    viewModel: TryOnViewModel,
    language: String,
    onHome: () -> Unit,
    onAi: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var before by rememberSaveable { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition(label = "motion-core")
        .animateFloat(
            initialValue = .96f,
            targetValue = 1.045f,
            animationSpec = infiniteRepeatable(tween(1350), RepeatMode.Reverse),
            label = "motion-core-pulse",
        )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEAF8FF), Color(0xFFF8F4FF), Color(0xFFFFF7FB)),
                ),
            )
            .statusBarsPadding(),
    ) {
        if (state.generatedVideo != null && !before) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            start()
                        }
                    }
                },
                update = { view ->
                    if (view.tag != state.generatedVideo) {
                        view.tag = state.generatedVideo
                        view.setVideoURI(Uri.parse(state.generatedVideo))
                        view.start()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = if (before) state.personImage else state.generatedImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = .20f),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFFEAF8FF).copy(alpha = .52f),
                        ),
                    ),
                ),
        )
        MirrorAtmosphere()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(50.dp).clickable { viewModel.returnToStudio() },
                shape = CircleShape,
                color = MirrorGlass,
                border = BorderStroke(1.dp, MirrorBlue.copy(alpha = .34f)),
                shadowElevation = 9.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(V12GlyphType.BACK, MirrorInk, Modifier.size(21.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ALMI / LIVE MIRROR", color = MirrorBlue, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                Text(if (language == "ar") "الإطلالة حيّة" else "FIT IS ALIVE", color = MirrorInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Surface(
                modifier = Modifier.size(50.dp).clickable(onClick = onHome),
                shape = CircleShape,
                color = MirrorGlass,
                border = BorderStroke(1.dp, MirrorMint.copy(alpha = .34f)),
                shadowElevation = 9.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⌂", color = MirrorMint, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        MirrorToggle(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 78.dp, end = 16.dp),
            label = if (language == "ar") "قبل" else "BEFORE",
            active = before,
            accent = MirrorViolet,
        ) { before = true }
        MirrorToggle(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 138.dp, end = 16.dp),
            label = if (language == "ar") "بعد" else "AFTER",
            active = !before,
            accent = MirrorBlue,
        ) { before = false }

        state.fitSimulation?.let { fit ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 80.dp, start = 16.dp),
                shape = RoundedCornerShape(999.dp),
                color = MirrorGlass,
                border = BorderStroke(1.dp, MirrorMint.copy(alpha = .38f)),
                shadowElevation = 7.dp,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("FIT SIGNAL", color = MirrorMint, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = .85.sp)
                    Text("${fit.size.label} • ${mirrorPressureLabel(fit.overallPressure, language)}", color = MirrorInk, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 126.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MotionOrbit(
                direction = MotionDirection.TURN,
                current = state.motion,
                glyph = V12GlyphType.TURN,
                label = if (language == "ar") "دوران" else "TURN",
                accent = MirrorViolet,
                onClick = viewModel::setMotion,
            )
            MotionOrbit(
                direction = MotionDirection.WALK,
                current = state.motion,
                glyph = V12GlyphType.WALK,
                label = if (language == "ar") "مشي" else "WALK",
                accent = MirrorBlue,
                onClick = viewModel::setMotion,
            )
            MotionOrbit(
                direction = MotionDirection.DETAIL,
                current = state.motion,
                glyph = V12GlyphType.DETAIL,
                label = if (language == "ar") "تفاصيل" else "DETAIL",
                accent = MirrorPink,
                onClick = viewModel::setMotion,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(88.dp)
                .scale(pulse)
                .clickable(enabled = !state.isGeneratingVideo && state.generatedVideo == null) {
                    viewModel.generateVideo()
                },
            shape = CircleShape,
            color = if (state.generatedVideo != null) MirrorMint.copy(alpha = .95f) else MirrorBlue.copy(alpha = .95f),
            border = BorderStroke(2.dp, Color.White.copy(alpha = .90f)),
            shadowElevation = 20.dp,
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (state.isGeneratingVideo) {
                    CircularProgressIndicator(modifier = Modifier.size(27.dp), color = Color.White, strokeWidth = 2.5.dp)
                } else {
                    V12Glyph(
                        if (state.generatedVideo != null) V12GlyphType.DETAIL else V12GlyphType.WALK,
                        Color.White,
                        Modifier.size(26.dp),
                    )
                }
                Text(
                    mirrorVideoLabel(state.videoStatus, state.generatedVideo != null, language),
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color.White,
                    fontSize = 6.2.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (state.videoError) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 30.dp).clickable(onClick = onAi),
                shape = RoundedCornerShape(999.dp),
                color = MirrorPink.copy(alpha = .92f),
                shadowElevation = 8.dp,
            ) {
                Text(
                    if (language == "ar") "راجع AI" else "CHECK AI",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun MirrorAtmosphere() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(MirrorBlue.copy(alpha = .07f), size.minDimension * .44f, Offset(size.width * .10f, size.height * .24f), style = Stroke(1.3f))
        drawCircle(MirrorPink.copy(alpha = .055f), size.minDimension * .38f, Offset(size.width * .92f, size.height * .68f), style = Stroke(1.2f))
    }
}

@Composable
private fun MirrorToggle(modifier: Modifier, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = modifier.size(54.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .93f) else MirrorGlass,
        border = BorderStroke(1.5.dp, accent.copy(alpha = .48f)),
        shadowElevation = if (active) 11.dp else 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (active) Color.White else MirrorInk, fontSize = 6.8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MotionOrbit(
    direction: MotionDirection,
    current: MotionDirection,
    glyph: V12GlyphType,
    label: String,
    accent: Color,
    onClick: (MotionDirection) -> Unit,
) {
    val active = direction == current
    Surface(
        modifier = Modifier.size(if (active) 76.dp else 64.dp).clickable { onClick(direction) },
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .92f) else MirrorGlass,
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = .50f)),
        shadowElevation = if (active) 14.dp else 6.dp,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            V12Glyph(glyph, if (active) Color.White else accent, Modifier.size(if (active) 25.dp else 20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = if (active) Color.White else MirrorInk, fontSize = 6.8.sp, fontWeight = FontWeight.Black)
        }
    }
}

private fun mirrorPressureLabel(value: FitPressure, language: String): String = when (value) {
    FitPressure.VERY_TIGHT -> if (language == "ar") "شديد الضيق" else "VERY TIGHT"
    FitPressure.TIGHT -> if (language == "ar") "ضيق" else "TIGHT"
    FitPressure.CLOSE -> if (language == "ar") "ملاصق" else "CLOSE"
    FitPressure.REGULAR -> if (language == "ar") "مناسب" else "REGULAR"
    FitPressure.LOOSE -> if (language == "ar") "واسع" else "LOOSE"
    FitPressure.UNKNOWN -> if (language == "ar") "تقديري" else "ESTIMATE"
}

private fun mirrorVideoLabel(status: VideoGenerationStatus, ready: Boolean, language: String): String = when {
    ready -> if (language == "ar") "جاهز" else "LIVE"
    status == VideoGenerationStatus.SUBMITTING -> if (language == "ar") "إرسال" else "SEND"
    status == VideoGenerationStatus.PROCESSING -> if (language == "ar") "معالجة" else "BUILD"
    status == VideoGenerationStatus.DOWNLOADING -> if (language == "ar") "تجهيز" else "PREP"
    else -> if (language == "ar") "حرّك" else "MOVE"
}

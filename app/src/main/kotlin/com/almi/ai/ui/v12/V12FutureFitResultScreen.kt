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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val ChamberInk = Color(0xFF123657)
private val ChamberBlue = Color(0xFF38B7F3)
private val ChamberCyan = Color(0xFF58E5F1)
private val ChamberPink = Color(0xFFFF7EA9)
private val ChamberMint = Color(0xFF54D9C2)
private val ChamberViolet = Color(0xFF9D8BFF)
private val ChamberGlass = Color(0xEEFFFFFF)

@Composable
internal fun V12FutureFitResultScreen(
    viewModel: TryOnViewModel,
    language: String,
    onHome: () -> Unit,
    onAi: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var before by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE8F8FF), Color(0xFFF7FCFF), Color(0xFFFFF7FB)),
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
                            Color.White.copy(alpha = .13f),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFFF1FAFF).copy(alpha = .52f),
                        ),
                    ),
                ),
        )
        ChamberScanField()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.width(76.dp).height(42.dp).clickable { viewModel.returnToStudio() },
                shape = RoundedCornerShape(15.dp),
                color = ChamberGlass,
                border = BorderStroke(1.dp, ChamberBlue.copy(alpha = .28f)),
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (language == "ar") "تعديل" else "EDIT", color = ChamberInk, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ALMI // RESULT CHAMBER", color = ChamberBlue, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                Text(if (language == "ar") "الاحتمال حيّ" else "POSSIBILITY IS LIVE", color = ChamberInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }

            Surface(
                modifier = Modifier.width(76.dp).height(42.dp).clickable(onClick = onHome),
                shape = RoundedCornerShape(15.dp),
                color = ChamberGlass,
                border = BorderStroke(1.dp, ChamberMint.copy(alpha = .30f)),
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (language == "ar") "الرئيسية" else "HOME", color = ChamberInk, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        FutureBeforeAfterSwitch(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp),
            language = language,
            before = before,
            onBefore = { before = true },
            onAfter = { before = false },
        )

        state.fitSimulation?.let { fit ->
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 128.dp, start = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = ChamberGlass,
                border = BorderStroke(1.dp, ChamberMint.copy(alpha = .34f)),
                shadowElevation = 7.dp,
            ) {
                Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                    Text("FIT SIGNAL", color = ChamberMint, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                    Text(
                        "${fit.size.label} • ${futurePressureLabel(fit.overallPressure, language)}",
                        color = ChamberInk,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 14.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = ChamberGlass,
            border = BorderStroke(1.2.dp, Color.White),
            shadowElevation = 20.dp,
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FutureMotionCell(
                        modifier = Modifier.weight(1f),
                        code = "TURN",
                        label = if (language == "ar") "دوران" else "Turn",
                        direction = MotionDirection.TURN,
                        current = state.motion,
                        accent = ChamberViolet,
                        onClick = viewModel::setMotion,
                    )
                    FutureMotionCell(
                        modifier = Modifier.weight(1f),
                        code = "WALK",
                        label = if (language == "ar") "مشي" else "Walk",
                        direction = MotionDirection.WALK,
                        current = state.motion,
                        accent = ChamberBlue,
                        onClick = viewModel::setMotion,
                    )
                    FutureMotionCell(
                        modifier = Modifier.weight(1f),
                        code = "DETAIL",
                        label = if (language == "ar") "تفاصيل" else "Detail",
                        direction = MotionDirection.DETAIL,
                        current = state.motion,
                        accent = ChamberPink,
                        onClick = viewModel::setMotion,
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clickable(enabled = !state.isGeneratingVideo && state.generatedVideo == null) {
                            viewModel.generateVideo()
                        },
                    shape = RoundedCornerShape(19.dp),
                    color = if (state.generatedVideo != null) ChamberMint else ChamberBlue,
                    border = BorderStroke(1.dp, ChamberCyan.copy(alpha = .72f)),
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 15.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isGeneratingVideo) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(27.dp),
                                color = Color.White,
                                strokeWidth = 2.4.dp,
                            )
                        } else {
                            Text("ALMI", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                futureVideoLabel(state.videoStatus, state.generatedVideo != null, language),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                if (state.generatedVideo != null) "MOTION READY" else "MOTION SYNTHESIS",
                                color = Color.White.copy(alpha = .62f),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = .65.sp,
                            )
                        }
                        Text("→", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        if (state.videoError) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 168.dp).clickable(onClick = onAi),
                shape = RoundedCornerShape(15.dp),
                color = ChamberPink.copy(alpha = .94f),
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
private fun FutureBeforeAfterSwitch(
    modifier: Modifier,
    language: String,
    before: Boolean,
    onBefore: () -> Unit,
    onAfter: () -> Unit,
) {
    Surface(
        modifier = modifier.width(192.dp).height(44.dp),
        shape = RoundedCornerShape(16.dp),
        color = ChamberGlass,
        border = BorderStroke(1.dp, ChamberBlue.copy(alpha = .25f)),
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChamberSwitchCell(
                modifier = Modifier.weight(1f),
                label = if (language == "ar") "قبل" else "BEFORE",
                active = before,
                accent = ChamberViolet,
                onClick = onBefore,
            )
            ChamberSwitchCell(
                modifier = Modifier.weight(1f),
                label = if (language == "ar") "بعد" else "AFTER",
                active = !before,
                accent = ChamberBlue,
                onClick = onAfter,
            )
        }
    }
}

@Composable
private fun ChamberSwitchCell(
    modifier: Modifier,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (active) accent.copy(alpha = .92f) else Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (active) Color.White else ChamberInk.copy(alpha = .62f), fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun FutureMotionCell(
    modifier: Modifier,
    code: String,
    label: String,
    direction: MotionDirection,
    current: MotionDirection,
    accent: Color,
    onClick: (MotionDirection) -> Unit,
) {
    val active = direction == current
    Surface(
        modifier = modifier.height(62.dp).clickable { onClick(direction) },
        shape = RoundedCornerShape(17.dp),
        color = if (active) accent.copy(alpha = .17f) else Color(0xFFF7FBFE),
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) .62f else .18f)),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(code, color = accent, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = .5.sp)
                Text(if (active) "●" else "○", color = if (active) ChamberMint else accent.copy(alpha = .38f), fontSize = 7.sp)
            }
            Text(label, color = ChamberInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ChamberScanField() {
    val sweep by rememberInfiniteTransition(label = "result-chamber-scan")
        .animateFloat(
            initialValue = .08f,
            targetValue = .92f,
            animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
            label = "result-chamber-scan-value",
        )
    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, ChamberCyan.copy(alpha = .05f), ChamberCyan.copy(alpha = .12f), Color.White.copy(alpha = .15f), Color.Transparent),
                startY = y - 70f,
                endY = y + 70f,
            ),
            topLeft = Offset(0f, y - 70f),
            size = Size(size.width, 140f),
        )
        drawLine(ChamberCyan.copy(alpha = .38f), Offset(size.width * .03f, y), Offset(size.width * .97f, y), 1.1f)
    }
}

private fun futurePressureLabel(value: FitPressure, language: String): String = when (value) {
    FitPressure.VERY_TIGHT -> if (language == "ar") "شديد الضيق" else "VERY TIGHT"
    FitPressure.TIGHT -> if (language == "ar") "ضيق" else "TIGHT"
    FitPressure.CLOSE -> if (language == "ar") "ملاصق" else "CLOSE"
    FitPressure.REGULAR -> if (language == "ar") "مناسب" else "REGULAR"
    FitPressure.LOOSE -> if (language == "ar") "واسع" else "LOOSE"
    FitPressure.UNKNOWN -> if (language == "ar") "تقديري" else "ESTIMATE"
}

private fun futureVideoLabel(status: VideoGenerationStatus, ready: Boolean, language: String): String = when {
    ready -> if (language == "ar") "الحركة جاهزة" else "MOTION READY"
    status == VideoGenerationStatus.SUBMITTING -> if (language == "ar") "إرسال الحركة" else "SUBMITTING MOTION"
    status == VideoGenerationStatus.PROCESSING -> if (language == "ar") "نبني الحركة" else "BUILDING MOTION"
    status == VideoGenerationStatus.DOWNLOADING -> if (language == "ar") "تجهيز النتيجة" else "PREPARING RESULT"
    else -> if (language == "ar") "حوّلها إلى حركة" else "SYNTHESIZE MOTION"
}

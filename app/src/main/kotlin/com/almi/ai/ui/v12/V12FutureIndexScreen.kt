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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val DeckInk = Color(0xFF123657)
private val DeckBlue = Color(0xFF45B8F4)
private val DeckBlueDeep = Color(0xFF3198F2)
private val DeckCyan = Color(0xFF58E3F1)
private val DeckPink = Color(0xFFFF7DA8)
private val DeckMint = Color(0xFF54D8C2)
private val DeckViolet = Color(0xFF9E8CFF)
private val DeckPeach = Color(0xFFFFB58C)
private val DeckGlass = Color(0xEFFFFFFF)

@Composable
internal fun V12FutureIndexScreen(
    language: String,
    personImage: String?,
    bodyReady: Boolean,
    avatarReady: Boolean,
    aiReady: Boolean,
    onFit: () -> Unit,
    onAvatar: () -> Unit,
    onBody: () -> Unit,
    onAi: () -> Unit,
    onControl: () -> Unit,
) {
    val orbit by rememberInfiniteTransition(label = "deck-orbit")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(14500), RepeatMode.Restart),
            label = "deck-orbit-value",
        )
    val reverseOrbit by rememberInfiniteTransition(label = "deck-reverse-orbit")
        .animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(10500), RepeatMode.Restart),
            label = "deck-reverse-orbit-value",
        )
    val corePulse by rememberInfiniteTransition(label = "deck-core-pulse")
        .animateFloat(
            initialValue = .96f,
            targetValue = 1.045f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "deck-core-pulse-value",
        )
    val sweep by rememberInfiniteTransition(label = "deck-sweep")
        .animateFloat(
            initialValue = .08f,
            targetValue = .92f,
            animationSpec = infiniteRepeatable(tween(3800), RepeatMode.Reverse),
            label = "deck-sweep-value",
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE8F8FF),
                        Color(0xFFF8FCFF),
                        Color(0xFFFFF8FC),
                        Color(0xFFF1FAFF),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        DeckLivingGrid(sweep)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("ALMI", color = DeckInk, fontSize = 35.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.6).sp)
                Text(
                    if (language == "ar") "نظامك البشري الرقمي" else "YOUR HUMAN DIGITAL SYSTEM",
                    color = DeckInk.copy(alpha = .45f),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.05.sp,
                )
            }

            Surface(
                modifier = Modifier.size(50.dp).clickable(onClick = onControl),
                shape = CircleShape,
                color = DeckGlass,
                border = BorderStroke(1.dp, DeckPeach.copy(alpha = .43f)),
                shadowElevation = 11.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(V12GlyphType.CONTROL, DeckInk, Modifier.size(22.dp))
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 82.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = .65f),
                border = BorderStroke(1.dp, DeckCyan.copy(alpha = .22f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(DeckMint, CircleShape))
                    Text("ALMI 12 / LIVE", color = DeckInk.copy(alpha = .61f), fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                }
            }
        }

        Box(
            modifier = Modifier.align(Alignment.Center).offset(y = (-78).dp).size(286.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .size(282.dp)
                    .graphicsLayer(rotationZ = orbit),
            ) {
                drawArc(
                    color = DeckBlue.copy(alpha = .27f),
                    startAngle = 12f,
                    sweepAngle = 102f,
                    useCenter = false,
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = DeckPink.copy(alpha = .25f),
                    startAngle = 164f,
                    sweepAngle = 78f,
                    useCenter = false,
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = DeckViolet.copy(alpha = .20f),
                    startAngle = 280f,
                    sweepAngle = 44f,
                    useCenter = false,
                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                )
                repeat(6) { index ->
                    val angle = Math.toRadians((index * 60).toDouble())
                    val radius = size.minDimension * .47f
                    val x = center.x + kotlin.math.cos(angle).toFloat() * radius
                    val y = center.y + kotlin.math.sin(angle).toFloat() * radius
                    drawCircle(Color.White, 4f, Offset(x, y))
                    drawCircle(DeckCyan.copy(alpha = .38f), 8f, Offset(x, y), style = Stroke(1.3f))
                }
            }

            Canvas(
                Modifier
                    .size(246.dp)
                    .graphicsLayer(rotationZ = reverseOrbit),
            ) {
                drawCircle(DeckBlue.copy(alpha = .10f), size.minDimension * .49f, style = Stroke(1.5f))
                drawArc(
                    color = DeckCyan.copy(alpha = .38f),
                    startAngle = 35f,
                    sweepAngle = 128f,
                    useCenter = false,
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round),
                )
            }

            Box(
                Modifier
                    .size(222.dp)
                    .graphicsLayer(scaleX = corePulse, scaleY = corePulse)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                DeckBlue.copy(alpha = .16f),
                                DeckCyan.copy(alpha = .08f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )

            Surface(
                modifier = Modifier.size(190.dp),
                shape = CircleShape,
                color = Color(0xF5FFFFFF),
                border = BorderStroke(1.5.dp, Color.White),
                shadowElevation = 24.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (personImage != null) {
                        AsyncImage(
                            model = personImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, DeckBlueDeep.copy(alpha = .23f))),
                            ),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            V12Glyph(V12GlyphType.AVATAR, DeckBlueDeep, Modifier.size(52.dp))
                            Spacer(Modifier.height(7.dp))
                            Text("HUMAN CORE", color = DeckInk, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("READY TO LINK", color = DeckMint, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 7.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = .91f),
                border = BorderStroke(1.dp, DeckBlue.copy(alpha = .23f)),
                shadowElevation = 8.dp,
            ) {
                Text(
                    if (language == "ar") "النواة الحية" else "LIVE HUMAN CORE",
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
                    color = DeckInk,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .7.sp,
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 15.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DeckPrimaryModule(
                title = if (language == "ar") "جرّب أي قطعة على نسختك" else "TRY ANY LOOK ON YOUR TWIN",
                subtitle = if (language == "ar") "مختبر الملاءمة بالذكاء الاصطناعي" else "AI FIT LAB",
                accent = DeckBlueDeep,
                glyph = V12GlyphType.FIT,
                onClick = onFit,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                DeckModule(
                    modifier = Modifier.weight(1f),
                    title = if (language == "ar") "الشخصية" else "AVATAR",
                    code = if (avatarReady) "LINKED" else "CREATE",
                    accent = DeckPink,
                    glyph = V12GlyphType.AVATAR,
                    onClick = onAvatar,
                )
                DeckModule(
                    modifier = Modifier.weight(1f),
                    title = if (language == "ar") "الجسم" else "BODY",
                    code = if (bodyReady) "MAPPED" else "SCAN",
                    accent = DeckMint,
                    glyph = V12GlyphType.BODY,
                    onClick = onBody,
                )
                DeckModule(
                    modifier = Modifier.weight(1f),
                    title = if (language == "ar") "الذكاء" else "AI",
                    code = if (aiReady) "ONLINE" else "SETUP",
                    accent = DeckViolet,
                    glyph = V12GlyphType.AI,
                    onClick = onAi,
                )
            }
        }
    }
}

@Composable
private fun DeckPrimaryModule(
    title: String,
    subtitle: String,
    accent: Color,
    glyph: V12GlyphType,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(82.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(29.dp),
        color = Color.White.copy(alpha = .83f),
        border = BorderStroke(1.4.dp, accent.copy(alpha = .34f)),
        shadowElevation = 15.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(Modifier.size(52.dp), RoundedCornerShape(19.dp), color = accent.copy(alpha = .13f)) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(glyph, accent, Modifier.size(25.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = DeckInk, fontSize = 16.5.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = accent, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            }
            Text("→", color = accent, fontSize = 23.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DeckModule(
    modifier: Modifier,
    title: String,
    code: String,
    accent: Color,
    glyph: V12GlyphType,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(88.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(25.dp),
        color = Color.White.copy(alpha = .77f),
        border = BorderStroke(1.dp, accent.copy(alpha = .27f)),
        shadowElevation = 9.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                V12Glyph(glyph, accent, Modifier.size(22.dp))
                Box(Modifier.size(6.dp).background(accent.copy(alpha = .72f), CircleShape))
            }
            Column {
                Text(title, color = DeckInk, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Text(code, color = accent, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = .65.sp)
            }
        }
    }
}

@Composable
private fun DeckLivingGrid(sweep: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val grid = Color(0xFF93CFEA).copy(alpha = .13f)
        val step = 48f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .15f), Offset(x, size.height * .88f), 1f)
            x += step
        }
        var y = size.height * .15f
        while (y <= size.height * .88f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }

        drawCircle(DeckBlue.copy(alpha = .075f), size.minDimension * .56f, Offset(size.width * .08f, size.height * .40f))
        drawCircle(DeckPink.copy(alpha = .06f), size.minDimension * .46f, Offset(size.width * .91f, size.height * .62f))
        drawCircle(DeckViolet.copy(alpha = .04f), size.minDimension * .35f, Offset(size.width * .58f, size.height * .24f))

        val beamY = size.height * sweep
        drawLine(DeckCyan.copy(alpha = .31f), Offset(size.width * .06f, beamY), Offset(size.width * .94f, beamY), 1.2f)
        drawCircle(Color.White.copy(alpha = .84f), 3.5f, Offset(size.width * .10f, beamY))
        drawCircle(Color.White.copy(alpha = .84f), 3.5f, Offset(size.width * .90f, beamY))
    }
}

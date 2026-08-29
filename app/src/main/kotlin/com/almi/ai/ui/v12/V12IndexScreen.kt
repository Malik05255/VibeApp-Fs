package com.almi.ai.ui.v12

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Ice = Color(0xFFF6FBFF)
private val Ink = Color(0xFF14345D)
private val Blue = Color(0xFF55B8FF)
private val Pink = Color(0xFFFF87B8)
private val Mint = Color(0xFF65D8C8)
private val Violet = Color(0xFFAA8BFF)
private val Peach = Color(0xFFFFB789)
private val Glass = Color(0xD9FFFFFF)

@Composable
internal fun V12IndexScreen(
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFEAF7FF),
                        Color(0xFFF9F7FF),
                        Color(0xFFFFF8FC),
                        Ice,
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        AuroraBackdrop()

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 22.dp, top = 12.dp),
        ) {
            Text(
                text = "ALMI",
                color = Ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
            )
            Text(
                text = if (language == "ar") "عالمك الرقمي" else "YOUR DIGITAL WORLD",
                color = Ink.copy(alpha = .48f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 18.dp, top = 12.dp)
                .size(52.dp)
                .clickable(onClick = onControl),
            shape = CircleShape,
            color = Glass,
            border = BorderStroke(1.dp, Peach.copy(alpha = .45f)),
            shadowElevation = 12.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                V12Glyph(V12GlyphType.CONTROL, Ink, Modifier.size(23.dp))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-18).dp)
                .size(270.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(250.dp)
                    .blur(34.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Blue.copy(alpha = .24f),
                                Violet.copy(alpha = .12f),
                                Color.Transparent,
                            ),
                        ),
                        CircleShape,
                    ),
            )
            Surface(
                modifier = Modifier.size(214.dp),
                shape = CircleShape,
                color = Color(0xEFFFFFFF),
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
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0x55285682)),
                                    ),
                                ),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            V12Glyph(V12GlyphType.AVATAR, Blue, Modifier.size(54.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("ALMI CORE", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = 16.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xF2FFFFFF),
                        border = BorderStroke(1.dp, Blue.copy(alpha = .25f)),
                        shadowElevation = 8.dp,
                    ) {
                        Text(
                            text = if (language == "ar") "المركز الحي" else "LIVE CORE",
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                            color = Ink,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = .9.sp,
                        )
                    }
                }
            }
        }

        FloatingWorld(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp).offset(y = (-160).dp),
            title = if (language == "ar") "القياس" else "BODY",
            status = if (bodyReady) "READY" else "MAP",
            accent = Mint,
            glyph = V12GlyphType.BODY,
            onClick = onBody,
        )
        FloatingWorld(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).offset(y = (-160).dp),
            title = if (language == "ar") "شخصيتي" else "AVATAR",
            status = if (avatarReady) "LIVE" else "CREATE",
            accent = Pink,
            glyph = V12GlyphType.AVATAR,
            onClick = onAvatar,
        )
        FloatingWorld(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 26.dp).offset(y = 174.dp),
            title = if (language == "ar") "التجربة" else "FIT",
            status = "MIRROR",
            accent = Blue,
            glyph = V12GlyphType.FIT,
            onClick = onFit,
        )
        FloatingWorld(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 26.dp).offset(y = 174.dp),
            title = "AI",
            status = if (aiReady) "ONLINE" else "SETUP",
            accent = Violet,
            glyph = V12GlyphType.AI,
            onClick = onAi,
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(Mint, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (language == "ar") "V12 • واجهة حية بدون قوائم تقليدية" else "V12 • LIVING SPATIAL INTERFACE",
                color = Ink.copy(alpha = .52f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .7.sp,
            )
        }
    }
}

@Composable
private fun AuroraBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            color = Blue.copy(alpha = .10f),
            radius = size.minDimension * .52f,
            center = androidx.compose.ui.geometry.Offset(size.width * .08f, size.height * .28f),
        )
        drawCircle(
            color = Pink.copy(alpha = .09f),
            radius = size.minDimension * .50f,
            center = androidx.compose.ui.geometry.Offset(size.width * .94f, size.height * .42f),
        )
        drawCircle(
            color = Violet.copy(alpha = .07f),
            radius = size.minDimension * .45f,
            center = androidx.compose.ui.geometry.Offset(size.width * .52f, size.height * .92f),
        )
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * .49f)
        repeat(3) { index ->
            drawCircle(
                color = Ink.copy(alpha = .055f - index * .012f),
                radius = size.minDimension * (.31f + index * .075f),
                center = center,
                style = Stroke(width = 1.2f),
            )
        }
        drawArc(
            color = Blue.copy(alpha = .35f),
            startAngle = 204f,
            sweepAngle = 74f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * .19f, size.height * .30f),
            size = androidx.compose.ui.geometry.Size(size.width * .62f, size.width * .62f),
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun FloatingWorld(
    modifier: Modifier,
    title: String,
    status: String,
    accent: Color,
    glyph: V12GlyphType,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .size(112.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(38.dp),
        color = Glass,
        border = BorderStroke(1.dp, accent.copy(alpha = .42f)),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = accent.copy(alpha = .14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(glyph, accent, Modifier.size(25.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(status, color = accent, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
        }
    }
}

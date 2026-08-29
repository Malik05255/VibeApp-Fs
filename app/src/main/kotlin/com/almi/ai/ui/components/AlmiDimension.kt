package com.almi.ai.ui.components

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * ALMI Eclipse design primitives.
 *
 * The public names intentionally stay compatible with the previous UI so every provider pane
 * inherits the new visual system without keeping duplicate legacy components in the repository.
 */
@Composable
fun DimensionBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "eclipse-backdrop")
    val drift by motion.animateFloat(
        initialValue = -0.08f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(8_000), RepeatMode.Reverse),
        label = "eclipse-drift",
    )

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.surface.copy(alpha = 0.96f),
                        scheme.background,
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val short = size.minDimension
            val violetCenter = Offset(size.width * (0.82f + drift), size.height * 0.12f)
            val magentaCenter = Offset(size.width * (0.18f - drift), size.height * 0.72f)

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(scheme.primary.copy(alpha = 0.23f), Color.Transparent),
                    center = violetCenter,
                    radius = short * 0.78f,
                ),
                radius = short * 0.78f,
                center = violetCenter,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(scheme.secondary.copy(alpha = 0.13f), Color.Transparent),
                    center = magentaCenter,
                    radius = short * 0.62f,
                ),
                radius = short * 0.62f,
                center = magentaCenter,
            )

            // Very subtle runway grid: visible enough to give depth, never enough to compete.
            val grid = scheme.outlineVariant.copy(alpha = 0.10f)
            val step = short / 7f
            var x = -step
            while (x < size.width + step) {
                drawLine(grid, Offset(x, 0f), Offset(x + size.height * 0.12f, size.height), 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
        }
        content()
    }
}

@Composable
fun DimensionCard(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (emphasized) 30.dp else 24.dp)
    val elevation = if (emphasized) 18.dp else 7.dp
    val click = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)

    Surface(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = scheme.primary.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.45f), clip = false)
            .then(click),
        shape = shape,
        color = if (emphasized) scheme.primaryContainer.copy(alpha = 0.34f)
        else scheme.surface.copy(alpha = 0.78f),
        border = BorderStroke(
            1.dp,
            if (emphasized) scheme.primary.copy(alpha = 0.58f)
            else scheme.outlineVariant.copy(alpha = 0.72f),
        ),
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
fun Glossy3DIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(19.dp)
    val scale by animateFloatAsState(if (active) 1.04f else 1f, label = "icon-scale")

    Box(
        modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(13.dp, shape, ambientColor = scheme.primary.copy(alpha = 0.30f), clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (active) {
                        listOf(scheme.primary, scheme.secondary, scheme.primary.copy(alpha = 0.86f))
                    } else {
                        listOf(scheme.surfaceVariant, scheme.surface, scheme.surfaceVariant)
                    }
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) Color.White else scheme.onSurfaceVariant,
            modifier = Modifier.size(25.dp),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                Color.White.copy(alpha = if (active) 0.52f else 0.23f),
                startAngle = 205f,
                sweepAngle = 112f,
                useCenter = false,
                topLeft = Offset(size.width * 0.12f, size.height * 0.08f),
                size = Size(size.width * 0.76f, size.height * 0.58f),
                style = Stroke(size.minDimension * 0.035f),
            )
        }
    }
}

@Composable
fun AiOrb3D(
    modifier: Modifier = Modifier,
    label: String = "AI",
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "ai-orb")
    val rotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000)),
        label = "orbit-rotation",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1_900), RepeatMode.Reverse),
        label = "orb-pulse",
    )

    Box(
        modifier
            .size(174.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(scheme.primary.copy(alpha = 0.06f), size.minDimension * 0.49f, center)
            drawCircle(scheme.primary.copy(alpha = 0.10f), size.minDimension * 0.40f, center)

            rotate(rotation, center) {
                drawOval(
                    scheme.primary.copy(alpha = 0.58f),
                    topLeft = Offset(size.width * 0.04f, size.height * 0.30f),
                    size = Size(size.width * 0.92f, size.height * 0.40f),
                    style = Stroke(1.8f),
                )
                drawOval(
                    scheme.secondary.copy(alpha = 0.46f),
                    topLeft = Offset(size.width * 0.18f, size.height * 0.08f),
                    size = Size(size.width * 0.64f, size.height * 0.84f),
                    style = Stroke(1.3f),
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.88f),
                        scheme.primary.copy(alpha = 0.98f),
                        scheme.secondary.copy(alpha = 0.96f),
                        scheme.primaryContainer.copy(alpha = 0.98f),
                    ),
                    center = Offset(size.width * 0.38f, size.height * 0.28f),
                    radius = size.minDimension * 0.42f,
                ),
                radius = size.minDimension * 0.30f,
                center = center,
            )
            drawCircle(
                Color.White.copy(alpha = 0.75f),
                radius = size.minDimension * 0.046f,
                center = Offset(size.width * 0.40f, size.height * 0.34f),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun ConnectionPill(text: String, connected: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "connection-dot")
    val dotAlpha by motion.animateFloat(
        initialValue = if (connected) 0.42f else 0.75f,
        targetValue = if (connected) 1f else 0.75f,
        animationSpec = infiniteRepeatable(tween(1_150), RepeatMode.Reverse),
        label = "connection-pulse",
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (connected) scheme.tertiaryContainer.copy(alpha = 0.54f) else scheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, if (connected) scheme.tertiary.copy(alpha = 0.36f) else scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(
                        (if (connected) scheme.tertiary else scheme.onSurfaceVariant).copy(alpha = dotAlpha),
                        CircleShape,
                    )
            )
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

enum class DimensionDestination { HOME, AI, SETTINGS }

@Composable
fun DimensionBottomBar(
    selected: DimensionDestination,
    language: String,
    onHome: () -> Unit,
    onAi: () -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = scheme.surface.copy(alpha = 0.90f),
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.70f)),
            shadowElevation = 18.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BottomItem(
                    icon = Icons.Outlined.Home,
                    label = if (language == "ar") "الرئيسية" else "Home",
                    selected = selected == DimensionDestination.HOME,
                    onClick = onHome,
                )
                BottomItem(
                    icon = Icons.Outlined.AutoAwesome,
                    label = if (language == "ar") "الذكاء الاصطناعي" else "AI",
                    selected = selected == DimensionDestination.AI,
                    special = true,
                    onClick = onAi,
                )
                BottomItem(
                    icon = Icons.Outlined.Settings,
                    label = if (language == "ar") "الإعدادات" else "Settings",
                    selected = selected == DimensionDestination.SETTINGS,
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun BottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    special: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, label = "nav-scale")

    Column(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = if (special && selected) {
                Modifier
                    .size(34.dp)
                    .shadow(10.dp, CircleShape, ambientColor = scheme.primary.copy(alpha = 0.55f))
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.secondary)))
            } else Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (special && selected) Color.White
                else if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(if (special && selected) 20.dp else 22.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

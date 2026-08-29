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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * ALMI v7 shared design primitives.
 *
 * Public function names intentionally remain stable so provider/settings logic can keep compiling,
 * while the old grid/gloss visual language disappears everywhere at once.
 */
@Composable
fun DimensionBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.primary.copy(alpha = 0.045f), Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.14f),
                    radius = size.minDimension * 0.82f,
                ),
                center = Offset(size.width * 0.78f, size.height * 0.14f),
                radius = size.minDimension * 0.82f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.error.copy(alpha = 0.022f), Color.Transparent),
                    center = Offset(size.width * 0.10f, size.height * 0.80f),
                    radius = size.minDimension * 0.58f,
                ),
                center = Offset(size.width * 0.10f, size.height * 0.80f),
                radius = size.minDimension * 0.58f,
            )
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
    val shape = RoundedCornerShape(if (emphasized) 28.dp else 22.dp)
    val clickable = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)

    Surface(
        modifier = modifier.then(clickable),
        shape = shape,
        color = if (emphasized) scheme.surfaceContainerHigh.copy(alpha = 0.96f)
        else scheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(
            1.dp,
            if (emphasized) scheme.primary.copy(alpha = 0.24f) else scheme.outlineVariant.copy(alpha = 0.82f),
        ),
        tonalElevation = if (emphasized) 2.dp else 0.dp,
        shadowElevation = if (emphasized) 3.dp else 0.dp,
        content = content,
    )
}

/** Kept under the legacy API name, but deliberately no longer glossy. */
@Composable
fun Glossy3DIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (active) 1.04f else 1f,
        animationSpec = tween(220),
        label = "v7-icon-scale",
    )

    Surface(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(16.dp),
        color = if (active) scheme.onSurface else scheme.surfaceContainerHighest.copy(alpha = 0.74f),
        border = BorderStroke(
            1.dp,
            if (active) Color.Transparent else scheme.outlineVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (active) scheme.surface else scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A restrained live AI nucleus. Motion is subtle and semantic instead of decorative 3D gloss.
 */
@Composable
fun AiOrb3D(
    modifier: Modifier = Modifier,
    label: String = "AI",
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "v7-ai-nucleus")
    val sweep by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000)),
        label = "v7-ai-sweep",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1_800), RepeatMode.Reverse),
        label = "v7-ai-pulse",
    )

    Box(
        modifier = modifier
            .size(128.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.31f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(scheme.primary.copy(alpha = 0.16f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.50f,
                ),
                radius = size.minDimension * 0.50f,
                center = center,
            )
            drawCircle(scheme.onSurface.copy(alpha = 0.92f), radius, center)
            drawCircle(scheme.outlineVariant.copy(alpha = 0.72f), radius * 1.38f, center, style = Stroke(1.2f))
            drawArc(
                color = scheme.primary,
                startAngle = sweep,
                sweepAngle = 62f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 1.38f, center.y - radius * 1.38f),
                size = androidx.compose.ui.geometry.Size(radius * 2.76f, radius * 2.76f),
                style = Stroke(3f, cap = StrokeCap.Round),
            )
            drawCircle(scheme.error, 4.5f, Offset(center.x + radius * 1.34f, center.y))
        }
        Text(
            label,
            color = scheme.surface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun ConnectionPill(text: String, connected: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "v7-live-state")
    val alpha by motion.animateFloat(
        initialValue = if (connected) 0.42f else 0.72f,
        targetValue = if (connected) 1f else 0.72f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "v7-live-dot",
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(
                        (if (connected) scheme.primary else scheme.outline).copy(alpha = alpha),
                        CircleShape,
                    ),
            )
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

enum class DimensionDestination { HOME, AI, SETTINGS }

/** Legacy-compatible navigation; MainActivity v7 uses AlmiV7BottomDock instead. */
@Composable
fun DimensionBottomBar(
    selected: DimensionDestination,
    language: String,
    onHome: () -> Unit,
    onAi: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MinimalNavItem(
                    code = "ST",
                    label = if (language == "ar") "الاستوديو" else "Studio",
                    selected = selected == DimensionDestination.HOME,
                    onClick = onHome,
                    modifier = Modifier.weight(1f),
                )
                MinimalNavItem(
                    code = "AI",
                    label = if (language == "ar") "الذكاء" else "AI",
                    selected = selected == DimensionDestination.AI,
                    onClick = onAi,
                    modifier = Modifier.weight(1f),
                )
                MinimalNavItem(
                    code = "SY",
                    label = if (language == "ar") "الإعدادات" else "Settings",
                    selected = selected == DimensionDestination.SETTINGS,
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MinimalNavItem(
    code: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) scheme.onSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            code,
            color = if (selected) scheme.surface else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
        )
        if (selected) {
            Text(
                label,
                modifier = Modifier.padding(start = 6.dp),
                color = scheme.surface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

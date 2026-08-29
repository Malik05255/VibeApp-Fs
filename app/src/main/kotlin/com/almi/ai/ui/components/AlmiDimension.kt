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

/** Shared ALMI Precision Atelier primitives. Public names stay stable for existing feature screens. */
@Composable
fun DimensionBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "atelier-backdrop")
    val drift by motion.animateFloat(
        initialValue = -0.025f,
        targetValue = 0.025f,
        animationSpec = infiniteRepeatable(tween(10_000), RepeatMode.Reverse),
        label = "atelier-drift",
    )

    Box(modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val grid = scheme.outlineVariant.copy(alpha = 0.24f)
            val strong = scheme.outline.copy(alpha = 0.10f)
            val step = size.width / 6f
            var x = -step
            while (x < size.width + step) {
                drawLine(
                    grid,
                    Offset(x + drift * size.width, 0f),
                    Offset(x, size.height),
                    1f,
                )
                x += step
            }
            var y = step
            var row = 0
            while (y < size.height) {
                drawLine(if (row % 4 == 0) strong else grid, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
                row++
            }
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(scheme.primary.copy(alpha = 0.055f), Color.Transparent),
                    center = Offset(size.width * 0.78f, size.height * 0.13f),
                    radius = size.minDimension * 0.72f,
                ),
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.78f, size.height * 0.13f),
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
    val shape = RoundedCornerShape(if (emphasized) 20.dp else 16.dp)
    val click = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)

    Surface(
        modifier = modifier.then(click),
        shape = shape,
        color = if (emphasized) scheme.primaryContainer.copy(alpha = 0.28f)
        else scheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            width = 1.dp,
            color = if (emphasized) scheme.primary.copy(alpha = 0.48f)
            else scheme.outlineVariant,
        ),
        tonalElevation = 0.dp,
        shadowElevation = if (emphasized) 5.dp else 1.dp,
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
    val scale by animateFloatAsState(if (active) 1.035f else 1f, label = "tool-scale")
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) scheme.primary else scheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                if (active) Color.White.copy(alpha = 0.55f) else scheme.outline,
                Offset(size.width * 0.18f, size.height * 0.18f),
                Offset(size.width * 0.42f, size.height * 0.18f),
                2f,
            )
            drawLine(
                if (active) Color.White.copy(alpha = 0.55f) else scheme.outline,
                Offset(size.width * 0.18f, size.height * 0.18f),
                Offset(size.width * 0.18f, size.height * 0.42f),
                2f,
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) Color.White else scheme.onSurface,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
fun AiOrb3D(
    modifier: Modifier = Modifier,
    label: String = "AI",
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "scanner-core")
    val rotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(15_000)),
        label = "scanner-rotation",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(2_200), RepeatMode.Reverse),
        label = "scanner-pulse",
    )

    Box(
        modifier
            .size(168.dp)
            .graphicsLayer { scaleX = pulse; scaleY = pulse },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(scheme.surface, size.minDimension * 0.34f, center)
            drawCircle(
                scheme.outlineVariant,
                size.minDimension * 0.34f,
                center,
                style = Stroke(2f),
            )
            drawCircle(
                scheme.primary.copy(alpha = 0.12f),
                size.minDimension * 0.46f,
                center,
                style = Stroke(1f),
            )
            rotate(rotation, center) {
                drawArc(
                    scheme.primary,
                    startAngle = -18f,
                    sweepAngle = 74f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.04f, size.height * 0.04f),
                    size = Size(size.width * 0.92f, size.height * 0.92f),
                    style = Stroke(3f),
                )
                drawOval(
                    scheme.outline.copy(alpha = 0.48f),
                    topLeft = Offset(size.width * 0.12f, size.height * 0.37f),
                    size = Size(size.width * 0.76f, size.height * 0.26f),
                    style = Stroke(1.2f),
                )
                drawOval(
                    scheme.outline.copy(alpha = 0.34f),
                    topLeft = Offset(size.width * 0.34f, size.height * 0.10f),
                    size = Size(size.width * 0.32f, size.height * 0.80f),
                    style = Stroke(1.2f),
                )
            }
            drawLine(
                scheme.primary.copy(alpha = 0.72f),
                Offset(center.x - size.width * 0.12f, center.y),
                Offset(center.x + size.width * 0.12f, center.y),
                2f,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = scheme.onSurface,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun ConnectionPill(text: String, connected: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "connection-dot")
    val dotAlpha by motion.animateFloat(
        initialValue = if (connected) 0.40f else 0.72f,
        targetValue = if (connected) 1f else 0.72f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "connection-pulse",
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = scheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        (if (connected) scheme.tertiary else scheme.onSurfaceVariant).copy(alpha = dotAlpha),
                        CircleShape,
                    )
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = scheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AtelierNavItem(
                    code = "ST",
                    label = if (language == "ar") "الاستوديو" else "Studio",
                    selected = selected == DimensionDestination.HOME,
                    onClick = onHome,
                    modifier = Modifier.weight(1f),
                )
                AtelierNavItem(
                    code = "AI",
                    label = if (language == "ar") "المحرك" else "Engine",
                    selected = selected == DimensionDestination.AI,
                    onClick = onAi,
                    modifier = Modifier.weight(1f),
                )
                AtelierNavItem(
                    code = "SY",
                    label = if (language == "ar") "النظام" else "System",
                    selected = selected == DimensionDestination.SETTINGS,
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AtelierNavItem(
    code: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val alpha by animateFloatAsState(if (selected) 1f else 0.58f, label = "nav-alpha")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (selected) scheme.primary else scheme.outline, CircleShape)
            )
            Text(
                text = code,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .fillMaxWidth(0.34f)
                .height(1.dp)
                .background(if (selected) scheme.primary else Color.Transparent)
        )
    }
}

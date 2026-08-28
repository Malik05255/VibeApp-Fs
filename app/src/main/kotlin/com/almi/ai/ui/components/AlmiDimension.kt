package com.almi.ai.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DimensionBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.primaryContainer.copy(alpha = 0.28f),
                        scheme.background,
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(scheme.primary.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.08f),
                    radius = size.minDimension * 0.55f,
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.92f, size.height * 0.08f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(scheme.tertiary.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.65f),
                    radius = size.minDimension * 0.48f,
                ),
                radius = size.minDimension * 0.48f,
                center = Offset(size.width * 0.05f, size.height * 0.65f),
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
    val shape = RoundedCornerShape(28.dp)
    val click = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Surface(
        modifier = modifier
            .shadow(if (emphasized) 14.dp else 6.dp, shape, clip = false)
            .then(click),
        shape = shape,
        color = if (emphasized) scheme.primaryContainer.copy(alpha = 0.90f) else scheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(
            1.dp,
            if (emphasized) scheme.primary.copy(alpha = 0.18f) else scheme.outlineVariant.copy(alpha = 0.75f),
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
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .size(58.dp)
            .shadow(10.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (active) {
                        listOf(
                            scheme.primary.copy(alpha = 0.98f),
                            scheme.secondary.copy(alpha = 0.94f),
                        )
                    } else {
                        listOf(
                            scheme.surface,
                            scheme.surfaceVariant,
                        )
                    }
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant,
            modifier = Modifier.size(27.dp),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = Color.White.copy(alpha = if (active) 0.38f else 0.55f),
                startAngle = 205f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(size.width * 0.16f, size.height * 0.10f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.56f),
                style = Stroke(width = size.minDimension * 0.045f),
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
    Box(modifier.size(158.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(scheme.primary.copy(alpha = 0.07f), size.minDimension * 0.49f, center)
            drawCircle(scheme.primary.copy(alpha = 0.11f), size.minDimension * 0.40f, center)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color.White.copy(alpha = 0.72f),
                        scheme.primary.copy(alpha = 0.95f),
                        scheme.secondary.copy(alpha = 0.90f),
                    ),
                    center = Offset(size.width * 0.38f, size.height * 0.30f),
                    radius = size.minDimension * 0.46f,
                ),
                radius = size.minDimension * 0.33f,
                center = center,
            )
            drawCircle(
                Color.White.copy(alpha = 0.58f),
                radius = size.minDimension * 0.055f,
                center = Offset(size.width * 0.39f, size.height * 0.34f),
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
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (connected) scheme.tertiaryContainer else scheme.surfaceVariant,
        border = BorderStroke(1.dp, if (connected) scheme.tertiary.copy(alpha = 0.22f) else scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (connected) scheme.tertiary else scheme.onSurfaceVariant, CircleShape)
            )
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = scheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.72f)),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .padding(horizontal = 22.dp),
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
                label = if (language == "ar") "الذكاء" else "AI",
                selected = selected == DimensionDestination.AI,
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

@Composable
private fun BottomItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(23.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

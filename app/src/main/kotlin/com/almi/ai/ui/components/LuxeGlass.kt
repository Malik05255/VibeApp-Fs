package com.almi.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LuxeBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.primaryContainer.copy(alpha = 0.22f),
                        colors.background,
                    )
                )
            )
    ) {
        Box(
            Modifier
                .size(250.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = 0.12f), Color.Transparent)
                    ),
                    CircleShape,
                )
        )
        Box(
            Modifier
                .size(220.dp)
                .align(Alignment.CenterStart)
                .background(
                    Brush.radialGradient(
                        listOf(colors.tertiary.copy(alpha = 0.08f), Color.Transparent)
                    ),
                    CircleShape,
                )
        )
        content()
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(26.dp),
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val base = if (emphasized) colors.primaryContainer.copy(alpha = 0.64f) else colors.surface.copy(alpha = 0.82f)
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.then(clickableModifier),
        shape = shape,
        color = base,
        border = BorderStroke(
            1.dp,
            if (emphasized) colors.primary.copy(alpha = 0.20f) else colors.outlineVariant.copy(alpha = 0.72f),
        ),
        shadowElevation = if (emphasized) 7.dp else 3.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

@Composable
fun GlassIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    if (active) {
                        listOf(colors.primary.copy(alpha = 0.90f), colors.primary.copy(alpha = 0.68f))
                    } else {
                        listOf(colors.surface.copy(alpha = 0.92f), colors.surfaceVariant.copy(alpha = 0.82f))
                    }
                )
            )
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) colors.onPrimary else colors.onSurfaceVariant,
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    positive: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (positive) colors.tertiaryContainer.copy(alpha = 0.82f) else colors.surfaceVariant.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, if (positive) colors.tertiary.copy(alpha = 0.18f) else colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (positive) colors.tertiary else colors.onSurfaceVariant, CircleShape)
            )
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

enum class LuxeNavDestination {
    HOME,
    AI,
    SETTINGS,
}

@Composable
fun LuxeBottomBar(
    selected: LuxeNavDestination,
    homeLabel: String,
    aiLabel: String,
    settingsLabel: String,
    onHome: () -> Unit,
    onAi: () -> Unit,
    onSettings: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(30.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LuxeNavItem(Icons.Outlined.Home, homeLabel, selected == LuxeNavDestination.HOME, onHome)
            LuxeNavItem(Icons.Outlined.AutoAwesome, aiLabel, selected == LuxeNavDestination.AI, onAi)
            LuxeNavItem(Icons.Outlined.Settings, settingsLabel, selected == LuxeNavDestination.SETTINGS, onSettings)
        }
    }
}

@Composable
private fun LuxeNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(23.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

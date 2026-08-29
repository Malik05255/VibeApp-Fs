package com.almi.ai.ui.v7

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AlmiV7Destination { STUDIO, AI, SETTINGS }

@Composable
fun AlmiV7Backdrop(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.primary.copy(alpha = 0.045f), Color.Transparent),
                    center = Offset(size.width * 0.72f, size.height * 0.16f),
                    radius = size.minDimension * 0.88f,
                ),
                center = Offset(size.width * 0.72f, size.height * 0.16f),
                radius = size.minDimension * 0.88f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(scheme.error.copy(alpha = 0.025f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.72f),
                    radius = size.minDimension * 0.66f,
                ),
                center = Offset(size.width * 0.18f, size.height * 0.72f),
                radius = size.minDimension * 0.66f,
            )
        }
        content()
    }
}

@Composable
fun AlmiV7BottomDock(
    selected: AlmiV7Destination,
    language: String,
    onStudio: () -> Unit,
    onAi: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DockItem(
                icon = Icons.Outlined.Checkroom,
                label = if (language == "ar") "الاستوديو" else "Studio",
                selected = selected == AlmiV7Destination.STUDIO,
                onClick = onStudio,
                modifier = Modifier.weight(1f),
            )
            DockItem(
                icon = Icons.Outlined.AutoAwesome,
                label = if (language == "ar") "الذكاء" else "AI",
                selected = selected == AlmiV7Destination.AI,
                onClick = onAi,
                modifier = Modifier.weight(1f),
            )
            DockItem(
                icon = Icons.Outlined.Settings,
                label = if (language == "ar") "الإعدادات" else "Settings",
                selected = selected == AlmiV7Destination.SETTINGS,
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val background = if (selected) scheme.onSurface else Color.Transparent
    val foreground = if (selected) scheme.surface else scheme.onSurfaceVariant

    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) foreground.copy(alpha = 0.12f) else Color.Transparent,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(4.dp).size(18.dp),
                tint = foreground,
            )
        }
        if (selected) {
            Text(
                label,
                modifier = Modifier.padding(start = 5.dp),
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

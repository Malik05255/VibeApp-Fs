package com.almi.ai.ui.v7

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.ui.theme.LocalAlmiUiScale

enum class AlmiV7Destination { STUDIO, AI, SETTINGS }

/** Static spatial backdrop: game-like depth without continuous animation or GPU-heavy blur. */
@Composable
fun AlmiV7Backdrop(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < .15f
    val gradient = if (dark) {
        Brush.linearGradient(
            listOf(Color(0xFF080A0F), Color(0xFF111528), Color(0xFF080B12)),
            start = Offset.Zero,
            end = Offset(1100f, 1900f),
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFF6F4F1), Color(0xFFEDEFFB), Color(0xFFF8F6F2)),
            start = Offset.Zero,
            end = Offset(1100f, 1900f),
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 54.dp, end = 22.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = if (dark) .08f else .06f)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(130.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = if (dark) .05f else .04f)),
        )
        content()
    }
}

/** v9 spatial dock. AI is the central action, while Studio and Control remain one tap away. */
@Composable
fun AlmiV7BottomDock(
    selected: AlmiV7Destination,
    language: String,
    onStudio: () -> Unit,
    onAi: () -> Unit,
    onSettings: () -> Unit,
) {
    val scale = LocalAlmiUiScale.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = (14f * scale).dp, vertical = (7f * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            shape = RoundedCornerShape((27f * scale).dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = (12f * scale).dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = (8f * scale).dp, vertical = (6f * scale).dp),
                horizontalArrangement = Arrangement.spacedBy((5f * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpatialDockItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Checkroom,
                    label = if (language == "ar") "الاستوديو" else "Studio",
                    selected = selected == AlmiV7Destination.STUDIO,
                    emphasized = false,
                    onClick = onStudio,
                )
                SpatialDockItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.AutoAwesome,
                    label = if (language == "ar") "المحرك" else "AI Core",
                    selected = selected == AlmiV7Destination.AI,
                    emphasized = true,
                    onClick = onAi,
                )
                SpatialDockItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Tune,
                    label = if (language == "ar") "التحكم" else "Control",
                    selected = selected == AlmiV7Destination.SETTINGS,
                    emphasized = false,
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun SpatialDockItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scale = LocalAlmiUiScale.current
    val background = when {
        selected -> scheme.primary
        emphasized -> scheme.tertiaryContainer.copy(alpha = .78f)
        else -> Color.Transparent
    }
    val foreground = when {
        selected -> scheme.onPrimary
        emphasized -> scheme.tertiary
        else -> scheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape((21f * scale).dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = (7f * scale).dp, vertical = (9f * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) Color.White.copy(alpha = .10f) else Color.Transparent,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(3.dp).size((18f * scale).dp),
                tint = foreground,
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = (5f * scale).dp),
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

package com.almi.ai.ui.v11

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Tune
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

enum class V11Destination { MIRROR, AVATAR, AI, CONTROL }

/** Quiet editorial stage. No particles, blur or infinite animation. */
@Composable
fun V11Stage(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.red < .15f
    val gradient = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF0B0A09), Color(0xFF11100E), Color(0xFF0C0B0A)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF5F0E9), Color(0xFFF0E8E0), Color(0xFFF7F2EC)))
    }
    Box(Modifier.fillMaxSize().background(gradient)) { content() }
}

@Composable
fun V11Dock(
    selected: V11Destination,
    language: String,
    onMirror: () -> Unit,
    onAvatar: () -> Unit,
    onAi: () -> Unit,
    onControl: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .98f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier.padding(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                DockItem(Modifier.weight(1f), Icons.Outlined.Checkroom, if (language == "ar") "المرآة" else "Mirror", selected == V11Destination.MIRROR, onMirror)
                DockItem(Modifier.weight(1f), Icons.Outlined.PersonOutline, if (language == "ar") "أنا" else "Avatar", selected == V11Destination.AVATAR, onAvatar)
                DockItem(Modifier.weight(1f), Icons.Outlined.AutoAwesome, "AI", selected == V11Destination.AI, onAi)
                DockItem(Modifier.weight(1f), Icons.Outlined.Tune, if (language == "ar") "تحكم" else "Control", selected == V11Destination.CONTROL, onControl)
            }
        }
    }
}

@Composable
private fun DockItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) scheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Surface(shape = CircleShape, color = if (selected) Color.White.copy(alpha = .08f) else Color.Transparent) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(2.dp).size(18.dp),
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
        Text(
            label,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

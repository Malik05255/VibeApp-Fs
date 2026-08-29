package com.almi.ai.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AppThemeMode

/** v9 Control Room — large portals for the systems people actually revisit. */
@Composable
fun SettingsHubScreen(
    viewModel: SettingsViewModel,
    language: String,
    onOpenAi: () -> Unit,
    onOpenBodyLab: () -> Unit,
    onOpenAvatar: () -> Unit,
) {
    val theme by viewModel.themeMode.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()
    val google by viewModel.googleAiStudioSettings.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ALMI / CONTROL ROOM", color = scheme.tertiary, style = MaterialTheme.typography.labelSmall)
            Text(
                if (language == "ar") "مركز التحكم" else "Control Room",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (language == "ar") "الجسم، الشخصية، المحرك والمظهر — بدون قوائم تقنية طويلة." else "Body, character, AI Core and appearance — without a wall of technical menus.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniPortal(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Straighten,
                title = "BODY MAP",
                subtitle = if (language == "ar") "القياسات" else "Measurements",
                active = true,
                onClick = onOpenBodyLab,
            )
            MiniPortal(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PersonOutline,
                title = "AVATAR LAB",
                subtitle = if (language == "ar") "Filament 3D" else "Filament 3D",
                active = false,
                onClick = onOpenAvatar,
            )
        }

        WidePortal(
            icon = Icons.Outlined.AutoAwesome,
            eyebrow = "AI CORE",
            title = if (language == "ar") "المحرك الذكي" else "Intelligence engine",
            subtitle = if (google.connected) {
                if (language == "ar") "Google AI Studio متصل • وبقية المسارات متاحة من نفس المكان." else "Google AI Studio connected • other routes remain available here."
            } else {
                if (language == "ar") "المسار الحالي: ${engineName(aiMode, language)}" else "Current route: ${engineName(aiMode, language)}"
            },
            badge = if (google.connected) "GOOGLE + ${engineShort(aiMode)}" else engineShort(aiMode),
            onClick = onOpenAi,
        )

        Text(if (language == "ar") "الواجهة" else "INTERFACE", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        CompactControl(icon = Icons.Outlined.Language, title = if (language == "ar") "اللغة" else "Language") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("العربية", language == "ar", { viewModel.setLanguage("ar") }, Modifier.weight(1f))
                Choice("English", language == "en", { viewModel.setLanguage("en") }, Modifier.weight(1f))
            }
        }
        CompactControl(icon = Icons.Outlined.Palette, title = if (language == "ar") "الثيم" else "Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemeChoice(AppThemeMode.SYSTEM, theme, if (language == "ar") "النظام" else "Auto", Icons.Outlined.PhoneAndroid, viewModel::setThemeMode, Modifier.weight(1f))
                ThemeChoice(AppThemeMode.LIGHT, theme, if (language == "ar") "Porcelain" else "Porcelain", Icons.Outlined.LightMode, viewModel::setThemeMode, Modifier.weight(1f))
                ThemeChoice(AppThemeMode.DARK, theme, if (language == "ar") "Obsidian" else "Obsidian", Icons.Outlined.DarkMode, viewModel::setThemeMode, Modifier.weight(1f))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = scheme.surface.copy(alpha = .84f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Surface(shape = CircleShape, color = scheme.primary) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(9.dp).size(19.dp), tint = scheme.onPrimary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (language == "ar") "Local-first" else "Local-first", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (language == "ar") "الأفاتار والقياسات والتفضيلات محلية. الشبكة تُستخدم فقط عندما تطلب ميزة AI ذلك." else "Avatar, measurements and preferences stay local. Network access is used only when an AI feature needs it.",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun MiniPortal(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (active) scheme.primary else scheme.surface.copy(alpha = .94f)
    val fg = if (active) scheme.onPrimary else scheme.onSurface
    Surface(
        modifier = modifier.height(156.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = bg,
        border = if (active) null else BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = if (active) 8.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (active) Color.White.copy(alpha = .11f) else scheme.surfaceVariant,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp), tint = fg)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = if (active) fg.copy(alpha = .68f) else scheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WidePortal(
    icon: ImageVector,
    eyebrow: String,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = scheme.tertiaryContainer.copy(alpha = .78f),
        border = BorderStroke(1.dp, scheme.tertiary.copy(alpha = .18f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = scheme.tertiary) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(22.dp), tint = scheme.onTertiary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(eyebrow, color = scheme.tertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(badge, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CompactControl(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface.copy(alpha = .92f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) scheme.primary else scheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ThemeChoice(
    mode: AppThemeMode,
    selected: AppThemeMode,
    label: String,
    icon: ImageVector,
    onClick: (AppThemeMode) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val active = mode == selected
    Surface(
        modifier = modifier.clickable { onClick(mode) },
        shape = RoundedCornerShape(15.dp),
        color = if (active) scheme.primary else scheme.surfaceVariant,
        border = BorderStroke(1.dp, if (active) scheme.primary else scheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant)
            Text(label, color = if (active) scheme.onPrimary else scheme.onSurface, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun engineName(mode: AiMode, language: String): String = when (mode) {
    AiMode.OPENROUTER -> "OpenRouter"
    AiMode.CUSTOM -> if (language == "ar") "واجهة مخصصة" else "Custom API"
    AiMode.FREE_AUTO -> if (language == "ar") "مجاني تلقائي" else "Free Auto"
}

private fun engineShort(mode: AiMode): String = when (mode) {
    AiMode.OPENROUTER -> "OPENROUTER"
    AiMode.CUSTOM -> "CUSTOM"
    AiMode.FREE_AUTO -> "FREE"
}

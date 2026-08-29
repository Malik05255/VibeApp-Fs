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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ALMI / CONTROL", color = scheme.tertiary, style = MaterialTheme.typography.labelSmall)
            Text(if (language == "ar") "مركز التحكم" else "Control", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                if (language == "ar") "أربع قرارات فقط: جسمك، شخصيتك، المحرك، والمظهر." else "Four things only: body, character, engine, appearance.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Straighten,
                overline = "BODY MAP",
                title = if (language == "ar") "القياسات" else "Measurements",
                subtitle = if (language == "ar") "15 نقطة تشريحية" else "15 anatomical points",
                emphasized = true,
                onClick = onOpenBodyLab,
            )
            HeroTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PersonOutline,
                overline = "AVATAR",
                title = if (language == "ar") "شخصيتي" else "My character",
                subtitle = "Filament 3D",
                emphasized = false,
                onClick = onOpenAvatar,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAi),
            shape = RoundedCornerShape(24.dp),
            color = scheme.tertiaryContainer.copy(alpha = .76f),
            border = BorderStroke(1.dp, scheme.tertiary.copy(alpha = .18f)),
        ) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(shape = CircleShape, color = scheme.tertiary) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.padding(10.dp).size(20.dp), tint = scheme.onTertiary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("AI ENGINE", color = scheme.tertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(if (google.connected) "GOOGLE + ${engineShort(aiMode)}" else engineShort(aiMode), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(if (language == "ar") "المحرك الذكي" else "Intelligence engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (google.connected) {
                            if (language == "ar") "Google AI Studio متصل. بقية المزوّدات من نفس المكان." else "Google AI Studio is connected. Other providers live here too."
                        } else {
                            if (language == "ar") "المسار الحالي: ${engineName(aiMode, language)}" else "Current route: ${engineName(aiMode, language)}"
                        },
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Text(if (language == "ar") "الواجهة" else "INTERFACE", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)

        ControlCard(Icons.Outlined.Language, if (language == "ar") "اللغة" else "Language") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Choice("العربية", language == "ar", { viewModel.setLanguage("ar") }, Modifier.weight(1f))
                Choice("English", language == "en", { viewModel.setLanguage("en") }, Modifier.weight(1f))
            }
        }

        ControlCard(Icons.Outlined.PhoneAndroid, if (language == "ar") "المظهر" else "Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemeChoice(AppThemeMode.SYSTEM, theme, if (language == "ar") "تلقائي" else "Auto", Icons.Outlined.PhoneAndroid, viewModel::setThemeMode, Modifier.weight(1f))
                ThemeChoice(AppThemeMode.LIGHT, theme, "Atelier", Icons.Outlined.LightMode, viewModel::setThemeMode, Modifier.weight(1f))
                ThemeChoice(AppThemeMode.DARK, theme, "Noir", Icons.Outlined.DarkMode, viewModel::setThemeMode, Modifier.weight(1f))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface.copy(alpha = .84f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = scheme.primary) {
                    Icon(Icons.Outlined.Lock, null, Modifier.padding(8.dp).size(18.dp), tint = scheme.onPrimary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (language == "ar") "Local-first" else "Local-first", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (language == "ar") "القياسات، الشخصية والتفضيلات محلية. الشبكة فقط عند تشغيل ميزة AI." else "Measurements, character and preferences are local. Network is used only when an AI feature runs.",
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
private fun HeroTile(
    modifier: Modifier,
    icon: ImageVector,
    overline: String,
    title: String,
    subtitle: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (emphasized) scheme.primary else scheme.surface
    val fg = if (emphasized) scheme.onPrimary else scheme.onSurface
    val muted = if (emphasized) scheme.onPrimary.copy(alpha = .62f) else scheme.onSurfaceVariant
    Surface(
        modifier = modifier.height(154.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = bg,
        border = if (emphasized) null else BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = if (emphasized) 7.dp else 0.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = if (emphasized) Color.White.copy(alpha = .09f) else scheme.surfaceVariant) {
                    Icon(icon, null, Modifier.padding(9.dp).size(20.dp), tint = fg)
                }
                Text(overline, color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, color = muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ControlCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), color = scheme.surface, border = BorderStroke(1.dp, scheme.outlineVariant)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(icon, null, Modifier.size(17.dp), tint = scheme.onSurfaceVariant)
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
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primary else scheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Text(label, Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center, color = if (selected) scheme.onPrimary else scheme.onSurface, style = MaterialTheme.typography.labelLarge)
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
        shape = RoundedCornerShape(14.dp),
        color = if (active) scheme.primary else scheme.surfaceVariant,
        border = BorderStroke(1.dp, if (active) scheme.primary else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (active) scheme.onPrimary else scheme.onSurfaceVariant)
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

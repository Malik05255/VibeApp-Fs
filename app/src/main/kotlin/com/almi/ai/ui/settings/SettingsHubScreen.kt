package com.almi.ai.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.ui.components.ConnectionPill
import com.almi.ai.ui.components.DimensionCard
import com.almi.ai.ui.components.Glossy3DIcon

@Composable
fun SettingsHubScreen(
    viewModel: SettingsViewModel,
    language: String,
    onOpenAi: () -> Unit,
) {
    val theme by viewModel.themeMode.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    if (language == "ar") "غرفة التحكم" else "Control room",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "ALMI ECLIPSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ConnectionPill(engineName(aiMode, language))
        }

        DimensionCard {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingTitle(Icons.Outlined.Language, if (language == "ar") "اللغة" else "Language")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (language == "ar") {
                        Choice(true, "العربية", { viewModel.setLanguage("ar") }, Modifier.weight(1f))
                        Choice(false, "English", { viewModel.setLanguage("en") }, Modifier.weight(1f))
                    } else {
                        Choice(true, "English", { viewModel.setLanguage("en") }, Modifier.weight(1f))
                        Choice(false, "العربية", { viewModel.setLanguage("ar") }, Modifier.weight(1f))
                    }
                }
            }
        }

        DimensionCard {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingTitle(Icons.Outlined.DarkMode, if (language == "ar") "المظهر" else "Appearance")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice(AppThemeMode.SYSTEM, theme, if (language == "ar") "تلقائي" else "Auto", Icons.Outlined.Smartphone, viewModel::setThemeMode, Modifier.weight(1f))
                    ThemeChoice(AppThemeMode.LIGHT, theme, if (language == "ar") "فاتح" else "Light", Icons.Outlined.LightMode, viewModel::setThemeMode, Modifier.weight(1f))
                    ThemeChoice(AppThemeMode.DARK, theme, if (language == "ar") "Eclipse" else "Eclipse", Icons.Outlined.DarkMode, viewModel::setThemeMode, Modifier.weight(1f))
                }
            }
        }

        AiEngineCard(
            language = language,
            mode = aiMode,
            onClick = onOpenAi,
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (language == "ar") "مفاتيحك تبقى على جهازك" else "Your keys stay on-device",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (language == "ar") "محفوظة داخل خزنة Android المشفرة" else "Stored in Android's encrypted vault",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingTitle(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        )
                    ),
                    RoundedCornerShape(13.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AiEngineCard(language: String, mode: AiMode, onClick: () -> Unit) {
    DimensionCard(emphasized = true, onClick = onClick) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                        )
                    )
                )
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Glossy3DIcon(Icons.Outlined.AutoAwesome, active = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (language == "ar") "إعدادات الذكاء الاصطناعي" else "AI settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        engineName(mode, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.68f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun engineName(mode: AiMode, language: String): String = when (mode) {
    AiMode.OPENROUTER -> "OpenRouter"
    AiMode.CUSTOM -> if (language == "ar") "API مخصص" else "Custom API"
    AiMode.FREE_AUTO -> if (language == "ar") "ذكاء مجاني" else "Free AI"
}

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(15.dp)) { Text(label, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(15.dp)) { Text(label) }
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
    val active = mode == selected
    if (active) {
        Button(onClick = { onClick(mode) }, modifier = modifier.height(74.dp), shape = RoundedCornerShape(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, maxLines = 1, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        OutlinedButton(onClick = { onClick(mode) }, modifier = modifier.height(74.dp), shape = RoundedCornerShape(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, maxLines = 1)
            }
        }
    }
}

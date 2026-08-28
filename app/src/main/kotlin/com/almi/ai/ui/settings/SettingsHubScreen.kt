package com.almi.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.BuildConfig
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
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ALMI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    if (language == "ar") "الإعدادات" else "Settings",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionPill(
                when (aiMode) {
                    AiMode.OPENROUTER -> "OpenRouter"
                    AiMode.CUSTOM -> if (language == "ar") "مخصص" else "Custom"
                    AiMode.FREE_AUTO -> if (language == "ar") "مجاني" else "Free"
                }
            )
        }

        Text(
            if (language == "ar") "تحكم بسيط" else "Simple controls",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )

        DimensionCard {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Glossy3DIcon(Icons.Outlined.Language)
                    Column(Modifier.weight(1f)) {
                        Text(if (language == "ar") "اللغة" else "Language", fontWeight = FontWeight.Bold)
                        Text(
                            if (language == "ar") "العربية" else "English",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (language == "ar") {
                        Choice(
                            selected = true,
                            label = "العربية",
                            onClick = { viewModel.setLanguage("ar") },
                            modifier = Modifier.weight(1f),
                        )
                        Choice(
                            selected = false,
                            label = "English",
                            onClick = { viewModel.setLanguage("en") },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Choice(
                            selected = true,
                            label = "English",
                            onClick = { viewModel.setLanguage("en") },
                            modifier = Modifier.weight(1f),
                        )
                        Choice(
                            selected = false,
                            label = "العربية",
                            onClick = { viewModel.setLanguage("ar") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        DimensionCard {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (language == "ar") "المظهر" else "Appearance", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice(
                        AppThemeMode.SYSTEM,
                        theme,
                        if (language == "ar") "تلقائي" else "Auto",
                        Icons.Outlined.Smartphone,
                        viewModel::setThemeMode,
                        Modifier.weight(1f),
                    )
                    ThemeChoice(
                        AppThemeMode.LIGHT,
                        theme,
                        if (language == "ar") "فاتح" else "Light",
                        Icons.Outlined.LightMode,
                        viewModel::setThemeMode,
                        Modifier.weight(1f),
                    )
                    ThemeChoice(
                        AppThemeMode.DARK,
                        theme,
                        if (language == "ar") "داكن" else "Dark",
                        Icons.Outlined.DarkMode,
                        viewModel::setThemeMode,
                        Modifier.weight(1f),
                    )
                }
            }
        }

        DimensionCard(emphasized = true, onClick = onOpenAi) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Glossy3DIcon(Icons.Outlined.AutoAwesome, active = true)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (language == "ar") "الذكاء الاصطناعي" else "Artificial Intelligence",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (aiMode) {
                            AiMode.OPENROUTER -> "OpenRouter"
                            AiMode.CUSTOM -> if (language == "ar") "API مخصص" else "Custom API"
                            AiMode.FREE_AUTO -> if (language == "ar") "مجاني" else "Free AI"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }

        DimensionCard {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ALMI", fontWeight = FontWeight.Bold)
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Choice(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(label) }
    }
}

@Composable
private fun ThemeChoice(
    mode: AppThemeMode,
    selected: AppThemeMode,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (AppThemeMode) -> Unit,
    modifier: Modifier,
) {
    val active = mode == selected
    if (active) {
        Button(onClick = { onClick(mode) }, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.height(3.dp))
                Text(label, maxLines = 1)
            }
        }
    } else {
        OutlinedButton(onClick = { onClick(mode) }, modifier = modifier, shape = RoundedCornerShape(14.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.height(3.dp))
                Text(label, maxLines = 1)
            }
        }
    }
}
package com.almi.ai.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.ui.components.ConnectionPill
import com.almi.ai.ui.components.DimensionCard

@Composable
fun SettingsHubScreen(
    viewModel: SettingsViewModel,
    language: String,
    onOpenAi: () -> Unit,
    onOpenBodyLab: () -> Unit,
) {
    val theme by viewModel.themeMode.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()
    val scheme = MaterialTheme.colorScheme

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
                    if (language == "ar") "النظام" else "System",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "ALMI / PRECISION ATELIER",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }
            ConnectionPill(engineName(aiMode, language))
        }

        DimensionCard {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("LANG", if (language == "ar") "اللغة" else "Language")
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
                SectionTitle("THEME", if (language == "ar") "المظهر" else "Appearance")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice(
                        AppThemeMode.SYSTEM,
                        theme,
                        if (language == "ar") "تلقائي" else "Auto",
                        viewModel::setThemeMode,
                        Modifier.weight(1f),
                    )
                    ThemeChoice(
                        AppThemeMode.LIGHT,
                        theme,
                        if (language == "ar") "فاتح" else "Light",
                        viewModel::setThemeMode,
                        Modifier.weight(1f),
                    )
                    ThemeChoice(
                        AppThemeMode.DARK,
                        theme,
                        if (language == "ar") "داكن" else "Dark",
                        viewModel::setThemeMode,
                        Modifier.weight(1f),
                    )
                }
            }
        }

        BodyLabCard(language = language, onClick = onOpenBodyLab)
        AiEngineCard(language = language, mode = aiMode, onClick = onOpenAi)

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = scheme.surface.copy(alpha = 0.78f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(7.dp).background(scheme.tertiary, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (language == "ar") "المفاتيح تبقى على جهازك" else "Keys remain on-device",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (language == "ar") "محفوظة داخل خزنة Android المشفرة" else "Stored in Android's encrypted vault",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text("SECURE", style = MaterialTheme.typography.labelSmall, color = scheme.tertiary, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(code: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(code, style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
        Box(Modifier.width(24.dp).height(1.dp).background(scheme.outline))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BodyLabCard(language: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DimensionCard(emphasized = true, onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("BODY / PROFILE", style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
            Text(
                if (language == "ar") "Body Lab — القياسات والمجسم" else "Body Lab — measurements & model",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (language == "ar") {
                    "عدّل قياساتك، أدر المجسم 360°، وأعد قياس أي جزء في أي وقت."
                } else {
                    "Edit measurements, rotate the model 360°, and re-measure any area at any time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                if (language == "ar") "فتح BODY LAB →" else "OPEN BODY LAB →",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun AiEngineCard(language: String, mode: AiMode, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = scheme.primary) {
                Text(
                    "AI",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (language == "ar") "محرك الذكاء الاصطناعي" else "AI engine",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    engineName(mode, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text("→", color = scheme.primary, fontWeight = FontWeight.Black)
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
        Button(onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(10.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(10.dp)) {
            Text(label)
        }
    }
}

@Composable
private fun ThemeChoice(
    mode: AppThemeMode,
    selected: AppThemeMode,
    label: String,
    onClick: (AppThemeMode) -> Unit,
    modifier: Modifier,
) {
    val active = mode == selected
    if (active) {
        Button(onClick = { onClick(mode) }, modifier = modifier.height(50.dp), shape = RoundedCornerShape(10.dp)) {
            Text(label, maxLines = 1, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = { onClick(mode) }, modifier = modifier.height(50.dp), shape = RoundedCornerShape(10.dp)) {
            Text(label, maxLines = 1)
        }
    }
}

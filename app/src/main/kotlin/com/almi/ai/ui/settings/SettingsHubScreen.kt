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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SettingsBrightness
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.ui.components.ConnectionPill

/** ALMI v7 system control center. No legacy card-grid visual language. */
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("ALMI / SYSTEM", style = MaterialTheme.typography.labelMedium, color = scheme.error, fontWeight = FontWeight.Black)
                Text(
                    if (language == "ar") "مركز التحكم" else "Control center",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            ConnectionPill(engineName(aiMode, language))
        }

        Text(
            if (language == "ar") {
                "كل ما يخص تجربتك الشخصية في مكان واحد."
            } else {
                "Everything that shapes your personal ALMI experience, in one place."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        V7SettingSection(
            eyebrow = "LANGUAGE",
            icon = Icons.Outlined.Language,
            title = if (language == "ar") "لغة التطبيق" else "App language",
            subtitle = if (language == "ar") "تتغير الواجهة كاملة مباشرة" else "Changes the entire interface immediately",
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(
                    selected = language == "ar",
                    label = "العربية",
                    onClick = { viewModel.setLanguage("ar") },
                    modifier = Modifier.weight(1f),
                )
                Choice(
                    selected = language == "en",
                    label = "English",
                    onClick = { viewModel.setLanguage("en") },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        V7SettingSection(
            eyebrow = "APPEARANCE",
            icon = Icons.Outlined.SettingsBrightness,
            title = if (language == "ar") "المظهر" else "Appearance",
            subtitle = if (language == "ar") "فاتح أو داكن أو يتبع جهازك" else "Light, dark, or follow your device",
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemeChoice(
                    mode = AppThemeMode.SYSTEM,
                    selected = theme,
                    label = if (language == "ar") "تلقائي" else "Auto",
                    icon = Icons.Outlined.PhoneAndroid,
                    onClick = viewModel::setThemeMode,
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    mode = AppThemeMode.LIGHT,
                    selected = theme,
                    label = if (language == "ar") "فاتح" else "Light",
                    icon = Icons.Outlined.LightMode,
                    onClick = viewModel::setThemeMode,
                    modifier = Modifier.weight(1f),
                )
                ThemeChoice(
                    mode = AppThemeMode.DARK,
                    selected = theme,
                    label = if (language == "ar") "داكن" else "Dark",
                    icon = Icons.Outlined.DarkMode,
                    onClick = viewModel::setThemeMode,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FeaturePortal(
            eyebrow = "DIGITAL TWIN",
            icon = Icons.Outlined.PersonOutline,
            title = if (language == "ar") "جسمي ثلاثي الأبعاد" else "My 3D body",
            subtitle = if (language == "ar") {
                "راجع القياسات، أعد قياس أي منطقة، وادخل تجربة المجسم الواقعي."
            } else {
                "Review measurements, remeasure any region, and reopen your real 3D body."
            },
            action = if (language == "ar") "فتح التوأم الرقمي" else "Open digital twin",
            emphasized = true,
            onClick = onOpenBodyLab,
        )

        FeaturePortal(
            eyebrow = "AI ENGINE",
            icon = Icons.Outlined.AutoAwesome,
            title = if (language == "ar") "محرك الذكاء الاصطناعي" else "AI engine",
            subtitle = if (language == "ar") {
                "المحرك الحالي: ${engineName(aiMode, language)}. غيّر المزوّد والنموذج والمفاتيح من هنا."
            } else {
                "Current engine: ${engineName(aiMode, language)}. Change provider, model, and keys here."
            },
            action = if (language == "ar") "إدارة الذكاء الاصطناعي" else "Manage AI",
            emphasized = false,
            onClick = onOpenAi,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceContainerHigh.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Surface(shape = CircleShape, color = scheme.onSurface) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(21.dp),
                        tint = scheme.surface,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (language == "ar") "خصوصية محلية" else "On-device privacy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (language == "ar") {
                            "مفاتيح API تحفظ في التخزين المشفر على جهازك. صور التجربة لا تُرسل إلا عند تنفيذ التوليد."
                        } else {
                            "API keys stay in encrypted device storage. Try-on images are only sent when you run generation."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Text("LOCAL", style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun V7SettingSection(
    eyebrow: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(shape = RoundedCornerShape(15.dp), color = scheme.surfaceContainerHighest) {
                    Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(21.dp), tint = scheme.onSurface)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = scheme.error, fontWeight = FontWeight.Black)
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun FeaturePortal(
    eyebrow: String,
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = if (emphasized) scheme.onSurface else scheme.surface,
        border = if (emphasized) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (emphasized) scheme.surface.copy(alpha = 0.12f) else scheme.surfaceContainerHighest,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                        tint = if (emphasized) scheme.surface else scheme.onSurface,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        eyebrow,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (emphasized) scheme.errorContainer else scheme.error,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (emphasized) scheme.surface else scheme.onSurface,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasized) scheme.surface.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
            )
            Text(
                "$action  →",
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) scheme.surface else scheme.primary,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun engineName(mode: AiMode, language: String): String = when (mode) {
    AiMode.OPENROUTER -> "OpenRouter"
    AiMode.CUSTOM -> if (language == "ar") "API مخصص" else "Custom API"
    AiMode.FREE_AUTO -> if (language == "ar") "ذكاء مجاني" else "Free AI"
}

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Text(label)
        }
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
        Button(
            onClick = { onClick(mode) },
            modifier = modifier.height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Text(label, modifier = Modifier.padding(start = 5.dp), maxLines = 1, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = { onClick(mode) },
            modifier = modifier.height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Text(label, modifier = Modifier.padding(start = 5.dp), maxLines = 1)
        }
    }
}

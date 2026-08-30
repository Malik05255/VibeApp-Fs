package com.almi.ai.ui.v12

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.ui.settings.SettingsViewModel
import com.almi.ai.update.AlmiUpdateManagementDialog
import com.almi.ai.update.AlmiUpdateManager
import kotlinx.coroutines.launch

@Composable
internal fun V12FutureControlScreen(
    viewModel: SettingsViewModel,
    updateManager: AlmiUpdateManager,
    language: String,
    bodyReady: Boolean,
    avatarReady: Boolean,
    onBack: () -> Unit,
    onBody: () -> Unit,
    onAvatar: () -> Unit,
    onAi: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val theme by viewModel.themeMode.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()
    val google by viewModel.googleAiStudioSettings.collectAsState()
    val scope = rememberCoroutineScope()
    var showUpdateManagement by remember { mutableStateOf(false) }

    val aiStatus = if (google.active) {
        "Google AI Studio"
    } else {
        when (aiMode) {
            AiMode.OPENROUTER -> "OpenRouter"
            AiMode.CUSTOM -> "Custom API"
            AiMode.FREE_AUTO -> if (language == "ar") "الوضع المجاني" else "Free Auto"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.primaryContainer.copy(alpha = .18f),
                        scheme.background,
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "ALMI / CONTROL",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (language == "ar") "الإعدادات" else "Settings",
                        color = scheme.onBackground,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
                Surface(
                    modifier = Modifier.size(46.dp).clickable(onClick = onBack),
                    shape = CircleShape,
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("×", color = scheme.onSurface, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel(if (language == "ar") "نظامك" else "Your system")
            Spacer(Modifier.height(9.dp))

            ControlCard(
                glyph = V12GlyphType.BODY,
                title = if (language == "ar") "قياسات الجسم" else "Body profile",
                subtitle = if (bodyReady) {
                    if (language == "ar") "القياسات جاهزة" else "Measurements ready"
                } else {
                    if (language == "ar") "يحتاج إلى إعداد" else "Setup required"
                },
                accent = scheme.tertiary,
                onClick = onBody,
            )
            Spacer(Modifier.height(8.dp))
            ControlCard(
                glyph = V12GlyphType.AVATAR,
                title = if (language == "ar") "النسخة الرقمية" else "Digital twin",
                subtitle = if (avatarReady) {
                    if (language == "ar") "الهوية مرتبطة" else "Identity linked"
                } else {
                    if (language == "ar") "أنشئ الهوية" else "Create identity"
                },
                accent = scheme.secondary,
                onClick = onAvatar,
            )
            Spacer(Modifier.height(8.dp))
            ControlCard(
                glyph = V12GlyphType.AI,
                title = if (language == "ar") "محرك الذكاء" else "AI engine",
                subtitle = aiStatus,
                accent = scheme.primary,
                onClick = onAi,
            )
            Spacer(Modifier.height(8.dp))
            ControlCard(
                glyph = V12GlyphType.CONTROL,
                title = if (language == "ar") "إدارة التحديث" else "Update management",
                subtitle = if (language == "ar") "أحدث إصدار فقط • تحديث فرق" else "Latest only • delta updates",
                accent = scheme.primary,
                onClick = { showUpdateManagement = true },
            )

            Spacer(Modifier.height(28.dp))
            SectionLabel(if (language == "ar") "التفضيلات" else "Preferences")
            Spacer(Modifier.height(9.dp))

            PreferencePanel(
                title = if (language == "ar") "اللغة" else "Language",
                subtitle = if (language == "ar") "يتغير اتجاه الواجهة فورًا" else "Layout direction changes instantly",
            ) {
                ChoiceChip(
                    label = "العربية",
                    active = language == "ar",
                    accent = scheme.primary,
                ) { viewModel.setLanguage("ar") }
                ChoiceChip(
                    label = "English",
                    active = language == "en",
                    accent = scheme.primary,
                ) { viewModel.setLanguage("en") }
            }

            Spacer(Modifier.height(10.dp))

            PreferencePanel(
                title = if (language == "ar") "المظهر" else "Appearance",
                subtitle = if (language == "ar") "نفس الهوية البصرية في الوضعين" else "One visual identity across light and dark",
            ) {
                ChoiceChip(
                    label = if (language == "ar") "النظام" else "System",
                    active = theme == AppThemeMode.SYSTEM,
                    accent = scheme.tertiary,
                ) { viewModel.setThemeMode(AppThemeMode.SYSTEM) }
                ChoiceChip(
                    label = if (language == "ar") "فاتح" else "Light",
                    active = theme == AppThemeMode.LIGHT,
                    accent = scheme.primary,
                ) { viewModel.setThemeMode(AppThemeMode.LIGHT) }
                ChoiceChip(
                    label = if (language == "ar") "داكن" else "Dark",
                    active = theme == AppThemeMode.DARK,
                    accent = scheme.secondary,
                ) { viewModel.setThemeMode(AppThemeMode.DARK) }
            }

            Spacer(Modifier.height(28.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = scheme.surfaceVariant.copy(alpha = .45f),
                border = BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("ALMI 12", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                        Text(
                            if (language == "ar") "نظام محلي أولًا" else "Local-first system",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Box(Modifier.size(8.dp).background(scheme.tertiary, CircleShape))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showUpdateManagement) {
        AlmiUpdateManagementDialog(
            language = language,
            onCheckLatest = {
                showUpdateManagement = false
                scope.launch { updateManager.check(manual = true) }
            },
            onRollback = {
                showUpdateManagement = false
                scope.launch { updateManager.rollbackPrevious() }
            },
            onClose = { showUpdateManagement = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun ControlCard(
    glyph: V12GlyphType,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .8f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = accent.copy(alpha = .11f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(glyph, accent, Modifier.size(23.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(subtitle, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PreferencePanel(
    title: String,
    subtitle: String,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .8f)),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = scheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(subtitle, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun RowScope.ChoiceChip(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.weight(1f).height(44.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (active) accent.copy(alpha = .12f) else scheme.surfaceVariant.copy(alpha = .45f),
        border = BorderStroke(1.dp, if (active) accent.copy(alpha = .55f) else scheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (active) accent else scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

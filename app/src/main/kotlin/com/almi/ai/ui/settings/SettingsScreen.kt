package com.almi.ai.ui.settings

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.BuildConfig
import com.almi.ai.R
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.ui.components.AlmiWordmark
import com.almi.ai.ui.components.GlassIconTile
import com.almi.ai.ui.components.GlassSurface
import com.almi.ai.ui.components.LuxeBackdrop
import com.almi.ai.ui.components.LuxeBottomBar
import com.almi.ai.ui.components.LuxeNavDestination
import com.almi.ai.ui.components.StatusPill

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenHome: () -> Unit,
) {
    val language by viewModel.language.collectAsState()
    val theme by viewModel.themeMode.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()

    LuxeBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { SettingsTopHeader(onBack) },
            bottomBar = {
                LuxeBottomBar(
                    selected = LuxeNavDestination.SETTINGS,
                    homeLabel = stringResource(R.string.luxe_nav_home),
                    aiLabel = stringResource(R.string.luxe_nav_ai),
                    settingsLabel = stringResource(R.string.luxe_nav_settings),
                    onHome = onOpenHome,
                    onAi = onOpenAiSettings,
                    onSettings = {},
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.luxe_settings_title), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.luxe_settings_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                PreferencesPanel(
                    language = language,
                    theme = theme,
                    setLanguage = viewModel::setLanguage,
                    setThemeMode = viewModel::setThemeMode,
                )

                AiEnginePanel(
                    mode = aiMode,
                    onOpen = onOpenAiSettings,
                )

                AboutPanel()
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SettingsTopHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AlmiWordmark(compact = true)
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(999.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp, vertical = 6.dp),
        ) {
            Text(stringResource(R.string.luxe_nav_home), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PreferencesPanel(
    language: String,
    theme: AppThemeMode,
    setLanguage: (String) -> Unit,
    setThemeMode: (AppThemeMode) -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingHeader(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_v2_language),
                subtitle = stringResource(R.string.settings_v2_language_hint),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ChoiceButton(
                    selected = language == "ar",
                    label = stringResource(R.string.settings_v2_arabic),
                    onClick = { setLanguage("ar") },
                    modifier = Modifier.weight(1f),
                )
                ChoiceButton(
                    selected = language == "en",
                    label = stringResource(R.string.settings_v2_english),
                    onClick = { setLanguage("en") },
                    modifier = Modifier.weight(1f),
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))

            SettingHeader(
                icon = Icons.Outlined.SettingsSuggest,
                title = stringResource(R.string.settings_v2_appearance),
                subtitle = stringResource(R.string.settings_v2_appearance_hint),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemeButton(
                    mode = AppThemeMode.SYSTEM,
                    selected = theme,
                    label = stringResource(R.string.settings_v2_system),
                    icon = Icons.Outlined.Smartphone,
                    onClick = setThemeMode,
                    modifier = Modifier.weight(1f),
                )
                ThemeButton(
                    mode = AppThemeMode.LIGHT,
                    selected = theme,
                    label = stringResource(R.string.settings_v2_light),
                    icon = Icons.Outlined.LightMode,
                    onClick = setThemeMode,
                    modifier = Modifier.weight(1f),
                )
                ThemeButton(
                    mode = AppThemeMode.DARK,
                    selected = theme,
                    label = stringResource(R.string.settings_v2_dark),
                    icon = Icons.Outlined.DarkMode,
                    onClick = setThemeMode,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassIconTile(icon = icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AiEnginePanel(
    mode: AiMode,
    onOpen: () -> Unit,
) {
    val active = when (mode) {
        AiMode.OPENROUTER -> stringResource(R.string.luxe_engine_openrouter)
        AiMode.CUSTOM -> stringResource(R.string.luxe_engine_custom)
        AiMode.FREE_AUTO -> stringResource(R.string.luxe_engine_free)
    }

    GlassSurface(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassIconTile(Icons.Outlined.AutoAwesome, active = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.luxe_ai_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.luxe_ai_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(active)
            }

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                Text(stringResource(R.string.settings_v2_open_ai), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AboutPanel() {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconTile(Icons.Outlined.Info)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_v2_about), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_v2_about_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_v2_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(15.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(15.dp)) { Text(label) }
    }
}

@Composable
private fun ThemeButton(
    mode: AppThemeMode,
    selected: AppThemeMode,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = mode == selected
    if (isSelected) {
        FilledTonalButton(
            onClick = { onClick(mode) },
            modifier = modifier.height(72.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        OutlinedButton(
            onClick = { onClick(mode) },
            modifier = modifier.height(72.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

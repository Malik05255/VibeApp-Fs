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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.BuildConfig
import com.almi.ai.R
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.ui.components.AlmiWordmark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val language by viewModel.language.collectAsState()
    val theme by viewModel.themeMode.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { AlmiWordmark(compact = true) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_v2_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text(
                    stringResource(R.string.settings_v2_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingSection(
                icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                title = stringResource(R.string.settings_v2_language),
                subtitle = stringResource(R.string.settings_v2_language_hint),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceButton(
                        selected = language == "ar",
                        label = stringResource(R.string.settings_v2_arabic),
                        onClick = { viewModel.setLanguage("ar") },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceButton(
                        selected = language == "en",
                        label = stringResource(R.string.settings_v2_english),
                        onClick = { viewModel.setLanguage("en") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SettingSection(
                icon = { Icon(Icons.Outlined.SettingsSuggest, contentDescription = null) },
                title = stringResource(R.string.settings_v2_appearance),
                subtitle = stringResource(R.string.settings_v2_appearance_hint),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeButton(
                        mode = AppThemeMode.SYSTEM,
                        selected = theme,
                        label = stringResource(R.string.settings_v2_system),
                        icon = { Icon(Icons.Outlined.Smartphone, contentDescription = null) },
                        onClick = viewModel::setThemeMode,
                        modifier = Modifier.weight(1f),
                    )
                    ThemeButton(
                        mode = AppThemeMode.LIGHT,
                        selected = theme,
                        label = stringResource(R.string.settings_v2_light),
                        icon = { Icon(Icons.Outlined.LightMode, contentDescription = null) },
                        onClick = viewModel::setThemeMode,
                        modifier = Modifier.weight(1f),
                    )
                    ThemeButton(
                        mode = AppThemeMode.DARK,
                        selected = theme,
                        label = stringResource(R.string.settings_v2_dark),
                        icon = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                        onClick = viewModel::setThemeMode,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_v2_ai), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(R.string.settings_v2_ai_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onOpenAiSettings,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(stringResource(R.string.settings_v2_open_ai), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                    }
                }
            }

            SettingSection(
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                title = stringResource(R.string.settings_v2_about),
                subtitle = stringResource(R.string.settings_v2_about_hint),
            ) {
                Text(
                    stringResource(R.string.settings_v2_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingSection(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(10.dp)) { icon() }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
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
        FilledTonalButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(label) }
    }
}

@Composable
private fun ThemeButton(
    mode: AppThemeMode,
    selected: AppThemeMode,
    label: String,
    icon: @Composable () -> Unit,
    onClick: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = mode == selected
    if (isSelected) {
        FilledTonalButton(
            onClick = { onClick(mode) },
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                icon()
                Text(label, maxLines = 1)
            }
        }
    } else {
        OutlinedButton(
            onClick = { onClick(mode) },
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                icon()
                Text(label, maxLines = 1)
            }
        }
    }
}

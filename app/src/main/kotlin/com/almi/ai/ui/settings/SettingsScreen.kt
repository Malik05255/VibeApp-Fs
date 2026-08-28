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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.almi.ai.BuildConfig
import com.almi.ai.R
import com.almi.ai.data.preferences.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val language by viewModel.language.collectAsState()
    val theme by viewModel.themeMode.collectAsState()
    val savedApiKey by viewModel.apiKey.collectAsState()
    var apiInput by remember { mutableStateOf("") }
    var savedNow by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard(
                icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_hint),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceButton(
                        selected = language == "ar",
                        text = stringResource(R.string.language_arabic),
                        onClick = { viewModel.setLanguage("ar") },
                        modifier = Modifier.weight(1f),
                    )
                    ChoiceButton(
                        selected = language == "en",
                        text = stringResource(R.string.language_english),
                        onClick = { viewModel.setLanguage("en") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SettingsCard(
                icon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_appearance_hint),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice(AppThemeMode.SYSTEM, theme, stringResource(R.string.theme_system), viewModel::setThemeMode)
                    ThemeChoice(AppThemeMode.LIGHT, theme, stringResource(R.string.theme_light), viewModel::setThemeMode)
                    ThemeChoice(AppThemeMode.DARK, theme, stringResource(R.string.theme_dark), viewModel::setThemeMode)
                }
            }

            SettingsCard(
                icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                title = stringResource(R.string.settings_api_title),
                subtitle = stringResource(R.string.settings_api_hint),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (savedApiKey.isBlank()) Icons.Outlined.Key else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (savedApiKey.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (savedApiKey.isBlank()) stringResource(R.string.api_not_configured)
                        else stringResource(R.string.api_configured),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = apiInput,
                    onValueChange = { apiInput = it; savedNow = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_key_label)) },
                    placeholder = { Text("sk-or-v1-…") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        if (apiInput.isNotBlank()) {
                            viewModel.saveApiKey(apiInput)
                            apiInput = ""
                            savedNow = true
                        }
                    },
                    enabled = apiInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.api_save))
                }
                if (savedNow) {
                    Text(stringResource(R.string.api_saved), color = MaterialTheme.colorScheme.primary)
                }
                if (savedApiKey.isNotBlank()) {
                    OutlinedButton(onClick = viewModel::clearApiKey, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Text(stringResource(R.string.api_clear), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text("OpenRouter", style = MaterialTheme.typography.labelLarge)
                Text(
                    "openai/gpt-image-1  •  bytedance/seedance-2.0-fast",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(
                icon = { Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null) },
                title = stringResource(R.string.settings_updates),
                subtitle = stringResource(R.string.settings_updates_hint),
            ) {
                Text(stringResource(R.string.settings_updates_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${BuildConfig.APPLICATION_ID}  •  ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                icon()
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        FilledTonalButton(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun ThemeChoice(
    mode: AppThemeMode,
    selected: AppThemeMode,
    text: String,
    onClick: (AppThemeMode) -> Unit,
) {
    ChoiceButton(
        selected = mode == selected,
        text = text,
        onClick = { onClick(mode) },
        modifier = Modifier.fillMaxWidth(),
    )
}

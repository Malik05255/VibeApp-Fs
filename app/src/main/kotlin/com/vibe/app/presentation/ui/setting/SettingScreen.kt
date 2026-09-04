package com.vibe.app.presentation.ui.setting

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.data.model.ThemeMode
import com.vibe.app.presentation.common.LocalThemeMode
import com.vibe.app.presentation.common.LocalThemeViewModel
import com.vibe.app.presentation.common.RadioItem
import com.vibe.app.presentation.common.SettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModelV2 = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onNavigateToProjectSettings: () -> Unit,
    onNavigateToAiProviderSettings: () -> Unit,
    onNavigateToGitHub: () -> Unit,
    onLogout: () -> Unit,
) {
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val debugMode by settingViewModel.debugMode.collectAsStateWithLifecycle()
    val currentLanguage by languageViewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val switchedHint = stringResource(R.string.switched_platform_hint)
    val lifecycleOwner = LocalLifecycleOwner.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingViewModel.switchedPlatformEvent.collect { name ->
            Toast.makeText(context, switchedHint.format(name), Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) settingViewModel.fetchPlatforms()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionTitle(stringResource(R.string.theme_settings))
            SettingsSectionCard {
                SettingItem(
                    title = stringResource(R.string.language),
                    description = if (currentLanguage == "ar") {
                        stringResource(R.string.arabic)
                    } else {
                        stringResource(R.string.english)
                    },
                    onItemClick = { showLanguageDialog = true },
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                SettingsSectionDivider()
                SettingItem(
                    title = stringResource(R.string.theme_settings),
                    description = stringResource(R.string.theme_description),
                    onItemClick = settingViewModel::openThemeDialog,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.settings_section_app))
            SettingsSectionCard {
                SettingItem(
                    title = stringResource(R.string.project_settings_title),
                    description = null,
                    onItemClick = onNavigateToProjectSettings,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                SettingsSectionDivider()
                SettingItem(
                    title = stringResource(R.string.ai_provider_settings_title),
                    description = null,
                    onItemClick = onNavigateToAiProviderSettings,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                SettingsSectionDivider()
                SettingItem(
                    title = stringResource(R.string.github),
                    description = null,
                    onItemClick = onNavigateToGitHub,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.debug_log))
            SettingsSectionCard {
                DebugModeSetting(
                    isEnabled = debugMode,
                    onToggle = settingViewModel::toggleDebugMode,
                )
            }

            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(stringResource(R.string.logout), fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showLanguageDialog) {
        LanguageSettingDialog(
            languageViewModel = languageViewModel,
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (dialogState.isThemeDialogOpen) ThemeSettingDialog(settingViewModel = settingViewModel)

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                ) {
                    Text(
                        stringResource(R.string.logout),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun ProjectSelectionDialog(
    state: ProjectBackupState,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = if (state.mode == ProjectBackupMode.RESTORE) {
        stringResource(R.string.project_select_restore_title)
    } else {
        stringResource(R.string.project_select_sync_title)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.availableProjects.forEach { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(project.projectId) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = project.projectId in state.selectedProjectIds,
                            onCheckedChange = { onToggle(project.projectId) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.name, fontWeight = FontWeight.Medium)
                            Text(
                                project.projectId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.availableProjects.isNotEmpty()) {
                    TextButton(onClick = onSelectAll) {
                        Text(stringResource(R.string.select_all))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.selectedProjectIds.isNotEmpty(),
            ) {
                Text(
                    if (state.mode == ProjectBackupMode.RESTORE) {
                        stringResource(R.string.restore_action)
                    } else {
                        stringResource(R.string.sync_action)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun ProjectBackupProgressDialog(
    state: ProjectBackupState,
    onDismiss: () -> Unit,
) {
    val title = when {
        state.error != null -> stringResource(R.string.operation_failed_title)
        state.completed && state.mode == ProjectBackupMode.RESTORE -> stringResource(R.string.restore_completed_title)
        state.completed -> stringResource(R.string.sync_completed_title)
        state.mode == ProjectBackupMode.RESTORE -> stringResource(R.string.restore_projects_title)
        else -> stringResource(R.string.sync_projects_title)
    }
    AlertDialog(
        onDismissRequest = { if (!state.isRunning) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.error ?: state.message.orEmpty())
                if (state.isRunning || state.completed) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${state.progress}%", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            if (!state.isRunning) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close_action))
                }
            }
        },
    )
}

@Composable
private fun LanguageSettingDialog(
    languageViewModel: LanguageViewModel,
    onDismiss: () -> Unit,
) {
    val currentLanguage by languageViewModel.language.collectAsStateWithLifecycle()
    var selectedLanguage by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                RadioItem(
                    value = "ar",
                    selected = selectedLanguage == "ar",
                    title = stringResource(R.string.arabic),
                    description = null,
                ) { selectedLanguage = it }
                RadioItem(
                    value = "en",
                    selected = selectedLanguage == "en",
                    title = stringResource(R.string.english),
                    description = null,
                ) { selectedLanguage = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    languageViewModel.setLanguage(selectedLanguage)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
    )
}

@Composable
private fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsSectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun DebugModeSetting(
    isEnabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Outlined.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.debug_log),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Switch(checked = isEnabled, onCheckedChange = { onToggle() })
    }
}

@Composable
fun ThemeSettingDialog(settingViewModel: SettingViewModelV2) {
    val currentTheme = LocalThemeMode.current
    val themeViewModel = LocalThemeViewModel.current
    var selectedTheme by remember(currentTheme) { mutableStateOf(currentTheme) }

    AlertDialog(
        onDismissRequest = settingViewModel::closeThemeDialog,
        title = { Text(stringResource(R.string.theme_settings)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    val modeKey = mode.name
                    RadioItem(
                        value = modeKey,
                        selected = selectedTheme == mode,
                        title = when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.system_default)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                        },
                        description = null,
                    ) { selectedKey ->
                        selectedTheme = ThemeMode.valueOf(selectedKey)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    themeViewModel.updateThemeMode(selectedTheme)
                    settingViewModel.closeThemeDialog()
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = settingViewModel::closeThemeDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
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
import androidx.compose.ui.graphics.Color
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
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ThemeMode
import com.vibe.app.presentation.common.LocalThemeMode
import com.vibe.app.presentation.common.LocalThemeViewModel
import com.vibe.app.presentation.common.RadioItem
import com.vibe.app.presentation.common.SettingItem
import com.vibe.app.util.getClientTypeDisplayName
import com.vibe.app.util.getThemeModeTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModelV2 = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToPlatformSetting: (String) -> Unit,
    onNavigateToGitHub: () -> Unit,
    onLogout: () -> Unit,
) {
    val platformState by settingViewModel.platformState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val debugMode by settingViewModel.debugMode.collectAsStateWithLifecycle()
    val currentLanguage by languageViewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val switchedHint = stringResource(R.string.switched_platform_hint)
    val lifecycleOwner = LocalLifecycleOwner.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingViewModel.switchedPlatformEvent.collect { name ->
            Toast.makeText(context, switchedHint.format(name), Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingViewModel.fetchPlatforms()
            }
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
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                    onItemClick = settingViewModel::openThemeDialog,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Language,
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
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.api_model))
            SettingsSectionCard {
                platformState.forEachIndexed { index, platform ->
                    PlatformItem(
                        platform = platform,
                        onItemClick = { onNavigateToPlatformSetting(platform.uid) },
                    )
                    if (index < platformState.lastIndex) {
                        SettingsSectionDivider()
                    }
                }

                if (platformState.isNotEmpty()) {
                    SettingsSectionDivider()
                }

                SettingItem(
                    title = stringResource(R.string.add_platform),
                    description = stringResource(R.string.add_platform_description),
                    onItemClick = onNavigateToAddPlatform,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            SettingsSectionTitle(stringResource(R.string.connected_accounts))
            SettingsSectionCard {
                SettingItem(
                    title = stringResource(R.string.github_integration),
                    description = stringResource(R.string.github_integration_description),
                    onItemClick = onNavigateToGitHub,
                    showLeadingIcon = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Code,
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
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.Start),
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

    if (dialogState.isThemeDialogOpen) {
        ThemeSettingDialog(
            settingViewModel = settingViewModel,
            languageViewModel = languageViewModel,
        )
    }

    if (dialogState.isDeleteDialogOpen) {
        DeletePlatformDialog(settingViewModel = settingViewModel)
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
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
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
    )
}

@Composable
private fun SettingsSectionCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
fun PlatformItem(
    platform: PlatformV2,
    onItemClick: () -> Unit,
) {
    SettingItem(
        title = platform.name,
        description = "${getClientTypeDisplayName(platform.compatibleType)} • " +
            if (platform.enabled) {
                stringResource(R.string.enabled)
            } else {
                stringResource(R.string.disabled)
            },
        onItemClick = onItemClick,
        showLeadingIcon = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = if (platform.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
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
            imageVector = Icons.Outlined.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.debug_log),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.debug_log_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
        )
    }
}

@Composable
fun ThemeSettingDialog(
    settingViewModel: SettingViewModelV2 = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
) {
    val themeViewModel = LocalThemeViewModel.current
    val currentLanguage by languageViewModel.language.collectAsStateWithLifecycle()
    var selectedLanguage by remember(currentLanguage) {
        mutableStateOf(currentLanguage)
    }

    AlertDialog(
        onDismissRequest = settingViewModel::closeThemeDialog,
        title = {
            Text(
                text = stringResource(R.string.theme_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                RadioItem(
                    title = stringResource(R.string.arabic),
                    description = null,
                    value = "ar",
                    selected = selectedLanguage == "ar",
                ) { selectedLanguage = "ar" }
                RadioItem(
                    title = stringResource(R.string.english),
                    description = null,
                    value = "en",
                    selected = selectedLanguage == "en",
                ) { selectedLanguage = "en" }

                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.dark_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { theme ->
                    RadioItem(
                        title = getThemeModeTitle(theme),
                        description = null,
                        value = theme.name,
                        selected = LocalThemeMode.current == theme,
                    ) {
                        themeViewModel.updateThemeMode(theme)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    languageViewModel.setLanguage(selectedLanguage)
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

@Composable
fun DeletePlatformDialog(
    settingViewModel: SettingViewModelV2 = hiltViewModel(),
) {
    AlertDialog(
        title = { Text(stringResource(R.string.delete_platform)) },
        text = { Text(stringResource(R.string.delete_platform_confirmation)) },
        onDismissRequest = settingViewModel::closeDeleteDialog,
        confirmButton = {
            TextButton(onClick = settingViewModel::confirmDelete) {
                Text(stringResource(R.string.delete_platform))
            }
        },
        dismissButton = {
            TextButton(onClick = settingViewModel::closeDeleteDialog) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

package com.vibe.app.presentation.ui.setting

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.vibe.app.feature.ai.AiExecutionMode
import com.vibe.app.feature.ai.AiProviderOrigin
import com.vibe.app.presentation.common.SettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderSettingsScreen(
    onBack: () -> Unit,
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToPlatformSetting: (String) -> Unit,
    settingViewModel: SettingViewModelV2 = hiltViewModel(),
    freeAiViewModel: FreeAiSettingsViewModel = hiltViewModel(),
) {
    val platforms by settingViewModel.platformState.collectAsStateWithLifecycle()
    val freeAiState by freeAiViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    LaunchedEffect(freeAiViewModel) {
        freeAiViewModel.openBrowser.collect { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure(freeAiViewModel::reportOpenRouterLaunchFailure)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                settingViewModel.fetchPlatforms()
                freeAiViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.ai_provider_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FreeAiControlCard(
                enabled = freeAiState.freeAiEnabled,
                customProviderActive = freeAiState.customProviderActive,
                networkAvailable = freeAiState.networkAvailable,
                openRouterConnected = freeAiState.openRouterConnected,
                mode = freeAiState.executionMode,
                onModeChange = freeAiViewModel::setExecutionMode,
            )

            OpenRouterOAuthCard(
                connected = freeAiState.openRouterConnected,
                connecting = freeAiState.openRouterConnecting,
                error = freeAiState.openRouterError,
                onConnect = freeAiViewModel::connectOpenRouter,
                onDisconnect = freeAiViewModel::disconnectOpenRouter,
            )

            platforms
                .filter { AiProviderOrigin.of(it) == AiProviderOrigin.EXTERNAL }
                .forEach { platform ->
                    SettingItem(
                        modifier = Modifier.fillMaxWidth(),
                        title = platform.name,
                        description = if (platform.enabled) {
                            stringResource(R.string.enabled)
                        } else {
                            stringResource(R.string.disabled)
                        },
                        onItemClick = { onNavigateToPlatformSetting(platform.uid) },
                        showLeadingIcon = true,
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }

            SettingItem(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.add_platform),
                description = null,
                onItemClick = onNavigateToAddPlatform,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
    }
}

@Composable
private fun OpenRouterOAuthCard(
    connected: Boolean,
    connecting: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.openrouter_oauth_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            if (connected) R.string.openrouter_oauth_connected
                            else R.string.openrouter_oauth_disconnected
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.openrouter_oauth_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (connected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.openrouter_oauth_disconnect))
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (connecting) R.string.openrouter_oauth_connecting
                            else R.string.openrouter_oauth_connect
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FreeAiControlCard(
    enabled: Boolean,
    customProviderActive: Boolean,
    networkAvailable: Boolean?,
    openRouterConnected: Boolean,
    mode: AiExecutionMode,
    onModeChange: (AiExecutionMode) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.free_ai_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val descriptionRes = when {
                        customProviderActive -> R.string.free_ai_standby_custom
                        networkAvailable == false -> R.string.free_ai_waiting_for_network
                        openRouterConnected -> R.string.free_ai_cloud_ready
                        else -> R.string.free_ai_cloud_connect
                    }
                    Text(
                        text = stringResource(descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = if (enabled) {
                        stringResource(R.string.enabled)
                    } else {
                        stringResource(R.string.disabled)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(
                text = stringResource(R.string.ai_execution_mode_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == AiExecutionMode.MANUAL,
                    onClick = { onModeChange(AiExecutionMode.MANUAL) },
                    label = { Text(stringResource(R.string.ai_execution_manual)) },
                )
                FilterChip(
                    selected = mode == AiExecutionMode.AUTOMATIC,
                    onClick = { onModeChange(AiExecutionMode.AUTOMATIC) },
                    label = { Text(stringResource(R.string.ai_execution_automatic)) },
                )
            }
        }
    }
}

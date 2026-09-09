package com.malik.lmai.presentation.ui.h

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.malik.lmai.R
import com.malik.lmai.presentation.ui.setting.FreeAiSettingsViewModel

/**
 * Generic owner control for H's replaceable hidden cloud pool.
 *
 * No provider name, model name, quota, or internal route is surfaced here. H can change
 * those implementation details across releases without changing the user's assistant.
 */
@Composable
fun HCloudCapacitySettingsCard(
    viewModel: FreeAiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.openBrowser.collect { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure(viewModel::reportOpenRouterLaunchFailure)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
            ),
            shadowElevation = 1.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setAutomaticCloudRoutesEnabled(
                                !state.automaticCloudRoutesEnabled,
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.h_cloud_capacity_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.h_cloud_capacity_desc),
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = state.automaticCloudRoutesEnabled,
                        onCheckedChange = viewModel::setAutomaticCloudRoutesEnabled,
                    )
                }

                if (state.automaticCloudRoutesEnabled) {
                    Column(
                        modifier = Modifier.padding(start = 66.dp, end = 14.dp, bottom = 10.dp),
                    ) {
                        if (state.openRouterConnected) {
                            Text(
                                text = stringResource(R.string.h_cloud_capacity_connected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        TextButton(
                            onClick = {
                                if (state.openRouterConnected) {
                                    viewModel.disconnectOpenRouter()
                                } else {
                                    viewModel.connectOpenRouter()
                                }
                            },
                            enabled = !state.openRouterConnecting,
                        ) {
                            Text(
                                text = stringResource(
                                    when {
                                        state.openRouterConnecting -> R.string.h_cloud_capacity_connecting
                                        state.openRouterConnected -> R.string.h_cloud_capacity_disconnect
                                        else -> R.string.h_cloud_capacity_connect
                                    }
                                )
                            )
                        }

                        state.openRouterError?.takeIf { it.isNotBlank() }?.let { error ->
                            Text(
                                text = stringResource(R.string.h_cloud_capacity_error, error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        HBackupCloudSettingsCard()
        HMoveSettingsCard()
    }
}

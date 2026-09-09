package com.malik.lmai.presentation.ui.h

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun HBackupCloudSettingsCard(
    viewModel: HBackupCloudViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.pendingSetupUrl) {
        val url = state.pendingSetupUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        viewModel.consumeSetupUrl()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                ) {
                    Icon(
                        Icons.Outlined.Backup,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.h_backup_cloud_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.h_backup_cloud_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = stringResource(
                    when {
                        state.backupReady && state.backupHealthy -> R.string.h_backup_cloud_ready
                        state.backupConfigured -> R.string.h_backup_cloud_needs_attention
                        else -> R.string.h_backup_cloud_not_configured
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.backupReady && state.backupHealthy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            val criticalCapacityWithoutStandby =
                state.capacityState == "last_cloud_capacity_at_risk" ||
                    (state.capacityState == "backup_available" && !state.automaticFailoverReady)
            when {
                criticalCapacityWithoutStandby -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.h_backup_cloud_capacity_critical),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                state.capacityState == "warning" && !state.automaticFailoverReady -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.h_backup_cloud_capacity_warning),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            if (state.backupConfigured) {
                Text(
                    text = stringResource(
                        when {
                            state.automaticFailoverReady -> R.string.h_backup_cloud_failover_ready
                            !state.replicationWorkerDeployed -> R.string.h_backup_cloud_replication_worker_missing
                            state.storageBackupReady && !state.replicationReady -> R.string.h_backup_cloud_replication_waiting
                            state.replicationReady && !state.replicationFresh -> R.string.h_backup_cloud_replication_stale
                            state.replicationFresh && !state.standbyRuntimeReady -> R.string.h_backup_cloud_standby_runtime_waiting
                            state.standbyRuntimeReady && !state.runtimeHealthOk -> R.string.h_backup_cloud_standby_health_waiting
                            else -> R.string.h_backup_cloud_storage_only
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.automaticFailoverReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.linked,
                onClick = viewModel::createBackupSetupLink,
            ) {
                Text(
                    stringResource(
                        if (state.backupConfigured) R.string.h_backup_cloud_replace
                        else R.string.h_backup_cloud_add
                    )
                )
            }

            if (state.storageBackupReady && !state.standbyRuntimeReady) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading && state.linked,
                    onClick = viewModel::prepareStandbyRuntime,
                ) {
                    Text(stringResource(R.string.h_backup_cloud_prepare_standby))
                }
                Text(
                    text = stringResource(R.string.h_backup_cloud_prepare_standby_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.backupConfigured) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                    onClick = viewModel::disconnectBackup,
                ) {
                    Text(stringResource(R.string.h_backup_cloud_disconnect))
                }
            }

            state.error?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.h_backup_cloud_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

package com.vibe.app.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.presentation.common.SettingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsScreen(
    onBack: () -> Unit,
    viewModel: ProjectBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.project_settings_title)) },
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
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingItem(
                title = stringResource(R.string.project_sync_title),
                description = stringResource(R.string.project_sync_description),
                onItemClick = viewModel::openBackupSelection,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            SettingItem(
                title = stringResource(R.string.project_restore_title),
                description = stringResource(R.string.project_restore_description),
                onItemClick = viewModel::openRestoreSelection,
                showLeadingIcon = true,
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
    }

    if (state.isSelectionOpen) {
        ProjectSelectionDialog(
            state = state,
            onToggle = viewModel::toggleProject,
            onSelectAll = viewModel::selectAll,
            onConfirm = viewModel::confirmSelection,
            onDismiss = viewModel::cancelSelection,
        )
    }

    if (state.isRunning || state.completed || state.error != null) {
        ProjectBackupProgressDialog(
            state = state,
            onDismiss = { if (!state.isRunning) viewModel.dismissResult() },
        )
    }
}

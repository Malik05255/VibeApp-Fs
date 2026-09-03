package com.vibe.app.presentation.ui.github

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubSettingsScreen(
    onBack: () -> Unit,
    viewModel: GitHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.github_integration)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.connectedLogin == null) {
                Text(stringResource(R.string.github_token_description))
                OutlinedTextField(
                    value = state.token,
                    onValueChange = viewModel::updateToken,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.github_token)) },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { viewModel.connect() },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.github_connect))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.github_connected_as, state.connectedLogin!!))
                    TextButton(onClick = viewModel::disconnect) {
                        Text(stringResource(R.string.github_disconnect))
                    }
                }
                Text(stringResource(R.string.github_repositories), style = MaterialTheme.typography.titleMedium)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.repositories, key = { it.id }) { repository ->
                        ListItem(
                            headlineContent = { Text(repository.name) },
                            supportingContent = {
                                Text("${repository.defaultBranch} • ${if (repository.permissions?.push == true) "write" else "read only"}")
                            },
                        )
                    }
                }
            }
        }
    }
}

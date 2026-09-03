package com.vibe.app.presentation.ui.github

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current
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
                Text(stringResource(R.string.github_oauth_description))
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (state.deviceUserCode == null) {
                    Button(
                        onClick = viewModel::startSignIn,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator()
                        } else {
                            Text(stringResource(R.string.github_sign_in))
                        }
                    }
                } else {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.github_enter_code))
                            Text(
                                state.deviceUserCode!!,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", state.deviceUserCode))
                                },
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null)
                                Text(stringResource(R.string.github_copy_code))
                            }
                            Button(
                                onClick = {
                                    state.verificationUri?.let { uri ->
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(uri)),
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.OpenInBrowser, null)
                                Text(stringResource(R.string.github_open_authorization))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.github_waiting_for_authorization))
                            }
                        }
                    }
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
                                val access = if (repository.permissions?.push == true) {
                                    stringResource(R.string.github_write_access)
                                } else {
                                    stringResource(R.string.github_read_only)
                                }
                                Text("${repository.defaultBranch} • $access")
                            },
                        )
                    }
                }
            }
        }
    }
}

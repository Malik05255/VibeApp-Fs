package com.vibe.app.presentation.ui.github

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.data.database.entity.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubSettingsScreen(
    onBack: () -> Unit,
    onProjectClick: (Project) -> Unit,
    viewModel: GitHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var repositoriesExpanded by remember { mutableStateOf(false) }
    var lastOpenedVerificationUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.verificationUri, state.deviceUserCode) {
        val uri = state.verificationUri
        val code = state.deviceUserCode
        if (!uri.isNullOrBlank() && !code.isNullOrBlank() && uri != lastOpenedVerificationUri) {
            lastOpenedVerificationUri = uri
            copyGitHubCode(context, code)
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.github_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.github_settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.connectedLogin == null) {
                Spacer(Modifier.height(24.dp))

                GitHubConnectButton(
                    enabled = !state.loading && state.deviceUserCode == null,
                    loading = state.loading && state.deviceUserCode == null,
                    onClick = viewModel::startSignIn,
                )

                Text(
                    text = stringResource(R.string.github_settings_connect_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let {
                    Text(
                        text = githubErrorMessage(it),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.deviceUserCode != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.github_settings_browser_opened),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(R.string.github_settings_waiting),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            Text(
                                stringResource(R.string.github_settings_code_copied_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(
                                    onClick = {
                                        state.deviceUserCode?.let { copyGitHubCode(context, it) }
                                    },
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.github_settings_copy_again))
                                }
                                TextButton(
                                    onClick = {
                                        state.verificationUri?.let { uri ->
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                                        }
                                    },
                                ) {
                                    Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.github_settings_open_again))
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.github_settings_connected),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("@${state.connectedLogin}", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = viewModel::disconnect) {
                        Text(stringResource(R.string.github_settings_disconnect))
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = repositoriesExpanded,
                    onExpandedChange = { repositoriesExpanded = !repositoriesExpanded },
                ) {
                    val selectedRepository = state.repositories.firstOrNull {
                        it.fullName == state.selectedRepositoryFullName
                    }
                    OutlinedTextField(
                        value = selectedRepository?.fullName.orEmpty(),
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        label = { Text(stringResource(R.string.github_settings_repository)) },
                        placeholder = {
                            Text(stringResource(R.string.github_settings_choose_repository))
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(repositoriesExpanded)
                        },
                    )
                    ExposedDropdownMenu(
                        expanded = repositoriesExpanded,
                        onDismissRequest = { repositoriesExpanded = false },
                    ) {
                        state.repositories.forEach { repository ->
                            DropdownMenuItem(
                                text = { Text(repository.fullName) },
                                onClick = {
                                    viewModel.selectRepository(repository.fullName)
                                    repositoriesExpanded = false
                                },
                            )
                        }
                    }
                }

                state.error?.let {
                    Text(githubErrorMessage(it), color = MaterialTheme.colorScheme.error)
                }

                state.activeRepositoryFullName?.let { activeRepo ->
                    Text(
                        stringResource(R.string.github_settings_linked_projects, activeRepo),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.linkedProjects.isEmpty()) {
                        Text(
                            stringResource(R.string.github_settings_no_linked_projects),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.linkedProjects, key = { it.projectId }) { project ->
                                ListItem(
                                    headlineContent = { Text(project.name) },
                                    supportingContent = { Text(project.githubBranch ?: "main") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onProjectClick(project) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubConnectButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF111214),
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF111214),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_github_mark),
                            contentDescription = null,
                            tint = Color(0xFF111214),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.size(16.dp))

            Text(
                text = stringResource(R.string.github_settings_connect),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun copyGitHubCode(context: Context, code: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", code))
    }
}

@Composable
private fun githubErrorMessage(error: GitHubSettingsError): String = when (error) {
    GitHubSettingsError.OAUTH_NOT_CONFIGURED ->
        stringResource(R.string.github_settings_error_not_configured)

    GitHubSettingsError.AUTH_CANCELLED ->
        stringResource(R.string.github_settings_error_cancelled)

    GitHubSettingsError.CODE_EXPIRED ->
        stringResource(R.string.github_settings_error_expired)

    GitHubSettingsError.INVALID_RESPONSE ->
        stringResource(R.string.github_settings_error_invalid_response)

    GitHubSettingsError.SIGN_IN_FAILED ->
        stringResource(R.string.github_settings_error_sign_in)

    GitHubSettingsError.CONNECT_FAILED ->
        stringResource(R.string.github_settings_error_connect)
}

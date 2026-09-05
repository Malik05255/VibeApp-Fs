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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
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
import com.vibe.app.presentation.common.AdaptiveContent

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
            openGitHubVerification(context, uri)
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
        AdaptiveContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            maxContentWidth = 680.dp,
        ) { dimensions ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = dimensions.horizontalPadding,
                        vertical = dimensions.verticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(dimensions.itemSpacing),
            ) {
                if (state.connectedLogin == null) {
                    GitHubConnectButton(
                        enabled = !state.loading && state.deviceUserCode == null,
                        loading = state.loading && state.deviceUserCode == null,
                        onClick = viewModel::startSignIn,
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
                        GitHubAuthorizationCard(
                            onOpenGitHub = {
                                state.verificationUri?.let { uri ->
                                    state.deviceUserCode?.let { copyGitHubCode(context, it) }
                                    openGitHubVerification(context, uri)
                                }
                            },
                        )
                    }
                } else {
                    ConnectedGitHubContent(
                        state = state,
                        repositoriesExpanded = repositoriesExpanded,
                        onRepositoriesExpandedChange = { repositoriesExpanded = it },
                        onRepositorySelected = viewModel::selectRepository,
                        onDisconnect = viewModel::disconnect,
                        onProjectSelected = { project ->
                            viewModel.linkProjectToSelectedRepository(project)
                            onProjectClick(project)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GitHubAuthorizationCard(onOpenGitHub: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Text(
                text = stringResource(R.string.github_auth_finish_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenGitHub) {
                Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.github_settings_open_again))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedGitHubContent(
    state: GitHubSettingsState,
    repositoriesExpanded: Boolean,
    onRepositoriesExpandedChange: (Boolean) -> Unit,
    onRepositorySelected: (String) -> Unit,
    onDisconnect: () -> Unit,
    onProjectSelected: (Project) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "@${state.connectedLogin}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        TextButton(onClick = onDisconnect) {
            Text(stringResource(R.string.github_settings_disconnect))
        }
    }

    ExposedDropdownMenuBox(
        expanded = repositoriesExpanded,
        onExpandedChange = onRepositoriesExpandedChange,
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
            singleLine = true,
            label = { Text(stringResource(R.string.github_settings_repository)) },
            placeholder = { Text(stringResource(R.string.github_settings_choose_repository)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(repositoriesExpanded) },
        )
        ExposedDropdownMenu(
            expanded = repositoriesExpanded,
            onDismissRequest = { onRepositoriesExpandedChange(false) },
        ) {
            state.repositories.forEach { repository ->
                DropdownMenuItem(
                    text = { Text(repository.fullName) },
                    onClick = {
                        onRepositorySelected(repository.fullName)
                        onRepositoriesExpandedChange(false)
                    },
                )
            }
        }
    }

    state.error?.let {
        Text(githubErrorMessage(it), color = MaterialTheme.colorScheme.error)
    }

    val activeRepository = state.activeRepositoryFullName
    if (activeRepository != null) {
        when {
            state.projectsLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }
            state.projects.isEmpty() -> {
                Text(
                    stringResource(R.string.no_search_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Text(
                    stringResource(R.string.projects),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.projects, key = { it.projectId }) { project ->
                        val linked = project.githubRepositoryFullName == activeRepository
                        ListItem(
                            headlineContent = { Text(project.name) },
                            trailingContent = {
                                if (linked) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProjectSelected(project) },
                        )
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
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF111214),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF111214),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_github_mark),
                            contentDescription = null,
                            tint = Color(0xFF111214),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.github_settings_connect),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.size(44.dp))
        }
    }
}

private fun openGitHubVerification(context: Context, uri: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
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

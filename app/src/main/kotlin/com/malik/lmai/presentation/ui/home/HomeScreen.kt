package com.malik.lmai.presentation.ui.home

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.data.database.entity.ProjectBuildStatus
import com.malik.lmai.data.database.entity.ProjectWithChat
import com.malik.lmai.feature.projecticon.ProjectIconRenderer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    settingOnClick: () -> Unit,
    onProjectClick: (chatId: Int, enabledPlatforms: List<String>) -> Unit,
    navigateToChat: (chatId: Int, enabledPlatforms: List<String>) -> Unit,
) {
    val listState = rememberLazyListState()
    val projectListState by homeViewModel.projectListState.collectAsStateWithLifecycle()
    val showDeleteWarningDialog by homeViewModel.showDeleteWarningDialog.collectAsStateWithLifecycle()
    val searchQuery by homeViewModel.searchQuery.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(projectListState.navigationEvent) {
        projectListState.navigationEvent?.let { event ->
            when (event) {
                is HomeViewModel.NavigationEvent.OpenProject -> {
                    navigateToChat(event.chatId, event.enabledPlatforms)
                    homeViewModel.consumeNavigationEvent()
                }
            }
        }
    }

    LaunchedEffect(lifecycleState) {
        if (
            lifecycleState == Lifecycle.State.RESUMED &&
            !projectListState.isSelectionMode &&
            !projectListState.isSearchMode
        ) {
            homeViewModel.fetchProjects()
            homeViewModel.fetchPlatformStatus()
        }
    }

    BackHandler(enabled = projectListState.isSelectionMode || projectListState.isSearchMode) {
        when {
            projectListState.isSelectionMode -> homeViewModel.disableSelectionMode()
            projectListState.isSearchMode -> homeViewModel.disableSearchMode()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopBar(
                isSelectionMode = projectListState.isSelectionMode,
                isSearchMode = projectListState.isSearchMode,
                selectedCount = projectListState.selectedProjects.count { it },
                settingsOnClick = settingOnClick,
                onSearchToggle = {
                    when {
                        projectListState.isSelectionMode -> homeViewModel.disableSelectionMode()
                        projectListState.isSearchMode -> homeViewModel.disableSearchMode()
                        else -> homeViewModel.enableSearchMode()
                    }
                },
            )
        },
        floatingActionButtonPosition = if (projectListState.isSelectionMode) FabPosition.End else FabPosition.Start,
        floatingActionButton = {
            if (projectListState.isSelectionMode) {
                DeleteProjectsButton(
                    selectedCount = projectListState.selectedProjects.count { it },
                    onClick = homeViewModel::openDeleteWarningDialog,
                )
            } else if (!projectListState.isSearchMode) {
                NewProjectButton(
                    expanded = listState.isScrollingUp(),
                    isCreating = projectListState.creationState is HomeViewModel.ProjectCreationState.InProgress,
                    onClick = homeViewModel::createNewProject,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (projectListState.isSearchMode) {
                    SearchPanel(
                        query = searchQuery,
                        onQueryChange = homeViewModel::updateSearchQuery,
                    )
                } else {
                    ProjectsHeader(projectListState.projects.size)
                }
            }

            if (projectListState.projects.isEmpty()) {
                item {
                    EmptyProjectsCard(
                        isSearch = projectListState.isSearchMode && searchQuery.isNotBlank(),
                    )
                }
            } else {
                itemsIndexed(
                    items = projectListState.projects,
                    key = { _, item -> item.project.projectId },
                ) { index, project ->
                    ProjectCard(
                        pwc = project,
                        activeSessionPlatformName = homeViewModel.getActiveSessionPlatformName(project.project.chatId),
                        isSelectionMode = projectListState.isSelectionMode,
                        isSelected = projectListState.selectedProjects.getOrElse(index) { false },
                        onLongClick = {
                            if (!projectListState.isSearchMode) {
                                homeViewModel.enableSelectionMode()
                                homeViewModel.selectProject(index)
                            }
                        },
                        onClick = {
                            if (projectListState.isSelectionMode) {
                                homeViewModel.selectProject(index)
                            } else {
                                val enabledPlatforms = homeViewModel.platformState.value
                                    .filter { it.enabled }
                                    .map { it.uid }
                                    .takeIf { it.isNotEmpty() }
                                    ?: project.chat.enabledPlatform
                                onProjectClick(project.project.chatId, enabledPlatforms)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showDeleteWarningDialog) {
        DeleteWarningDialog(
            onDismissRequest = homeViewModel::closeDeleteWarningDialog,
            onConfirm = {
                val deletedCount = projectListState.selectedProjects.count { it }
                homeViewModel.deleteSelectedProjects()
                Toast.makeText(
                    context,
                    context.getString(R.string.deleted_projects, deletedCount),
                    Toast.LENGTH_SHORT,
                ).show()
                homeViewModel.closeDeleteWarningDialog()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    isSelectionMode: Boolean,
    isSearchMode: Boolean,
    selectedCount: Int,
    settingsOnClick: () -> Unit,
    onSearchToggle: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = if (isSelectionMode) {
                    stringResource(R.string.projects_selected, selectedCount)
                } else {
                    stringResource(R.string.projects)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = if (isSelectionMode || isSearchMode) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (!isSelectionMode && !isSearchMode) {
                IconButton(onClick = settingsOnClick) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = if (isSelectionMode) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.background
            },
        ),
    )
}

@Composable
private fun ProjectsHeader(count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.projects),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
                }
            }
        },
        placeholder = { Text(stringResource(R.string.search_projects)) },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun EmptyProjectsCard(isSearch: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = if (isSearch) stringResource(R.string.no_search_results) else stringResource(R.string.projects),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    pwc: ProjectWithChat,
    activeSessionPlatformName: String?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                )
            } else {
                ProjectListItemIcon(workspacePath = pwc.project.workspacePath)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = pwc.project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val subtitle = if (activeSessionPlatformName != null) {
                    stringResource(R.string.home_session_thinking, activeSessionPlatformName)
                } else {
                    pwc.lastMessageContent
                        ?.replace('\n', ' ')
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: formatUpdatedAt(pwc.chat.updatedAt)
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (activeSessionPlatformName != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            BuildStatusBadge(status = pwc.project.buildStatus)
        }
    }
}

@Composable
private fun ProjectListItemIcon(workspacePath: String) {
    val iconSize = 50.dp
    val iconSizePx = with(LocalDensity.current) { iconSize.roundToPx() }
    val iconSignature = ProjectIconRenderer.iconSignature(workspacePath)
    val iconBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        workspacePath,
        iconSignature,
        iconSizePx,
    ) {
        value = ProjectIconRenderer.loadProjectIcon(workspacePath, iconSizePx)
    }

    Box(
        modifier = Modifier
            .size(iconSize)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(15.dp),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.12f),
            )
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_rounded_chat),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BuildStatusBadge(status: ProjectBuildStatus) {
    when (status) {
        ProjectBuildStatus.INITIALIZING,
        ProjectBuildStatus.BUILDING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
        ProjectBuildStatus.SUCCESS -> {
            StatusPill(text = "✓", isError = false)
        }
        ProjectBuildStatus.FAILED -> {
            StatusPill(text = "!", isError = true)
        }
        ProjectBuildStatus.READY -> Unit
    }
}

@Composable
private fun StatusPill(text: String, isError: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun NewProjectButton(
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    isCreating: Boolean = false,
    onClick: () -> Unit = {},
) {
    val orientation = LocalConfiguration.current.orientation
    val fabModifier = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        modifier.systemBarsPadding()
    } else {
        modifier
    }

    ExtendedFloatingActionButton(
        modifier = fabModifier.padding(bottom = 12.dp),
        onClick = { if (!isCreating) onClick() },
        expanded = expanded,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = {
            if (isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_project))
            }
        },
        text = { Text(stringResource(R.string.new_project)) },
    )
}

@Composable
private fun DeleteProjectsButton(
    selectedCount: Int,
    onClick: () -> Unit,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        icon = {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_platform))
        },
        text = {
            Text(stringResource(R.string.delete_platform) + " ($selectedCount)")
        },
    )
}

@Composable
private fun DeleteWarningDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.delete_selected_projects)) },
        text = { Text(stringResource(R.string.this_operation_can_t_be_undone)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_platform))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }

    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

private fun formatUpdatedAt(unixSeconds: Long): String {
    val date = Date(unixSeconds * 1000)
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }

    return when {
        isSameDay(now, target) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        isYesterday(now, target) -> {
            val locale = Locale.getDefault()
            val yesterday = if (locale.language == "zh") "昨天" else "Yesterday"
            "$yesterday ${SimpleDateFormat("HH:mm", locale).format(date)}"
        }
        now.get(Calendar.YEAR) == target.get(Calendar.YEAR) ->
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date)
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(now: Calendar, target: Calendar): Boolean {
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now.timeInMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return isSameDay(yesterday, target)
}

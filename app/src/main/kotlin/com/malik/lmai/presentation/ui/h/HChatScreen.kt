package com.malik.lmai.presentation.ui.h

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider.getUriForFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.presentation.ui.chat.ChatScreen
import com.malik.lmai.presentation.ui.chat.ChatViewModel
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Presentation wrapper for the primary H workspace.
 *
 * The underlying chat remains the source of truth for conversation/runtime behavior. This layer
 * replaces the old top-bar chrome with a quieter H identity, adds a left-edge retractable tool
 * rail, and gives an intentionally sparse empty state useful context without turning the chat
 * into a dashboard.
 */
@Composable
fun HChatScreen(
    chatViewModel: ChatViewModel = hiltViewModel(),
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToDiagnostic: () -> Unit,
    onBackAction: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    showBackButton: Boolean = true,
) {
    val chatRoom by chatViewModel.chatRoom.collectAsStateWithLifecycle()
    val projectName by chatViewModel.projectName.collectAsStateWithLifecycle()
    val groupedMessages by chatViewModel.groupedMessages.collectAsStateWithLifecycle()
    val loadingStates by chatViewModel.loadingStates.collectAsStateWithLifecycle()
    val currentProjectId by chatViewModel.currentProjectId.collectAsStateWithLifecycle()
    val isBuildRunning by chatViewModel.isBuildRunning.collectAsStateWithLifecycle()
    val isLoaded by chatViewModel.isLoaded.collectAsStateWithLifecycle()
    val platforms by chatViewModel.platformsInApp.collectAsStateWithLifecycle()
    val isDebugEnabled by chatViewModel.isDebugEnabled.collectAsStateWithLifecycle()
    val crashPrompt by chatViewModel.crashPrompt.collectAsStateWithLifecycle()

    val projectTitle = (projectName ?: chatRoom.title).ifBlank { "H" }
    val isIdle = loadingStates.all { it == ChatViewModel.LoadingState.Idle }
    val runEnabled = isIdle && !isBuildRunning && currentProjectId != null
    val hasProject = currentProjectId != null
    val hasChat = chatRoom.id > 0

    var railExpanded by remember { mutableStateOf(false) }
    var moreActionsOpen by remember { mutableStateOf(false) }
    var clearChatDialogOpen by remember { mutableStateOf(false) }

    // Mirror ChatScreen's hidden-history behavior so the empty canvas remains useful when an
    // existing project is reopened and its older turns are intentionally not rendered.
    var initialMessageCount by remember(chatRoom.id) { mutableIntStateOf(-1) }
    LaunchedEffect(isLoaded, chatRoom.id) {
        if (isLoaded && initialMessageCount < 0) {
            initialMessageCount = groupedMessages.userMessages.size
        }
    }
    val visibleTurnCount = if (initialMessageCount < 0) {
        groupedMessages.userMessages.size
    } else {
        (groupedMessages.userMessages.size - initialMessageCount).coerceAtLeast(0)
    }
    val showSmartEmptyState = isLoaded &&
        visibleTurnCount == 0 &&
        isIdle &&
        crashPrompt == null &&
        platforms.isNotEmpty()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        ChatScreen(
            chatViewModel = chatViewModel,
            onNavigateToAddPlatform = onNavigateToAddPlatform,
            onNavigateToDiagnostic = onNavigateToDiagnostic,
            onBackAction = onBackAction,
            onNavigateToSettings = onNavigateToSettings,
            showBackButton = showBackButton,
        )

        if (showSmartEmptyState) {
            HSmartEmptyState(
                projectTitle = projectTitle,
                onSuggestion = chatViewModel::updateQuestion,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 116.dp, bottom = 150.dp),
            )
        }

        HChatHeader(
            projectTitle = projectTitle,
            showBackButton = showBackButton,
            onBackAction = onBackAction,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        // Keep this rail on the physical left edge even when the selected app language is RTL.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize()) {
                HQuickRail(
                    expanded = railExpanded,
                    runEnabled = runEnabled,
                    projectEnabled = hasProject,
                    onToggle = { railExpanded = !railExpanded },
                    onRun = {
                        railExpanded = false
                        chatViewModel.runBuild()
                    },
                    onRenameProject = {
                        railExpanded = false
                        chatViewModel.openProjectNameDialog()
                    },
                    onSnapshotHistory = {
                        railExpanded = false
                        chatViewModel.openSnapshotHistory()
                    },
                    onProjectMemo = {
                        railExpanded = false
                        chatViewModel.openProjectMemo()
                    },
                    onSettings = {
                        railExpanded = false
                        onNavigateToSettings()
                    },
                    onMore = {
                        railExpanded = false
                        moreActionsOpen = true
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }

    if (moreActionsOpen) {
        ModalBottomSheet(onDismissRequest = { moreActionsOpen = false }) {
            HMoreActionsSheet(
                isChatEnabled = hasChat,
                isProjectEnabled = hasProject,
                isDebugEnabled = isDebugEnabled,
                onInstallApk = {
                    moreActionsOpen = false
                    chatViewModel.installBuild()
                },
                onExportChat = {
                    moreActionsOpen = false
                    scope.launch { exportChatH(context, chatViewModel) }
                },
                onExportSource = {
                    moreActionsOpen = false
                    val projectId = currentProjectId ?: return@HMoreActionsSheet
                    scope.launch { exportSourceCodeH(context, projectId) }
                },
                onExportApk = {
                    moreActionsOpen = false
                    scope.launch {
                        val apkPath = withContext(Dispatchers.IO) { chatViewModel.getSignedApkPath() }
                        if (apkPath != null) {
                            shareApkH(context, apkPath)
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.no_built_apk),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                onClearChat = {
                    moreActionsOpen = false
                    clearChatDialogOpen = true
                },
                onDiagnostic = {
                    moreActionsOpen = false
                    onNavigateToDiagnostic()
                },
            )
        }
    }

    if (clearChatDialogOpen) {
        AlertDialog(
            onDismissRequest = { clearChatDialogOpen = false },
            title = { Text(stringResource(R.string.clear_chat_history_title)) },
            text = { Text(stringResource(R.string.clear_chat_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.clearChatHistory()
                        initialMessageCount = 0
                        clearChatDialogOpen = false
                    },
                ) {
                    Text(
                        stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearChatDialogOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun HChatHeader(
    projectTitle: String,
    showBackButton: Boolean,
    onBackAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = {},
            )
            .statusBarsPadding()
            .height(70.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBackAction) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.go_back),
                    )
                }
            }

            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "H",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(start = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "H AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.h_ui_context_assistant),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
            ) {
                Text(
                    text = projectTitle,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HSmartEmptyState(
    projectTitle: String,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "H",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = stringResource(R.string.h_ui_empty_title),
                modifier = Modifier.padding(top = 22.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.h_ui_empty_body),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                modifier = Modifier.padding(top = 18.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                ),
            ) {
                Text(
                    text = stringResource(R.string.h_ui_context_format, projectTitle),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = stringResource(R.string.h_ui_try_one),
                modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HSuggestionChip(
                    text = stringResource(R.string.h_ui_summarize_project),
                    onClick = { onSuggestion(it) },
                    modifier = Modifier.weight(1f),
                )
                HSuggestionChip(
                    text = stringResource(R.string.h_ui_open_latest),
                    onClick = { onSuggestion(it) },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.h_ui_context_sources),
                modifier = Modifier.padding(top = 28.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun HSuggestionChip(
    text: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick(text) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun HQuickRail(
    expanded: Boolean,
    runEnabled: Boolean,
    projectEnabled: Boolean,
    onToggle: () -> Unit,
    onRun: () -> Unit,
    onRenameProject: () -> Unit,
    onSnapshotHistory: () -> Unit,
    onProjectMemo: () -> Unit,
    onSettings: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .width(24.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                    .clickable(onClick = onToggle),
                shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.h_ui_quick_tools),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.width(58.dp),
                shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f),
                ),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RailIconButton(
                        enabled = runEnabled,
                        onClick = onRun,
                        icon = {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.run))
                        },
                    )
                    RailIconButton(
                        enabled = projectEnabled,
                        onClick = onRenameProject,
                        icon = {
                            Icon(
                                Icons.Outlined.DriveFileRenameOutline,
                                contentDescription = stringResource(R.string.update_project_name),
                            )
                        },
                    )
                    RailIconButton(
                        enabled = projectEnabled,
                        onClick = onSnapshotHistory,
                        icon = {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = stringResource(R.string.snapshot_history_title),
                            )
                        },
                    )
                    RailIconButton(
                        enabled = projectEnabled,
                        onClick = onProjectMemo,
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.StickyNote2,
                                contentDescription = stringResource(R.string.project_memo_title),
                            )
                        },
                    )
                    RailIconButton(
                        onClick = onSettings,
                        icon = {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.settings),
                            )
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    RailIconButton(
                        onClick = onMore,
                        icon = {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.h_ui_more_actions),
                            )
                        },
                    )
                    RailIconButton(
                        onClick = onToggle,
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.h_ui_close_tools),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RailIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(46.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            icon()
        }
    }
}

@Composable
private fun HMoreActionsSheet(
    isChatEnabled: Boolean,
    isProjectEnabled: Boolean,
    isDebugEnabled: Boolean,
    onInstallApk: () -> Unit,
    onExportChat: () -> Unit,
    onExportSource: () -> Unit,
    onExportApk: () -> Unit,
    onClearChat: () -> Unit,
    onDiagnostic: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.h_ui_more_actions),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )

        HActionRow(
            title = stringResource(R.string.install_apk),
            enabled = isProjectEnabled,
            icon = { Icon(Icons.Outlined.InstallMobile, contentDescription = null) },
            onClick = onInstallApk,
        )
        HActionRow(
            title = stringResource(R.string.export_chat),
            enabled = isChatEnabled,
            icon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
            onClick = onExportChat,
        )
        HActionRow(
            title = stringResource(R.string.export_source_code),
            enabled = isProjectEnabled,
            icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
            onClick = onExportSource,
        )
        HActionRow(
            title = stringResource(R.string.export_apk),
            enabled = isProjectEnabled,
            icon = { Icon(Icons.Outlined.Android, contentDescription = null) },
            onClick = onExportApk,
        )

        if (isDebugEnabled) {
            HActionRow(
                title = stringResource(R.string.debug_log),
                icon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                onClick = onDiagnostic,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        HActionRow(
            title = stringResource(R.string.clear_chat_history),
            enabled = isChatEnabled,
            tintError = true,
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            onClick = onClearChat,
        )

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun HActionRow(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tintError: Boolean = false,
) {
    val contentColor = if (tintError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
            icon()
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = contentColor.copy(alpha = if (enabled) 1f else 0.38f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

private suspend fun exportSourceCodeH(context: Context, projectId: String) {
    try {
        val sourceDir = File(context.filesDir, "projects/$projectId/app")
        if (!sourceDir.exists()) {
            Toast.makeText(
                context,
                context.getString(R.string.project_workspace_not_found),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val zipFile = File(context.getExternalFilesDir(null), "${projectId}_source.zip")
        withContext(Dispatchers.IO) {
            if (zipFile.exists()) zipFile.delete()
            ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                sourceDir.walkTopDown()
                    .filter { file ->
                        file.toRelativeString(sourceDir).split(File.separator).none { it == "build" }
                    }
                    .forEach { file ->
                        if (file.isFile) {
                            zos.putNextEntry(ZipEntry(file.toRelativeString(sourceDir)))
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
            }
        }
        val uri = getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        shareFile(
            context = context,
            uri = uri,
            mimeType = "application/zip",
            chooserTitle = context.getString(R.string.share_source_code),
        )
    } catch (e: Exception) {
        Log.e("HExportSource", "Failed to export source code", e)
        Toast.makeText(
            context,
            context.getString(R.string.failed_export_source_code),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun shareApkH(context: Context, apkPath: String) {
    try {
        val file = File(apkPath)
        if (!file.exists()) {
            Toast.makeText(
                context,
                context.getString(R.string.apk_file_not_found),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val uri = getUriForFile(context, "${context.packageName}.fileprovider", file)
        shareFile(
            context = context,
            uri = uri,
            mimeType = "application/vnd.android.package-archive",
            chooserTitle = context.getString(R.string.share_apk),
        )
    } catch (e: Exception) {
        Log.e("HExportApk", "Failed to share APK", e)
        Toast.makeText(
            context,
            context.getString(R.string.failed_share_apk),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private suspend fun exportChatH(context: Context, chatViewModel: ChatViewModel) {
    try {
        val exportBundle = chatViewModel.exportChat()
        val zipFile = File(context.getExternalFilesDir(null), exportBundle.zipFileName)
        withContext(Dispatchers.IO) {
            if (zipFile.exists()) zipFile.delete()
            ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                zos.putNextEntry(ZipEntry("chat.md"))
                zos.write(exportBundle.chatMarkdown.toByteArray())
                zos.closeEntry()

                exportBundle.diagnosticLogContent?.takeIf { it.isNotBlank() }?.let { content ->
                    zos.putNextEntry(ZipEntry("diagnostic-log.ndjson"))
                    zos.write(content.toByteArray())
                    zos.closeEntry()
                }

                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(exportBundle.manifestJson.toByteArray())
                zos.closeEntry()
            }
        }
        val uri = getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        shareFile(
            context = context,
            uri = uri,
            mimeType = "application/zip",
            chooserTitle = context.getString(R.string.share_chat_export),
        )
    } catch (e: Exception) {
        Log.e("HChatExport", "Failed to export chat", e)
        Toast.makeText(
            context,
            context.getString(R.string.failed_export_chat),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun shareFile(
    context: Context,
    uri: android.net.Uri,
    mimeType: String,
    chooserTitle: String,
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.packageManager
        .queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
        .forEach { result ->
            context.grantUriPermission(
                result.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    context.startActivity(chooser)
}

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.presentation.ui.chat.ChatViewModel
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Refined physical-left quick rail.
 *
 * The 24x92dp grip is always anchored at exactly the same screen position. Expanding the rail
 * reveals the actions beside the grip instead of replacing the grip with a differently-sized
 * surface, which prevents the perceived jump the previous implementation had.
 */
@Composable
internal fun HRefinedQuickRail(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    chatViewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chatRoom by chatViewModel.chatRoom.collectAsStateWithLifecycle()
    val loadingStates by chatViewModel.loadingStates.collectAsStateWithLifecycle()
    val currentProjectId by chatViewModel.currentProjectId.collectAsStateWithLifecycle()
    val isBuildRunning by chatViewModel.isBuildRunning.collectAsStateWithLifecycle()
    val isDebugEnabled by chatViewModel.isDebugEnabled.collectAsStateWithLifecycle()

    val isIdle = loadingStates.all { it == ChatViewModel.LoadingState.Idle }
    val runEnabled = isIdle && !isBuildRunning && currentProjectId != null
    val hasProject = currentProjectId != null
    val hasChat = chatRoom.id > 0

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var moreActionsOpen by remember { mutableStateOf(false) }
    var clearChatDialogOpen by remember { mutableStateOf(false) }

    // Fixed outer width means the physical-left grip never changes its x coordinate. The whole
    // control is centered vertically by the caller, while the grip is centered within this box,
    // so its y coordinate also remains unchanged as the action column expands/collapses.
    Box(
        modifier = modifier.width(84.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 24.dp),
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        ) {
            Surface(
                modifier = Modifier.width(58.dp),
                shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f),
                ),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HRefinedRailIconButton(
                        enabled = runEnabled,
                        onClick = {
                            onExpandedChange(false)
                            chatViewModel.runBuild()
                        },
                        icon = {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResourceSafe(R.string.run),
                            )
                        },
                    )
                    HRefinedRailIconButton(
                        enabled = hasProject,
                        onClick = {
                            onExpandedChange(false)
                            chatViewModel.openProjectNameDialog()
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.DriveFileRenameOutline,
                                contentDescription = stringResourceSafe(R.string.update_project_name),
                            )
                        },
                    )
                    HRefinedRailIconButton(
                        enabled = hasProject,
                        onClick = {
                            onExpandedChange(false)
                            chatViewModel.openSnapshotHistory()
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.History,
                                contentDescription = stringResourceSafe(R.string.snapshot_history_title),
                            )
                        },
                    )
                    HRefinedRailIconButton(
                        enabled = hasProject,
                        onClick = {
                            onExpandedChange(false)
                            chatViewModel.openProjectMemo()
                        },
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.StickyNote2,
                                contentDescription = stringResourceSafe(R.string.project_memo_title),
                            )
                        },
                    )
                    HRefinedRailIconButton(
                        onClick = {
                            onExpandedChange(false)
                            onNavigateToSettings()
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResourceSafe(R.string.settings),
                            )
                        },
                    )
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    HRefinedRailIconButton(
                        onClick = {
                            onExpandedChange(false)
                            moreActionsOpen = true
                        },
                        icon = {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResourceSafe(R.string.h_ui_more_actions),
                            )
                        },
                    )
                }
            }
        }

        // This is the SAME grip in both states. It never gets replaced, resized, or re-aligned.
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(24.dp)
                .height(92.dp)
                .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                .clickable { onExpandedChange(!expanded) },
            shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 3.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResourceSafe(R.string.h_ui_quick_tools),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }

    if (moreActionsOpen) {
        ModalBottomSheet(onDismissRequest = { moreActionsOpen = false }) {
            HRefinedMoreActionsSheet(
                isChatEnabled = hasChat,
                isProjectEnabled = hasProject,
                isDebugEnabled = isDebugEnabled,
                onInstallApk = {
                    moreActionsOpen = false
                    chatViewModel.installBuild()
                },
                onExportChat = {
                    moreActionsOpen = false
                    scope.launch { exportChatHRefined(context, chatViewModel) }
                },
                onExportSource = {
                    moreActionsOpen = false
                    val projectId = currentProjectId ?: return@HRefinedMoreActionsSheet
                    scope.launch { exportSourceCodeHRefined(context, projectId) }
                },
                onExportApk = {
                    moreActionsOpen = false
                    scope.launch {
                        val apkPath = withContext(Dispatchers.IO) { chatViewModel.getSignedApkPath() }
                        if (apkPath != null) {
                            shareApkHRefined(context, apkPath)
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
            title = { Text(androidx.compose.ui.res.stringResource(R.string.clear_chat_history_title)) },
            text = { Text(androidx.compose.ui.res.stringResource(R.string.clear_chat_history_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatViewModel.clearChatHistory()
                        clearChatDialogOpen = false
                    },
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearChatDialogOpen = false }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun stringResourceSafe(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun HRefinedRailIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(46.dp),
    ) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Ltr) {
            icon()
        }
    }
}

@Composable
private fun HRefinedMoreActionsSheet(
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
            text = androidx.compose.ui.res.stringResource(R.string.h_ui_more_actions),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )

        HRefinedActionRow(
            title = androidx.compose.ui.res.stringResource(R.string.install_apk),
            enabled = isProjectEnabled,
            icon = { Icon(Icons.Outlined.InstallMobile, contentDescription = null) },
            onClick = onInstallApk,
        )
        HRefinedActionRow(
            title = androidx.compose.ui.res.stringResource(R.string.export_chat),
            enabled = isChatEnabled,
            icon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null) },
            onClick = onExportChat,
        )
        HRefinedActionRow(
            title = androidx.compose.ui.res.stringResource(R.string.export_source_code),
            enabled = isProjectEnabled,
            icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
            onClick = onExportSource,
        )
        HRefinedActionRow(
            title = androidx.compose.ui.res.stringResource(R.string.export_apk),
            enabled = isProjectEnabled,
            icon = { Icon(Icons.Outlined.Android, contentDescription = null) },
            onClick = onExportApk,
        )

        if (isDebugEnabled) {
            HRefinedActionRow(
                title = androidx.compose.ui.res.stringResource(R.string.debug_log),
                icon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                onClick = onDiagnostic,
            )
        }

        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        HRefinedActionRow(
            title = androidx.compose.ui.res.stringResource(R.string.clear_chat_history),
            enabled = isChatEnabled,
            tintError = true,
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            onClick = onClearChat,
        )

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun HRefinedActionRow(
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
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
    }
}

private suspend fun exportSourceCodeHRefined(context: Context, projectId: String) {
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
        shareFileHRefined(
            context = context,
            uri = uri,
            mimeType = "application/zip",
            chooserTitle = context.getString(R.string.share_source_code),
        )
    } catch (e: Exception) {
        Log.e("HRefinedExportSource", "Failed to export source code", e)
        Toast.makeText(
            context,
            context.getString(R.string.failed_export_source_code),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun shareApkHRefined(context: Context, apkPath: String) {
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
        shareFileHRefined(
            context = context,
            uri = uri,
            mimeType = "application/vnd.android.package-archive",
            chooserTitle = context.getString(R.string.share_apk),
        )
    } catch (e: Exception) {
        Log.e("HRefinedExportApk", "Failed to share APK", e)
        Toast.makeText(
            context,
            context.getString(R.string.failed_share_apk),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private suspend fun exportChatHRefined(context: Context, chatViewModel: ChatViewModel) {
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
        shareFileHRefined(
            context = context,
            uri = uri,
            mimeType = "application/zip",
            chooserTitle = context.getString(R.string.share_chat_export),
        )
    } catch (e: Exception) {
        Log.e("HRefinedChatExport", "Failed to export chat", e)
        Toast.makeText(
            context,
            context.getString(R.string.failed_export_chat),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun shareFileHRefined(
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

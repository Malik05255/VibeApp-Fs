package com.malik.lmai.presentation.ui.h

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * User-controlled portable H transfer surface.
 *
 * The selected document is read/written directly by Android. Snapshot content is never
 * injected into model context and is not persisted to app storage/cache by this surface.
 */
@Composable
fun HMoveSettingsCard(
    viewModel: HMoveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingExportText
        pendingExportText = null
        if (uri != null && content != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(content)
                        }
                    }.isSuccess
                }
                viewModel.reportExportResult(success)
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val content = withContext(Dispatchers.IO) {
                    runCatching {
                        readPortableDocument(context.contentResolver, uri)
                    }.getOrNull()
                }
                if (content == null) {
                    viewModel.reportLocalImportError("portable_snapshot_file_too_large_or_unreadable")
                } else {
                    viewModel.validateImport(content)
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.exportReady.collect { snapshot ->
            pendingExportText = snapshot
            createDocument.launch("h-portable-${System.currentTimeMillis()}.json")
        }
    }

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
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.h_move_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.h_move_desc),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = viewModel::prepareExport,
                    enabled = !state.busy,
                ) {
                    Text(stringResource(R.string.h_move_export))
                }
                TextButton(
                    onClick = {
                        viewModel.beginImportSelection()
                        openDocument.launch(arrayOf("application/json", "text/plain"))
                    },
                    enabled = !state.busy,
                ) {
                    Text(stringResource(R.string.h_move_import))
                }
            }

            when (state.operation) {
                HMoveViewModel.Operation.EXPORT -> HMoveBusyText(R.string.h_move_exporting)
                HMoveViewModel.Operation.VALIDATE -> HMoveBusyText(R.string.h_move_validating)
                HMoveViewModel.Operation.RESTORE -> HMoveBusyText(R.string.h_move_restoring)
                HMoveViewModel.Operation.NONE -> Unit
            }

            state.validation?.let { validation ->
                Text(
                    text = stringResource(
                        R.string.h_move_validation_summary,
                        validation.memories,
                        validation.tasks,
                        validation.reminders,
                        validation.learningState,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(
                    onClick = { showRestoreConfirmation = true },
                    enabled = !state.busy,
                ) {
                    Text(stringResource(R.string.h_move_restore_action))
                }
            }

            state.notice?.let { notice ->
                val text = when (notice) {
                    HMoveViewModel.Notice.EXPORT_SAVED -> stringResource(R.string.h_move_export_saved)
                    HMoveViewModel.Notice.EXPORT_FAILED -> stringResource(R.string.h_move_export_failed)
                    HMoveViewModel.Notice.RESTORE_COMPLETE -> stringResource(R.string.h_move_restore_complete)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notice == HMoveViewModel.Notice.EXPORT_FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }

            state.error?.let { error ->
                Text(
                    text = stringResource(R.string.h_move_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = stringResource(R.string.h_move_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text(stringResource(R.string.h_move_restore_confirm_title)) },
            text = { Text(stringResource(R.string.h_move_restore_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmation = false
                        viewModel.restoreValidatedSnapshot()
                    },
                ) {
                    Text(stringResource(R.string.h_move_restore_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun HMoveBusyText(textRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun readPortableDocument(
    contentResolver: ContentResolver,
    uri: Uri,
): String? {
    val input = contentResolver.openInputStream(uri) ?: return null
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_PORTABLE_FILE_BYTES) return null
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}

private const val MAX_PORTABLE_FILE_BYTES = 8 * 1024 * 1024

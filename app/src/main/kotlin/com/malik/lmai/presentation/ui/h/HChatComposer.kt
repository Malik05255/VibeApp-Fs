package com.malik.lmai.presentation.ui.h

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.malik.lmai.R
import java.io.File

@Composable
internal fun HRefinedComposer(
    value: String,
    onValueChange: (String) -> Unit,
    chatEnabled: Boolean,
    disabledText: String,
    isResponding: Boolean,
    selectedFiles: List<String>,
    onFileSelected: (String) -> Unit,
    onFileRemoved: (String) -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val originalDirection = LocalLayoutDirection.current
    val unsupportedText = stringResource(R.string.image_input_not_supported)
    val failedToSelectText = stringResource(R.string.failed_to_select_image)
    var attachmentActionVisible by remember { mutableStateOf(false) }

    // Keep a local TextFieldValue instead of binding the IME directly to the ViewModel String.
    // This preserves the cursor, selection and Arabic IME composition while still mirroring the
    // text into ChatViewModel on every edit. External clears (after send) are synchronized back.
    var editingValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }
    LaunchedEffect(value) {
        if (value != editingValue.text) {
            editingValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            )
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        attachmentActionVisible = false
        if (uri == null) return@rememberLauncherForActivityResult
        val filePath = copyAttachmentToHWorkspace(context, uri)
        if (filePath != null) {
            onFileSelected(filePath)
        } else {
            Toast.makeText(context, failedToSelectText, Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Identical physical margins on both sides, matching the requested ChatGPT-like
                // composer footprint.
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            if (selectedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedFiles.forEach { filePath ->
                        HAttachmentChip(
                            filePath = filePath,
                            onRemove = { onFileRemoved(filePath) },
                        )
                    }
                }
            }

            // The shell uses physical LTR positioning so send is always on the physical left and
            // image insertion is always on the physical right. The editable text restores the app
            // language direction inside this shell.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 168.dp),
                    shape = RoundedCornerShape(34.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f),
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 168.dp),
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                            BasicTextField(
                                value = editingValue,
                                onValueChange = { next ->
                                    if (chatEnabled) {
                                        editingValue = next
                                        onValueChange(next.text)
                                    }
                                },
                                enabled = chatEnabled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp, max = 168.dp)
                                    // Equal reserved space keeps text clear of the two circular
                                    // controls while the field itself still spans the full width.
                                    .absolutePadding(
                                        left = 70.dp,
                                        top = 21.dp,
                                        right = 70.dp,
                                        bottom = 21.dp,
                                    ),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                minLines = 1,
                                maxLines = 6,
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = if (originalDirection == LayoutDirection.Rtl) {
                                            Alignment.CenterEnd
                                        } else {
                                            Alignment.CenterStart
                                        },
                                    ) {
                                        if (editingValue.text.isEmpty()) {
                                            Text(
                                                text = if (chatEnabled) {
                                                    stringResource(R.string.ask_a_question)
                                                } else {
                                                    disabledText
                                                },
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                                                textAlign = TextAlign.Start,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }

                        HSendOrStopButton(
                            canSend = chatEnabled && editingValue.text.trim().isNotEmpty(),
                            isResponding = isResponding,
                            onSend = {
                                attachmentActionVisible = false
                                onSend()
                            },
                            onStop = onStop,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 10.dp),
                        )

                        // Keep the hidden/reveal interaction, but move it to the physical right.
                        // The grip is darker than before so it is discoverable without becoming
                        // visually dominant.
                        if (!attachmentActionVisible) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 4.dp)
                                    .size(width = 14.dp, height = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { attachmentActionVisible = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    modifier = Modifier.size(width = 5.dp, height = 26.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                                ) {}
                            }
                        }

                        HAttachmentReveal(
                            visible = attachmentActionVisible,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp),
                            onClick = {
                                if (chatEnabled) {
                                    filePicker.launch("image/*")
                                } else {
                                    Toast.makeText(
                                        context,
                                        unsupportedText,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HAttachmentReveal(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.44f),
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.select_image),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
    }
}

@Composable
private fun HSendOrStopButton(
    canSend: Boolean,
    isResponding: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = isResponding || canSend
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    }

    Surface(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) {
                if (isResponding) onStop() else onSend()
            },
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ImageVector.vectorResource(
                    if (isResponding) R.drawable.ic_pause else R.drawable.ic_send_btn,
                ),
                contentDescription = stringResource(
                    if (isResponding) R.string.stop else R.string.send,
                ),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun HAttachmentChip(
    filePath: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = File(filePath).name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = onRemove,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.remove),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun copyAttachmentToHWorkspace(context: Context, uri: Uri): String? {
    return try {
        val rawName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: "attachment_${System.currentTimeMillis()}"

        val sanitized = rawName
            .replace("..", "")
            .replace("/", "")
            .replace("\\", "")
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .take(200)
            .trim('.')
            .ifEmpty { "attachment_${System.currentTimeMillis()}" }

        val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
        var target = File(attachmentsDir, sanitized)
        if (target.exists()) {
            val base = sanitized.substringBeforeLast('.')
            val ext = sanitized.substringAfterLast('.', "")
            val suffix = System.currentTimeMillis()
            target = File(
                attachmentsDir,
                if (ext.isNotEmpty()) "${base}_$suffix.$ext" else "${sanitized}_$suffix",
            )
        }

        val root = attachmentsDir.canonicalFile
        val safeTarget = target.canonicalFile
        if (safeTarget.parentFile != root) return null

        context.contentResolver.openInputStream(uri)?.use { input ->
            safeTarget.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        safeTarget.absolutePath
    } catch (_: Exception) {
        null
    }
}

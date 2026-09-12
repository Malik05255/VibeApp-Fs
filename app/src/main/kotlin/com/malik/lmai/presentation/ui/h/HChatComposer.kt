package com.malik.lmai.presentation.ui.h

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    isResponding: Boolean,
    selectedFiles: List<String>,
    onFileRemoved: (String) -> Unit,
    onFileSelected: (String) -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalDirection = LocalLayoutDirection.current
    val context = LocalContext.current
    val failedToSelectText = stringResource(R.string.failed_to_select_image)
    val unsupportedText = stringResource(R.string.image_input_not_supported)
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val filePath = copyAttachmentToHWorkspace(context, uri)
        if (filePath != null) {
            onFileSelected(filePath)
        } else {
            Toast.makeText(context, failedToSelectText, Toast.LENGTH_SHORT).show()
        }
    }

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
                .absolutePadding(
                    left = 5.dp,
                    top = 8.dp,
                    right = 16.dp,
                    bottom = 8.dp,
                ),
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

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 68.dp, max = 164.dp),
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
                            .heightIn(min = 68.dp, max = 164.dp),
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                            BasicTextField(
                                value = editingValue,
                                onValueChange = { next ->
                                    onUserInteraction()
                                    editingValue = next
                                    onValueChange(next.text)
                                },
                                enabled = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 68.dp, max = 164.dp)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) onUserInteraction()
                                    }
                                    .absolutePadding(
                                        left = 70.dp,
                                        top = 18.dp,
                                        right = 70.dp,
                                        bottom = 18.dp,
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
                                                text = stringResource(R.string.ask_a_question),
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

                        IconButton(
                            onClick = {
                                onUserInteraction()
                                if (chatEnabled) {
                                    attachmentPicker.launch(
                                        arrayOf(
                                            "image/*",
                                            "audio/*",
                                            "video/*",
                                            "application/pdf",
                                            "text/*",
                                        ),
                                    )
                                } else {
                                    Toast.makeText(context, unsupportedText, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.select_image),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(25.dp),
                            )
                        }

                        HSendOrStopButton(
                            canSend = chatEnabled &&
                                (editingValue.text.trim().isNotEmpty() || selectedFiles.isNotEmpty()),
                            isResponding = isResponding,
                            onSend = {
                                onUserInteraction()
                                onSend()
                            },
                            onStop = {
                                onUserInteraction()
                                onStop()
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HAttachmentEdgeAction(
    visible: Boolean,
    enabled: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onFileSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val swipeThresholdPx = with(LocalDensity.current) { 12.dp.toPx() }
    val unsupportedText = stringResource(R.string.image_input_not_supported)
    val failedToSelectText = stringResource(R.string.failed_to_select_image)

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        onVisibleChange(false)
        if (uri == null) return@rememberLauncherForActivityResult
        val filePath = copyAttachmentToHWorkspace(context, uri)
        if (filePath != null) {
            onFileSelected(filePath)
        } else {
            Toast.makeText(context, failedToSelectText, Toast.LENGTH_SHORT).show()
        }
    }

    val gripTravel by animateDpAsState(
        targetValue = if (visible) (-48).dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "HImageGripOffset",
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .absoluteOffset(x = 12.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 8.dp)
                .size(width = 62.dp, height = 68.dp),
        ) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(AbsoluteAlignment.CenterRight),
                enter = slideInHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(durationMillis = 140)),
                exit = slideOutHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    targetOffsetX = { it },
                ) + fadeOut(animationSpec = tween(durationMillis = 120)),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.48f),
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp,
                ) {
                    IconButton(
                        onClick = {
                            onVisibleChange(false)
                            if (enabled) {
                                filePicker.launch(
                                    arrayOf(
                                        "image/*",
                                        "audio/*",
                                        "video/*",
                                        "application/pdf",
                                        "text/*",
                                    ),
                                )
                            } else {
                                Toast.makeText(context, unsupportedText, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.select_image),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }
            }

            // Keep the blue strip visually identical, but give it a 30dp invisible horizontal
            // hit lane. A short left swipe opens the attachment action; a right swipe closes it.
            Box(
                modifier = Modifier
                    .align(AbsoluteAlignment.CenterRight)
                    .absoluteOffset(x = gripTravel)
                    .size(width = 30.dp, height = 68.dp)
                    .pointerInput(visible, swipeThresholdPx) {
                        var horizontalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                horizontalDrag += dragAmount
                            },
                            onDragEnd = {
                                when {
                                    !visible && horizontalDrag <= -swipeThresholdPx -> {
                                        onVisibleChange(true)
                                    }
                                    visible && horizontalDrag >= swipeThresholdPx -> {
                                        onVisibleChange(false)
                                    }
                                }
                            },
                        )
                    }
                    .clickable { onVisibleChange(!visible) },
                contentAlignment = AbsoluteAlignment.CenterRight,
            ) {
                Surface(
                    modifier = Modifier.size(width = 14.dp, height = 52.dp),
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.46f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(width = 5.dp, height = 44.dp),
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {}
                    }
                }
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

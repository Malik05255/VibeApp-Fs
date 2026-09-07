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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.presentation.ui.chat.ChatViewModel
import java.io.File

/**
 * Exact-route overload used by NavigationGraph.
 *
 * Keeping this overload separate lets the proven runtime HChatScreen continue owning all chat,
 * build, export and quick-rail behavior while this layer replaces only the visible H chat chrome.
 */
@Composable
fun HChatScreen(
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToDiagnostic: () -> Unit,
    onBackAction: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    showBackButton: Boolean = true,
) {
    val chatViewModel: ChatViewModel = hiltViewModel()
    HRefinedChatScreen(
        chatViewModel = chatViewModel,
        onNavigateToAddPlatform = onNavigateToAddPlatform,
        onNavigateToDiagnostic = onNavigateToDiagnostic,
        onBackAction = onBackAction,
        onNavigateToSettings = onNavigateToSettings,
        showBackButton = showBackButton,
    )
}

@Composable
private fun HRefinedChatScreen(
    chatViewModel: ChatViewModel,
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToDiagnostic: () -> Unit,
    onBackAction: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showBackButton: Boolean,
) {
    val chatRoom by chatViewModel.chatRoom.collectAsStateWithLifecycle()
    val projectName by chatViewModel.projectName.collectAsStateWithLifecycle()
    val groupedMessages by chatViewModel.groupedMessages.collectAsStateWithLifecycle()
    val loadingStates by chatViewModel.loadingStates.collectAsStateWithLifecycle()
    val isLoaded by chatViewModel.isLoaded.collectAsStateWithLifecycle()
    val platforms by chatViewModel.platformsInApp.collectAsStateWithLifecycle()
    val enabledPlatforms by chatViewModel.enabledPlatformsInApp.collectAsStateWithLifecycle()
    val crashPrompt by chatViewModel.crashPrompt.collectAsStateWithLifecycle()
    val question by chatViewModel.question.collectAsStateWithLifecycle()
    val selectedFiles by chatViewModel.selectedFiles.collectAsStateWithLifecycle()

    val rawProjectTitle = (projectName ?: chatRoom.title).ifBlank { "H" }
    val isArabic = LocalLayoutDirection.current == LayoutDirection.Rtl
    val displayProjectTitle = if (isArabic && rawProjectTitle.equals("Demo", ignoreCase = true)) {
        stringResource(R.string.h_ui_demo_project)
    } else {
        rawProjectTitle
    }

    val isIdle = loadingStates.all { it == ChatViewModel.LoadingState.Idle }
    val canUseChat = enabledPlatforms.isNotEmpty()
    val disabledInputText = if (platforms.isNotEmpty()) {
        stringResource(R.string.some_platforms_disabled)
    } else {
        stringResource(R.string.add_api_key_to_start_chatting)
    }

    // Match the underlying chat's hidden-history behavior so its previous welcome surface can be
    // covered exactly when the refined welcome is shown.
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
    val isWelcomeCanvas = isLoaded &&
        visibleTurnCount == 0 &&
        isIdle &&
        crashPrompt == null &&
        platforms.isNotEmpty()

    // The greeting and starter actions disappear immediately after the first entered character.
    val showStarterPrompts = isWelcomeCanvas && question.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        // Existing implementation remains the runtime source of truth for messages and all actions.
        HChatScreen(
            chatViewModel = chatViewModel,
            onNavigateToAddPlatform = onNavigateToAddPlatform,
            onNavigateToDiagnostic = onNavigateToDiagnostic,
            onBackAction = onBackAction,
            onNavigateToSettings = onNavigateToSettings,
            showBackButton = showBackButton,
        )

        // Cover only the previous central welcome content. The physical-left quick rail remains
        // untouched and fully interactive.
        if (isWelcomeCanvas) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .height(420.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {}
        }

        HPersistentChatMark(
            showStarterPrompts = showStarterPrompts,
            onSuggestion = chatViewModel::updateQuestion,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .offset(y = (-72).dp)
                .padding(horizontal = 24.dp, vertical = 150.dp),
        )

        HRefinedComposer(
            value = question,
            onValueChange = chatViewModel::updateQuestion,
            chatEnabled = canUseChat,
            disabledText = disabledInputText,
            isResponding = !isIdle,
            selectedFiles = selectedFiles,
            onFileSelected = chatViewModel::addSelectedFile,
            onFileRemoved = chatViewModel::removeSelectedFile,
            onStop = chatViewModel::stopResponding,
            onSend = chatViewModel::askQuestion,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )

        HRefinedHeader(
            projectTitle = displayProjectTitle,
            showBackButton = showBackButton,
            onBackAction = onBackAction,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun HRefinedHeader(
    projectTitle: String,
    showBackButton: Boolean,
    onBackAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
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

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
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
private fun HPersistentChatMark(
    showStarterPrompts: Boolean,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Persistent identity mark: same primary family as the original H badge, but intentionally
        // faint so it remains visible behind a long conversation without competing with messages.
        Surface(
            modifier = Modifier.size(86.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "H AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(
            visible = showStarterPrompts,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.h_ui_welcome),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HStarterChip(
                        text = stringResource(R.string.h_ui_summarize_project),
                        onClick = onSuggestion,
                        modifier = Modifier.weight(1f),
                    )
                    HStarterChip(
                        text = stringResource(R.string.h_ui_build_app),
                        onClick = onSuggestion,
                        modifier = Modifier.weight(1f),
                    )
                    HStarterChip(
                        text = stringResource(R.string.h_ui_chat),
                        onClick = onSuggestion,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HRefinedComposer(
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
    val interactionSource = remember { MutableInteractionSource() }
    val unsupportedText = stringResource(R.string.image_input_not_supported)
    val failedToSelectText = stringResource(R.string.failed_to_select_image)
    var attachmentActionVisible by remember { mutableStateOf(false) }

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
            .imePadding()
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = {},
            ),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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

            // Force only the control layout to physical LTR so the send button is always on the
            // physical left. The text field restores the user's actual language direction.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HSendOrStopButton(
                        canSend = chatEnabled && value.trim().isNotEmpty(),
                        isResponding = isResponding,
                        onSend = {
                            attachmentActionVisible = false
                            onSend()
                        },
                        onStop = onStop,
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 60.dp, max = 132.dp),
                        shape = RoundedCornerShape(30.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                        ),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                                BasicTextField(
                                    value = value,
                                    onValueChange = { if (chatEnabled) onValueChange(it) },
                                    enabled = chatEnabled,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 60.dp, max = 132.dp)
                                        .absolutePadding(
                                            left = if (attachmentActionVisible) 58.dp else 18.dp,
                                            top = 17.dp,
                                            right = 18.dp,
                                            bottom = 17.dp,
                                        ),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    minLines = 1,
                                    maxLines = 5,
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (!chatEnabled && value.isEmpty()) {
                                                Text(
                                                    text = disabledText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                )
                            }

                            // A tiny edge grip is the only visible affordance. The + itself stays
                            // hidden until the user explicitly taps this physical-left edge.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 2.dp)
                                    .size(width = 10.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { attachmentActionVisible = !attachmentActionVisible },
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    modifier = Modifier.size(width = 4.dp, height = 24.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                ) {}
                            }

                            AnimatedVisibility(
                                visible = attachmentActionVisible,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp),
                                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                    ),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                ) {
                                    IconButton(
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
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = stringResource(R.string.select_image),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
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
) {
    val enabled = isResponding || canSend
    val containerColor = when {
        isResponding -> MaterialTheme.colorScheme.primary
        canSend -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isResponding || canSend -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Surface(
        modifier = Modifier
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

@Composable
private fun HStarterChip(
    text: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable { onClick(text) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

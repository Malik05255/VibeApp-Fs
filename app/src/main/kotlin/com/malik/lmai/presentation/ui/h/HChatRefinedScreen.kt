package com.malik.lmai.presentation.ui.h

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.presentation.ui.chat.ChatScreen
import com.malik.lmai.presentation.ui.chat.ChatViewModel

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
    val enabledPlatformsInChat by chatViewModel.enabledPlatformsInChat.collectAsStateWithLifecycle()
    val crashPrompt by chatViewModel.crashPrompt.collectAsStateWithLifecycle()
    val question by chatViewModel.question.collectAsStateWithLifecycle()
    val selectedFiles by chatViewModel.selectedFiles.collectAsStateWithLifecycle()

    val persistedProjectName = projectName?.trim().orEmpty()
    val displayProjectTitle = if (persistedProjectName.isMeaningfulHProjectName()) {
        persistedProjectName
    } else {
        stringResource(R.string.h_ui_chat)
    }

    val isIdle = loadingStates.all { it == ChatViewModel.LoadingState.Idle }
    val canUseChat = enabledPlatformsInChat.isNotEmpty()
    val dismissInteractionSource = remember { MutableInteractionSource() }

    var quickRailExpanded by remember { mutableStateOf(false) }
    var attachmentActionVisible by remember { mutableStateOf(false) }

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

    val showStarterPrompts = isWelcomeCanvas && question.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        ChatScreen(
            chatViewModel = chatViewModel,
            onNavigateToAddPlatform = onNavigateToAddPlatform,
            onNavigateToDiagnostic = onNavigateToDiagnostic,
            onBackAction = onBackAction,
            onNavigateToSettings = onNavigateToSettings,
            showBackButton = showBackButton,
        )

        if (isWelcomeCanvas) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .absolutePadding(left = 64.dp, right = 28.dp)
                    .height(420.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {}
        }

        HPersistentChatMark(
            showStarterPrompts = showStarterPrompts,
            onSuggestion = { suggestion ->
                quickRailExpanded = false
                attachmentActionVisible = false
                chatViewModel.updateQuestion(suggestion)
            },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .offset(y = (-72).dp)
                .padding(horizontal = 24.dp, vertical = 150.dp),
        )

        if (quickRailExpanded || attachmentActionVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = dismissInteractionSource,
                    ) {
                        quickRailExpanded = false
                        attachmentActionVisible = false
                    },
            )
        }

        HRefinedComposer(
            value = question,
            onValueChange = chatViewModel::updateQuestion,
            chatEnabled = canUseChat,
            isResponding = !isIdle,
            selectedFiles = selectedFiles,
            onFileRemoved = chatViewModel::removeSelectedFile,
            onStop = chatViewModel::stopResponding,
            onSend = chatViewModel::askQuestion,
            onUserInteraction = {
                quickRailExpanded = false
                attachmentActionVisible = false
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            HAttachmentEdgeAction(
                visible = attachmentActionVisible,
                enabled = canUseChat,
                onVisibleChange = { visible ->
                    attachmentActionVisible = visible
                    if (visible) quickRailExpanded = false
                },
                onFileSelected = chatViewModel::addSelectedFile,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        HRefinedHeader(
            projectTitle = displayProjectTitle,
            showBackButton = showBackButton,
            onBackAction = {
                quickRailExpanded = false
                attachmentActionVisible = false
                onBackAction()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        // Always anchor the tool grip to the physical-left edge and exact vertical center,
        // regardless of Arabic/English layout direction. Slightly compress only its width.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            HRefinedQuickRail(
                expanded = quickRailExpanded,
                onExpandedChange = { expanded ->
                    quickRailExpanded = expanded
                    if (expanded) attachmentActionVisible = false
                },
                chatViewModel = chatViewModel,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToDiagnostic = onNavigateToDiagnostic,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer(
                        scaleX = 0.84f,
                        transformOrigin = TransformOrigin(0f, 0.5f),
                    ),
            )
        }
    }
}

private fun String.isMeaningfulHProjectName(): Boolean {
    if (isBlank()) return false
    return !equals("Demo", ignoreCase = true) &&
        !equals("مشروع تجريبي", ignoreCase = true) &&
        !equals("H", ignoreCase = true)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBackAction,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.go_back),
                    )
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
            ) {
                Text(
                    text = projectTitle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

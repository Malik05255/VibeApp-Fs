package com.malik.lmai.presentation.ui.h

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.presentation.ui.chat.ChatViewModel

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

        // Cover the legacy central welcome content without drawing over the physical-left rail.
        // The rail is 58dp wide; 72dp leaves its border and shadow fully visible instead of
        // clipping the right half as happened in the previous layout.
        if (isWelcomeCanvas) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .absolutePadding(left = 72.dp, right = 28.dp)
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

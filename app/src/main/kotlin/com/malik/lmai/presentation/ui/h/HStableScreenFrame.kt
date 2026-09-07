package com.malik.lmai.presentation.ui.h

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.malik.lmai.presentation.ui.chat.ChatViewModel
import com.malik.lmai.presentation.ui.setting.LanguageViewModel
import com.malik.lmai.presentation.ui.setting.SettingViewModelV2

/**
 * H screens keep one 360dp reference composition while the app-level adaptive density scales
 * that reference proportionally to the device width. A 6-inch phone, 9-inch device, or 10-inch
 * tablet therefore keeps the same visual hierarchy, spacing ratios, control positions, and
 * overall composition instead of switching to a separate tablet layout.
 */
private val HStableCanvasWidth = 360.dp

@Composable
private fun HStableScreenFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = HStableCanvasWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}

@Composable
fun HStableChatRoute(
    chatViewModel: ChatViewModel = hiltViewModel(),
    onNavigateToAddPlatform: () -> Unit,
    onNavigateToDiagnostic: () -> Unit,
    onBackAction: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showBackButton: Boolean,
) {
    HStableScreenFrame {
        HChatScreen(
            chatViewModel = chatViewModel,
            onNavigateToAddPlatform = onNavigateToAddPlatform,
            onNavigateToDiagnostic = onNavigateToDiagnostic,
            onBackAction = onBackAction,
            onNavigateToSettings = onNavigateToSettings,
            showBackButton = showBackButton,
        )
    }
}

@Composable
fun HStableSettingsRoute(
    settingViewModel: SettingViewModelV2,
    languageViewModel: LanguageViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
    onNavigateToProjectSettings: () -> Unit,
    onNavigateToAiProviderSettings: () -> Unit,
    onNavigateToGitHub: () -> Unit,
    onLogout: () -> Unit,
) {
    HStableScreenFrame {
        HSettingsScreen(
            settingViewModel = settingViewModel,
            languageViewModel = languageViewModel,
            onNavigationClick = onNavigationClick,
            onNavigateToProjectSettings = onNavigateToProjectSettings,
            onNavigateToAiProviderSettings = onNavigateToAiProviderSettings,
            onNavigateToGitHub = onNavigateToGitHub,
            onLogout = onLogout,
        )
    }
}

package com.malik.lmai.presentation.ui.h

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.malik.lmai.presentation.ui.chat.ChatViewModel
import com.malik.lmai.presentation.ui.setting.LanguageViewModel
import com.malik.lmai.presentation.ui.setting.SettingViewModelV2

/**
 * Canonical H composition.
 *
 * The app-level adaptive density calculates one uniform scale from both available width and height.
 * This frame then keeps the actual H canvas at the Honor reference aspect ratio (360 x 800), so
 * controls, composer position, spacing, typography hierarchy, and the left rail retain the same
 * visual coordinates. Different aspect ratios receive neutral surrounding space rather than a
 * rearranged UI.
 */
private val HStableCanvasWidth = 360.dp
private val HStableCanvasHeight = 800.dp

@Composable
private fun HStableScreenFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(HStableCanvasWidth)
                .height(HStableCanvasHeight),
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

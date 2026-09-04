package com.vibe.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.vibe.app.R
import com.vibe.app.presentation.ui.auth.AuthViewModel
import com.vibe.app.presentation.ui.chat.ChatScreen
import com.vibe.app.presentation.ui.diagnostic.DiagnosticScreen
import com.vibe.app.presentation.ui.github.GitHubSettingsScreen
import com.vibe.app.presentation.ui.home.HomeScreen
import com.vibe.app.presentation.ui.setting.AiProviderSettingsScreen
import com.vibe.app.presentation.ui.setting.LanguageViewModel
import com.vibe.app.presentation.ui.setting.PlatformSettingScreen
import com.vibe.app.presentation.ui.setting.ProjectSettingsScreen
import com.vibe.app.presentation.ui.setting.SettingScreen
import com.vibe.app.presentation.ui.setting.SettingViewModelV2
import com.vibe.app.presentation.ui.setup.SetupCompleteScreen
import com.vibe.app.presentation.ui.setup.SetupPlatformTypeScreen
import com.vibe.app.presentation.ui.setup.SetupPlatformWizardScreen
import com.vibe.app.presentation.ui.setup.SetupViewModelV2

@Composable
fun SetupNavGraph(navController: NavHostController) {
    val languageViewModel: LanguageViewModel = hiltViewModel()
    val currentLanguage by languageViewModel.language.collectAsStateWithLifecycle()
    val languageSelected = languageViewModel.isLanguageSelected()
    val startDestination = if (languageSelected) Route.CHAT_LIST else Route.LANGUAGE_SELECTION

    CompositionLocalProvider(
        LocalLayoutDirection provides if (currentLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        NavHost(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            navController = navController,
            startDestination = startDestination
        ) {
            languageSelectionNavigation(navController, languageViewModel)
            homeScreenNavigation(navController)
            setupNavigation(navController)
            settingNavigation(navController)
            chatScreenNavigation(navController)
            diagnosticNavigation(navController)
        }
    }
}

fun NavGraphBuilder.languageSelectionNavigation(
    navController: NavHostController,
    languageViewModel: LanguageViewModel
) {
    composable(route = Route.LANGUAGE_SELECTION) {
        LanguageSelectionScreen(
            languageViewModel = languageViewModel,
            onLanguageConfirmed = {
                languageViewModel.confirmLanguage()
                navController.navigate(Route.CHAT_LIST) {
                    popUpTo(Route.LANGUAGE_SELECTION) { inclusive = true }
                    launchSingleTop = true
                }
            }
        )
    }
}

@Composable
private fun LanguageSelectionScreen(
    languageViewModel: LanguageViewModel,
    onLanguageConfirmed: () -> Unit
) {
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsStateWithLifecycle()
    val layoutDirection = if (selectedLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.choose_app_language),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(32.dp))
            LanguageSelectionItem(
                title = if (selectedLanguage == "ar") "العربية" else "Arabic",
                selected = selectedLanguage == "ar",
                onClick = { languageViewModel.selectLanguage("ar") }
            )
            Spacer(Modifier.height(8.dp))
            LanguageSelectionItem(
                title = if (selectedLanguage == "ar") "الإنجليزية" else "English",
                selected = selectedLanguage == "en",
                onClick = { languageViewModel.selectLanguage("en") }
            )
            Spacer(Modifier.height(32.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = onLanguageConfirmed) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}

@Composable
private fun LanguageSelectionItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

fun NavGraphBuilder.setupNavigation(navController: NavHostController) {
    navigation(startDestination = Route.SETUP_PLATFORM_TYPE, route = Route.SETUP_ROUTE) {
        composable(route = Route.SETUP_PLATFORM_TYPE) {
            val parentEntry = remember(it) { navController.getBackStackEntry(Route.SETUP_ROUTE) }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformTypeScreen(
                setupViewModel = setupViewModel,
                onPlatformTypeSelected = { navController.navigate(Route.SETUP_PLATFORM_WIZARD) },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_PLATFORM_WIZARD) {
            val parentEntry = remember(it) { navController.getBackStackEntry(Route.SETUP_ROUTE) }
            val setupViewModel: SetupViewModelV2 = hiltViewModel(parentEntry)
            SetupPlatformWizardScreen(
                setupViewModel = setupViewModel,
                onComplete = {
                    val fromSettings = runCatching { navController.getBackStackEntry(Route.SETTING_ROUTE) }.isSuccess
                    val fromChat = runCatching { navController.getBackStackEntry(Route.CHAT_ROOM) }.isSuccess
                    if (fromSettings) {
                        navController.popBackStack(Route.SETTINGS, inclusive = false)
                    } else if (fromChat) {
                        navController.popBackStack(Route.CHAT_ROOM, inclusive = false)
                    } else {
                        navController.navigate(Route.SETUP_COMPLETE) {
                            popUpTo(Route.SETUP_ROUTE) { inclusive = false }
                        }
                    }
                },
                onBackAction = { navController.navigateUp() }
            )
        }
        composable(route = Route.SETUP_COMPLETE) {
            SetupCompleteScreen(
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Route.SETUP_ROUTE) { inclusive = true }
                    }
                },
                onBackAction = { navController.navigateUp() }
            )
        }
    }
}

fun NavGraphBuilder.homeScreenNavigation(navController: NavHostController) {
    composable(Route.CHAT_LIST) {
        HomeScreen(
            settingOnClick = { navController.navigate(Route.SETTING_ROUTE) { launchSingleTop = true } },
            onProjectClick = { chatId, enabledPlatforms ->
                val enabledPlatformString = enabledPlatforms.joinToString(",")
                navController.navigate(Route.CHAT_ROOM.replace("{chatRoomId}", "$chatId").replace("{enabledPlatforms}", enabledPlatformString))
            },
            navigateToChat = { chatId, enabledPlatforms ->
                val enabledPlatformString = enabledPlatforms.joinToString(",")
                navController.navigate(Route.CHAT_ROOM.replace("{chatRoomId}", "$chatId").replace("{enabledPlatforms}", enabledPlatformString))
            }
        )
    }
}

fun NavGraphBuilder.chatScreenNavigation(navController: NavHostController) {
    composable(
        Route.CHAT_ROOM,
        arguments = listOf(
            navArgument("chatRoomId") { type = NavType.IntType },
            navArgument("enabledPlatforms") { defaultValue = "" }
        )
    ) { backStackEntry ->
        val chatRoomId = backStackEntry.arguments?.getInt("chatRoomId") ?: return@composable
        ChatScreen(
            onNavigateToAddPlatform = { navController.navigate(Route.SETUP_ROUTE) { launchSingleTop = true } },
            onNavigateToDiagnostic = { navController.navigate(Route.DIAGNOSTIC.replace("{chatRoomId}", "$chatRoomId")) },
            onBackAction = { navController.navigateUp() }
        )
    }
}

fun NavGraphBuilder.diagnosticNavigation(navController: NavHostController) {
    composable(Route.DIAGNOSTIC, arguments = listOf(navArgument("chatRoomId") { type = NavType.IntType })) {
        DiagnosticScreen(onBackAction = { navController.navigateUp() })
    }
}

fun NavGraphBuilder.settingNavigation(navController: NavHostController) {
    navigation(startDestination = Route.SETTINGS, route = Route.SETTING_ROUTE) {
        composable(Route.SETTINGS) {
            val parentEntry = remember(it) { navController.getBackStackEntry(Route.SETTING_ROUTE) }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            val authViewModel: AuthViewModel = hiltViewModel()

            SettingScreen(
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() },
                onNavigateToProjectSettings = { navController.navigate(Route.PROJECT_SETTINGS) },
                onNavigateToAiProviderSettings = { navController.navigate(Route.AI_PROVIDER_SETTINGS) },
                onNavigateToGitHub = { navController.navigate(Route.GITHUB_SETTINGS) },
                onLogout = {
                    authViewModel.logout {
                        navController.context.getSharedPreferences("language_settings", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("language_selected", true)
                            .apply()
                        val intent = navController.context.packageManager
                            .getLaunchIntentForPackage(navController.context.packageName)
                            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        if (intent != null) navController.context.startActivity(intent)
                    }
                }
            )
        }

        composable(Route.PROJECT_SETTINGS) {
            ProjectSettingsScreen(onBack = { navController.navigateUp() })
        }

        composable(Route.AI_PROVIDER_SETTINGS) {
            val parentEntry = remember(it) { navController.getBackStackEntry(Route.SETTING_ROUTE) }
            val settingViewModel: SettingViewModelV2 = hiltViewModel(parentEntry)
            AiProviderSettingsScreen(
                settingViewModel = settingViewModel,
                onBack = { navController.navigateUp() },
                onNavigateToAddPlatform = { navController.navigate(Route.SETUP_ROUTE) },
                onNavigateToPlatformSetting = { platformUid ->
                    navController.navigate(Route.PLATFORM_SETTINGS.replace("{platformUid}", platformUid))
                },
            )
        }

        composable(Route.GITHUB_SETTINGS) {
            GitHubSettingsScreen(
                onBack = { navController.navigateUp() },
                onProjectClick = { project ->
                    navController.navigate(
                        Route.CHAT_ROOM
                            .replace("{chatRoomId}", project.chatId.toString())
                            .replace("{enabledPlatforms}", "")
                    ) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            Route.PLATFORM_SETTINGS,
            arguments = listOf(navArgument("platformUid") { type = NavType.StringType })
        ) {
            PlatformSettingScreen(onNavigationClick = { navController.navigateUp() })
        }
    }
}

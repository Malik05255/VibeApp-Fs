from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# 1) Chat: make Settings part of the three-dot menu and make the back arrow optional.
chat_path = Path("app/src/main/kotlin/com/malik/lmai/presentation/ui/chat/ChatScreen.kt")
chat = chat_path.read_text()

chat = replace_once(
    chat,
    """fun ChatScreen(\n    chatViewModel: ChatViewModel = hiltViewModel(),\n    onNavigateToAddPlatform: () -> Unit,\n    onNavigateToDiagnostic: () -> Unit,\n    onBackAction: () -> Unit\n) {""",
    """fun ChatScreen(\n    chatViewModel: ChatViewModel = hiltViewModel(),\n    onNavigateToAddPlatform: () -> Unit,\n    onNavigateToDiagnostic: () -> Unit,\n    onBackAction: () -> Unit,\n    onNavigateToSettings: () -> Unit = {},\n    showBackButton: Boolean = true,\n) {""",
    "ChatScreen signature",
)

chat = replace_once(
    chat,
    """                showRestoreHistoryButton = hasPreviousHistory && !showPreviousHistory,\n                onRestoreHistoryClick = { showPreviousHistory = true },\n                onBackAction,\n                scrollBehavior,""",
    """                showRestoreHistoryButton = hasPreviousHistory && !showPreviousHistory,\n                onRestoreHistoryClick = { showPreviousHistory = true },\n                showBackButton = showBackButton,\n                onBackAction = onBackAction,\n                scrollBehavior = scrollBehavior,""",
    "ChatTopBar call",
)

chat = replace_once(
    chat,
    """                onClearChatHistoryClick = { isClearChatDialogOpen = true },\n                onDiagnosticClick = onNavigateToDiagnostic,\n                onOpenSnapshotHistory = chatViewModel::openSnapshotHistory,""",
    """                onClearChatHistoryClick = { isClearChatDialogOpen = true },\n                onDiagnosticClick = onNavigateToDiagnostic,\n                onSettingsClick = onNavigateToSettings,\n                onOpenSnapshotHistory = chatViewModel::openSnapshotHistory,""",
    "ChatTopBar settings callback",
)

chat = replace_once(
    chat,
    """    showRestoreHistoryButton: Boolean,\n    onRestoreHistoryClick: () -> Unit,\n    onBackAction: () -> Unit,""",
    """    showRestoreHistoryButton: Boolean,\n    onRestoreHistoryClick: () -> Unit,\n    showBackButton: Boolean,\n    onBackAction: () -> Unit,""",
    "ChatTopBar back visibility parameter",
)

# The first occurrence here is ChatTopBar; ChatDropdownMenu is patched separately below.
needle = """    onClearChatHistoryClick: () -> Unit,\n    onDiagnosticClick: () -> Unit,\n    onOpenSnapshotHistory: () -> Unit,"""
if chat.count(needle) != 2:
    raise SystemExit(f"settings callback signatures: expected 2 matches, found {chat.count(needle)}")
chat = chat.replace(
    needle,
    """    onClearChatHistoryClick: () -> Unit,\n    onDiagnosticClick: () -> Unit,\n    onSettingsClick: () -> Unit,\n    onOpenSnapshotHistory: () -> Unit,""",
    2,
)

chat = replace_once(
    chat,
    """            navigationIcon = {\n                IconButton(\n                    onClick = onBackAction\n                ) {\n                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))\n                }\n            },""",
    """            navigationIcon = {\n                if (showBackButton) {\n                    IconButton(onClick = onBackAction) {\n                        Icon(\n                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,\n                            contentDescription = stringResource(R.string.go_back),\n                        )\n                    }\n                }\n            },""",
    "conditional chat back button",
)

chat = replace_once(
    chat,
    """                    onDiagnosticClick = {\n                        onDiagnosticClick()\n                    },\n                    onOpenSnapshotHistory = {""",
    """                    onDiagnosticClick = {\n                        onDiagnosticClick()\n                    },\n                    onSettingsClick = {\n                        onSettingsClick()\n                        isDropDownMenuExpanded = false\n                    },\n                    onOpenSnapshotHistory = {""",
    "settings menu callback wiring",
)

chat = replace_once(
    chat,
    """        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))\n\n        DropdownMenuItem(\n            enabled = isChatMenuEnabled,\n            text = {\n                Text(\n                    text = stringResource(R.string.clear_chat_history),""",
    """        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))\n\n        DropdownMenuItem(\n            text = { Text(text = stringResource(R.string.settings)) },\n            onClick = onSettingsClick,\n            leadingIcon = {\n                Icon(Icons.Outlined.Settings, contentDescription = null)\n            },\n        )\n\n        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))\n\n        DropdownMenuItem(\n            enabled = isChatMenuEnabled,\n            text = {\n                Text(\n                    text = stringResource(R.string.clear_chat_history),""",
    "Settings dropdown item",
)

# Fix the one legacy visible assistant name that survived the final H rebrand.
legacy_label = 'text = "\\u0645\\u062d\\u0645\\u062f"'
if legacy_label in chat:
    chat = chat.replace(legacy_label, 'text = "H"', 1)

chat_path.write_text(chat)


# 2) Keep project persistence internally, but make it only bootstrap the user's primary workspace.
home_vm_path = Path("app/src/main/kotlin/com/malik/lmai/presentation/ui/home/HomeViewModel.kt")
home_vm = home_vm_path.read_text()
marker = "    fun createNewProject() {\n"
if home_vm.count(marker) != 1:
    raise SystemExit(f"HomeViewModel insertion marker count={home_vm.count(marker)}")

open_primary = r'''    fun openPrimaryProject() {
        val currentState = _projectListState.value
        if (
            currentState.creationState is ProjectCreationState.InProgress ||
            currentState.navigationEvent != null
        ) return

        viewModelScope.launch {
            _projectListState.update {
                it.copy(creationState = ProjectCreationState.InProgress("primary"))
            }

            val projects = runCatching { projectRepository.fetchProjects() }
                .getOrElse { error ->
                    Log.e("HomeViewModel", "Failed to load primary workspace", error)
                    _projectListState.update {
                        it.copy(
                            creationState = ProjectCreationState.Failed(
                                error.message ?: "Unable to open workspace",
                            ),
                        )
                    }
                    return@launch
                }

            val primaryProject = projects.firstOrNull()
            if (primaryProject != null) {
                runCatching {
                    projectInitializer.ensureProjectLauncherResources(primaryProject.project.projectId)
                }.onFailure { error ->
                    Log.w("HomeViewModel", "Primary workspace launcher refresh failed", error)
                }

                var enabledPlatforms = primaryProject.chat.enabledPlatform
                if (enabledPlatforms.isEmpty()) {
                    val platforms = runCatching { freeAiBootstrapper.ensureReady() }
                        .getOrElse {
                            Log.w("HomeViewModel", "Free AI bootstrap failed while opening workspace", it)
                            runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(emptyList())
                        }
                    _platformState.update { platforms }
                    enabledPlatforms = platforms.filter { it.enabled }.map { it.uid }
                }

                _projectListState.update {
                    it.copy(
                        projects = projects,
                        selectedProjects = List(projects.size) { false },
                        creationState = ProjectCreationState.Idle,
                        navigationEvent = NavigationEvent.OpenProject(
                            chatId = primaryProject.project.chatId,
                            enabledPlatforms = enabledPlatforms,
                        ),
                    )
                }
                return@launch
            }

            val platforms = runCatching { freeAiBootstrapper.ensureReady() }
                .getOrElse {
                    Log.w("HomeViewModel", "Free AI bootstrap failed before workspace creation", it)
                    runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(_platformState.value)
                }
            _platformState.update { platforms }
            val enabledPlatforms = platforms.filter { it.enabled }.map { it.uid }

            runCatching {
                projectManager.createProject(enabledPlatforms = enabledPlatforms)
            }.onSuccess { project ->
                _projectListState.update {
                    it.copy(
                        creationState = ProjectCreationState.Idle,
                        navigationEvent = NavigationEvent.OpenProject(
                            chatId = project.chatId,
                            enabledPlatforms = enabledPlatforms,
                        ),
                    )
                }
            }.onFailure { error ->
                Log.e("HomeViewModel", "Failed to create primary workspace", error)
                _projectListState.update {
                    it.copy(
                        creationState = ProjectCreationState.Failed(
                            error.message ?: "Unable to create workspace",
                        ),
                    )
                }
            }
        }
    }

'''
home_vm = home_vm.replace(marker, open_primary + marker, 1)
home_vm_path.write_text(home_vm)


# 3) Turn the old CHAT_LIST route into an invisible bootstrap route; chat becomes the visible root.
nav_path = Path("app/src/main/kotlin/com/malik/lmai/presentation/common/NavigationGraph.kt")
nav = nav_path.read_text()
nav = replace_once(
    nav,
    "import androidx.compose.material3.Button\n",
    "import androidx.compose.material3.Button\nimport androidx.compose.material3.CircularProgressIndicator\n",
    "CircularProgressIndicator import",
)
nav = replace_once(
    nav,
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n",
    "LaunchedEffect import",
)
nav = replace_once(
    nav,
    "import com.malik.lmai.presentation.ui.home.HomeScreen\n",
    "import com.malik.lmai.presentation.ui.home.HomeViewModel\n",
    "HomeScreen import replacement",
)

old_home_navigation = '''fun NavGraphBuilder.homeScreenNavigation(navController: NavHostController) {
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
'''
new_home_navigation = '''fun NavGraphBuilder.homeScreenNavigation(navController: NavHostController) {
    composable(Route.CHAT_LIST) {
        val homeViewModel: HomeViewModel = hiltViewModel()
        val projectListState by homeViewModel.projectListState.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            homeViewModel.openPrimaryProject()
        }

        LaunchedEffect(projectListState.navigationEvent) {
            val event = projectListState.navigationEvent ?: return@LaunchedEffect
            when (event) {
                is HomeViewModel.NavigationEvent.OpenProject -> {
                    val enabledPlatformString = event.enabledPlatforms.joinToString(",")
                    val route = Route.CHAT_ROOM
                        .replace("{chatRoomId}", event.chatId.toString())
                        .replace("{enabledPlatforms}", enabledPlatformString)
                    homeViewModel.consumeNavigationEvent()
                    navController.navigate(route) {
                        popUpTo(Route.CHAT_LIST) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val creationState = projectListState.creationState) {
                is HomeViewModel.ProjectCreationState.Failed -> {
                    Text(
                        text = creationState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = homeViewModel::openPrimaryProject) {
                        Text(stringResource(R.string.retry))
                    }
                }
                else -> CircularProgressIndicator()
            }
        }
    }
}
'''
nav = replace_once(nav, old_home_navigation, new_home_navigation, "projects screen navigation")

old_chat_navigation = '''        ChatScreen(
            onNavigateToAddPlatform = { navController.navigate(Route.SETUP_ROUTE) { launchSingleTop = true } },
            onNavigateToDiagnostic = { navController.navigate(Route.DIAGNOSTIC.replace("{chatRoomId}", "$chatRoomId")) },
            onBackAction = { navController.navigateUp() }
        )'''
new_chat_navigation = '''        val showBackButton = navController.previousBackStackEntry != null
        ChatScreen(
            onNavigateToAddPlatform = { navController.navigate(Route.SETUP_ROUTE) { launchSingleTop = true } },
            onNavigateToDiagnostic = { navController.navigate(Route.DIAGNOSTIC.replace("{chatRoomId}", "$chatRoomId")) },
            onBackAction = { navController.navigateUp() },
            onNavigateToSettings = { navController.navigate(Route.SETTING_ROUTE) { launchSingleTop = true } },
            showBackButton = showBackButton,
        )'''
nav = replace_once(nav, old_chat_navigation, new_chat_navigation, "chat Settings navigation")
nav_path.write_text(nav)


# 4) Remove the projects-list screen itself. Internal project persistence remains for the workspace.
home_screen_path = Path("app/src/main/kotlin/com/malik/lmai/presentation/ui/home/HomeScreen.kt")
if not home_screen_path.exists():
    raise SystemExit("HomeScreen.kt is missing before migration")
home_screen_path.unlink()

# Remove all temporary migration machinery from the final branch diff.
Path(".github/workflows/one-shot-single-workspace.yml").unlink(missing_ok=True)
Path(".github/scripts/apply-single-workspace.py").unlink(missing_ok=True)

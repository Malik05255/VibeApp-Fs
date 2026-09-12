from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file_path = Path(path)
    text = file_path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}")
    file_path.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Give H chat a private execution anchor even when the owner has not enabled a BYOK provider.
chat_vm = "app/src/main/kotlin/com/malik/lmai/presentation/ui/chat/ChatViewModel.kt"
replace_once(
    chat_vm,
    "import com.malik.lmai.feature.agent.service.BuildMutex\n",
    "import com.malik.lmai.feature.agent.service.BuildMutex\n"
    "import com.malik.lmai.feature.ai.AiProviderOrigin\n"
    "import com.malik.lmai.feature.ai.FreeAiBootstrapper\n",
)
replace_once(
    chat_vm,
    "    private val settingRepository: SettingRepository,\n"
    "    private val projectRepository: ProjectRepository,\n",
    "    private val settingRepository: SettingRepository,\n"
    "    private val freeAiBootstrapper: FreeAiBootstrapper,\n"
    "    private val projectRepository: ProjectRepository,\n",
)
replace_once(
    chat_vm,
    '''    private suspend fun refreshPlatformsInternal() {
        val allPlatforms = settingRepository.fetchPlatformV2s()
        _platformsInApp.update { allPlatforms }
        val enabledPlatforms = allPlatforms.filter { it.enabled }
        _enabledPlatformsInApp.update { enabledPlatforms }

        val currentEnabledUids = enabledPlatforms.map { it.uid }
        if (currentEnabledUids.isNotEmpty() && currentEnabledUids != _enabledPlatformsInChat.value) {
            _enabledPlatformsInChat.update { currentEnabledUids }
            _loadingStates.update { List(currentEnabledUids.size) { LoadingState.Idle } }
            _chatRoom.update { it.copy(enabledPlatform = currentEnabledUids) }
        }

        initializeChatPlatformModels(allPlatforms)
    }
''',
    '''    private suspend fun refreshPlatformsInternal() {
        // H owns a private execution pool. A fresh install may intentionally have no
        // owner-managed provider enabled, so bootstrap H before gating the composer.
        val allPlatforms = runCatching { freeAiBootstrapper.ensureReady() }
            .getOrElse { settingRepository.fetchPlatformV2s() }
        _platformsInApp.update { allPlatforms }

        // Internal H routes stay hidden/disabled in provider settings. They are execution
        // anchors only; ProviderAgentGatewayRouter still owns free/BYOK routing per turn.
        val userEnabledPlatforms = allPlatforms.filter { platform ->
            platform.enabled && AiProviderOrigin.of(platform) == AiProviderOrigin.EXTERNAL
        }
        val hCore = allPlatforms.firstOrNull { platform ->
            AiProviderOrigin.of(platform) == AiProviderOrigin.INTERNAL_FREE &&
                AiProviderOrigin.baseProviderId(platform.provider) == "local"
        } ?: allPlatforms.firstOrNull { platform ->
            AiProviderOrigin.of(platform) == AiProviderOrigin.INTERNAL_FREE
        }

        val executionPlatforms = if (userEnabledPlatforms.isNotEmpty()) {
            userEnabledPlatforms
        } else {
            listOfNotNull(hCore)
        }
        _enabledPlatformsInApp.update { executionPlatforms }

        // Never preserve a stale provider UID after BYOK is removed. Fall back to H Core
        // so both new and existing chats remain sendable without exposing hidden routes.
        val targetUids = if (userEnabledPlatforms.isNotEmpty()) {
            userEnabledPlatforms.map { it.uid }
        } else {
            listOfNotNull(hCore?.uid)
        }
        if (targetUids != _enabledPlatformsInChat.value) {
            _enabledPlatformsInChat.update { targetUids }
            _loadingStates.update { List(targetUids.size) { LoadingState.Idle } }
            _chatRoom.update { it.copy(enabledPlatform = targetUids) }
        }

        initializeChatPlatformModels(allPlatforms)
    }
''',
)
replace_once(
    chat_vm,
    '''            "pdf", "txt", "doc", "docx", "xls", "xlsx" -> "document"
            else -> null
''',
    '''            "pdf", "txt", "doc", "docx", "xls", "xlsx", "md", "csv", "json" -> "document"
            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus" -> "audio"
            "mp4", "mov", "m4v", "mpeg", "mpg", "3gp", "webm" -> "video"
            else -> null
''',
)

# 2) Make attachments visible in the composer and support image/audio/video (+ existing docs).
composer = "app/src/main/kotlin/com/malik/lmai/presentation/ui/h/HChatComposer.kt"
replace_once(
    composer,
    "    onFileRemoved: (String) -> Unit,\n    onStop: () -> Unit,\n",
    "    onFileRemoved: (String) -> Unit,\n    onFileSelected: (String) -> Unit,\n    onStop: () -> Unit,\n",
)
replace_once(
    composer,
    '''    val originalDirection = LocalLayoutDirection.current

    var editingValue by remember {
''',
    '''    val originalDirection = LocalLayoutDirection.current
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
''',
)
replace_once(
    composer,
    '''                                    .absolutePadding(
                                        left = 70.dp,
                                        top = 18.dp,
                                        right = 24.dp,
                                        bottom = 18.dp,
                                    ),
''',
    '''                                    .absolutePadding(
                                        left = 70.dp,
                                        top = 18.dp,
                                        right = 70.dp,
                                        bottom = 18.dp,
                                    ),
''',
)
replace_once(
    composer,
    '''                        HSendOrStopButton(
                            canSend = chatEnabled &&
                                (editingValue.text.trim().isNotEmpty() || selectedFiles.isNotEmpty()),
''',
    '''                        IconButton(
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
''',
)
replace_once(
    composer,
    "        contract = ActivityResultContracts.GetContent(),\n",
    "        contract = ActivityResultContracts.OpenDocument(),\n",
)
replace_once(
    composer,
    '                                filePicker.launch("image/*")\n',
    '''                                filePicker.launch(
                                    arrayOf(
                                        "image/*",
                                        "audio/*",
                                        "video/*",
                                        "application/pdf",
                                        "text/*",
                                    ),
                                )
''',
)
replace_once(
    composer,
    "// hit lane. A short left swipe opens the image action; a right swipe closes it.\n",
    "// hit lane. A short left swipe opens the attachment action; a right swipe closes it.\n",
)

# 3) Wire the new visible attachment action to ChatViewModel.
refined = "app/src/main/kotlin/com/malik/lmai/presentation/ui/h/HChatRefinedScreen.kt"
replace_once(
    refined,
    "            onFileRemoved = chatViewModel::removeSelectedFile,\n"
    "            onStop = chatViewModel::stopResponding,\n",
    "            onFileRemoved = chatViewModel::removeSelectedFile,\n"
    "            onFileSelected = chatViewModel::addSelectedFile,\n"
    "            onStop = chatViewModel::stopResponding,\n",
)

# 4) Preprocess audio/video/document attachments through H's existing transient media path.
router = "app/src/main/kotlin/com/malik/lmai/feature/agent/loop/ProviderAgentGatewayRouter.kt"
replace_once(
    router,
    "import com.malik.lmai.feature.assistant.HAssistantContext\n",
    "import com.malik.lmai.feature.assistant.HAssistantContext\n"
    "import com.malik.lmai.feature.assistant.HAppMediaPreprocessor\n",
)
replace_once(
    router,
    "    private val openRouterCredentialStore: OpenRouterCredentialStore,\n"
    "    private val mohammedAssistantContext: HAssistantContext,\n",
    "    private val openRouterCredentialStore: OpenRouterCredentialStore,\n"
    "    private val hAppMediaPreprocessor: HAppMediaPreprocessor,\n"
    "    private val mohammedAssistantContext: HAssistantContext,\n",
)
replace_once(
    router,
    '''        val preparedRequest = runCatching {
            mohammedAssistantContext.prepare(request)
        }.getOrDefault(request)

        val userFacingRequest = ChatTurnPolicy.adapt(preparedRequest)
''',
    '''        val mediaPreparedRequest = try {
            hAppMediaPreprocessor.prepare(request)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            request
        }
        val preparedRequest = try {
            mohammedAssistantContext.prepare(mediaPreparedRequest)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            mediaPreparedRequest
        }

        val userFacingRequest = ChatTurnPolicy.adapt(preparedRequest)
''',
)

print("H chat send/media hotfix applied")

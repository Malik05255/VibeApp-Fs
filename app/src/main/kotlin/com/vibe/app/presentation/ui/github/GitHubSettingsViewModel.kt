package com.vibe.app.presentation.ui.github

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.BuildConfig
import com.vibe.app.data.database.dao.ProjectDao
import com.vibe.app.data.database.entity.Project
import com.vibe.app.feature.github.GitHubActionsApi
import com.vibe.app.feature.github.GitHubApi
import com.vibe.app.feature.github.GitHubApiException
import com.vibe.app.feature.github.GitHubCredentialStore
import com.vibe.app.feature.github.GitHubOAuthCallbackBus
import com.vibe.app.feature.github.GitHubProjectCandidate
import com.vibe.app.feature.github.GitHubRepository
import com.vibe.app.feature.github.GitHubWorkflowRun
import com.vibe.app.presentation.ui.auth.GoogleAccountSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GitHubSettingsError {
    OAUTH_NOT_CONFIGURED,
    AUTH_CANCELLED,
    CODE_EXPIRED,
    INVALID_RESPONSE,
    SIGN_IN_FAILED,
    CONNECT_FAILED,
    PROJECTS_LOAD_FAILED,
    CLOUD_BUILD_PERMISSION_DENIED,
    CLOUD_BUILD_FAILED,
}

enum class GitHubCloudBuildStatus {
    IDLE,
    PREPARING,
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class GitHubSettingsState(
    val connectedLogin: String? = null,
    val repositories: List<GitHubRepository> = emptyList(),
    val selectedRepositoryFullName: String? = null,
    val activeRepositoryFullName: String? = null,
    val githubProjects: List<GitHubProjectCandidate> = emptyList(),
    val projects: List<Project> = emptyList(),
    val loading: Boolean = false,
    val projectsLoading: Boolean = false,
    val authorizationUri: String? = null,
    val deviceUserCode: String? = null,
    val verificationUri: String? = null,
    val error: GitHubSettingsError? = null,
    val cloudBuildStatus: GitHubCloudBuildStatus = GitHubCloudBuildStatus.IDLE,
    val cloudBuildRunId: Long? = null,
    val cloudBuildUrl: String? = null,
    val cloudBuildProjectPath: String? = null,
    val cloudBuildRequestId: String? = null,
)

@HiltViewModel
class GitHubSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GitHubApi,
    private val actionsApi: GitHubActionsApi,
    private val credentialStore: GitHubCredentialStore,
    private val projectDao: ProjectDao,
) : ViewModel() {
    private val _state = MutableStateFlow(
        GitHubSettingsState(
            activeRepositoryFullName = credentialStore.getSelectedRepository(),
        )
    )
    val state: StateFlow<GitHubSettingsState> = _state.asStateFlow()
    private var authorizationJob: Job? = null
    private var projectLoadJob: Job? = null
    private var cloudBuildJob: Job? = null

    init {
        viewModelScope.launch {
            GitHubOAuthCallbackBus.callback.filterNotNull().collect(::handleOAuthCallback)
        }
        credentialStore.getToken()?.let(::connectWithToken)
    }

    fun startSignIn() {
        val clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID.trim()
        if (clientId.isEmpty()) {
            _state.value = _state.value.copy(error = GitHubSettingsError.OAUTH_NOT_CONFIGURED)
            return
        }

        if (BuildConfig.GITHUB_OAUTH_CLIENT_SECRET.isNotBlank()) {
            startBrowserAuthorization(clientId)
        } else {
            startDeviceAuthorization(clientId)
        }
    }

    private fun startBrowserAuthorization(clientId: String) {
        authorizationJob?.cancel()
        credentialStore.clearPendingOAuth()

        val verifier = randomUrlSafe(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val state = randomUrlSafe(32)
        val redirectUri = oauthRedirectUri()
        credentialStore.savePendingOAuth(state, verifier)

        _state.value = _state.value.copy(
            loading = true,
            error = null,
            deviceUserCode = null,
            verificationUri = null,
            authorizationUri = api.buildAuthorizationUrl(
                clientId = clientId,
                redirectUri = redirectUri,
                state = state,
                codeChallenge = challenge,
            ),
        )
    }

    private fun handleOAuthCallback(uri: Uri) {
        GitHubOAuthCallbackBus.consume(uri)

        val expectedState = credentialStore.getPendingOAuthState()
        val verifier = credentialStore.getPendingOAuthVerifier()
        val returnedState = uri.getQueryParameter("state")
        val code = uri.getQueryParameter("code")
        val oauthError = uri.getQueryParameter("error")

        if (!oauthError.isNullOrBlank()) {
            credentialStore.clearPendingOAuth()
            _state.value = _state.value.copy(
                loading = false,
                authorizationUri = null,
                error = GitHubSettingsError.AUTH_CANCELLED,
            )
            return
        }

        if (expectedState.isNullOrBlank() ||
            verifier.isNullOrBlank() ||
            returnedState != expectedState ||
            code.isNullOrBlank()
        ) {
            credentialStore.clearPendingOAuth()
            _state.value = _state.value.copy(
                loading = false,
                authorizationUri = null,
                error = GitHubSettingsError.INVALID_RESPONSE,
            )
            return
        }

        authorizationJob?.cancel()
        authorizationJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, authorizationUri = null, error = null)
            runCatching {
                api.exchangeAuthorizationCode(
                    clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID,
                    clientSecret = BuildConfig.GITHUB_OAUTH_CLIENT_SECRET,
                    code = code,
                    redirectUri = oauthRedirectUri(),
                    codeVerifier = verifier,
                )
            }.onSuccess { tokenResponse ->
                credentialStore.clearPendingOAuth()
                val token = tokenResponse.accessToken
                if (token.isNullOrBlank()) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = GitHubSettingsError.INVALID_RESPONSE,
                    )
                } else {
                    connectWithToken(token)
                }
            }.onFailure {
                credentialStore.clearPendingOAuth()
                _state.value = _state.value.copy(
                    loading = false,
                    error = GitHubSettingsError.SIGN_IN_FAILED,
                )
            }
        }
    }

    private fun startDeviceAuthorization(clientId: String) {
        authorizationJob?.cancel()
        authorizationJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null,
                authorizationUri = null,
                deviceUserCode = null,
                verificationUri = null,
            )
            try {
                val device = api.startDeviceAuthorization(clientId)
                _state.value = _state.value.copy(
                    loading = false,
                    deviceUserCode = device.userCode,
                    verificationUri = api.buildDeviceVerificationUrl(
                        verificationUri = device.verificationUri,
                        userCode = device.userCode,
                    ),
                )

                val expiresAt = System.currentTimeMillis() + device.expiresIn * 1_000
                var pollingInterval = device.interval.coerceAtLeast(5)
                while (System.currentTimeMillis() < expiresAt) {
                    delay(pollingInterval * 1_000)
                    val tokenResponse = api.pollDeviceAuthorization(clientId, device.deviceCode)
                    val token = tokenResponse.accessToken
                    if (!token.isNullOrBlank()) {
                        connectWithToken(token)
                        return@launch
                    }
                    when (tokenResponse.error) {
                        "authorization_pending" -> Unit
                        "slow_down" -> pollingInterval += 5
                        "access_denied" -> {
                            _state.value = _state.value.copy(
                                loading = false,
                                deviceUserCode = null,
                                verificationUri = null,
                                error = GitHubSettingsError.AUTH_CANCELLED,
                            )
                            return@launch
                        }
                        "expired_token" -> {
                            _state.value = _state.value.copy(
                                loading = false,
                                deviceUserCode = null,
                                verificationUri = null,
                                error = GitHubSettingsError.CODE_EXPIRED,
                            )
                            return@launch
                        }
                        null -> {
                            _state.value = _state.value.copy(
                                loading = false,
                                deviceUserCode = null,
                                verificationUri = null,
                                error = GitHubSettingsError.INVALID_RESPONSE,
                            )
                            return@launch
                        }
                        else -> {
                            _state.value = _state.value.copy(
                                loading = false,
                                deviceUserCode = null,
                                verificationUri = null,
                                error = GitHubSettingsError.SIGN_IN_FAILED,
                            )
                            return@launch
                        }
                    }
                }
                _state.value = _state.value.copy(
                    loading = false,
                    deviceUserCode = null,
                    verificationUri = null,
                    error = GitHubSettingsError.CODE_EXPIRED,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    deviceUserCode = null,
                    verificationUri = null,
                    error = GitHubSettingsError.SIGN_IN_FAILED,
                )
            }
        }
    }

    private fun connectWithToken(token: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, authorizationUri = null, error = null)
            try {
                val user = api.getCurrentUser(token)
                val repositories = api.listRepositories(token)
                credentialStore.saveToken(token)
                val active = credentialStore.getSelectedRepository()
                    ?.takeIf { selected -> repositories.any { it.fullName == selected } }
                if (active == null) credentialStore.clearSelectedRepository()
                _state.value = GitHubSettingsState(
                    connectedLogin = user.login,
                    repositories = repositories,
                    selectedRepositoryFullName = active,
                    activeRepositoryFullName = active,
                )
                if (active != null) {
                    repositories.firstOrNull { it.fullName == active }?.let { repository ->
                        loadRepositoryProjects(repository)
                        refreshLatestCloudBuild(repository)
                    }
                    loadLocalProjects()
                }
            } catch (_: Exception) {
                credentialStore.clear()
                _state.value = GitHubSettingsState(
                    error = GitHubSettingsError.CONNECT_FAILED,
                )
            }
        }
    }

    fun selectRepository(fullName: String) {
        val repository = _state.value.repositories.firstOrNull { it.fullName == fullName } ?: return
        cloudBuildJob?.cancel()
        credentialStore.saveSelectedRepository(repository.fullName)
        _state.value = _state.value.copy(
            selectedRepositoryFullName = repository.fullName,
            activeRepositoryFullName = repository.fullName,
            githubProjects = emptyList(),
            projects = emptyList(),
            error = null,
            cloudBuildStatus = GitHubCloudBuildStatus.IDLE,
            cloudBuildRunId = null,
            cloudBuildUrl = null,
            cloudBuildProjectPath = null,
            cloudBuildRequestId = null,
        )
        loadRepositoryProjects(repository)
        loadLocalProjects()
        refreshLatestCloudBuild(repository)
    }

    private fun loadRepositoryProjects(repository: GitHubRepository) {
        val token = credentialStore.getToken() ?: return
        projectLoadJob?.cancel()
        projectLoadJob = viewModelScope.launch {
            _state.value = _state.value.copy(projectsLoading = true, error = null)
            runCatching {
                api.listProjectCandidates(token, repository)
            }.onSuccess { projects ->
                if (_state.value.activeRepositoryFullName != repository.fullName) return@onSuccess
                _state.value = _state.value.copy(
                    githubProjects = projects,
                    projectsLoading = false,
                    error = null,
                )
            }.onFailure {
                if (_state.value.activeRepositoryFullName != repository.fullName) return@onFailure
                _state.value = _state.value.copy(
                    githubProjects = emptyList(),
                    projectsLoading = false,
                    error = GitHubSettingsError.PROJECTS_LOAD_FAILED,
                )
            }
        }
    }

    fun startCloudBuild(project: GitHubProjectCandidate) {
        val repository = activeRepository() ?: return
        val token = credentialStore.getToken() ?: return

        if (repository.permissions?.push == false) {
            _state.value = _state.value.copy(
                error = GitHubSettingsError.CLOUD_BUILD_PERMISSION_DENIED,
                cloudBuildStatus = GitHubCloudBuildStatus.FAILED,
            )
            return
        }

        cloudBuildJob?.cancel()
        cloudBuildJob = viewModelScope.launch {
            val repositoryName = repository.fullName
            val projectPath = project.path.ifBlank { "." }
            val requestId = "lmai-${System.currentTimeMillis()}"

            _state.value = _state.value.copy(
                error = null,
                cloudBuildStatus = GitHubCloudBuildStatus.PREPARING,
                cloudBuildRunId = null,
                cloudBuildUrl = null,
                cloudBuildProjectPath = projectPath,
                cloudBuildRequestId = requestId,
            )

            try {
                val workflowCreated = actionsApi.ensureCloudBuildWorkflow(
                    token = token,
                    repositoryFullName = repositoryName,
                    defaultBranch = repository.defaultBranch,
                )

                val dispatch = dispatchCloudBuildWithPropagationRetry(
                    token = token,
                    repository = repository,
                    projectPath = projectPath,
                    requestId = requestId,
                    retryAfterCreation = workflowCreated,
                )

                if (_state.value.activeRepositoryFullName != repositoryName) return@launch
                _state.value = _state.value.copy(
                    cloudBuildStatus = GitHubCloudBuildStatus.QUEUED,
                    cloudBuildRunId = dispatch.workflowRunId,
                    cloudBuildUrl = dispatch.htmlUrl,
                )

                val initialRun = dispatch.workflowRunId?.let { runId ->
                    runCatching {
                        actionsApi.getWorkflowRun(token, repositoryName, runId)
                    }.getOrNull()
                } ?: awaitCloudBuildRun(
                    token = token,
                    repository = repository,
                    requestId = requestId,
                )

                if (initialRun == null) {
                    throw IllegalStateException("GitHub accepted the cloud build but its workflow run could not be located.")
                }

                observeCloudBuildRun(
                    token = token,
                    repository = repository,
                    initialRun = initialRun,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_state.value.activeRepositoryFullName == repositoryName) {
                    _state.value = _state.value.copy(
                        error = GitHubSettingsError.CLOUD_BUILD_FAILED,
                        cloudBuildStatus = GitHubCloudBuildStatus.FAILED,
                    )
                }
            }
        }
    }

    private suspend fun dispatchCloudBuildWithPropagationRetry(
        token: String,
        repository: GitHubRepository,
        projectPath: String,
        requestId: String,
        retryAfterCreation: Boolean,
    ) = run {
        var lastError: GitHubApiException? = null
        val attempts = if (retryAfterCreation) 5 else 1

        repeat(attempts) { attempt ->
            try {
                return@run actionsApi.dispatchCloudBuild(
                    token = token,
                    repositoryFullName = repository.fullName,
                    branch = repository.defaultBranch,
                    projectPath = projectPath,
                    requestId = requestId,
                )
            } catch (e: GitHubApiException) {
                if (e.statusCode != 404 || attempt == attempts - 1) throw e
                lastError = e
                delay(1_500)
            }
        }

        throw lastError ?: IllegalStateException("Cloud workflow dispatch failed")
    }

    private suspend fun awaitCloudBuildRun(
        token: String,
        repository: GitHubRepository,
        requestId: String,
    ): GitHubWorkflowRun? {
        repeat(12) {
            val run = actionsApi.findCloudBuildRun(
                token = token,
                repositoryFullName = repository.fullName,
                branch = repository.defaultBranch,
                requestId = requestId,
            )
            if (run != null) return run
            delay(1_500)
        }
        return null
    }

    private suspend fun observeCloudBuildRun(
        token: String,
        repository: GitHubRepository,
        initialRun: GitHubWorkflowRun,
    ) {
        var run = initialRun
        while (cloudBuildJob?.isActive == true && viewModelScope.isActive) {
            if (_state.value.activeRepositoryFullName != repository.fullName) return
            applyCloudRun(run)
            if (run.status.equals("completed", ignoreCase = true)) return

            delay(3_000)
            run = actionsApi.getWorkflowRun(
                token = token,
                repositoryFullName = repository.fullName,
                runId = run.id,
            )
        }
    }

    private fun refreshLatestCloudBuild(repository: GitHubRepository? = activeRepository()) {
        val targetRepository = repository ?: return
        val token = credentialStore.getToken() ?: return
        viewModelScope.launch {
            val run = runCatching {
                actionsApi.findCloudBuildRun(
                    token = token,
                    repositoryFullName = targetRepository.fullName,
                    branch = targetRepository.defaultBranch,
                )
            }.getOrNull() ?: return@launch

            if (_state.value.activeRepositoryFullName == targetRepository.fullName) {
                applyCloudRun(run)
            }
        }
    }

    private fun applyCloudRun(run: GitHubWorkflowRun) {
        val status = when {
            !run.status.equals("completed", ignoreCase = true) &&
                run.status.equals("in_progress", ignoreCase = true) ->
                GitHubCloudBuildStatus.RUNNING

            !run.status.equals("completed", ignoreCase = true) ->
                GitHubCloudBuildStatus.QUEUED

            run.conclusion.equals("success", ignoreCase = true) ->
                GitHubCloudBuildStatus.SUCCESS

            run.conclusion.equals("cancelled", ignoreCase = true) ->
                GitHubCloudBuildStatus.CANCELLED

            else -> GitHubCloudBuildStatus.FAILED
        }

        _state.value = _state.value.copy(
            cloudBuildStatus = status,
            cloudBuildRunId = run.id,
            cloudBuildUrl = run.htmlUrl ?: _state.value.cloudBuildUrl,
        )
    }

    private fun activeRepository(): GitHubRepository? =
        _state.value.repositories.firstOrNull {
            it.fullName == _state.value.activeRepositoryFullName
        }

    fun linkProjectToSelectedRepository(
        project: Project,
        onSuccess: () -> Unit = {},
    ) {
        val repository = activeRepository() ?: return

        viewModelScope.launch {
            val ownerKey = GoogleAccountSession.currentOwnerKey(context)
            runCatching {
                projectDao.linkGitHubRepository(
                    projectId = project.projectId,
                    ownerKey = ownerKey,
                    legacyOwnerKey = GoogleAccountSession.LOCAL_OWNER_KEY,
                    repositoryId = repository.id,
                    repositoryFullName = repository.fullName,
                    branch = repository.defaultBranch,
                    updatedAt = System.currentTimeMillis() / 1000,
                )
            }.onSuccess { updatedRows ->
                if (updatedRows <= 0) {
                    _state.value = _state.value.copy(error = GitHubSettingsError.CONNECT_FAILED)
                    return@onSuccess
                }

                val updatedAt = System.currentTimeMillis() / 1000
                _state.value = _state.value.copy(
                    error = null,
                    projects = _state.value.projects.map { item ->
                        if (item.projectId == project.projectId) {
                            item.copy(
                                ownerKey = ownerKey,
                                githubRepositoryId = repository.id,
                                githubRepositoryFullName = repository.fullName,
                                githubBranch = repository.defaultBranch,
                                updatedAt = updatedAt,
                            )
                        } else {
                            item
                        }
                    },
                )
                onSuccess()
            }.onFailure {
                _state.value = _state.value.copy(error = GitHubSettingsError.CONNECT_FAILED)
            }
        }
    }

    private fun loadLocalProjects() {
        viewModelScope.launch {
            val ownerKey = GoogleAccountSession.currentOwnerKey(context)
            runCatching {
                projectDao.getProjectsForGitHub(
                    ownerKey = ownerKey,
                    legacyOwnerKey = GoogleAccountSession.LOCAL_OWNER_KEY,
                )
            }.onSuccess { projects ->
                _state.value = _state.value.copy(projects = projects)
            }
        }
    }

    fun disconnect() {
        authorizationJob?.cancel()
        projectLoadJob?.cancel()
        cloudBuildJob?.cancel()
        credentialStore.clear()
        _state.value = GitHubSettingsState()
    }

    private fun oauthRedirectUri(): String =
        BuildConfig.GITHUB_OAUTH_REDIRECT_URI.trim().ifBlank { GitHubOAuthCallbackBus.CALLBACK_URI }

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return Base64.encodeToString(
            buffer,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }
}

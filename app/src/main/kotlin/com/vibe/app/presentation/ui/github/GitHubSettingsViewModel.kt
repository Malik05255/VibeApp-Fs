package com.vibe.app.presentation.ui.github

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.BuildConfig
import com.vibe.app.data.database.dao.ProjectDao
import com.vibe.app.data.database.entity.Project
import com.vibe.app.feature.github.GitHubApi
import com.vibe.app.feature.github.GitHubCredentialStore
import com.vibe.app.feature.github.GitHubRepository
import com.vibe.app.presentation.ui.auth.GoogleAccountSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GitHubSettingsError {
    OAUTH_NOT_CONFIGURED,
    AUTH_CANCELLED,
    CODE_EXPIRED,
    INVALID_RESPONSE,
    SIGN_IN_FAILED,
    CONNECT_FAILED,
}

data class GitHubSettingsState(
    val connectedLogin: String? = null,
    val repositories: List<GitHubRepository> = emptyList(),
    val selectedRepositoryFullName: String? = null,
    val activeRepositoryFullName: String? = null,
    val projects: List<Project> = emptyList(),
    val loading: Boolean = false,
    val projectsLoading: Boolean = false,
    val deviceUserCode: String? = null,
    val verificationUri: String? = null,
    val error: GitHubSettingsError? = null,
)

@HiltViewModel
class GitHubSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: GitHubApi,
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

    init {
        credentialStore.getToken()?.let(::connectWithToken)
    }

    fun startSignIn() {
        val clientId = BuildConfig.GITHUB_OAUTH_CLIENT_ID.trim()
        if (clientId.isEmpty()) {
            _state.value = _state.value.copy(error = GitHubSettingsError.OAUTH_NOT_CONFIGURED)
            return
        }

        authorizationJob?.cancel()
        authorizationJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null,
                deviceUserCode = null,
                verificationUri = null,
            )
            try {
                val device = api.startDeviceAuthorization(clientId)
                _state.value = _state.value.copy(
                    loading = false,
                    deviceUserCode = device.userCode,
                    verificationUri = device.verificationUri,
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
            _state.value = _state.value.copy(loading = true, error = null)
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
                if (active != null) loadLocalProjects()
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
        credentialStore.saveSelectedRepository(repository.fullName)
        _state.value = _state.value.copy(
            selectedRepositoryFullName = repository.fullName,
            activeRepositoryFullName = repository.fullName,
            projects = emptyList(),
            error = null,
        )
        loadLocalProjects()
    }

    fun linkProjectToSelectedRepository(
        project: Project,
        onSuccess: () -> Unit = {},
    ) {
        val repository = _state.value.repositories.firstOrNull {
            it.fullName == _state.value.activeRepositoryFullName
        } ?: return

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
            _state.value = _state.value.copy(projectsLoading = true, error = null)
            val ownerKey = GoogleAccountSession.currentOwnerKey(context)
            runCatching {
                projectDao.getProjectsForGitHub(
                    ownerKey = ownerKey,
                    legacyOwnerKey = GoogleAccountSession.LOCAL_OWNER_KEY,
                )
            }.onSuccess { projects ->
                _state.value = _state.value.copy(
                    projects = projects,
                    projectsLoading = false,
                    error = null,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    projects = emptyList(),
                    projectsLoading = false,
                    error = GitHubSettingsError.CONNECT_FAILED,
                )
            }
        }
    }

    fun disconnect() {
        authorizationJob?.cancel()
        credentialStore.clear()
        _state.value = GitHubSettingsState()
    }
}

package com.vibe.app.presentation.ui.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.BuildConfig
import com.vibe.app.feature.github.GitHubApi
import com.vibe.app.feature.github.GitHubCredentialStore
import com.vibe.app.feature.github.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GitHubSettingsState(
    val connectedLogin: String? = null,
    val repositories: List<GitHubRepository> = emptyList(),
    val selectedRepositoryFullName: String? = null,
    val activeRepositoryFullName: String? = null,
    val loading: Boolean = false,
    val deviceUserCode: String? = null,
    val verificationUri: String? = null,
    val error: String? = null,
)

@HiltViewModel
class GitHubSettingsViewModel @Inject constructor(
    private val api: GitHubApi,
    private val credentialStore: GitHubCredentialStore,
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
            _state.value = _state.value.copy(error = "GitHub OAuth is not configured in this build.")
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
                        "access_denied" -> error("GitHub authorization was cancelled.")
                        "expired_token" -> error("The GitHub sign-in code expired. Try again.")
                        null -> error("GitHub returned an invalid sign-in response.")
                        else -> error(tokenResponse.errorDescription ?: "GitHub sign-in failed.")
                    }
                }
                error("The GitHub sign-in code expired. Try again.")
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    deviceUserCode = null,
                    verificationUri = null,
                    error = error.message ?: "Could not sign in to GitHub",
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
            } catch (error: Exception) {
                credentialStore.clear()
                _state.value = GitHubSettingsState(
                    error = error.message ?: "Could not connect to GitHub",
                )
            }
        }
    }

    fun selectRepository(fullName: String) {
        if (_state.value.repositories.none { it.fullName == fullName }) return
        _state.value = _state.value.copy(selectedRepositoryFullName = fullName, error = null)
    }

    fun executeSelection() {
        val selected = _state.value.selectedRepositoryFullName
        if (selected.isNullOrBlank()) {
            _state.value = _state.value.copy(error = "اختر مستودعًا أولًا")
            return
        }
        credentialStore.saveSelectedRepository(selected)
        _state.value = _state.value.copy(activeRepositoryFullName = selected, error = null)
    }

    fun disconnect() {
        authorizationJob?.cancel()
        credentialStore.clear()
        _state.value = GitHubSettingsState()
    }
}

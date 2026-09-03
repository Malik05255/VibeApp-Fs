package com.vibe.app.presentation.ui.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.feature.github.GitHubApi
import com.vibe.app.feature.github.GitHubCredentialStore
import com.vibe.app.feature.github.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GitHubSettingsState(
    val token: String = "",
    val connectedLogin: String? = null,
    val repositories: List<GitHubRepository> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GitHubSettingsViewModel @Inject constructor(
    private val api: GitHubApi,
    private val credentialStore: GitHubCredentialStore,
) : ViewModel() {
    private val _state = MutableStateFlow(GitHubSettingsState())
    val state: StateFlow<GitHubSettingsState> = _state.asStateFlow()

    init {
        credentialStore.getToken()?.let(::connect)
    }

    fun updateToken(value: String) {
        _state.value = _state.value.copy(token = value, error = null)
    }

    fun connect(tokenOverride: String? = null) {
        val token = (tokenOverride ?: _state.value.token).trim()
        if (token.isEmpty()) {
            _state.value = _state.value.copy(error = "Token is required")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val user = api.getCurrentUser(token)
                val repositories = api.listRepositories(token)
                credentialStore.saveToken(token)
                user to repositories
            }.onSuccess { (user, repositories) ->
                _state.value = GitHubSettingsState(
                    connectedLogin = user.login,
                    repositories = repositories,
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: "Could not connect to GitHub",
                )
            }
        }
    }

    fun disconnect() {
        credentialStore.clear()
        _state.value = GitHubSettingsState()
    }
}

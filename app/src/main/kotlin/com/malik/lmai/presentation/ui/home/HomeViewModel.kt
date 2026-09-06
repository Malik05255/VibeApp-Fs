package com.malik.lmai.presentation.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.database.entity.ProjectWithChat
import com.malik.lmai.data.repository.ProjectRepository
import com.malik.lmai.data.repository.SettingRepository
import com.malik.lmai.feature.agent.service.AgentSessionManager
import com.malik.lmai.feature.ai.FreeAiBootstrapper
import com.malik.lmai.feature.project.ProjectManager
import com.malik.lmai.feature.projectinit.ProjectInitializer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val projectManager: ProjectManager,
    private val settingRepository: SettingRepository,
    private val projectInitializer: ProjectInitializer,
    private val sessionManager: AgentSessionManager,
    private val freeAiBootstrapper: FreeAiBootstrapper,
) : ViewModel() {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    sealed interface ProjectCreationState {
        data object Idle : ProjectCreationState
        data class InProgress(val projectId: String) : ProjectCreationState
        data class Failed(val message: String) : ProjectCreationState
    }

    sealed interface NavigationEvent {
        data class OpenProject(val chatId: Int, val enabledPlatforms: List<String>) : NavigationEvent
    }

    data class ProjectListState(
        val projects: List<ProjectWithChat> = emptyList(),
        val isSelectionMode: Boolean = false,
        val isSearchMode: Boolean = false,
        val selectedProjects: List<Boolean> = emptyList(),
        val creationState: ProjectCreationState = ProjectCreationState.Idle,
        val navigationEvent: NavigationEvent? = null,
    )

    private val _projectListState = MutableStateFlow(ProjectListState())
    val projectListState: StateFlow<ProjectListState> = _projectListState.asStateFlow()

    private val _platformState = MutableStateFlow(listOf<PlatformV2>())
    val platformState = _platformState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _showDeleteWarningDialog = MutableStateFlow(false)
    val showDeleteWarningDialog: StateFlow<Boolean> = _showDeleteWarningDialog.asStateFlow()

    init {
        _searchQuery
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { query -> searchProjects(query) }
            .launchIn(viewModelScope)
    }

    fun fetchProjects() {
        viewModelScope.launch {
            val projects = projectRepository.fetchProjects()
            projects.forEach { projectWithChat ->
                projectInitializer.ensureProjectLauncherResources(projectWithChat.project.projectId)
            }
            _projectListState.update {
                it.copy(
                    projects = projects,
                    selectedProjects = List(projects.size) { false },
                )
            }
            Log.d("HomeViewModel", "Loaded ${projects.size} projects")
        }
    }

    /**
     * Returns the platform name if an agent session is actively running for the given chatId.
     */
    fun getActiveSessionPlatformName(chatId: Int): String? {
        return sessionManager.getActiveSessionPlatformName(chatId)
    }

    fun fetchPlatformStatus() {
        viewModelScope.launch {
            val platforms = runCatching { freeAiBootstrapper.ensureReady() }
                .getOrElse {
                    Log.w("HomeViewModel", "Free AI bootstrap failed while refreshing providers", it)
                    settingRepository.fetchPlatformV2s()
                }
            _platformState.update { platforms }
        }
    }

    fun createNewProject() {
        if (_projectListState.value.creationState is ProjectCreationState.InProgress) return

        viewModelScope.launch {
            _projectListState.update {
                it.copy(creationState = ProjectCreationState.InProgress(""))
            }

            val platforms = runCatching { freeAiBootstrapper.ensureReady() }
                .getOrElse {
                    Log.w("HomeViewModel", "Free AI bootstrap failed before project creation", it)
                    runCatching { settingRepository.fetchPlatformV2s() }.getOrDefault(_platformState.value)
                }
            _platformState.update { platforms }

            // Project creation must never be silently blocked just because provider rows
            // exist but none are currently enabled. Free AI can be activated later when
            // connectivity/credentials become ready.
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
            }.onFailure { e ->
                Log.e("HomeViewModel", "Failed to create project", e)
                _projectListState.update {
                    it.copy(creationState = ProjectCreationState.Failed(e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun consumeNavigationEvent() {
        _projectListState.update { it.copy(navigationEvent = null) }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.update { query }
    }

    private fun searchProjects(query: String) {
        viewModelScope.launch {
            val results = if (query.isBlank()) {
                projectRepository.fetchProjects()
            } else {
                projectRepository.searchProjects(query)
            }
            _projectListState.update {
                it.copy(
                    projects = results,
                    selectedProjects = List(results.size) { false },
                )
            }
        }
    }

    fun openDeleteWarningDialog() {
        _showDeleteWarningDialog.update { true }
    }

    fun closeDeleteWarningDialog() {
        _showDeleteWarningDialog.update { false }
    }

    fun deleteSelectedProjects() {
        viewModelScope.launch {
            val toDelete = _projectListState.value.projects
                .filterIndexed { idx, _ -> _projectListState.value.selectedProjects.getOrElse(idx) { false } }

            toDelete.forEach { pwc ->
                projectManager.deleteProject(pwc.project.projectId)
            }

            disableSelectionMode()
            fetchProjects()
        }
    }

    fun disableSelectionMode() {
        _projectListState.update {
            it.copy(
                selectedProjects = List(it.projects.size) { false },
                isSelectionMode = false,
            )
        }
    }

    fun disableSearchMode() {
        _projectListState.update { it.copy(isSearchMode = false) }
        _searchQuery.update { "" }
        fetchProjects()
    }

    fun enableSelectionMode() {
        val wasInSearchMode = _projectListState.value.isSearchMode
        // Update state atomically — do NOT call disableSearchMode() because that
        // triggers fetchProjects() which would race-reset isSelectionMode back to false.
        _projectListState.update { it.copy(isSelectionMode = true, isSearchMode = false) }
        _searchQuery.update { "" }
        if (wasInSearchMode) {
            // We were showing a filtered list; reload the full list now.
            viewModelScope.launch {
                val projects = projectRepository.fetchProjects()
                _projectListState.update {
                    it.copy(projects = projects, selectedProjects = List(projects.size) { false })
                }
            }
        }
    }

    fun enableSearchMode() {
        disableSelectionMode()
        _projectListState.update { it.copy(isSearchMode = true) }
    }

    fun selectProject(projectIdx: Int) {
        if (projectIdx < 0 || projectIdx >= _projectListState.value.projects.size) return

        _projectListState.update {
            it.copy(
                selectedProjects = it.selectedProjects.mapIndexed { index, b ->
                    if (index == projectIdx) !b else b
                },
            )
        }

        if (_projectListState.value.selectedProjects.count { it } == 0) {
            disableSelectionMode()
        }
    }
}

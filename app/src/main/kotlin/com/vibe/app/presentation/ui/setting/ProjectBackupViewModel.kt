package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.auth.SupabaseAuthRepository
import com.vibe.app.data.database.dao.ProjectDao
import com.vibe.app.sync.SupabaseSyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProjectBackupViewModel @Inject constructor(
    private val authRepository: SupabaseAuthRepository,
    private val projectDao: ProjectDao,
    private val syncRepository: SupabaseSyncRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectBackupState())
    val state: StateFlow<ProjectBackupState> = _state.asStateFlow()

    fun backupProjects() {
        runOperation(ProjectBackupMode.BACKUP)
    }

    fun restoreProjects() {
        runOperation(ProjectBackupMode.RESTORE)
    }

    fun dismissResult() {
        _state.value = ProjectBackupState()
    }

    private fun runOperation(mode: ProjectBackupMode) {
        if (_state.value.isRunning) return

        viewModelScope.launch {
            val userId = authRepository.currentUserId()
            if (userId.isNullOrBlank()) {
                _state.value = ProjectBackupState(
                    error = "سجل الدخول بحساب Google أولاً"
                )
                return@launch
            }

            _state.value = ProjectBackupState(
                isRunning = true,
                mode = mode,
                progress = 0,
                message = if (mode == ProjectBackupMode.BACKUP) {
                    "يتم الآن مزامنة مشاريعك على مساحة تخزين حسابك"
                } else {
                    "يتم الآن استعادة مشاريعك من حسابك"
                }
            )

            runCatching {
                when (mode) {
                    ProjectBackupMode.BACKUP -> backup(userId)
                    ProjectBackupMode.RESTORE -> restore(userId)
                }
            }.onSuccess { result ->
                _state.value = ProjectBackupState(
                    isRunning = false,
                    mode = mode,
                    progress = 100,
                    message = result,
                    completed = true,
                )
            }.onFailure { error ->
                _state.value = ProjectBackupState(
                    isRunning = false,
                    mode = mode,
                    progress = _state.value.progress,
                    error = error.message ?: "تعذر إكمال العملية",
                )
            }
        }
    }

    private suspend fun backup(userId: String): String {
        val localProjects = projectDao.getProjects()
        if (localProjects.isEmpty()) {
            updateProgress(100)
            return "لا توجد مشاريع جديدة للمزامنة"
        }

        updateProgress(10)
        val cloudProjects = syncRepository.downloadProjects(userId)
        updateProgress(25)

        val cloudById = cloudProjects.associateBy { it.projectId }
        val pending = localProjects.filter { local ->
            val cloud = cloudById[local.projectId]
            cloud == null || local.updatedAt > cloud.updatedAt
        }

        if (pending.isEmpty()) {
            updateProgress(100)
            return "مشاريعك محدثة بالفعل"
        }

        pending.forEachIndexed { index, project ->
            syncRepository.uploadProjects(userId, listOf(project))
            val ratio = (index + 1).toFloat() / pending.size.toFloat()
            updateProgress(25 + (ratio * 75).toInt())
        }

        return "تمت مزامنة ${pending.size} مشروع بنجاح"
    }

    private suspend fun restore(userId: String): String {
        updateProgress(10)
        val cloudProjects = syncRepository.downloadProjects(userId)
        updateProgress(25)

        if (cloudProjects.isEmpty()) {
            updateProgress(100)
            return "لا توجد مشاريع محفوظة على حسابك"
        }

        val localProjects = projectDao.getProjects().associateBy { it.projectId }
        val restorable = cloudProjects.filter { cloud ->
            val local = localProjects[cloud.projectId]
            local == null || cloud.updatedAt > local.updatedAt
        }

        if (restorable.isEmpty()) {
            updateProgress(100)
            return "كل مشاريع حسابك موجودة بالفعل في التطبيق"
        }

        restorable.forEachIndexed { index, cloudProject ->
            val existing = localProjects[cloudProject.projectId]
            val projectToInsert = if (existing == null) {
                cloudProject
            } else {
                cloudProject.copy(
                    chatId = existing.chatId,
                    workspacePath = existing.workspacePath,
                )
            }

            projectDao.insertProject(projectToInsert)
            val ratio = (index + 1).toFloat() / restorable.size.toFloat()
            updateProgress(25 + (ratio * 75).toInt())
        }

        return "تمت استعادة ${restorable.size} مشروع بنجاح"
    }

    private fun updateProgress(progress: Int) {
        _state.value = _state.value.copy(progress = progress.coerceIn(0, 100))
    }
}

enum class ProjectBackupMode {
    BACKUP,
    RESTORE,
}

data class ProjectBackupState(
    val isRunning: Boolean = false,
    val mode: ProjectBackupMode? = null,
    val progress: Int = 0,
    val message: String? = null,
    val completed: Boolean = false,
    val error: String? = null,
)

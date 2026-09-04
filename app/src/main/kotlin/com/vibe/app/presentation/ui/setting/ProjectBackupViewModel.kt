package com.vibe.app.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.database.dao.ProjectDao
import com.vibe.app.data.database.entity.Project
import com.vibe.app.presentation.ui.auth.GoogleAccountSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProjectBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectBackupState())
    val state: StateFlow<ProjectBackupState> = _state.asStateFlow()

    fun openBackupSelection() {
        loadSelection(ProjectBackupMode.BACKUP)
    }

    fun openRestoreSelection() {
        loadSelection(ProjectBackupMode.RESTORE)
    }

    fun toggleProject(projectId: String) {
        val selected = _state.value.selectedProjectIds.toMutableSet()
        if (!selected.add(projectId)) selected.remove(projectId)
        _state.value = _state.value.copy(selectedProjectIds = selected)
    }

    fun selectAll() {
        _state.value = _state.value.copy(
            selectedProjectIds = _state.value.availableProjects.map { it.projectId }.toSet()
        )
    }

    fun cancelSelection() {
        _state.value = ProjectBackupState()
    }

    fun confirmSelection() {
        val current = _state.value
        if (current.selectedProjectIds.isEmpty()) {
            _state.value = current.copy(error = "اختر مشروعًا واحدًا على الأقل")
            return
        }
        runSelectedOperation(current.mode ?: return)
    }

    fun dismissResult() {
        _state.value = ProjectBackupState()
    }

    private fun loadSelection(mode: ProjectBackupMode) {
        if (_state.value.isRunning) return
        viewModelScope.launch {
            if (GoogleAccountSession.get(context) == null) {
                _state.value = ProjectBackupState(error = "سجل الدخول بحساب Google أولاً")
                return@launch
            }

            val projects = when (mode) {
                ProjectBackupMode.BACKUP -> projectDao.getProjects()
                ProjectBackupMode.RESTORE -> emptyList()
            }

            _state.value = ProjectBackupState(
                mode = mode,
                isSelectionOpen = true,
                availableProjects = projects,
                selectedProjectIds = projects.map { it.projectId }.toSet(),
                message = if (mode == ProjectBackupMode.BACKUP) {
                    if (projects.isEmpty()) "لا توجد مشاريع محلية للمزامنة" else null
                } else {
                    "استعادة المشاريع من Google Drive تتطلب ربط Google Drive API بالنسخة القادمة"
                }
            )
        }
    }

    private fun runSelectedOperation(mode: ProjectBackupMode) {
        viewModelScope.launch {
            val selected = _state.value.availableProjects
                .filter { it.projectId in _state.value.selectedProjectIds }

            _state.value = _state.value.copy(
                isSelectionOpen = false,
                isRunning = true,
                progress = 0,
                completed = false,
                error = null,
                message = if (mode == ProjectBackupMode.BACKUP) {
                    "يتم الآن تجهيز المشاريع المحددة للمزامنة"
                } else {
                    "يتم الآن استعادة المشاريع المحددة"
                }
            )

            if (mode == ProjectBackupMode.RESTORE) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    error = "Google Drive غير مربوط بعد. تم إزالة Supabase بالكامل من مسار المشاريع."
                )
                return@launch
            }

            if (selected.isEmpty()) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    progress = 100,
                    completed = true,
                    message = "لا توجد مشاريع محددة للمزامنة"
                )
                return@launch
            }

            selected.forEachIndexed { index, _ ->
                val progress = (((index + 1).toFloat() / selected.size.toFloat()) * 100).toInt()
                _state.value = _state.value.copy(progress = progress)
            }

            _state.value = _state.value.copy(
                isRunning = false,
                progress = 100,
                completed = true,
                message = "تم تجهيز ${selected.size} مشروع للمزامنة. يلزم الآن ربط Google Drive API للحفظ الفعلي في مساحة الحساب."
            )
        }
    }
}

enum class ProjectBackupMode {
    BACKUP,
    RESTORE,
}

data class ProjectBackupState(
    val isSelectionOpen: Boolean = false,
    val availableProjects: List<Project> = emptyList(),
    val selectedProjectIds: Set<String> = emptySet(),
    val isRunning: Boolean = false,
    val mode: ProjectBackupMode? = null,
    val progress: Int = 0,
    val message: String? = null,
    val completed: Boolean = false,
    val error: String? = null,
)

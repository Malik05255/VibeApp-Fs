package com.vibe.app.presentation.ui.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.app.data.database.dao.ChatRoomV2Dao
import com.vibe.app.data.database.dao.ProjectDao
import com.vibe.app.data.database.entity.ChatRoomV2
import com.vibe.app.data.database.entity.Project
import com.vibe.app.presentation.ui.auth.GoogleAccountSession
import com.vibe.app.sync.DriveBackupItem
import com.vibe.app.sync.GoogleDriveProjectBackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ProjectBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
    private val chatRoomV2Dao: ChatRoomV2Dao,
    private val driveRepository: GoogleDriveProjectBackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectBackupState())
    val state: StateFlow<ProjectBackupState> = _state.asStateFlow()

    fun openBackupSelection() = loadSelection(ProjectBackupMode.BACKUP)
    fun openRestoreSelection() = loadSelection(ProjectBackupMode.RESTORE)

    fun toggleProject(projectId: String) {
        val selected = _state.value.selectedProjectIds.toMutableSet()
        if (!selected.add(projectId)) selected.remove(projectId)
        _state.value = _state.value.copy(selectedProjectIds = selected, error = null)
    }

    fun selectAll() {
        _state.value = _state.value.copy(selectedProjectIds = _state.value.availableProjects.map { it.projectId }.toSet())
    }

    fun cancelSelection() { _state.value = ProjectBackupState() }

    fun confirmSelection() {
        val current = _state.value
        if (current.selectedProjectIds.isEmpty()) {
            _state.value = current.copy(error = "اختر مشروعًا واحدًا على الأقل")
            return
        }
        runSelectedOperation(current.mode ?: return)
    }

    fun dismissResult() { _state.value = ProjectBackupState() }

    private fun loadSelection(mode: ProjectBackupMode) {
        if (_state.value.isRunning) return
        viewModelScope.launch {
            if (GoogleAccountSession.get(context) == null) {
                _state.value = ProjectBackupState(error = "سجل الدخول بحساب Google أولاً")
                return@launch
            }
            val ownerKey = GoogleAccountSession.currentOwnerKey(context)

            _state.value = ProjectBackupState(
                isRunning = mode == ProjectBackupMode.RESTORE,
                mode = mode,
                progress = 0,
                message = if (mode == ProjectBackupMode.RESTORE) "يتم جلب مشاريعك من Google Drive" else null,
            )

            runCatching {
                when (mode) {
                    ProjectBackupMode.BACKUP -> Pair(projectDao.getProjects(ownerKey), emptyMap<String, DriveBackupItem>())
                    ProjectBackupMode.RESTORE -> {
                        val remote = withContext(Dispatchers.IO) { driveRepository.listBackups() }
                        val projects = remote.map { item ->
                            Project(
                                projectId = item.projectId,
                                name = item.name,
                                chatId = 0,
                                workspacePath = "",
                                buildStatus = com.vibe.app.data.database.entity.ProjectBuildStatus.READY,
                                ownerKey = ownerKey,
                                createdAt = 0,
                                updatedAt = item.updatedAt,
                            )
                        }
                        Pair(projects, remote.associateBy { it.projectId })
                    }
                }
            }.onSuccess { (projects, remoteMap) ->
                _state.value = ProjectBackupState(
                    mode = mode,
                    isSelectionOpen = true,
                    availableProjects = projects,
                    selectedProjectIds = projects.map { it.projectId }.toSet(),
                    remoteBackups = remoteMap,
                    message = when {
                        projects.isEmpty() && mode == ProjectBackupMode.BACKUP -> "لا توجد مشاريع محلية للمزامنة"
                        projects.isEmpty() && mode == ProjectBackupMode.RESTORE -> "لا توجد مشاريع محفوظة على حسابك"
                        else -> null
                    }
                )
            }.onFailure { error ->
                _state.value = ProjectBackupState(mode = mode, error = safeError(error))
            }
        }
    }

    private fun runSelectedOperation(mode: ProjectBackupMode) {
        viewModelScope.launch {
            val ownerKey = GoogleAccountSession.currentOwnerKey(context)
            val selected = _state.value.availableProjects.filter { it.projectId in _state.value.selectedProjectIds }
            val remoteBackups = _state.value.remoteBackups

            _state.value = _state.value.copy(
                isSelectionOpen = false,
                isRunning = true,
                progress = 0,
                completed = false,
                error = null,
                message = if (mode == ProjectBackupMode.BACKUP) "يتم الآن مزامنة المشاريع المحددة" else "يتم الآن استعادة المشاريع المحددة",
            )

            runCatching {
                if (mode == ProjectBackupMode.BACKUP) {
                    selected.forEachIndexed { index, project ->
                        withContext(Dispatchers.IO) { driveRepository.uploadProject(project) }
                        updateProgress(index + 1, selected.size)
                    }
                    "تمت مزامنة ${selected.size} مشروع بنجاح"
                } else {
                    val restoreRoot = File(context.filesDir, "restored_projects/$ownerKey").apply { mkdirs() }
                    selected.forEachIndexed { index, project ->
                        val item = remoteBackups[project.projectId] ?: error("تعذر العثور على النسخة الاحتياطية للمشروع ${project.name}")
                        val manifest = withContext(Dispatchers.IO) { driveRepository.restoreProject(item, restoreRoot) }
                        val existing = projectDao.getProject(project.projectId, ownerKey)
                        val restoredWorkspace = File(restoreRoot, project.projectId).absolutePath

                        if (existing == null) {
                            val chatId = chatRoomV2Dao.addChatRoom(
                                ChatRoomV2(title = manifest.name, enabledPlatform = emptyList()),
                            ).toInt()
                            projectDao.insertProject(
                                project.copy(
                                    name = manifest.name,
                                    chatId = chatId,
                                    workspacePath = restoredWorkspace,
                                    ownerKey = ownerKey,
                                    createdAt = manifest.createdAt,
                                    updatedAt = manifest.updatedAt,
                                ),
                            )
                        } else {
                            projectDao.insertProject(
                                existing.copy(
                                    name = manifest.name,
                                    workspacePath = restoredWorkspace,
                                    ownerKey = ownerKey,
                                    updatedAt = manifest.updatedAt,
                                ),
                            )
                        }
                        updateProgress(index + 1, selected.size)
                    }
                    "تمت استعادة ${selected.size} مشروع بنجاح"
                }
            }.onSuccess { message ->
                _state.value = _state.value.copy(isRunning = false, progress = 100, completed = true, message = message)
            }.onFailure { error ->
                _state.value = _state.value.copy(isRunning = false, error = safeError(error))
            }
        }
    }

    private fun updateProgress(done: Int, total: Int) {
        val progress = if (total <= 0) 100 else ((done.toFloat() / total.toFloat()) * 100).toInt()
        _state.value = _state.value.copy(progress = progress.coerceIn(0, 100))
    }

    private fun safeError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("insufficient", ignoreCase = true) || raw.contains("scope", ignoreCase = true) -> "صلاحية Google Drive غير متاحة. سجّل الخروج ثم ادخل بحساب Google من جديد ووافق على صلاحية الملفات."
            raw.contains("401") || raw.contains("403") -> "تعذر الوصول إلى Google Drive. أعد تسجيل الدخول بحساب Google ثم حاول مرة أخرى."
            else -> raw.take(180).ifBlank { "تعذر إكمال العملية" }
        }
    }
}

enum class ProjectBackupMode { BACKUP, RESTORE }

data class ProjectBackupState(
    val isSelectionOpen: Boolean = false,
    val availableProjects: List<Project> = emptyList(),
    val selectedProjectIds: Set<String> = emptySet(),
    val remoteBackups: Map<String, DriveBackupItem> = emptyMap(),
    val isRunning: Boolean = false,
    val mode: ProjectBackupMode? = null,
    val progress: Int = 0,
    val message: String? = null,
    val completed: Boolean = false,
    val error: String? = null,
)

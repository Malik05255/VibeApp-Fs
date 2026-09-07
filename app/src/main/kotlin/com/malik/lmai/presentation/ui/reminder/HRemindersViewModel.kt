package com.malik.lmai.presentation.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malik.lmai.feature.reminder.HReminder
import com.malik.lmai.feature.reminder.HReminderRepository
import com.malik.lmai.feature.reminder.HReminderStatus
import com.malik.lmai.feature.reminder.HReminderType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HReminderFilter { ALL, ACTIVE, LOCATION, TIME, COMPLETED }

data class HRemindersUiState(
    val reminders: List<HReminder> = emptyList(),
    val filter: HReminderFilter = HReminderFilter.ALL,
    val selected: HReminder? = null,
    val editing: HReminder? = null,
    val deleteCandidate: HReminder? = null,
)

@HiltViewModel
class HRemindersViewModel @Inject constructor(
    private val repository: HReminderRepository,
) : ViewModel() {
    private val filter = MutableStateFlow(HReminderFilter.ALL)
    private val selected = MutableStateFlow<HReminder?>(null)
    private val editing = MutableStateFlow<HReminder?>(null)
    private val deleteCandidate = MutableStateFlow<HReminder?>(null)

    val uiState = combine(
        repository.observePersonal(),
        filter,
        selected,
        editing,
        deleteCandidate,
    ) { reminders, currentFilter, currentSelected, currentEditing, currentDelete ->
        val filtered = when (currentFilter) {
            HReminderFilter.ALL -> reminders
            HReminderFilter.ACTIVE -> reminders.filter { it.status == HReminderStatus.ACTIVE || it.status == HReminderStatus.DEFERRED }
            HReminderFilter.LOCATION -> reminders.filter { it.type == HReminderType.LOCATION }
            HReminderFilter.TIME -> reminders.filter { it.type == HReminderType.TIME || it.type == HReminderType.RECURRING }
            HReminderFilter.COMPLETED -> reminders.filter { it.status == HReminderStatus.COMPLETED }
        }
        HRemindersUiState(filtered, currentFilter, currentSelected, currentEditing, currentDelete)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HRemindersUiState(),
    )

    fun setFilter(value: HReminderFilter) { filter.value = value }
    fun open(reminder: HReminder) { selected.value = reminder }
    fun closeDetails() { selected.value = null }
    fun beginEdit(reminder: HReminder) { editing.value = reminder }
    fun closeEdit() { editing.value = null }
    fun requestDelete(reminder: HReminder) { deleteCandidate.value = reminder }
    fun cancelDelete() { deleteCandidate.value = null }

    fun save(reminder: HReminder) = viewModelScope.launch {
        repository.update(reminder)
        editing.value = null
        selected.value = reminder
    }

    fun delete(reminder: HReminder) = viewModelScope.launch {
        repository.delete(reminder.id)
        deleteCandidate.value = null
        if (selected.value?.id == reminder.id) selected.value = null
    }

    fun complete(reminder: HReminder) = setStatus(reminder, HReminderStatus.COMPLETED)
    fun defer(reminder: HReminder) = setStatus(reminder, HReminderStatus.DEFERRED)
    fun toggleEnabled(reminder: HReminder) = setStatus(
        reminder,
        if (reminder.status == HReminderStatus.DISABLED) HReminderStatus.ACTIVE else HReminderStatus.DISABLED,
    )

    private fun setStatus(reminder: HReminder, status: HReminderStatus) = viewModelScope.launch {
        repository.setStatus(reminder.id, status)
        selected.value = repository.get(reminder.id)
    }

    fun reschedule() = viewModelScope.launch { repository.rescheduleAll() }
}

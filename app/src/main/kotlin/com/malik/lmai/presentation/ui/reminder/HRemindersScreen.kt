package com.malik.lmai.presentation.ui.reminder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.feature.reminder.HLocationTriggerMode
import com.malik.lmai.feature.reminder.HReminder
import com.malik.lmai.feature.reminder.HReminderSource
import com.malik.lmai.feature.reminder.HReminderStatus
import com.malik.lmai.feature.reminder.HReminderType
import java.text.DateFormat
import java.util.Date

@Composable
fun HRemindersScreen(
    onBack: () -> Unit,
    viewModel: HRemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.h_reminders_title), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.h_reminders_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ReminderFilters(
                current = state.filter,
                onSelect = viewModel::setFilter,
            )

            HReminderPermissionCard(
                needsNotifications = state.hasAnyReminder,
                needsLocation = state.hasLocationReminder,
                onPermissionsChanged = { viewModel.reschedule() },
            )

            if (state.reminders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.h_reminders_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.h_reminders_empty_desc),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.reminders, key = { it.id }) { reminder ->
                        HReminderCard(reminder = reminder, onClick = { viewModel.open(reminder) })
                    }
                }
            }
        }
    }

    state.selected?.let { reminder ->
        ModalBottomSheet(onDismissRequest = viewModel::closeDetails) {
            HReminderDetails(
                reminder = reminder,
                onEdit = { viewModel.beginEdit(reminder) },
                onDelete = { viewModel.requestDelete(reminder) },
                onComplete = { viewModel.complete(reminder) },
                onDefer = { viewModel.defer(reminder) },
                onToggleEnabled = { viewModel.toggleEnabled(reminder) },
            )
        }
    }

    state.editing?.let { reminder ->
        ModalBottomSheet(onDismissRequest = viewModel::closeEdit) {
            HReminderEditSheet(
                reminder = reminder,
                onCancel = viewModel::closeEdit,
                onSave = viewModel::save,
            )
        }
    }

    state.deleteCandidate?.let { reminder ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.h_reminder_delete)) },
            text = { Text(stringResource(R.string.h_reminder_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(reminder) }) {
                    Text(stringResource(R.string.h_reminder_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.h_reminder_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReminderFilters(current: HReminderFilter, onSelect: (HReminderFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HReminderFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == current,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        when (filter) {
                            HReminderFilter.ALL -> stringResource(R.string.h_reminder_filter_all)
                            HReminderFilter.ACTIVE -> stringResource(R.string.h_reminder_filter_active)
                            HReminderFilter.LOCATION -> stringResource(R.string.h_reminder_filter_location)
                            HReminderFilter.TIME -> stringResource(R.string.h_reminder_filter_time)
                            HReminderFilter.COMPLETED -> stringResource(R.string.h_reminder_filter_done)
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun HReminderCard(reminder: HReminder, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            ) {
                Icon(
                    imageVector = when (reminder.type) {
                        HReminderType.LOCATION -> Icons.Outlined.LocationOn
                        HReminderType.TIME, HReminderType.RECURRING -> Icons.Outlined.AccessTime
                        else -> Icons.Outlined.Notifications
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    reminderSummary(reminder),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                statusLabel(reminder.status),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor(reminder.status),
            )
        }
    }
}

@Composable
private fun HReminderDetails(
    reminder: HReminder,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit,
    onDefer: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(reminder.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${typeLabel(reminder.type)} · ${statusLabel(reminder.status)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { HDetailBlock(stringResource(R.string.h_reminder_original), reminder.originalText) }
        item { HDetailBlock(stringResource(R.string.h_reminder_understood), reminder.interpretedText) }
        reminder.scheduledAtMs?.let { time ->
            item { HDetailBlock(stringResource(R.string.h_reminder_time), DateFormat.getDateTimeInstance().format(Date(time))) }
        }
        reminder.personName?.let { person ->
            item { HDetailBlock(stringResource(R.string.h_reminder_person), person) }
        }
        reminder.recurrenceRule?.let { recurrence ->
            item { HDetailBlock(stringResource(R.string.h_reminder_recurring), recurrence) }
        }
        reminder.location?.let { location ->
            item {
                Text(stringResource(R.string.h_reminder_location), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HReminderMapEditor(location = location, editable = false, onLocationChange = {})
                HDetailBlock(
                    stringResource(R.string.h_reminder_condition),
                    locationTriggerLabel(location.triggerMode, location.dwellMinutes, location.radiusMeters),
                )
            }
        }
        item { HDetailBlock(stringResource(R.string.h_reminder_source), sourceLabel(reminder.source)) }
        item { HDetailBlock(stringResource(R.string.h_reminder_created), DateFormat.getDateTimeInstance().format(Date(reminder.createdAtMs))) }
        item {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                    Text(stringResource(R.string.h_reminder_edit), modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text(stringResource(R.string.h_reminder_delete), modifier = Modifier.padding(start = 6.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reminder.status != HReminderStatus.COMPLETED) {
                    TextButton(onClick = onComplete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Text(stringResource(R.string.h_reminder_done), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                TextButton(onClick = onDefer, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PauseCircle, contentDescription = null)
                    Text(stringResource(R.string.h_reminder_defer), modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = onToggleEnabled, modifier = Modifier.weight(1f)) {
                    Text(
                        if (reminder.status == HReminderStatus.DISABLED) stringResource(R.string.h_reminder_enable)
                        else stringResource(R.string.h_reminder_disable)
                    )
                }
            }
        }
    }
}

@Composable
private fun HReminderEditSheet(
    reminder: HReminder,
    onCancel: () -> Unit,
    onSave: (HReminder) -> Unit,
) {
    var title by remember(reminder.id) { mutableStateOf(reminder.title) }
    var interpreted by remember(reminder.id) { mutableStateOf(reminder.interpretedText) }
    var location by remember(reminder.id) { mutableStateOf(reminder.location) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.h_reminder_edit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.h_reminders_title)) },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = interpreted,
                onValueChange = { interpreted = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.h_reminder_understood)) },
                minLines = 2,
                maxLines = 5,
            )
        }
        location?.let { currentLocation ->
            item {
                Text(stringResource(R.string.h_reminder_edit_location), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                HReminderMapEditor(
                    location = currentLocation,
                    editable = true,
                    onLocationChange = { location = it },
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.h_reminder_cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            reminder.copy(
                                title = title.trim().ifBlank { reminder.title },
                                interpretedText = interpreted.trim().ifBlank { reminder.interpretedText },
                                location = location,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.h_reminder_save))
                }
            }
        }
    }
}

@Composable
private fun HDetailBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun typeLabel(type: HReminderType): String = when (type) {
    HReminderType.TIME -> stringResource(R.string.h_reminder_time)
    HReminderType.LOCATION -> stringResource(R.string.h_reminder_location)
    HReminderType.PERSON -> stringResource(R.string.h_reminder_person)
    HReminderType.RECURRING -> stringResource(R.string.h_reminder_recurring)
    HReminderType.CONTEXTUAL -> stringResource(R.string.h_reminder_contextual)
}

@Composable
private fun statusLabel(status: HReminderStatus): String = when (status) {
    HReminderStatus.ACTIVE -> stringResource(R.string.h_reminder_active)
    HReminderStatus.DEFERRED -> stringResource(R.string.h_reminder_deferred)
    HReminderStatus.COMPLETED -> stringResource(R.string.h_reminder_completed)
    HReminderStatus.DISABLED, HReminderStatus.CANCELLED -> stringResource(R.string.h_reminder_disabled)
}

@Composable
private fun statusColor(status: HReminderStatus) = when (status) {
    HReminderStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    HReminderStatus.DEFERRED -> MaterialTheme.colorScheme.tertiary
    HReminderStatus.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant
    HReminderStatus.DISABLED, HReminderStatus.CANCELLED -> MaterialTheme.colorScheme.error
}

private fun reminderSummary(reminder: HReminder): String = when {
    reminder.location != null -> "${reminder.location.placeNameAr} · ${locationTriggerLabel(reminder.location.triggerMode, reminder.location.dwellMinutes, reminder.location.radiusMeters)}"
    reminder.scheduledAtMs != null -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(reminder.scheduledAtMs))
    reminder.personName != null -> reminder.personName
    else -> reminder.interpretedText
}

private fun locationTriggerLabel(mode: HLocationTriggerMode, dwellMinutes: Int, radiusMeters: Float): String = when (mode) {
    HLocationTriggerMode.DWELL -> "بعد البقاء $dwellMinutes دقيقة داخل نطاق ${radiusMeters.toInt()} م"
    HLocationTriggerMode.ARRIVE -> "عند الوصول داخل نطاق ${radiusMeters.toInt()} م"
    HLocationTriggerMode.DEPART -> "عند مغادرة المكان"
    HLocationTriggerMode.NEARBY -> "عند الاقتراب لمسافة ${radiusMeters.toInt()} م"
}

private fun sourceLabel(source: HReminderSource): String = when (source) {
    HReminderSource.APP_CHAT -> "محادثة H في التطبيق"
    HReminderSource.WHATSAPP -> "WhatsApp"
    HReminderSource.MANUAL -> "يدوي"
    HReminderSource.IMPORTED -> "مستورد"
}

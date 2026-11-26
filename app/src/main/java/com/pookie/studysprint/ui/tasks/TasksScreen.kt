package com.pookie.studysprint.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pookie.studysprint.data.local.TaskEntity
import com.pookie.studysprint.domain.model.Priority
import com.pookie.studysprint.ui.AppViewModelProvider
import com.pookie.studysprint.ui.components.AppCard
import com.pookie.studysprint.ui.components.AppTextField
import com.pookie.studysprint.ui.components.ChipKind
import com.pookie.studysprint.ui.components.EmptyState
import com.pookie.studysprint.ui.components.SectionHeader
import com.pookie.studysprint.ui.components.StatusChip
import com.pookie.studysprint.ui.theme.Radius
import com.pookie.studysprint.ui.theme.Spacing
import com.pookie.studysprint.ui.theme.extraColors

private sealed interface TaskDialogState {
    data object None : TaskDialogState
    data object Add : TaskDialogState
    data class Edit(val task: TaskEntity) : TaskDialogState
    data class Delete(val task: TaskEntity) : TaskDialogState
}

private fun Priority.label(): String = when (this) {
    Priority.LOW -> "Low"
    Priority.MEDIUM -> "Medium"
    Priority.HIGH -> "High"
}

@Composable
fun TasksScreen(
    onBack: () -> Unit,
    viewModel: TasksViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<TaskDialogState>(TaskDialogState.None) }
    val palette = MaterialTheme.extraColors.subjectColors
    val dotColor = palette[state.colorIndex.coerceIn(0, palette.lastIndex)]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { dialog = TaskDialogState.Add },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Radius.control
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add task")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.screenHorizontal,
                end = Spacing.screenHorizontal,
                top = Spacing.sm,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.offset(x = (-12).dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = state.subjectName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = if (state.isLoading) {
                            "Loading..."
                        } else {
                            "${state.pending.size} pending · ${state.completed.size} done"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!state.isLoading && state.pending.isEmpty() && state.completed.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.TaskAlt,
                        title = "No tasks yet",
                        message = "Tap the + button to add the first task for this subject.",
                        actionLabel = "Add task",
                        onAction = { dialog = TaskDialogState.Add }
                    )
                }
            }

            if (state.pending.isNotEmpty()) {
                item { SectionHeader(title = "Pending (${state.pending.size})") }
                items(state.pending, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { viewModel.setCompleted(task, it) },
                        onEdit = { dialog = TaskDialogState.Edit(task) },
                        onDelete = { dialog = TaskDialogState.Delete(task) }
                    )
                }
            }

            if (state.completed.isNotEmpty()) {
                item { SectionHeader(title = "Completed (${state.completed.size})") }
                items(state.completed, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { viewModel.setCompleted(task, it) },
                        onEdit = { dialog = TaskDialogState.Edit(task) },
                        onDelete = { dialog = TaskDialogState.Delete(task) }
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        TaskDialogState.None -> Unit

        TaskDialogState.Add -> TaskEditDialog(
            title = "New task",
            initialTitle = "",
            initialPriority = Priority.MEDIUM,
            initialMinutes = 25,
            onDismiss = { dialog = TaskDialogState.None },
            onConfirm = { name, priority, minutes ->
                viewModel.addTask(name, priority, minutes)
                dialog = TaskDialogState.None
            }
        )

        is TaskDialogState.Edit -> TaskEditDialog(
            title = "Edit task",
            initialTitle = d.task.title,
            initialPriority = d.task.priority,
            initialMinutes = d.task.estimatedMinutes,
            onDismiss = { dialog = TaskDialogState.None },
            onConfirm = { name, priority, minutes ->
                viewModel.updateTask(d.task, name, priority, minutes)
                dialog = TaskDialogState.None
            }
        )

        is TaskDialogState.Delete -> AlertDialog(
            onDismissRequest = { dialog = TaskDialogState.None },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete \"${d.task.title}\"?") },
            text = { Text("This task will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(d.task)
                    dialog = TaskDialogState.None
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = TaskDialogState.None }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard(onClick = onEdit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics {
                    contentDescription = "Mark ${task.title} as done"
                }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (task.isCompleted) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusChip(label = task.priority.label(), kind = ChipKind.Neutral)
                    StatusChip(label = "${task.estimatedMinutes} min", kind = ChipKind.Neutral)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete ${task.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TaskEditDialog(
    title: String,
    initialTitle: String,
    initialPriority: Priority,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Priority, Int) -> Unit
) {
    var name by remember { mutableStateOf(initialTitle) }
    var priority by remember { mutableStateOf(initialPriority) }
    var minutesText by remember { mutableStateOf(initialMinutes.toString()) }

    val minutes = minutesText.toIntOrNull()
    val isValid = name.isNotBlank() && minutes != null && minutes in 1..480

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Task title"
                )

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Priority",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Priority.entries.forEach { option ->
                            val selected = option == priority
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(Radius.control)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        Radius.control
                                    )
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { priority = option }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.label(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                AppTextField(
                    value = minutesText,
                    onValueChange = { value ->
                        if (value.length <= 3 && value.all { it.isDigit() }) {
                            minutesText = value
                        }
                    },
                    label = "Estimated minutes (1-480)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), priority, minutes ?: 25) },
                enabled = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
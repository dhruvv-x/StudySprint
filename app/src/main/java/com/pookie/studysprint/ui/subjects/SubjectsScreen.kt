package com.pookie.studysprint.ui.subjects

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pookie.studysprint.data.local.SubjectWithCounts
import com.pookie.studysprint.ui.AppViewModelProvider
import com.pookie.studysprint.ui.components.AppCard
import com.pookie.studysprint.ui.components.AppTextField
import com.pookie.studysprint.ui.components.EmptyState
import com.pookie.studysprint.ui.components.SecondaryButton
import com.pookie.studysprint.ui.theme.Radius
import com.pookie.studysprint.ui.theme.Spacing
import com.pookie.studysprint.ui.theme.extraColors

private sealed interface DialogState {
    data object None : DialogState
    data object Add : DialogState
    data class Edit(val subject: SubjectWithCounts) : DialogState
    data class Delete(val subject: SubjectWithCounts) : DialogState
}

@Composable
fun SubjectsScreen(
    onOpenSubject: (Long) -> Unit,
    onPlanSession: () -> Unit,
    onOpenActive: () -> Unit,
    viewModel: SubjectsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val hasLiveSession by viewModel.hasLiveSession.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DialogState>(DialogState.None) }
    val palette = MaterialTheme.extraColors.subjectColors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { dialog = DialogState.Add },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Radius.control
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add subject")
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
                top = Spacing.xl,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "Subjects",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = subjects?.let { "${it.size} total" } ?: "Loading...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Live session ho to naya plan nahi: wo chalte session ko abort kar deta.
                    SecondaryButton(
                        text = if (hasLiveSession) "Open session" else "Plan session",
                        onClick = if (hasLiveSession) onOpenActive else onPlanSession
                    )
                }
            }

            val list = subjects
            when {
                list == null -> Unit
                list.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Outlined.Book,
                        title = "No subjects yet",
                        message = "Tap the + button to create your first subject.",
                        actionLabel = "Add subject",
                        onAction = { dialog = DialogState.Add }
                    )
                }
                else -> items(list, key = { it.id }) { subject ->
                    SubjectCard(
                        subject = subject,
                        dotColor = palette[subject.colorIndex.coerceIn(0, palette.lastIndex)],
                        onClick = { onOpenSubject(subject.id) },
                        onEdit = { dialog = DialogState.Edit(subject) },
                        onDelete = { dialog = DialogState.Delete(subject) }
                    )
                }
            }
        }
    }

    when (val d = dialog) {
        DialogState.None -> Unit

        DialogState.Add -> SubjectDialog(
            title = "New subject",
            initialName = "",
            initialColor = (subjects?.size ?: 0) % palette.size,
            onDismiss = { dialog = DialogState.None },
            onConfirm = { name, color ->
                viewModel.addSubject(name, color)
                dialog = DialogState.None
            }
        )

        is DialogState.Edit -> SubjectDialog(
            title = "Edit subject",
            initialName = d.subject.name,
            initialColor = d.subject.colorIndex.coerceIn(0, palette.lastIndex),
            onDismiss = { dialog = DialogState.None },
            onConfirm = { name, color ->
                viewModel.updateSubject(d.subject.id, name, color)
                dialog = DialogState.None
            }
        )

        is DialogState.Delete -> AlertDialog(
            onDismissRequest = { dialog = DialogState.None },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete \"${d.subject.name}\"?") },
            text = {
                Text(
                    "All tasks in this subject will also be deleted. " +
                            "Past session history will be kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubject(d.subject.id)
                    dialog = DialogState.None
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = DialogState.None }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SubjectCard(
    subject: SubjectWithCounts,
    dotColor: Color,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (subject.pendingCount + subject.completedCount == 0) {
                        "No tasks yet"
                    } else {
                        "${subject.pendingCount} pending · ${subject.completedCount} done"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit ${subject.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete ${subject.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SubjectDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }
    val palette = MaterialTheme.extraColors.subjectColors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Subject name"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    palette.forEachIndexed { index, swatch ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .selectable(
                                    selected = index == color,
                                    role = Role.RadioButton,
                                    onClick = { color = index }
                                )
                                .semantics { contentDescription = "Color ${index + 1}" },
                            contentAlignment = Alignment.Center
                        ) {
                            if (index == color) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), color) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
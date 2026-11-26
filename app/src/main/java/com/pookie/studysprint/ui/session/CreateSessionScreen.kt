package com.pookie.studysprint.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pookie.studysprint.domain.model.Priority
import com.pookie.studysprint.ui.AppViewModelProvider
import com.pookie.studysprint.ui.components.AppCard
import com.pookie.studysprint.ui.components.ChipKind
import com.pookie.studysprint.ui.components.EmptyState
import com.pookie.studysprint.ui.components.PrimaryButton
import com.pookie.studysprint.ui.components.SectionHeader
import com.pookie.studysprint.ui.components.StatusChip
import com.pookie.studysprint.ui.theme.Radius
import com.pookie.studysprint.ui.theme.Spacing
import com.pookie.studysprint.ui.theme.extraColors
import java.util.Date

private fun Priority.label(): String = when (this) {
    Priority.LOW -> "Low"
    Priority.MEDIUM -> "Medium"
    Priority.HIGH -> "High"
}

private fun formatMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours h"
        else -> "$hours h $minutes min"
    }
}

@Composable
fun CreateSessionScreen(
    onBack: () -> Unit,
    onSessionStarted: () -> Unit,
    viewModel: CreateSessionViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val palette = MaterialTheme.extraColors.subjectColors
    val onSessionStartedLatest by rememberUpdatedState(onSessionStarted)

    // SharedFlow ke ek-baar wale events yahan sunte hain.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateSessionEvent.Started -> onSessionStartedLatest()
                is CreateSessionEvent.Failed ->
                    snackbarHostState.showSnackbar("Couldn't start the session: ${event.message}")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SummaryBar(
                selectedCount = state.selectedCount,
                totalMinutes = state.totalMinutes,
                focusMinutes = state.focusMinutes,
                breakMinutes = state.breakTotalMinutes,
                projectedEndMillis = state.projectedEndMillis,
                isSaving = state.isSaving,
                onStart = viewModel::startSession
            )
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
                bottom = Spacing.xl
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
                    Text(
                        text = "New session",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Pick tasks and set a time for each.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Breaks between tasks",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Added automatically between tasks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.includeBreaks,
                                onCheckedChange = viewModel::setIncludeBreaks,
                                modifier = Modifier.semantics {
                                    contentDescription = "Breaks between tasks"
                                }
                            )
                        }
                        if (state.includeBreaks) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Break length",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                MinutesStepper(
                                    value = state.breakMinutes,
                                    onValueChange = viewModel::setBreakMinutes,
                                    min = CreateSessionViewModel.MIN_BREAK_MINUTES,
                                    max = CreateSessionViewModel.MAX_BREAK_MINUTES,
                                    step = 1
                                )
                            }
                        }
                    }
                }
            }

            if (!state.isLoading && state.items.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.TaskAlt,
                        title = "No pending tasks",
                        message = "Add a task to a subject first, then plan a session.",
                        actionLabel = "Back to subjects",
                        onAction = onBack
                    )
                }
            }

            if (state.items.isNotEmpty()) {
                item { SectionHeader(title = "Pending tasks (${state.items.size})") }
                items(state.items, key = { it.task.id }) { item ->
                    PlanTaskCard(
                        item = item,
                        dotColor = palette[item.colorIndex.coerceIn(0, palette.lastIndex)],
                        onToggle = { viewModel.toggleTask(item.task) },
                        onMinutesChange = { viewModel.setTaskMinutes(item.task.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanTaskCard(
    item: PlanItem,
    dotColor: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    AppCard(onClick = onToggle) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Checkbox(
                    checked = item.isSelected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics {
                        contentDescription = "Select ${item.task.title}"
                    }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = item.subjectName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusChip(label = item.task.priority.label(), kind = ChipKind.Neutral)
                    }
                    // History se suggestion: saaf niyam, pichhli baar ka average.
                    if (item.suggestedMinutes != null && item.suggestedRuns != null) {
                        Text(
                            text = "Averaged ${item.suggestedMinutes} min over your last " +
                                    "${item.suggestedRuns} ${if (item.suggestedRuns == 1) "run" else "runs"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (item.isSelected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Time for this session",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MinutesStepper(
                        value = item.minutes,
                        onValueChange = onMinutesChange,
                        min = CreateSessionViewModel.MIN_TASK_MINUTES,
                        max = CreateSessionViewModel.MAX_TASK_MINUTES,
                        step = 5
                    )
                }
            }
        }
    }
}

@Composable
private fun MinutesStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    step: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(Radius.control)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.control),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { onValueChange((value - step).coerceAtLeast(min)) },
            enabled = value > min
        ) {
            Icon(imageVector = Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text(
            text = "$value min",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp)
        )
        IconButton(
            onClick = { onValueChange((value + step).coerceAtMost(max)) },
            enabled = value < max
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Increase")
        }
    }
}

@Composable
private fun SummaryBar(
    selectedCount: Int,
    totalMinutes: Int,
    focusMinutes: Int,
    breakMinutes: Int,
    projectedEndMillis: Long,
    isSaving: Boolean,
    onStart: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (selectedCount == 0) {
                    Text(
                        text = "No tasks selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tick tasks above",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val endTime = android.text.format.DateFormat
                        .getTimeFormat(context)
                        .format(Date(projectedEndMillis))
                    Text(
                        text = "$selectedCount tasks · ${formatMinutes(totalMinutes)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Focus ${formatMinutes(focusMinutes)} · " +
                                "Breaks ${formatMinutes(breakMinutes)} · ends ~$endTime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PrimaryButton(
                text = if (isSaving) "Starting..." else "Start session",
                onClick = onStart,
                enabled = selectedCount > 0 && !isSaving
            )
        }
    }
}
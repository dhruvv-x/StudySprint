package com.pookie.studysprint.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pookie.studysprint.data.local.SessionBrief
import com.pookie.studysprint.domain.model.SessionStatus
import com.pookie.studysprint.ui.AppViewModelProvider
import com.pookie.studysprint.ui.components.AppCard
import com.pookie.studysprint.ui.components.ChipKind
import com.pookie.studysprint.ui.components.PrimaryButton
import com.pookie.studysprint.ui.components.RaisedCard
import com.pookie.studysprint.ui.components.SecondaryButton
import com.pookie.studysprint.ui.components.SectionHeader
import com.pookie.studysprint.ui.components.StatusChip
import com.pookie.studysprint.ui.components.TimerProgressBar
import com.pookie.studysprint.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onPlanSession: () -> Unit,
    onOpenSubjects: () -> Unit,
    onOpenActive: () -> Unit,
    onOpenSummary: (sessionId: Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val live = state.liveSession

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    top = Spacing.xl,
                    bottom = Spacing.xxl
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (live != null) {
                AppCard(onClick = onOpenActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Session in progress",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusChip(
                                label = if (live.isPaused) "Paused" else "Running",
                                kind = if (live.isPaused) ChipKind.Neutral else ChipKind.Success
                            )
                        }
                        Text(
                            text = "Segment ${live.segmentNumber} of ${live.segmentCount}: ${live.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PrimaryButton(
                            text = "Open session",
                            onClick = onOpenActive,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            TodayCard(
                focusedMs = state.todayFocusedMs,
                goalMinutes = state.dailyGoalMinutes
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PrimaryButton(
                    text = "Plan session",
                    onClick = onPlanSession,
                    enabled = live == null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (live != null) {
                    Text(
                        text = "You can plan a new session once the current one ends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SecondaryButton(
                    text = "Subjects and tasks (${state.pendingTasks} pending)",
                    onClick = onOpenSubjects,
                    modifier = Modifier.fillMaxWidth()
                )
                SecondaryButton(
                    text = "Analytics",
                    onClick = onOpenAnalytics,
                    modifier = Modifier.fillMaxWidth()
                )
                SecondaryButton(
                    text = "Settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionHeader(
                    title = "Recent sessions",
                    actionLabel = if (state.recent.isNotEmpty()) "See all" else null,
                    onAction = onOpenHistory
                )
                if (state.recent.isEmpty() && !state.isLoading) {
                    Text(
                        text = "No sessions yet. Plan your first one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.recent.forEach { brief ->
                    RecentRow(brief = brief, onClick = { onOpenSummary(brief.id) })
                }
            }
        }
    }
}

@Composable
private fun TodayCard(focusedMs: Long, goalMinutes: Int) {
    val focusedMinutes = focusedMs / 60_000L
    val progress =
        if (goalMinutes > 0) focusedMs.toFloat() / (goalMinutes * 60_000L).toFloat() else 0f

    RaisedCard {
        Text(
            text = "Today",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$focusedMinutes min",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Goal: $goalMinutes min",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TimerProgressBar(progress = progress)
    }
}

@Composable
private fun RecentRow(brief: SessionBrief, onClick: () -> Unit) {
    val completed = brief.status == SessionStatus.COMPLETED
    val focusedMinutes = brief.focusedMs / 60_000L

    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "$focusedMinutes min focused",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatWhen(brief.startedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(
                label = if (completed) "Completed" else "Stopped",
                kind = if (completed) ChipKind.Success else ChipKind.Skipped
            )
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun formatWhen(epochMs: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(epochMs))
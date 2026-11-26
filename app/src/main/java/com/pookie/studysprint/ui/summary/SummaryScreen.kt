package com.pookie.studysprint.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SegmentType
import com.pookie.studysprint.domain.model.SessionStatus
import com.pookie.studysprint.ui.AppViewModelProvider
import com.pookie.studysprint.ui.components.AppCard
import com.pookie.studysprint.ui.components.ChipKind
import com.pookie.studysprint.ui.components.PrimaryButton
import com.pookie.studysprint.ui.components.SectionHeader
import com.pookie.studysprint.ui.components.StatusChip
import com.pookie.studysprint.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Itna se zyada chal gaya ho to "Overrun", warna "Success" (auto-advance ka chhota farq ignore). */
private const val OVERRUN_TOLERANCE_MS = 5_000L

@Composable
fun SummaryScreen(
    onClose: () -> Unit,
    viewModel: SummaryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            session == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                Text(
                    text = "Session not found",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                PrimaryButton(
                    text = "Close",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            else -> {
                val completed = session.status == SessionStatus.COMPLETED
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = if (completed) "Session complete" else "Session stopped",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = formatStart(session.startedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            StatRow("Focused time", formatDuration(state.focusedMs))
                            StatRow("Planned (total)", formatDuration(state.plannedMs))
                            StatRow("Actual (total)", formatDuration(state.actualMs))
                            StatRow("Tasks finished", "${state.finishedTasks} of ${state.taskCount}")
                            StatRow("Tasks skipped", "${state.skippedTasks}")
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SectionHeader(title = "Segments")
                        state.segments.forEach { segment ->
                            SegmentRow(segment)
                        }
                    }

                    PrimaryButton(
                        text = "Close",
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentRow(segment: SegmentEntity) {
    val (chipLabel, chipKind) = when (segment.status) {
        SegmentStatus.COMPLETED ->
            if (segment.actualMs > segment.plannedMs + OVERRUN_TOLERANCE_MS) {
                "Overrun" to ChipKind.Overrun
            } else {
                "Success" to ChipKind.Success
            }
        SegmentStatus.SKIPPED -> "Skipped" to ChipKind.Skipped
        else -> "Not started" to ChipKind.Neutral
    }
    val kindText = if (segment.type == SegmentType.BREAK) "Break" else "Focus"

    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = kindText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(label = chipLabel, kind = chipKind)
            }
            StatRow("Planned", formatDuration(segment.plannedMs))
            StatRow("Actual", formatDuration(segment.actualMs))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000L
    return "${totalSeconds / 60L} min ${totalSeconds % 60L} s"
}

private fun formatStart(epochMs: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(epochMs))
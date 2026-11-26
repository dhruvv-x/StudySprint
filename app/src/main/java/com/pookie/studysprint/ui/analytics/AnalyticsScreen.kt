package com.pookie.studysprint.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pookie.studysprint.ui.AppViewModelProvider
import com.pookie.studysprint.ui.components.AppCard
import com.pookie.studysprint.ui.components.EmptyState
import com.pookie.studysprint.ui.components.SectionHeader
import com.pookie.studysprint.ui.components.TimerProgressBar
import com.pookie.studysprint.ui.theme.Spacing
import com.pookie.studysprint.ui.theme.extraColors

private val BAR_AREA_HEIGHT = 120.dp

@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = Spacing.screenHorizontal,
                    end = Spacing.screenHorizontal,
                    top = Spacing.sm,
                    bottom = Spacing.xxl
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
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
                    text = "Analytics",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your study sessions at a glance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = state.rangeDays == 7,
                    onClick = { viewModel.setRange(7) },
                    label = { Text("7 days") }
                )
                FilterChip(
                    selected = state.rangeDays == 30,
                    onClick = { viewModel.setRange(30) },
                    label = { Text("30 days") }
                )
            }

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                state.isError -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "Couldn't load data",
                    message = "Something went wrong while calculating your analytics.",
                    actionLabel = "Retry",
                    onAction = viewModel::retry
                )

                !state.hasData -> EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = "No data in this range",
                    message = "Stats will appear here once you finish a session."
                )

                else -> AnalyticsContent(state)
            }
        }
    }
}

@Composable
private fun AnalyticsContent(state: AnalyticsUiState) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatRow("Total focused", formatDuration(state.totalFocusedMs))
            StatRow("Daily average", formatDuration(state.dailyAverageMs))
            StatRow("Sessions", "${state.sessionCount}")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(title = "Daily focus")
        AppCard {
            DailyBars(state.bars)
        }
    }

    if (state.subjects.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionHeader(title = "By subject")
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    state.subjects.forEach { share -> SubjectRow(share) }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(title = "Planned vs actual")
        AppCard {
            val pace = state.pace
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatRow("Planned", formatDuration(pace.plannedMs))
                StatRow("Actual", formatDuration(pace.actualMs))
                if (pace.plannedMs > 0L) {
                    TimerProgressBar(
                        progress = pace.actualMs.toFloat() / pace.plannedMs.toFloat(),
                        overrun = pace.actualMs > pace.plannedMs
                    )
                }
                Text(
                    text = "Completed tasks only.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader(title = "Tasks")
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatRow("Completed", "${state.pace.completedTasks}")
                StatRow("Skipped", "${state.pace.skippedTasks}")
            }
        }
    }
}

@Composable
private fun DailyBars(bars: List<DayBar>) {
    val maxMs = bars.maxOfOrNull { it.focusedMs } ?: 0L
    val many = bars.size > 7

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = if (maxMs > 0L) "Peak: ${formatDuration(maxMs)}" else "No focus time",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_AREA_HEIGHT),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (many) 2.dp else Spacing.sm)
        ) {
            bars.forEach { bar ->
                val fraction =
                    if (maxMs > 0L) bar.focusedMs.toFloat() / maxMs.toFloat() else 0f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (bar.focusedMs > 0L) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (many) 2.dp else Spacing.sm)
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SubjectRow(share: SubjectShare) {
    val palette = MaterialTheme.extraColors.subjectColors
    val color: Color =
        if (share.colorIndex in palette.indices) palette[share.colorIndex]
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = share.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatDuration(share.focusedMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
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
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "$hours h $minutes min" else "$minutes min"
}
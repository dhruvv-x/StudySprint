package com.pookie.studysprint.ui.session

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pookie.studysprint.domain.model.SegmentType
import com.pookie.studysprint.domain.model.SessionStatus
import com.pookie.studysprint.engine.EngineState
import com.pookie.studysprint.engine.SessionEvent
import com.pookie.studysprint.engine.TimerMath
import com.pookie.studysprint.ui.AppViewModelProvider
import com.pookie.studysprint.ui.components.AppCard
import com.pookie.studysprint.ui.components.ChipKind
import com.pookie.studysprint.ui.components.RaisedCard
import com.pookie.studysprint.ui.components.SectionHeader
import com.pookie.studysprint.ui.components.StatusChip
import com.pookie.studysprint.ui.components.TimerProgressBar
import com.pookie.studysprint.ui.theme.NeuButton
import com.pookie.studysprint.ui.theme.Spacing
import com.pookie.studysprint.ui.theme.TimerTextStyle
import com.pookie.studysprint.ui.theme.extraColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ACTION_COOLDOWN_MS = 800L
private const val MAX_UPCOMING = 5

@Composable
fun ActiveSessionScreen(
    onClosed: () -> Unit,
    onFinished: (sessionId: Long) -> Unit,
    viewModel: ActiveSessionViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recoveryFinished by viewModel.recoveryFinished.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onClosedLatest by rememberUpdatedState(onClosed)
    val onFinishedLatest by rememberUpdatedState(onFinished)
    var showStopDialog by remember { mutableStateOf(false) }
    var lastActionAt by remember { mutableLongStateOf(0L) }

    val live = state.isRunning || state.isPaused
    val finished = state.status == SessionStatus.COMPLETED || state.status == SessionStatus.ABORTED

    // Ignore a second tap on Done/Skip that arrives too quickly, so two segments are never skipped by accident.
    fun runGuarded(action: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt >= ACTION_COOLDOWN_MS) {
            lastActionAt = now
            action()
        }
    }

    // Session finished or stopped: go to the Summary screen (its data comes from the database).
    LaunchedEffect(finished, state.sessionId) {
        val id = state.sessionId
        if (finished && id != null) onFinishedLatest(id)
    }

    // Engine went idle (Summary dismissed it): close this screen.
    // While recovery is still running, idle only means "still loading", so do not close yet.
    LaunchedEffect(state.isIdle, recoveryFinished) {
        if (state.isIdle && recoveryFinished) onClosedLatest()
    }

    // Keep the screen on during a session.
    val view = LocalView.current
    DisposableEffect(live) {
        view.keepScreenOn = live
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = live) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar("A session is in progress. Tap Stop to end it.")
        }
    }

    // One-time events from the SharedFlow.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is SessionEvent.SegmentStarted -> "Now: ${event.label}"
                is SessionEvent.SegmentSkipped -> "Segment skipped"
                is SessionEvent.ScheduleAdjusted -> event.message
                is SessionEvent.PlannedTimeReached ->
                    "Planned time reached. Tap Done, or keep going to run over."
                is SessionEvent.Error -> "Error: ${event.message}"
                else -> null
            }
            if (message != null) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            when {
                state.isIdle || finished -> Unit
                else -> LiveContent(
                    state = state,
                    onPauseResume = {
                        runGuarded { if (state.isPaused) viewModel.resume() else viewModel.pause() }
                    },
                    onDone = { runGuarded { viewModel.done() } },
                    onSkip = { runGuarded { viewModel.skip() } },
                    onStop = { showStopDialog = true }
                )
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Stop this session?") },
            text = {
                Text("The time so far will be saved. The current segment will count as skipped.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    viewModel.stop()
                }) {
                    Text("Stop", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Keep going") }
            }
        )
    }
}

@Composable
private fun LiveContent(
    state: EngineState,
    onPauseResume: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit
) {
    val current = state.current ?: return
    val isBreak = current.type == SegmentType.BREAK
    val timerColor =
        if (state.isOverrun) MaterialTheme.extraColors.overrun
        else MaterialTheme.colorScheme.onBackground
    val (chipLabel, chipKind) = when {
        state.isPaused -> "Paused" to ChipKind.Neutral
        state.isOverrun -> "Overrun" to ChipKind.Overrun
        else -> "Running" to ChipKind.Success
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Segment ${state.currentIndex + 1} of ${state.segments.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusChip(label = chipLabel, kind = chipKind)
        }

        if (state.recovered) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "Session restored",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "The app was closed, so this session is paused. Resume it, or tap Stop to end it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = current.label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = if (isBreak) "Break" else "Focus",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // One spoken description for the whole timer card, so a screen reader
        // says "12 minutes 30 seconds remaining" instead of reading "12:30" and fragments.
        RaisedCard(
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = timerDescription(state)
            }
        ) {
            Text(
                text = TimerMath.formatClock(state.remainingMs),
                style = TimerTextStyle,
                color = timerColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "of ${current.plannedMs / 60_000L} min planned",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            TimerProgressBar(progress = state.progress, overrun = state.isOverrun)
            Text(
                text = sessionLeftText(state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            NeuButton(
                text = if (state.isPaused) "Resume" else "Pause",
                onClick = onPauseResume,
                modifier = Modifier.weight(1f),
                primary = true
            )
            NeuButton(
                text = if (isBreak) "End break" else "Done",
                onClick = onDone,
                modifier = Modifier.weight(1f)
            )
            NeuButton(
                text = "Skip",
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            )
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onStop) {
                Text(
                    text = "Stop session",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        val upcoming = state.upcoming
        if (upcoming.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SectionHeader(title = "Up next")
                upcoming.take(MAX_UPCOMING).forEach { segment ->
                    AppCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Text(
                                text = segment.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            StatusChip(
                                label = "${segment.plannedMs / 60_000L} min",
                                kind = ChipKind.Neutral
                            )
                        }
                    }
                }
                if (upcoming.size > MAX_UPCOMING) {
                    Text(
                        text = "+${upcoming.size - MAX_UPCOMING} more",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "About 42 min left in session", plus a rough end time while the session is running. */
private fun sessionLeftText(state: EngineState): String {
    val leftMin = (state.remainingSessionMs + 59_999L) / 60_000L
    val base = "About $leftMin min left in session"
    if (!state.isRunning) return base
    val endAt = System.currentTimeMillis() + state.remainingSessionMs
    val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(endAt))
    return "$base, ends ~$clock"
}

/** Text a screen reader speaks for the timer card. */
private fun timerDescription(state: EngineState): String {
    val current = state.current ?: return ""
    val ms = state.remainingMs
    val overrun = ms < 0L
    // Same rounding as TimerMath.formatClock, so what is spoken matches what is shown.
    val totalSeconds = if (overrun) (-ms) / 1000L else (ms + 999L) / 1000L
    val spoken = "${plural(totalSeconds / 60L, "minute")} ${plural(totalSeconds % 60L, "second")}"
    val timeText = if (overrun) "$spoken over the planned time" else "$spoken remaining"
    val planned = plural(current.plannedMs / 60_000L, "minute")
    return "$timeText. $planned planned. ${sessionLeftText(state)}"
}

private fun plural(count: Long, unit: String): String =
    if (count == 1L) "$count $unit" else "$count ${unit}s"
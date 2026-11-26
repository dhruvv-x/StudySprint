package com.pookie.studysprint.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.studysprint.data.local.SessionBrief
import com.pookie.studysprint.data.repository.SessionRepository
import com.pookie.studysprint.data.repository.TaskRepository
import com.pookie.studysprint.data.settings.SettingsStore
import com.pookie.studysprint.engine.SessionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/** Chalta ya paused session ki jhalak (Resume card ke liye). */
data class LiveSessionInfo(
    val label: String,
    val isPaused: Boolean,
    val segmentNumber: Int,
    val segmentCount: Int
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val liveSession: LiveSessionInfo? = null,
    val todayFocusedMs: Long = 0L,
    val dailyGoalMinutes: Int = 120,
    val pendingTasks: Int = 0,
    val recent: List<SessionBrief> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    engine: SessionEngine,
    sessionRepository: SessionRepository,
    taskRepository: TaskRepository,
    settingsStore: SettingsStore
) : ViewModel() {

    /**
     * Engine har 250 ms mein state badalta hai (ticker). Home ko sirf "live hai ya nahi"
     * chahiye, isliye map + distinctUntilChanged: label/pause badle tabhi aage jaata hai.
     */
    private val live: Flow<LiveSessionInfo?> = engine.state
        .map { st ->
            val current = st.current
            if ((st.isRunning || st.isPaused) && current != null) {
                LiveSessionInfo(
                    label = current.label,
                    isPaused = st.isPaused,
                    segmentNumber = st.currentIndex + 1,
                    segmentCount = st.segments.size
                )
            } else {
                null
            }
        }
        .distinctUntilChanged()

    /** Har minute "aaj ki subah 12 baje" check karta hai, taaki raat 12 ke baad "Today" khud reset ho. */
    private val dayStart: Flow<Long> = flow {
        while (true) {
            emit(startOfToday())
            delay(DAY_CHECK_MS)
        }
    }.distinctUntilChanged()

    /** Din badalte hi purani query cancel, nayi query shuru (flatMapLatest). */
    private val todayFocused: Flow<Long> = dayStart.flatMapLatest { since ->
        sessionRepository.observeFocusedSince(since)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        live,
        todayFocused,
        settingsStore.settings.map { it.dailyGoalMinutes },
        taskRepository.observePending().map { it.size },
        sessionRepository.observeRecentBriefs(RECENT_LIMIT)
    ) { liveInfo, focused, goal, pending, recent ->
        HomeUiState(
            isLoading = false,
            liveSession = liveInfo,
            todayFocusedMs = focused,
            dailyGoalMinutes = goal,
            pendingTasks = pending,
            recent = recent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    private fun startOfToday(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private companion object {
        const val DAY_CHECK_MS = 60_000L
        const val RECENT_LIMIT = 3
    }
}
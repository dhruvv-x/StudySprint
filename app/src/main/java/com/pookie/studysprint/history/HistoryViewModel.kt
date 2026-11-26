package com.pookie.studysprint.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.studysprint.data.local.SessionBrief
import com.pookie.studysprint.data.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Ek din ke sessions (Today, Yesterday ya tareekh ke naam se). */
data class HistoryGroup(
    val label: String,
    val sessions: List<SessionBrief>
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val groups: List<HistoryGroup> = emptyList(),
    val totalSessions: Int = 0
)

class HistoryViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val briefs: Flow<List<SessionBrief>> =
        sessionRepository.observeRecentBriefs(HISTORY_LIMIT)

    /**
     * Din ke hisaab se grouping list par loop hai, isliye flowOn(Default):
     * Room ka kaam IO par, ye CPU ka kaam Default par, main thread khali.
     */
    val uiState: StateFlow<HistoryUiState> = briefs
        .map { list ->
            HistoryUiState(
                isLoading = false,
                groups = groupByDay(list),
                totalSessions = list.size
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState()
        )

    fun delete(sessionId: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { sessionRepository.deleteFinishedById(sessionId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Delete fail hua to list jaisi thi waisi rahegi (Room flow se wahi dikhta hai).
            }
        }
    }

    private fun groupByDay(list: List<SessionBrief>): List<HistoryGroup> {
        val today = dayStart(System.currentTimeMillis())
        val yesterday = today - DAY_MS
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

        return list
            .groupBy { dayStart(it.startedAt) }
            .map { (day, sessions) ->
                val label = when (day) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> dateFormat.format(Date(day))
                }
                HistoryGroup(label = label, sessions = sessions)
            }
    }

    private fun dayStart(epochMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private companion object {
        const val HISTORY_LIMIT = 500
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
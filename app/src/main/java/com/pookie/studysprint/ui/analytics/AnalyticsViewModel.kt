package com.pookie.studysprint.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.studysprint.data.local.AnalyticsRow
import com.pookie.studysprint.data.local.SubjectEntity
import com.pookie.studysprint.data.repository.SessionRepository
import com.pookie.studysprint.data.repository.SubjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val rangeDays: Int = 7,
    val hasData: Boolean = false,
    val totalFocusedMs: Long = 0L,
    val dailyAverageMs: Long = 0L,
    val sessionCount: Int = 0,
    val bars: List<DayBar> = emptyList(),
    val subjects: List<SubjectShare> = emptyList(),
    val pace: PaceTotals = PaceTotals(0L, 0L, 0, 0)
)

private data class RawAnalytics(
    val days: Int,
    val rows: List<AnalyticsRow>,
    val subjects: List<SubjectEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    sessionRepository: SessionRepository,
    subjectRepository: SubjectRepository
) : ViewModel() {

    private val rangeDays = MutableStateFlow(DEFAULT_DAYS)

    /** Retry dabane par badhta hai, taaki flatMapLatest poori pipeline dobara chalaye. */
    private val retryCount = MutableStateFlow(0)

    /**
     * Range ya retry badalte hi flatMapLatest purani DB query cancel karke nayi shuru karta hai.
     * combine: sessions ka data aur subjects ki list, dono me se koi badle to dobara hisaab.
     * mapLatest: hisaab chal raha ho aur naya data aa jaye to purana hisaab cancel.
     * catch flatMapLatest ke ANDAR hai: error aaye to sirf ye round error state banata hai,
     * bahar ka flow zinda rehta hai aur Retry se dobara chal sakta hai.
     */
    val uiState: StateFlow<AnalyticsUiState> = combine(rangeDays, retryCount) { days, _ -> days }
        .flatMapLatest { days ->
            val since = AnalyticsCalculator.windowStart(days, System.currentTimeMillis())
            combine(
                sessionRepository.observeAnalyticsRows(since),
                subjectRepository.observeAll()
            ) { rows, subjects -> RawAnalytics(days, rows, subjects) }
                .mapLatest { raw -> compute(raw) }
                .catch { e ->
                    // Cancellation ko kabhi nigalna nahi: upar tak jaane do.
                    if (e is CancellationException) throw e
                    emit(AnalyticsUiState(isLoading = false, isError = true, rangeDays = days))
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnalyticsUiState()
        )

    fun setRange(days: Int) {
        rangeDays.value = days
    }

    fun retry() {
        retryCount.value += 1
    }

    /**
     * Teen hisaab ek dusre par depend nahi karte, isliye teeno async se ek saath
     * Dispatchers.Default par (CPU ka kaam). coroutineScope: teeno khatam hone tak
     * wait karta hai, ek fail ho to baaki bhi cancel.
     */
    private suspend fun compute(raw: RawAnalytics): AnalyticsUiState =
        withContext(Dispatchers.Default) {
            coroutineScope {
                val now = System.currentTimeMillis()
                val barsDeferred = async { AnalyticsCalculator.dailyBars(raw.rows, raw.days, now) }
                val subjectsDeferred = async { AnalyticsCalculator.subjectShares(raw.rows, raw.subjects) }
                val paceDeferred = async { AnalyticsCalculator.pace(raw.rows) }

                val total = raw.rows.sumOf { it.actualMs }
                AnalyticsUiState(
                    isLoading = false,
                    rangeDays = raw.days,
                    hasData = raw.rows.isNotEmpty(),
                    totalFocusedMs = total,
                    dailyAverageMs = total / raw.days,
                    sessionCount = raw.rows.map { it.sessionId }.distinct().size,
                    bars = barsDeferred.await(),
                    subjects = subjectsDeferred.await(),
                    pace = paceDeferred.await()
                )
            }
        }

    companion object {
        const val DEFAULT_DAYS = 7
    }
}
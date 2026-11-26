package com.pookie.studysprint.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.studysprint.data.local.SubjectEntity
import com.pookie.studysprint.data.local.TaskEntity
import com.pookie.studysprint.data.repository.SessionRepository
import com.pookie.studysprint.data.repository.SubjectRepository
import com.pookie.studysprint.data.repository.TaskRepository
import com.pookie.studysprint.data.settings.SettingsStore
import com.pookie.studysprint.domain.scheduler.PlannedTask
import com.pookie.studysprint.domain.scheduler.SessionPlanner
import com.pookie.studysprint.domain.scheduler.TimeSuggestion
import com.pookie.studysprint.domain.scheduler.TimeSuggestionRules
import com.pookie.studysprint.engine.SessionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Screen par dikhne wali ek task ki row. */
data class PlanItem(
    val task: TaskEntity,
    val subjectName: String,
    val colorIndex: Int,
    val isSelected: Boolean,
    val minutes: Int,
    /** History se suggestion mili ho to: suggested minutes aur kitni baar ke average se. */
    val suggestedMinutes: Int? = null,
    val suggestedRuns: Int? = null
)

data class CreateSessionUiState(
    val isLoading: Boolean = true,
    val items: List<PlanItem> = emptyList(),
    val includeBreaks: Boolean = true,
    val breakMinutes: Int = 5,
    val selectedCount: Int = 0,
    val focusMinutes: Int = 0,
    val breakTotalMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val projectedEndMillis: Long = 0L,
    val isSaving: Boolean = false
)

/** Ek baar ke signals: rotation par dobara nahi aate (StateFlow mein aate). */
sealed interface CreateSessionEvent {
    /** Engine ne session shuru kar diya: Active Session screen par jao. */
    data class Started(val sessionId: Long) : CreateSessionEvent
    data class Failed(val message: String) : CreateSessionEvent
}

private data class Catalog(
    val tasks: List<TaskEntity>,
    val subjects: Map<Long, SubjectEntity>,
    val suggestions: Map<Long, TimeSuggestion>
)

private data class Options(
    val selection: Map<Long, Int>,
    val includeBreaks: Boolean,
    val breakMinutes: Int
)

class CreateSessionViewModel(
    taskRepository: TaskRepository,
    subjectRepository: SubjectRepository,
    sessionRepository: SessionRepository,
    private val sessionEngine: SessionEngine,
    settingsStore: SettingsStore
) : ViewModel() {

    // taskId -> is session ke liye chuna gaya duration (minutes). Khaali = kuch select nahi.
    private val selection = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val includeBreaks = MutableStateFlow(true)
    // null = Settings wali default break length use karo.
    private val breakOverride = MutableStateFlow<Int?>(null)
    private val isSaving = MutableStateFlow(false)

    private val _events = MutableSharedFlow<CreateSessionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CreateSessionEvent> = _events.asSharedFlow()

    /**
     * taskId -> suggestion. DB ka Flow hai, to history badalte hi apne aap naya aata hai.
     * Averages ko suggestion mein badalna chhota CPU kaam hai: flowOn(Default) se Main thread se door.
     */
    private val suggestions: Flow<Map<Long, TimeSuggestion>> =
        sessionRepository.observeTaskAverages()
            .map { averages ->
                averages
                    .mapNotNull { average ->
                        TimeSuggestionRules.suggest(average)?.let { average.taskId to it }
                    }
                    .toMap()
            }
            .flowOn(Dispatchers.Default)

    private val catalog: Flow<Catalog> = combine(
        taskRepository.observePending(),
        subjectRepository.observeAll(),
        suggestions
    ) { tasks, subjects, suggestionMap ->
        Catalog(
            tasks = tasks,
            subjects = subjects.associateBy { it.id },
            suggestions = suggestionMap
        )
    }

    private val options: Flow<Options> = combine(
        selection,
        includeBreaks,
        breakOverride,
        settingsStore.settings.map { it.breakMinutes }
    ) { chosen, withBreaks, override, defaultBreak ->
        Options(
            selection = chosen,
            includeBreaks = withBreaks,
            breakMinutes = override ?: defaultBreak
        )
    }

    /**
     * Har 30 second mein "abhi ka time" emit karta hai, taaki projected end time ruka na rahe.
     * delay() cancellable hai: screen band hote hi WhileSubscribed ke saath ye loop khud ruk jaata hai.
     */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    val uiState: StateFlow<CreateSessionUiState> = combine(
        catalog,
        options,
        ticker,
        isSaving
    ) { catalog, options, now, saving ->
        val items = catalog.tasks.map { task ->
            val subject = catalog.subjects[task.subjectId]
            val suggestion = catalog.suggestions[task.id]
            val chosenMinutes = options.selection[task.id]
            PlanItem(
                task = task,
                subjectName = subject?.name ?: "Unknown subject",
                colorIndex = subject?.colorIndex ?: 0,
                isSelected = chosenMinutes != null,
                // Niyam: user ne badla ho to wahi, warna history suggestion, warna task ka estimate.
                minutes = chosenMinutes ?: suggestion?.minutes ?: task.estimatedMinutes,
                suggestedMinutes = suggestion?.minutes,
                suggestedRuns = suggestion?.runs
            )
        }
        val planned = items.filter { it.isSelected }.map { PlannedTask(it.task, it.minutes) }
        val focus = SessionPlanner.totalFocusMinutes(planned)
        val breakTotal =
            SessionPlanner.breakCount(planned.size, options.includeBreaks) * options.breakMinutes
        val total = focus + breakTotal

        CreateSessionUiState(
            isLoading = false,
            items = items,
            includeBreaks = options.includeBreaks,
            breakMinutes = options.breakMinutes,
            selectedCount = planned.size,
            focusMinutes = focus,
            breakTotalMinutes = breakTotal,
            totalMinutes = total,
            projectedEndMillis = now + total * SessionPlanner.MINUTE_MS,
            isSaving = saving
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CreateSessionUiState()
    )

    fun toggleTask(task: TaskEntity) {
        // Tick karte waqt wahi minutes lo jo row par dikh rahe hain (suggestion ya estimate).
        val shownMinutes = uiState.value.items
            .firstOrNull { it.task.id == task.id }?.minutes
            ?: task.estimatedMinutes
        selection.value = selection.value.toMutableMap().also { map ->
            if (map.containsKey(task.id)) {
                map.remove(task.id)
            } else {
                map[task.id] = shownMinutes
            }
        }
    }

    fun setTaskMinutes(taskId: Long, minutes: Int) {
        val current = selection.value
        if (!current.containsKey(taskId)) return
        selection.value = current + (taskId to minutes.coerceIn(MIN_TASK_MINUTES, MAX_TASK_MINUTES))
    }

    fun setIncludeBreaks(enabled: Boolean) {
        includeBreaks.value = enabled
    }

    fun setBreakMinutes(minutes: Int) {
        breakOverride.value = minutes.coerceIn(MIN_BREAK_MINUTES, MAX_BREAK_MINUTES)
    }

    /**
     * Plan ko engine ke hawale karta hai. Engine purane adhoore sessions khud abort kar deta hai.
     * Session ban jaane par Started event jaata hai aur screen Active Session par navigate karti hai.
     */
    fun startSession() {
        val state = uiState.value
        if (state.isSaving || state.selectedCount == 0) return

        val planned = state.items
            .filter { it.isSelected }
            .map { PlannedTask(it.task, it.minutes) }

        viewModelScope.launch {
            isSaving.value = true
            try {
                val segments = SessionPlanner.buildSegments(
                    tasks = planned,
                    includeBreaks = state.includeBreaks,
                    breakMinutes = state.breakMinutes
                )
                val sessionId = sessionEngine.start(segments)
                selection.value = emptyMap()
                _events.emit(CreateSessionEvent.Started(sessionId))
            } catch (e: CancellationException) {
                // Cancellation ko kabhi nigalna nahi: upar tak jaane do.
                throw e
            } catch (e: Exception) {
                _events.emit(CreateSessionEvent.Failed(e.message ?: "Unknown error"))
            } finally {
                isSaving.value = false
            }
        }
    }

    companion object {
        private const val TICK_MS = 30_000L
        const val MIN_TASK_MINUTES = 1
        const val MAX_TASK_MINUTES = 480
        const val MIN_BREAK_MINUTES = 1
        const val MAX_BREAK_MINUTES = 60
    }
}
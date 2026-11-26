package com.pookie.studysprint.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.data.local.SessionEntity
import com.pookie.studysprint.data.repository.SessionRepository
import com.pookie.studysprint.domain.model.SegmentType
import com.pookie.studysprint.engine.SessionEngine
import com.pookie.studysprint.ui.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SummaryUiState(
    val isLoading: Boolean = true,
    val session: SessionEntity? = null,
    val segments: List<SegmentEntity> = emptyList()
) {
    private val tasks: List<SegmentEntity>
        get() = segments.filter { it.type == SegmentType.TASK }

    val focusedMs: Long get() = tasks.sumOf { it.actualMs }
    val plannedMs: Long get() = segments.sumOf { it.plannedMs }
    val actualMs: Long get() = segments.sumOf { it.actualMs }
    val taskCount: Int get() = tasks.size
    val finishedTasks: Int
        get() = tasks.count { it.status == com.pookie.studysprint.domain.model.SegmentStatus.COMPLETED }
    val skippedTasks: Int
        get() = tasks.count { it.status == com.pookie.studysprint.domain.model.SegmentStatus.SKIPPED }
}

/**
 * Summary data DB se aata hai (sessionId navigation argument se), engine ki memory se nahi.
 * Isliye History se bhi yahi screen khul sakegi. Load hone ke baad engine ko idle kar dete hain.
 */
class SummaryViewModel(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val engine: SessionEngine
) : ViewModel() {

    private val sessionId: Long =
        checkNotNull(savedStateHandle.get<Long>(Routes.ARG_SESSION_ID)) {
            "sessionId navigation argument missing"
        }

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                // Session aur segments ek dusre par depend nahi karte: dono ek saath IO par.
                val (session, segments) = coroutineScope {
                    val sessionDeferred = async(Dispatchers.IO) { sessionRepository.getSession(sessionId) }
                    val segmentsDeferred = async(Dispatchers.IO) { sessionRepository.getSegments(sessionId) }
                    sessionDeferred.await() to segmentsDeferred.await()
                }
                _uiState.value = SummaryUiState(
                    isLoading = false,
                    session = session,
                    segments = segments
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = SummaryUiState(isLoading = false)
            }
            // Data mil gaya: engine ka finished state saaf. Live session ho to ye kuch nahi karta.
            engine.dismissFinished()
        }
    }
}
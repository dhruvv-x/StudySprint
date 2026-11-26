package com.pookie.studysprint.ui.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.studysprint.data.local.SubjectWithCounts
import com.pookie.studysprint.data.repository.SubjectRepository
import com.pookie.studysprint.engine.SessionEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubjectsViewModel(
    private val repository: SubjectRepository,
    engine: SessionEngine
) : ViewModel() {

    /** null = abhi load ho raha hai, empty list = koi subject nahi. */
    val subjects: StateFlow<List<SubjectWithCounts>?> = repository.observeWithCounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /**
     * Koi session chal raha ya paused hai ya nahi. Engine har 250 ms state badalta hai,
     * isliye distinctUntilChanged: Subjects screen sirf true/false badalne par recompose hoti hai.
     * Live session mein naya session start karna purane ko abort kar dega, isliye yahan rok lagti hai.
     */
    val hasLiveSession: StateFlow<Boolean> = engine.state
        .map { it.isRunning || it.isPaused }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun addSubject(name: String, colorIndex: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.add(name, colorIndex) }
    }

    fun updateSubject(id: Long, name: String, colorIndex: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val existing = repository.getById(id) ?: return@launch
            repository.update(existing.copy(name = name.trim(), colorIndex = colorIndex))
        }
    }

    fun deleteSubject(id: Long) {
        viewModelScope.launch {
            val existing = repository.getById(id) ?: return@launch
            repository.delete(existing)
        }
    }
}
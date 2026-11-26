package com.pookie.studysprint.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.studysprint.data.local.TaskEntity
import com.pookie.studysprint.data.repository.SubjectRepository
import com.pookie.studysprint.data.repository.TaskRepository
import com.pookie.studysprint.domain.model.Priority
import com.pookie.studysprint.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val isLoading: Boolean = true,
    val subjectName: String = "",
    val colorIndex: Int = 0,
    val pending: List<TaskEntity> = emptyList(),
    val completed: List<TaskEntity> = emptyList()
)

class TasksViewModel(
    savedStateHandle: SavedStateHandle,
    subjectRepository: SubjectRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val subjectId: Long =
        checkNotNull(savedStateHandle.get<Long>(Routes.ARG_SUBJECT_ID)) {
            "subjectId navigation argument missing"
        }

    val uiState: StateFlow<TasksUiState> = combine(
        subjectRepository.observeAll().map { list -> list.firstOrNull { it.id == subjectId } },
        taskRepository.observeBySubject(subjectId)
    ) { subject, tasks ->
        TasksUiState(
            isLoading = false,
            subjectName = subject?.name ?: "Tasks",
            colorIndex = subject?.colorIndex ?: 0,
            pending = tasks.filter { !it.isCompleted },
            completed = tasks.filter { it.isCompleted }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState()
    )

    fun addTask(title: String, priority: Priority, estimatedMinutes: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.add(subjectId, title, priority, estimatedMinutes)
        }
    }

    fun updateTask(task: TaskEntity, title: String, priority: Priority, estimatedMinutes: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.update(
                task.copy(
                    title = title.trim(),
                    priority = priority,
                    estimatedMinutes = estimatedMinutes
                )
            )
        }
    }

    fun setCompleted(task: TaskEntity, completed: Boolean) {
        viewModelScope.launch { taskRepository.setCompleted(task.id, completed) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { taskRepository.delete(task) }
    }
}
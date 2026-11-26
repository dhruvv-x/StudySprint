package com.pookie.studysprint.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pookie.studysprint.StudySprintApp
import com.pookie.studysprint.ui.analytics.AnalyticsViewModel
import com.pookie.studysprint.ui.history.HistoryViewModel
import com.pookie.studysprint.ui.home.HomeViewModel
import com.pookie.studysprint.ui.session.ActiveSessionViewModel
import com.pookie.studysprint.ui.session.CreateSessionViewModel
import com.pookie.studysprint.ui.settings.SettingsViewModel
import com.pookie.studysprint.ui.subjects.SubjectsViewModel
import com.pookie.studysprint.ui.summary.SummaryViewModel
import com.pookie.studysprint.ui.tasks.TasksViewModel

/** Saare ViewModels yahin se bante hain, AppContainer ke repositories ke saath. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val container = studySprintApp().container
            HomeViewModel(
                engine = container.sessionEngine,
                sessionRepository = container.sessionRepository,
                taskRepository = container.taskRepository,
                settingsStore = container.settingsStore
            )
        }
        initializer {
            val container = studySprintApp().container
            AnalyticsViewModel(
                sessionRepository = container.sessionRepository,
                subjectRepository = container.subjectRepository
            )
        }
        initializer {
            SettingsViewModel(studySprintApp().container.settingsStore)
        }
        initializer {
            HistoryViewModel(studySprintApp().container.sessionRepository)
        }
        initializer {
            val container = studySprintApp().container
            SubjectsViewModel(
                repository = container.subjectRepository,
                engine = container.sessionEngine
            )
        }
        initializer {
            val container = studySprintApp().container
            TasksViewModel(
                savedStateHandle = createSavedStateHandle(),
                subjectRepository = container.subjectRepository,
                taskRepository = container.taskRepository
            )
        }
        initializer {
            val container = studySprintApp().container
            CreateSessionViewModel(
                taskRepository = container.taskRepository,
                subjectRepository = container.subjectRepository,
                sessionRepository = container.sessionRepository,
                sessionEngine = container.sessionEngine,
                settingsStore = container.settingsStore
            )
        }
        initializer {
            ActiveSessionViewModel(studySprintApp().container.sessionEngine)
        }
        initializer {
            val container = studySprintApp().container
            SummaryViewModel(
                savedStateHandle = createSavedStateHandle(),
                sessionRepository = container.sessionRepository,
                engine = container.sessionEngine
            )
        }
    }
}

fun CreationExtras.studySprintApp(): StudySprintApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as StudySprintApp
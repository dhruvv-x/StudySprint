package com.pookie.studysprint.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pookie.studysprint.data.settings.AppSettings
import com.pookie.studysprint.data.settings.SettingsStore
import com.pookie.studysprint.data.settings.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore
) : ViewModel() {

    /** DataStore ka Flow, StateFlow bana kar. Screen band hone ke 5 s baad collection ruk jaata hai. */
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun setBreakMinutes(minutes: Int) =
        save { settingsStore.setBreakMinutes(minutes.coerceIn(MIN_BREAK, MAX_BREAK)) }

    fun setAutoAdvance(enabled: Boolean) =
        save { settingsStore.setAutoAdvance(enabled) }

    fun setDailyGoalMinutes(minutes: Int) =
        save { settingsStore.setDailyGoalMinutes(minutes.coerceIn(MIN_GOAL, MAX_GOAL)) }

    fun setThemeMode(mode: ThemeMode) =
        save { settingsStore.setThemeMode(mode) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Save fail hua to DataStore ka Flow purani value hi dikhata rahega.
            }
        }
    }

    companion object {
        const val MIN_BREAK = 1
        const val MAX_BREAK = 60
        const val MIN_GOAL = 15
        const val MAX_GOAL = 600
    }
}
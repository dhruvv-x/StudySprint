package com.pookie.studysprint.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val breakMinutes: Int = 5,
    val autoAdvance: Boolean = true,
    val dailyGoalMinutes: Int = 120,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
        val THEME = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            breakMinutes = prefs[Keys.BREAK_MINUTES] ?: defaults.breakMinutes,
            autoAdvance = prefs[Keys.AUTO_ADVANCE] ?: defaults.autoAdvance,
            dailyGoalMinutes = prefs[Keys.DAILY_GOAL] ?: defaults.dailyGoalMinutes,
            themeMode = prefs[Keys.THEME]
                ?.let { name -> ThemeMode.values().firstOrNull { it.name == name } }
                ?: defaults.themeMode
        )
    }

    suspend fun setBreakMinutes(minutes: Int) {
        dataStore.edit { it[Keys.BREAK_MINUTES] = minutes }
    }

    suspend fun setAutoAdvance(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_ADVANCE] = enabled }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DAILY_GOAL] = minutes }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = mode.name }
    }
}
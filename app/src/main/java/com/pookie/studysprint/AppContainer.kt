package com.pookie.studysprint

import android.content.Context
import com.pookie.studysprint.data.local.StudyDatabase
import com.pookie.studysprint.data.repository.SessionRepository
import com.pookie.studysprint.data.repository.SubjectRepository
import com.pookie.studysprint.data.repository.TaskRepository
import com.pookie.studysprint.data.settings.SettingsStore
import com.pookie.studysprint.engine.SessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection. Har cheez yahan ek baar banti hai,
 * aur baaki app yahin se leti hai. (Hilt ke bina "constructor injection".)
 */
class AppContainer(context: Context) {

    private val database: StudyDatabase = StudyDatabase.create(context)

    val subjectRepository = SubjectRepository(database.subjectDao())
    val taskRepository = TaskRepository(database.taskDao())
    val sessionRepository = SessionRepository(
        database = database,
        sessionDao = database.sessionDao(),
        segmentDao = database.segmentDao()
    )
    val settingsStore = SettingsStore(context)

    /**
     * App-scoped scope: process ke saath jeeta hai, GlobalScope nahi (hamara naam wala,
     * hamare control ka scope). SupervisorJob: ek child fail ho to baaki scope na toote.
     * Default dispatcher: engine ka kaam CPU/timing ka hai, DB I/O engine ke andar IO par jaata hai.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val sessionEngine = SessionEngine(
        scope = applicationScope,
        sessionRepository = sessionRepository,
        taskRepository = taskRepository,
        settingsStore = settingsStore
    )
}
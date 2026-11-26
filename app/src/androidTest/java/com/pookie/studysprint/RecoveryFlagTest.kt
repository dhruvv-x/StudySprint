package com.pookie.studysprint

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.data.local.StudyDatabase
import com.pookie.studysprint.data.repository.SessionRepository
import com.pookie.studysprint.data.repository.TaskRepository
import com.pookie.studysprint.data.settings.SettingsStore
import com.pookie.studysprint.domain.model.SegmentType
import com.pookie.studysprint.engine.SessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** recoveryFinished flag: false se shuru, recover() ke baad hamesha true (session ho ya na ho). */
@RunWith(AndroidJUnit4::class)
class RecoveryFlagTest {

    private lateinit var db: StudyDatabase
    private lateinit var sessionRepository: SessionRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var firstScope: CoroutineScope
    private lateinit var secondScope: CoroutineScope

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StudyDatabase::class.java).build()
        sessionRepository = SessionRepository(db, db.sessionDao(), db.segmentDao())
        taskRepository = TaskRepository(db.taskDao())
        settingsStore = SettingsStore(context)
        firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        runBlocking {
            firstScope.coroutineContext.job.cancelAndJoin()
            secondScope.coroutineContext.job.cancelAndJoin()
            db.close()
        }
    }

    private fun engine(scope: CoroutineScope) =
        SessionEngine(scope, sessionRepository, taskRepository, settingsStore)

    @Test
    fun flagStartsFalse() {
        assertFalse(engine(firstScope).recoveryFinished.value)
    }

    @Test
    fun recoverWithNothingStored_setsFlagAndStaysIdle() = runBlocking<Unit> {
        val engine = engine(firstScope)
        engine.recover()

        withTimeout(3_000L) { engine.recoveryFinished.first { it } }
        assertTrue(engine.state.value.isIdle)
    }

    @Test
    fun recoverWithStoredSession_setsFlagAndRestoresPaused() = runBlocking<Unit> {
        val dead = engine(firstScope)
        dead.start(
            listOf(
                SegmentEntity(
                    sessionId = 0,
                    orderIndex = 0,
                    type = SegmentType.TASK,
                    label = "A",
                    plannedMs = 60_000L
                )
            )
        )
        firstScope.coroutineContext.job.cancelAndJoin() // process death

        val revived = engine(secondScope)
        revived.recover()

        withTimeout(3_000L) { revived.recoveryFinished.first { it } }
        assertTrue(revived.state.value.isPaused)
        assertTrue(revived.state.value.recovered)
    }
}
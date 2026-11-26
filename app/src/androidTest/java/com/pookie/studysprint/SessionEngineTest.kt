package com.pookie.studysprint

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.data.local.StudyDatabase
import com.pookie.studysprint.data.repository.SessionRepository
import com.pookie.studysprint.data.repository.SubjectRepository
import com.pookie.studysprint.data.repository.TaskRepository
import com.pookie.studysprint.data.settings.SettingsStore
import com.pookie.studysprint.domain.model.Priority
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SegmentType
import com.pookie.studysprint.domain.model.SessionStatus
import com.pookie.studysprint.engine.EngineState
import com.pookie.studysprint.engine.SessionEngine
import com.pookie.studysprint.engine.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SessionEngine ke asli tests: in-memory Room (app ka asli data nahi chhuta) aur real clock.
 * Segments chhote rakhe hain (300 ms se 1 s) taaki test jaldi chale.
 * Sirf autoAdvance setting real DataStore se jaati hai: test se pehle ki value tearDown mein wapas.
 */
@RunWith(AndroidJUnit4::class)
class SessionEngineTest {

    private lateinit var db: StudyDatabase
    private lateinit var sessionRepository: SessionRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var subjectRepository: SubjectRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var scope: CoroutineScope
    private lateinit var firstProcessScope: CoroutineScope
    private lateinit var engine: SessionEngine

    private var subjectId = 0L
    private var originalAutoAdvance = true

    private val state: EngineState get() = engine.state.value

    @Before
    fun setUp() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            db = Room.inMemoryDatabaseBuilder(context, StudyDatabase::class.java).build()
            sessionRepository = SessionRepository(db, db.sessionDao(), db.segmentDao())
            taskRepository = TaskRepository(db.taskDao())
            subjectRepository = SubjectRepository(db.subjectDao())
            settingsStore = SettingsStore(context)

            originalAutoAdvance = settingsStore.settings.first().autoAdvance
            settingsStore.setAutoAdvance(true)

            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            firstProcessScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            engine = SessionEngine(scope, sessionRepository, taskRepository, settingsStore)
            subjectId = subjectRepository.add("Test subject", 0)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            scope.coroutineContext.job.cancelAndJoin()
            firstProcessScope.coroutineContext.job.cancelAndJoin()
            settingsStore.setAutoAdvance(originalAutoAdvance)
            db.close()
        }
    }

    // ---------------- helpers ----------------

    private fun taskSegment(label: String, plannedMs: Long, taskId: Long? = null) = SegmentEntity(
        sessionId = 0,
        orderIndex = 0,
        type = SegmentType.TASK,
        taskId = taskId,
        subjectId = if (taskId != null) subjectId else null,
        label = label,
        plannedMs = plannedMs
    )

    private fun breakSegment(plannedMs: Long) = SegmentEntity(
        sessionId = 0,
        orderIndex = 0,
        type = SegmentType.BREAK,
        label = "Break",
        plannedMs = plannedMs
    )

    private suspend fun newTask(title: String): Long =
        taskRepository.add(subjectId, title, Priority.MEDIUM, 25)

    private suspend fun awaitState(
        timeoutMs: Long = 5_000L,
        predicate: (EngineState) -> Boolean
    ): EngineState = withTimeout(timeoutMs) { engine.state.first(predicate) }

    // ---------------- tests ----------------

    @Test
    fun start_createsActiveSession() = runBlocking<Unit> {
        val taskId = newTask("A")
        val id = engine.start(listOf(taskSegment("A", 60_000L, taskId)))

        assertTrue(state.isRunning)
        assertEquals(id, state.sessionId)
        assertEquals("A", state.current?.label)

        val stored = sessionRepository.getSession(id)
        assertNotNull(stored)
        assertEquals(SessionStatus.ACTIVE, stored!!.status)
        assertNotNull(stored.lastResumedAt)
        assertEquals(SegmentStatus.ACTIVE, sessionRepository.getSegments(id)[0].status)
    }

    @Test
    fun pauseAndResume_freezeAndRestartTimer() = runBlocking<Unit> {
        val id = engine.start(listOf(taskSegment("A", 60_000L)))
        delay(300)

        engine.pause()
        awaitState { it.isPaused }
        val frozen = state.segmentElapsedMs
        assertTrue("elapsed pause se pehle badhna chahiye", frozen >= 250L)

        delay(400)
        assertEquals("paused mein elapsed nahi badhna chahiye", frozen, state.segmentElapsedMs)
        val pausedRow = sessionRepository.getSession(id)!!
        assertEquals(SessionStatus.PAUSED, pausedRow.status)
        assertNull(pausedRow.lastResumedAt)
        assertEquals(frozen, pausedRow.accumulatedActiveMs)

        engine.resume()
        awaitState { it.isRunning }
        delay(600)
        assertTrue("resume ke baad elapsed badhna chahiye", state.segmentElapsedMs > frozen)
        assertNotNull(sessionRepository.getSession(id)!!.lastResumedAt)
    }

    @Test
    fun timerAccuracy_matchesWallClock() = runBlocking<Unit> {
        engine.start(listOf(taskSegment("A", 60_000L)))
        delay(1_000)
        engine.pause()
        awaitState { it.isPaused }

        val elapsed = state.segmentElapsedMs
        assertTrue("elapsed $elapsed ms, 900..1500 ke beech hona chahiye", elapsed in 900L..1_500L)
    }

    @Test
    fun complete_marksTaskDoneAndMovesOn() = runBlocking<Unit> {
        val taskA = newTask("A")
        val taskB = newTask("B")
        val id = engine.start(
            listOf(
                taskSegment("A", 60_000L, taskA),
                breakSegment(60_000L),
                taskSegment("B", 60_000L, taskB)
            )
        )

        engine.completeCurrent()
        awaitState { it.currentIndex == 1 }

        val segments = sessionRepository.getSegments(id)
        assertEquals(SegmentStatus.COMPLETED, segments[0].status)
        assertEquals(SegmentStatus.ACTIVE, segments[1].status)
        assertEquals("break ka time nahi badalna chahiye", 60_000L, segments[1].plannedMs)
        assertTrue("Done se task completed hona chahiye", taskRepository.getById(taskA)!!.isCompleted)
        assertFalse(taskRepository.getById(taskB)!!.isCompleted)
    }

    @Test
    fun skip_keepsTaskPendingAndDropsBreak() = runBlocking<Unit> {
        val taskA = newTask("A")
        val taskB = newTask("B")
        val id = engine.start(
            listOf(
                taskSegment("A", 60_000L, taskA),
                breakSegment(60_000L),
                taskSegment("B", 60_000L, taskB)
            )
        )

        // 1 min se kam chala, to adaptive rule agla break hata deta hai.
        engine.skipCurrent()
        awaitState { it.currentIndex == 2 }

        val segments = sessionRepository.getSegments(id)
        assertEquals(SegmentStatus.SKIPPED, segments[0].status)
        assertEquals(SegmentStatus.SKIPPED, segments[1].status)
        assertEquals(SegmentStatus.ACTIVE, segments[2].status)
        assertFalse("Skip se task pending hi rehna chahiye", taskRepository.getById(taskA)!!.isCompleted)
    }

    @Test
    fun complete_lastSegmentFinishesSession() = runBlocking<Unit> {
        val taskA = newTask("A")
        val id = engine.start(listOf(taskSegment("A", 60_000L, taskA)))

        engine.completeCurrent()
        awaitState { it.status == SessionStatus.COMPLETED }

        val stored = sessionRepository.getSession(id)!!
        assertEquals(SessionStatus.COMPLETED, stored.status)
        assertNotNull(stored.endedAt)
        assertNull(stored.lastResumedAt)
    }

    @Test
    fun stop_abortsSessionAndSavesProgress() = runBlocking<Unit> {
        val id = engine.start(listOf(taskSegment("A", 60_000L), taskSegment("B", 60_000L)))
        delay(300)

        engine.stop()
        awaitState { it.status == SessionStatus.ABORTED }

        val stored = sessionRepository.getSession(id)!!
        assertEquals(SessionStatus.ABORTED, stored.status)
        assertNotNull(stored.endedAt)
        val first = sessionRepository.getSegments(id)[0]
        assertEquals(SegmentStatus.SKIPPED, first.status)
        assertTrue("jitna time laga wo save hona chahiye", first.actualMs >= 250L)
    }

    @Test
    fun newSession_abortsTheOldOne() = runBlocking<Unit> {
        val first = engine.start(listOf(taskSegment("A", 60_000L)))
        val second = engine.start(listOf(taskSegment("B", 60_000L)))

        assertNotEquals(first, second)
        assertEquals(SessionStatus.ABORTED, sessionRepository.getSession(first)!!.status)
        assertEquals(second, sessionRepository.getActive()!!.id)
        assertEquals(second, state.sessionId)
    }

    @Test
    fun autoAdvanceOn_movesOnAtPlannedTime() = runBlocking<Unit> {
        val taskA = newTask("A")
        val id = engine.start(
            listOf(taskSegment("A", 500L, taskA), taskSegment("B", 60_000L))
        )

        awaitState { it.currentIndex == 1 }

        val first = sessionRepository.getSegments(id)[0]
        assertEquals(SegmentStatus.COMPLETED, first.status)
        assertTrue("actual ~planned hona chahiye, mila ${first.actualMs}", first.actualMs >= 400L)
        assertFalse(
            "timer se khatam hone par task done mark nahi hota",
            taskRepository.getById(taskA)!!.isCompleted
        )
    }

    @Test
    fun autoAdvanceOff_staysOnSegmentAndOverruns() = runBlocking<Unit> {
        settingsStore.setAutoAdvance(false)

        val reached = async(start = CoroutineStart.UNDISPATCHED) {
            engine.events.first { it is SessionEvent.PlannedTimeReached }
        }
        engine.start(listOf(taskSegment("A", 500L), taskSegment("B", 60_000L)))

        withTimeout(5_000L) { reached.await() }
        val overrunState = awaitState { it.isOverrun }

        assertEquals("agle segment par nahi jaana chahiye", 0, overrunState.currentIndex)
        assertTrue(overrunState.isRunning)
    }

    @Test
    fun overrun_shortensNextBreak() = runBlocking<Unit> {
        settingsStore.setAutoAdvance(false)
        val id = engine.start(
            listOf(
                taskSegment("A", 300L),
                breakSegment(120_000L),
                taskSegment("B", 60_000L)
            )
        )

        awaitState { it.isOverrun }
        delay(5_500) // overrun 5 s ki tolerance se zyada ho jaye

        engine.completeCurrent()
        awaitState { it.currentIndex == 1 }

        val breakPlanned = sessionRepository.getSegments(id)[1].plannedMs
        assertTrue("break chhota hona chahiye, mila $breakPlanned", breakPlanned < 120_000L)
        assertTrue("break floor (1 min) se neeche nahi", breakPlanned >= 60_000L)
    }

    @Test
    fun processDeath_recoversPausedWithElapsedTime() = runBlocking<Unit> {
        // Pehla "process": session shuru, phir bina pause kiye achanak maut (scope cancel).
        val firstEngine = SessionEngine(
            firstProcessScope, sessionRepository, taskRepository, settingsStore
        )
        val id = firstEngine.start(listOf(taskSegment("A", 60_000L), taskSegment("B", 60_000L)))
        firstProcessScope.coroutineContext.job.cancelAndJoin()
        delay(400) // app band rehne ka time

        // Naya "process": naya engine, recover().
        engine.recover()
        val restored = awaitState { !it.isIdle }

        assertTrue("recovery ke baad PAUSED hona chahiye", restored.isPaused)
        assertTrue(restored.recovered)
        assertEquals(id, restored.sessionId)
        assertEquals(0, restored.currentIndex)
        assertTrue(
            "band rehne ka time judna chahiye, mila ${restored.segmentElapsedMs}",
            restored.segmentElapsedMs >= 350L
        )

        val stored = sessionRepository.getSession(id)!!
        assertEquals(SessionStatus.PAUSED, stored.status)
        assertNull(stored.lastResumedAt)
        assertEquals(SegmentStatus.ACTIVE, sessionRepository.getSegments(id)[0].status)

        // User Resume dabata hai.
        engine.resume()
        val resumed = awaitState { it.isRunning }
        assertFalse(resumed.recovered)
    }

    @Test
    fun rapidCommands_keepStateConsistent() = runBlocking<Unit> {
        val id = engine.start(listOf(taskSegment("A", 60_000L)))

        // Kai coroutines ek saath pause/resume bhejein: mutex state ko ek-ek karke badalta hai.
        coroutineScope {
            repeat(20) {
                launch(Dispatchers.Default) {
                    engine.pause()
                    engine.resume()
                }
            }
        }
        delay(1_500)

        val inMemory = state
        val stored = sessionRepository.getSession(id)!!
        assertEquals("memory aur DB ka status same hona chahiye", inMemory.status, stored.status)
        assertTrue(inMemory.segmentElapsedMs in 0L..10_000L)
        if (stored.status == SessionStatus.ACTIVE) {
            assertNotNull(stored.lastResumedAt)
        } else {
            assertNull(stored.lastResumedAt)
        }
    }
}
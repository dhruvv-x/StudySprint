package com.pookie.studysprint.engine

import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.data.local.SessionEntity
import com.pookie.studysprint.data.repository.SessionRepository
import com.pookie.studysprint.data.repository.TaskRepository
import com.pookie.studysprint.data.settings.SettingsStore
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SegmentType
import com.pookie.studysprint.domain.model.SessionStatus
import com.pookie.studysprint.domain.scheduler.AdaptiveRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** transitionJob ko jaanne ke liye ki kab tak wait karna hai. Isme koi tick-value nahi, sirf timing ke asli inputs. */
private data class TimingKey(
    val sessionId: Long,
    val index: Int,
    val accumulatedActiveMs: Long,
    val lastResumedAt: Long?,
    val plannedMs: Long
)

/**
 * Study session ka engine. App-scoped (AppContainer mein ek hi instance).
 *
 * Source of truth: DB ke timestamps (SessionEntity.accumulatedActiveMs + lastResumedAt).
 * Koi bhi loop time "count" nahi karta. Ticker sirf UI ko refresh karta hai.
 * Isiliye app kill hone par bhi time sahi rehta hai.
 *
 * Saare state-changing kaam [mutex] ke andar chalte hain, taaki pause, skip aur
 * auto-advance ek saath aa jayein to bhi state ek-ek karke badle.
 */
class SessionEngine(
    private val scope: CoroutineScope,
    private val sessionRepository: SessionRepository,
    private val taskRepository: TaskRepository,
    private val settingsStore: SettingsStore,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val mutex = Mutex()

    // In-memory copy. Sirf mutex ke andar padho/badlo.
    private var session: SessionEntity? = null
    private var segments: List<SegmentEntity> = emptyList()
    private var recovered = false
    private var overrunNotifiedIndex = -1
    private var sessionJob: Job? = null

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _timing = MutableStateFlow<TimingKey?>(null)

    /**
     * false = app start par recover() abhi chal raha hai. true = recovery khatam
     * (session mila ho ya na mila ho, ya fail hui ho). Splash aur Active screen isse
     * jaanti hain ki "idle" ka matlab sach mein "koi session nahi" hai ya "abhi load ho raha hai".
     */
    private val _recoveryFinished = MutableStateFlow(false)
    val recoveryFinished: StateFlow<Boolean> = _recoveryFinished.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Naya session shuru karta hai aur uska id return karta hai.
     * Pehle koi chalta/adhoora session ho to use abort karta hai.
     * Kaam scope.async mein hota hai: caller (screen/ViewModel) cancel ho jaaye
     * to bhi session aadha nahi banega.
     */
    suspend fun start(newSegments: List<SegmentEntity>): Long {
        require(newSegments.isNotEmpty()) { "A session needs at least one segment" }
        return scope.async {
            mutex.withLock {
                val now = clock()
                abortEverythingLocked(now)
                val id = io { sessionRepository.createSession(newSegments, now) }
                loadLocked(id, isRecovered = false, now = now)
                launchSessionJobs()
                _events.tryEmit(SessionEvent.SegmentStarted(0, newSegments.first().label))
                id
            }
        }.await()
    }

    fun pause() = command { pauseLocked() }

    fun resume() = command { resumeLocked() }

    /** "Done": current segment complete. Task ho to task bhi complete ho jaata hai. */
    fun completeCurrent() = command {
        advanceLocked(clock(), SegmentStatus.COMPLETED, auto = false, markTaskDone = true)
    }

    /** Skip: segment SKIPPED, task pending hi rehta hai. */
    fun skipCurrent() = command {
        advanceLocked(clock(), SegmentStatus.SKIPPED, auto = false, markTaskDone = false)
    }

    /** Session beech mein rok do (ABORTED). */
    fun stop() = command { abortLocked(clock()) }

    /** App start par ek baar bulao: adhoora session mile to PAUSED state mein wapas laata hai. */
    fun recover() = command {
        try {
            recoverLocked()
        } finally {
            // Chahe session mila, na mila, ya error aaya: UI ko intezaar mein atka nahi rehna.
            _recoveryFinished.value = true
        }
    }

    /** Summary dekh liya: engine ko idle kar do. */
    fun dismissFinished() = command {
        val s = session
        if (s != null && s.status != SessionStatus.ACTIVE && s.status != SessionStatus.PAUSED) {
            session = null
            segments = emptyList()
            recovered = false
            publish(clock())
        }
    }

    /** Recovery dialog ka jawab mil gaya (End dabaya ya dialog band kiya). */
    fun acknowledgeRecovery() = command {
        if (recovered) {
            recovered = false
            publish(clock())
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    private fun command(block: suspend () -> Unit) {
        scope.launch {
            try {
                mutex.withLock { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(SessionEvent.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private suspend fun pauseLocked() {
        val s = session ?: return
        if (s.status != SessionStatus.ACTIVE) return
        val now = clock()
        val idx = s.currentSegmentIndex
        val current = segments.getOrNull(idx) ?: return
        val elapsed = TimerMath.elapsedMs(s.accumulatedActiveMs, s.lastResumedAt, now)

        val paused = s.copy(
            status = SessionStatus.PAUSED,
            accumulatedActiveMs = elapsed,
            lastResumedAt = null
        )
        val currentUpdated = current.copy(actualMs = elapsed)

        io { sessionRepository.saveProgress(paused, listOf(currentUpdated)) }
        session = paused
        segments = segments.withUpdated(listOf(currentUpdated))
        publish(now)
        _events.tryEmit(SessionEvent.Paused)
    }

    private suspend fun resumeLocked() {
        val s = session ?: return
        if (s.status != SessionStatus.PAUSED) return
        val now = clock()

        val resumed = s.copy(status = SessionStatus.ACTIVE, lastResumedAt = now)
        io { sessionRepository.saveProgress(resumed, emptyList()) }
        session = resumed
        recovered = false
        publish(now)
        _events.tryEmit(SessionEvent.Resumed)
    }

    /**
     * Current segment ko COMPLETED ya SKIPPED karke agle par jaata hai.
     * Agla nahi hai to poora session COMPLETED.
     * Hamesha pehle save (suspend), phir state publish: publish ke baad koi suspension nahi.
     */
    private suspend fun advanceLocked(
        now: Long,
        outcome: SegmentStatus,
        auto: Boolean,
        markTaskDone: Boolean
    ) {
        val s = session ?: return
        if (s.status != SessionStatus.ACTIVE && s.status != SessionStatus.PAUSED) return
        val idx = s.currentSegmentIndex
        val current = segments.getOrNull(idx) ?: return
        val elapsed = TimerMath.elapsedMs(s.accumulatedActiveMs, s.lastResumedAt, now)

        val finishedCurrent = current.copy(actualMs = elapsed, status = outcome)

        // Adaptive rules: kya agle break par kuch badalna hai? (AdaptiveRules mein saaf niyam)
        val following = segments.getOrNull(idx + 1)
        val adjustment = AdaptiveRules.adjustAfter(finishedCurrent, outcome, following)

        val droppedBreak = if (adjustment.dropNextBreak && following != null) {
            following.copy(actualMs = 0L, status = SegmentStatus.SKIPPED)
        } else {
            null
        }
        val nextIndex = if (droppedBreak != null) idx + 2 else idx + 1
        val nextBase = if (droppedBreak != null) segments.getOrNull(nextIndex) else following
        val next = nextBase
            ?.let { base ->
                val newPlanned = adjustment.newBreakPlannedMs
                if (newPlanned != null) base.copy(plannedMs = newPlanned) else base
            }
            ?.copy(status = SegmentStatus.ACTIVE)
        val changedSegments = listOfNotNull(finishedCurrent, droppedBreak, next)

        val newSession = if (next != null) {
            s.copy(
                currentSegmentIndex = nextIndex,
                accumulatedActiveMs = 0L,
                // Paused tha to agla segment bhi paused rahe, chal raha tha to chalta rahe.
                lastResumedAt = if (s.status == SessionStatus.ACTIVE) now else null
            )
        } else {
            s.copy(
                status = SessionStatus.COMPLETED,
                endedAt = now,
                accumulatedActiveMs = 0L,
                lastResumedAt = null
            )
        }

        io { sessionRepository.saveProgress(newSession, changedSegments) }

        val taskId = current.taskId
        if (markTaskDone && current.type == SegmentType.TASK && taskId != null) {
            io { taskRepository.setCompleted(taskId, true) }
        }

        session = newSession
        segments = segments.withUpdated(changedSegments)
        publish(now)

        _events.tryEmit(
            if (outcome == SegmentStatus.SKIPPED) SessionEvent.SegmentSkipped(idx)
            else SessionEvent.SegmentFinished(idx, auto)
        )
        if (next != null) {
            val message = adjustment.message
            if (message != null) {
                // Ek hi snackbar: badlav ka karan aur agla segment, dono ek saath.
                _events.tryEmit(SessionEvent.ScheduleAdjusted("$message. Now: ${next.label}"))
            } else {
                _events.tryEmit(SessionEvent.SegmentStarted(nextIndex, next.label))
            }
        } else {
            _events.tryEmit(SessionEvent.SessionFinished(newSession.id))
            stopSessionJobs()
        }
    }

    /** Session beech mein roka gaya: current segment SKIPPED (jitna time laga wo saved), session ABORTED. */
    private suspend fun abortLocked(now: Long) {
        val s = session ?: return
        if (s.status != SessionStatus.ACTIVE && s.status != SessionStatus.PAUSED) return
        val current = segments.getOrNull(s.currentSegmentIndex)
        val elapsed = TimerMath.elapsedMs(s.accumulatedActiveMs, s.lastResumedAt, now)

        val changed = if (current != null && current.status == SegmentStatus.ACTIVE) {
            listOf(current.copy(actualMs = elapsed, status = SegmentStatus.SKIPPED))
        } else {
            emptyList()
        }
        val aborted = s.copy(
            status = SessionStatus.ABORTED,
            endedAt = now,
            accumulatedActiveMs = 0L,
            lastResumedAt = null
        )

        io { sessionRepository.saveProgress(aborted, changed) }
        session = aborted
        segments = segments.withUpdated(changed)
        publish(now)
        _events.tryEmit(SessionEvent.SessionAborted(aborted.id))
        stopSessionJobs()
    }

    // ------------------------------------------------------------------
    // Recovery aur safai
    // ------------------------------------------------------------------

    private suspend fun recoverLocked() {
        if (session != null) return
        val now = clock()
        val stored = io { sessionRepository.getActive() } ?: return
        val storedSegments = io { sessionRepository.getSegments(stored.id) }
        if (storedSegments.isEmpty()) {
            abortOrphanLocked(stored, now)
            return
        }

        val idx = stored.currentSegmentIndex.coerceIn(0, storedSegments.lastIndex)
        val current = storedSegments[idx]

        // ACTIVE tha: app band rehne ka time bhi judta hai, lekin segment ke planned time par cap.
        // PAUSED tha: jo jama tha wahi.
        val elapsed = if (stored.status == SessionStatus.ACTIVE) {
            TimerMath.elapsedMs(stored.accumulatedActiveMs, stored.lastResumedAt, now)
                .coerceAtMost(maxOf(current.plannedMs, stored.accumulatedActiveMs))
        } else {
            stored.accumulatedActiveMs
        }

        val restored = stored.copy(
            currentSegmentIndex = idx,
            status = SessionStatus.PAUSED,
            accumulatedActiveMs = elapsed,
            lastResumedAt = null
        )
        val restoredCurrent = current.copy(actualMs = elapsed, status = SegmentStatus.ACTIVE)

        io { sessionRepository.saveProgress(restored, listOf(restoredCurrent)) }
        session = restored
        segments = storedSegments.withUpdated(listOf(restoredCurrent))
        recovered = true
        publish(now)
        launchSessionJobs()
    }

    private suspend fun abortEverythingLocked(now: Long) {
        abortLocked(now)
        while (true) {
            val stale = io { sessionRepository.getActive() } ?: break
            abortOrphanLocked(stale, now)
        }
    }

    /**
     * Purana adhoora session jo memory mein load nahi hai. Uska time bharosemand nahi
     * (app band thi), isliye 0 maana jaata hai.
     */
    private suspend fun abortOrphanLocked(stale: SessionEntity, now: Long) {
        val storedSegments = io { sessionRepository.getSegments(stale.id) }
        val fixed = storedSegments
            .filter { it.status == SegmentStatus.ACTIVE }
            .map { it.copy(status = SegmentStatus.SKIPPED, actualMs = 0L) }
        val aborted = stale.copy(
            status = SessionStatus.ABORTED,
            endedAt = now,
            accumulatedActiveMs = 0L,
            lastResumedAt = null
        )
        io { sessionRepository.saveProgress(aborted, fixed) }
    }

    private suspend fun loadLocked(id: Long, isRecovered: Boolean, now: Long) {
        val loadedSession = io { sessionRepository.getSession(id) }
            ?: error("Session $id was not found in the database")
        session = loadedSession
        segments = io { sessionRepository.getSegments(id) }
        recovered = isRecovered
        overrunNotifiedIndex = -1
        publish(now)
    }

    // ------------------------------------------------------------------
    // State publish
    // ------------------------------------------------------------------

    private fun publish(now: Long) {
        val s = session
        if (s == null) {
            _state.value = EngineState()
            _timing.value = null
            return
        }
        val idx = s.currentSegmentIndex
        val current = segments.getOrNull(idx)

        _state.value = EngineState(
            sessionId = s.id,
            status = s.status,
            segments = segments,
            currentIndex = idx,
            segmentElapsedMs = TimerMath.elapsedMs(s.accumulatedActiveMs, s.lastResumedAt, now),
            recovered = recovered
        )
        _timing.value = if (s.status == SessionStatus.ACTIVE && current != null) {
            TimingKey(
                sessionId = s.id,
                index = idx,
                accumulatedActiveMs = s.accumulatedActiveMs,
                lastResumedAt = s.lastResumedAt,
                plannedMs = current.plannedMs
            )
        } else {
            null
        }
    }

    // ------------------------------------------------------------------
    // Session ke jobs: sessionJob -> ticker / transition / persist
    // ------------------------------------------------------------------

    private fun launchSessionJobs() {
        sessionJob?.cancel()
        sessionJob = scope.launch {
            // supervisorScope: ek child fail ho to baaki chalte rahein.
            supervisorScope {
                launch { guarded("ticker") { runTicker() } }
                launch { guarded("transition") { runTransitions() } }
                launch { guarded("persist") { runHeartbeat() } }
            }
        }
    }

    private fun stopSessionJobs() {
        sessionJob?.cancel()
        sessionJob = null
    }

    /**
     * supervisorScope ke child ka uncaught exception app crash kar deta hai,
     * isliye har child yahan se guard hota hai. CancellationException hamesha aage jaata hai.
     */
    private suspend fun guarded(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.tryEmit(SessionEvent.Error("$name: ${e.message ?: "unknown"}"))
        }
    }

    /** tickerJob: sirf UI refresh. Asli time yahan se nahi nikalta. */
    private suspend fun runTicker() {
        while (true) {
            delay(TICK_MS)
            mutex.withLock {
                val s = session
                if (s != null && s.status == SessionStatus.ACTIVE) {
                    val elapsed = TimerMath.elapsedMs(s.accumulatedActiveMs, s.lastResumedAt, clock())
                    _state.update { it.copy(segmentElapsedMs = elapsed) }
                }
            }
        }
    }

    /**
     * transitionJob: timing badalte hi (pause, resume, skip, next segment) purana
     * delay cancel hota hai (collectLatest) aur naye hisaab se naya delay shuru hota hai.
     */
    private suspend fun runTransitions() {
        _timing.collectLatest { key ->
            if (key == null) return@collectLatest
            val elapsed = TimerMath.elapsedMs(key.accumulatedActiveMs, key.lastResumedAt, clock())
            val wait = key.plannedMs - elapsed
            if (wait > 0L) delay(wait)
            // Time pura ho gaya: "save + state update" beech mein cancel nahi hona chahiye.
            withContext(NonCancellable) { onPlannedTimeReached(key) }
        }
    }

    private suspend fun onPlannedTimeReached(key: TimingKey) {
        mutex.withLock {
            val s = session ?: return
            // Intezaar ke dauran kuch badal gaya ho (skip/pause) to ye signal purana hai.
            if (s.id != key.sessionId ||
                s.currentSegmentIndex != key.index ||
                s.status != SessionStatus.ACTIVE
            ) return

            val now = clock()
            val planned = segments.getOrNull(key.index)?.plannedMs ?: return
            val elapsed = TimerMath.elapsedMs(s.accumulatedActiveMs, s.lastResumedAt, now)
            // delay() aur wall-clock mein chhota farq ho sakta hai.
            if (planned - elapsed > TOLERANCE_MS) return

            val autoAdvance = settingsStore.settings.first().autoAdvance
            if (autoAdvance) {
                advanceLocked(now, SegmentStatus.COMPLETED, auto = true, markTaskDone = false)
            } else if (overrunNotifiedIndex != key.index) {
                overrunNotifiedIndex = key.index
                _events.tryEmit(SessionEvent.PlannedTimeReached(key.index))
            }
        }
    }

    /**
     * persistJob: har 10 s mein current segment ka actualMs DB mein. Zaroori state
     * (pause/skip/done) commands khud transaction mein save karte hain. Ye sirf isliye hai
     * ki app kill hone par bhi History/analytics mein taaza time dikhe.
     */
    private suspend fun runHeartbeat() {
        while (true) {
            delay(HEARTBEAT_MS)
            mutex.withLock {
                val s = session
                if (s != null && s.status == SessionStatus.ACTIVE) {
                    val current = segments.getOrNull(s.currentSegmentIndex)
                    if (current != null) {
                        val elapsed = TimerMath.elapsedMs(s.accumulatedActiveMs, s.lastResumedAt, clock())
                        val updated = current.copy(actualMs = elapsed)
                        io { sessionRepository.updateSegment(updated) }
                        segments = segments.withUpdated(listOf(updated))
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Chhote helpers
    // ------------------------------------------------------------------

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun List<SegmentEntity>.withUpdated(updates: List<SegmentEntity>): List<SegmentEntity> {
        if (updates.isEmpty()) return this
        val byId = updates.associateBy { it.id }
        return map { byId[it.id] ?: it }
    }

    private companion object {
        const val TICK_MS = 250L
        const val HEARTBEAT_MS = 10_000L
        const val TOLERANCE_MS = 1_000L
    }
}
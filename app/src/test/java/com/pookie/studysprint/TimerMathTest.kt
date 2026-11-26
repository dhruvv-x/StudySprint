package com.pookie.studysprint.engine

import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SegmentType
import com.pookie.studysprint.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerMathTest {

    @Test
    fun elapsed_whileRunning_addsTimeSinceResume() {
        // 2 min pehle ka jama hua + 30 s se chal raha hai
        val elapsed = TimerMath.elapsedMs(accumulatedActiveMs = 120_000, lastResumedAt = 1_000, now = 31_000)
        assertEquals(150_000L, elapsed)
    }

    @Test
    fun elapsed_whilePaused_doesNotGrow() {
        val elapsed = TimerMath.elapsedMs(accumulatedActiveMs = 120_000, lastResumedAt = null, now = 999_999)
        assertEquals(120_000L, elapsed)
    }

    @Test
    fun elapsed_afterProcessDeath_countsRealTimePassed() {
        // App 10 min pehle kill hui thi: restore par poora beeta hua time judna chahiye
        val killedAt = 5_000L
        val restoredAt = killedAt + 10 * 60_000L
        val elapsed = TimerMath.elapsedMs(accumulatedActiveMs = 0, lastResumedAt = killedAt, now = restoredAt)
        assertEquals(600_000L, elapsed)
    }

    @Test
    fun elapsed_ifClockWentBackwards_neverGoesNegative() {
        val elapsed = TimerMath.elapsedMs(accumulatedActiveMs = 60_000, lastResumedAt = 50_000, now = 10_000)
        assertEquals(60_000L, elapsed)
    }

    @Test
    fun remaining_andOverrun() {
        assertEquals(30_000L, TimerMath.remainingMs(plannedMs = 60_000, elapsedMs = 30_000))
        assertEquals(0L, TimerMath.remainingMs(plannedMs = 60_000, elapsedMs = 60_000))
        assertEquals(-15_000L, TimerMath.remainingMs(plannedMs = 60_000, elapsedMs = 75_000))
    }

    @Test
    fun progress_isClampedBetweenZeroAndOne() {
        assertEquals(0.5f, TimerMath.progress(plannedMs = 60_000, elapsedMs = 30_000), 0.0001f)
        assertEquals(1f, TimerMath.progress(plannedMs = 60_000, elapsedMs = 90_000), 0.0001f)
        assertEquals(0f, TimerMath.progress(plannedMs = 0, elapsedMs = 5_000), 0.0001f)
    }

    @Test
    fun formatClock_countdown() {
        assertEquals("25:00", TimerMath.formatClock(25 * 60_000L))
        assertEquals("01:30", TimerMath.formatClock(90_000))
        assertEquals("01:00", TimerMath.formatClock(59_999))
        assertEquals("00:00", TimerMath.formatClock(0))
        assertEquals("1:01:01", TimerMath.formatClock(3_661_000))
    }

    @Test
    fun formatClock_overrunHasPlusSign() {
        assertEquals("+00:05", TimerMath.formatClock(-5_000))
        assertEquals("+02:15", TimerMath.formatClock(-135_000))
    }

    // ---- EngineState ke derived values ----

    private fun segment(index: Int, type: SegmentType, minutes: Int, status: SegmentStatus) = SegmentEntity(
        id = index + 1L,
        sessionId = 1,
        orderIndex = index,
        type = type,
        label = "S$index",
        plannedMs = minutes * 60_000L,
        status = status
    )

    private fun state(elapsedMs: Long) = EngineState(
        sessionId = 1,
        status = SessionStatus.ACTIVE,
        segments = listOf(
            segment(0, SegmentType.TASK, 30, SegmentStatus.ACTIVE),
            segment(1, SegmentType.BREAK, 5, SegmentStatus.PENDING),
            segment(2, SegmentType.TASK, 25, SegmentStatus.PENDING)
        ),
        currentIndex = 0,
        segmentElapsedMs = elapsedMs
    )

    @Test
    fun engineState_idleByDefault() {
        val idle = EngineState()
        assertTrue(idle.isIdle)
        assertFalse(idle.isRunning)
        assertFalse(idle.isOverrun)
    }

    @Test
    fun engineState_midSegment() {
        val s = state(elapsedMs = 10 * 60_000L)
        assertEquals(20 * 60_000L, s.remainingMs)
        assertFalse(s.isOverrun)
        assertEquals(2, s.upcoming.size)
    }

    @Test
    fun engineState_exactlyAtPlanned_isNotYetOverrun() {
        assertFalse(state(elapsedMs = 30 * 60_000L).isOverrun)
    }

    @Test
    fun engineState_pastPlanned_isOverrun() {
        assertTrue(state(elapsedMs = 31 * 60_000L).isOverrun)
    }
}
package com.pookie.studysprint.domain.scheduler

import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveRulesTest {

    private fun task(plannedMin: Long, actualMs: Long) = SegmentEntity(
        sessionId = 1, orderIndex = 0, type = SegmentType.TASK, label = "Task",
        plannedMs = plannedMin * 60_000L, actualMs = actualMs
    )

    private fun breakSegment(plannedMin: Long) = SegmentEntity(
        sessionId = 1, orderIndex = 1, type = SegmentType.BREAK, label = "Break",
        plannedMs = plannedMin * 60_000L
    )

    @Test
    fun overrun_shortensNextBreakByHalfTheOverrun() {
        // 10 min ka task 4 min zyada chala, break 10 min: 10 - 2 = 8 min
        val result = AdaptiveRules.adjustAfter(
            task(10, 14 * 60_000L), SegmentStatus.COMPLETED, breakSegment(10)
        )
        assertEquals(8 * 60_000L, result.newBreakPlannedMs)
        assertNotNull(result.message)
        assertFalse(result.dropNextBreak)
    }

    @Test
    fun overrun_breakNeverBelowHalfOfPlanned() {
        // Bahut zyada overrun: break 10 min se 5 min (aadhe) tak hi ghat sakta hai
        val result = AdaptiveRules.adjustAfter(
            task(10, 40 * 60_000L), SegmentStatus.COMPLETED, breakSegment(10)
        )
        assertEquals(5 * 60_000L, result.newBreakPlannedMs)
    }

    @Test
    fun overrun_shortBreakStaysAtOneMinuteFloor() {
        // 1 min ka break pehle se floor par hai, kuch nahi badlega
        val result = AdaptiveRules.adjustAfter(
            task(10, 20 * 60_000L), SegmentStatus.COMPLETED, breakSegment(1)
        )
        assertNull(result.newBreakPlannedMs)
        assertNull(result.message)
    }

    @Test
    fun overrun_withinToleranceDoesNothing() {
        val result = AdaptiveRules.adjustAfter(
            task(10, 10 * 60_000L + 3_000L), SegmentStatus.COMPLETED, breakSegment(5)
        )
        assertNull(result.newBreakPlannedMs)
    }

    @Test
    fun overrun_withoutNextBreakDoesNothing() {
        val result = AdaptiveRules.adjustAfter(
            task(10, 15 * 60_000L), SegmentStatus.COMPLETED, null
        )
        assertNull(result.newBreakPlannedMs)
        assertNull(result.message)
    }

    @Test
    fun quickSkip_dropsNextBreak() {
        val result = AdaptiveRules.adjustAfter(
            task(10, 20_000L), SegmentStatus.SKIPPED, breakSegment(5)
        )
        assertTrue(result.dropNextBreak)
    }

    @Test
    fun skipAfterRealFocus_keepsBreak() {
        val result = AdaptiveRules.adjustAfter(
            task(10, 3 * 60_000L), SegmentStatus.SKIPPED, breakSegment(5)
        )
        assertFalse(result.dropNextBreak)
    }

    @Test
    fun earlyFinish_givesNoticeOnly() {
        val result = AdaptiveRules.adjustAfter(
            task(10, 7 * 60_000L), SegmentStatus.COMPLETED, breakSegment(5)
        )
        assertNull(result.newBreakPlannedMs)
        assertFalse(result.dropNextBreak)
        assertTrue(result.message!!.startsWith("3 min"))
    }

    @Test
    fun breakSegments_neverAdjusted() {
        val result = AdaptiveRules.adjustAfter(
            breakSegment(5).copy(actualMs = 20 * 60_000L), SegmentStatus.COMPLETED, task(10, 0)
        )
        assertNull(result.message)
    }
}
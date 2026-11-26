package com.pookie.studysprint.domain.scheduler

import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SegmentType

/** What changes for the upcoming segment after a segment finishes. */
data class ScheduleAdjustment(
    /** true = drop the next BREAK (it will be marked SKIPPED). */
    val dropNextBreak: Boolean = false,
    /** null = keep the break's planned time. Otherwise the new planned time in ms. */
    val newBreakPlannedMs: Long? = null,
    /** Short message shown to the user. null = nothing changed. */
    val message: String? = null
)

/**
 * Transparent rule-based scheduling. This is not AI; it is three simple rules:
 *
 *  1. A task ran past its planned time (overrun): the next break is shortened by half
 *     of the overrun, but a break never goes below half its planned time or 1 minute.
 *  2. A task was skipped after less than 1 minute: the break that follows is pointless,
 *     so it is removed.
 *  3. A task finished early (by 1 minute or more): just tell the user the session will
 *     end sooner. (The remaining session time on the Active screen is recalculated
 *     from the current segments at every moment.)
 *
 * These are pure functions (no coroutines, no Android classes), so they are easy to unit test.
 */
object AdaptiveRules {

    const val OVERRUN_TOLERANCE_MS = 5_000L
    const val MIN_BREAK_MS = 60_000L
    const val QUICK_SKIP_MS = 60_000L
    const val EARLY_NOTICE_MS = 60_000L

    /**
     * [finished] = the segment that just ended (actualMs and plannedMs are both filled in).
     * [outcome] = COMPLETED or SKIPPED. [next] = the segment after it, if any.
     */
    fun adjustAfter(
        finished: SegmentEntity,
        outcome: SegmentStatus,
        next: SegmentEntity?
    ): ScheduleAdjustment {
        if (finished.type != SegmentType.TASK) return ScheduleAdjustment()

        val actual = finished.actualMs
        val planned = finished.plannedMs
        val nextIsBreak = next != null && next.type == SegmentType.BREAK

        // Rule 2: quick skip.
        if (outcome == SegmentStatus.SKIPPED) {
            return if (nextIsBreak && actual < QUICK_SKIP_MS) {
                ScheduleAdjustment(
                    dropNextBreak = true,
                    message = "Task skipped early, so the next break was removed"
                )
            } else {
                ScheduleAdjustment()
            }
        }
        if (outcome != SegmentStatus.COMPLETED) return ScheduleAdjustment()

        // Rule 1: overrun.
        val overrun = actual - planned
        if (overrun > OVERRUN_TOLERANCE_MS && nextIsBreak) {
            val breakPlanned = next!!.plannedMs
            val floor = maxOf(breakPlanned / 2, MIN_BREAK_MS)
            val reduced = maxOf(breakPlanned - overrun / 2, floor)
            if (reduced < breakPlanned) {
                return ScheduleAdjustment(
                    newBreakPlannedMs = reduced,
                    message = "Task ran over, so the break was shortened from " +
                            "${formatMs(breakPlanned)} to ${formatMs(reduced)}"
                )
            }
            return ScheduleAdjustment()
        }

        // Rule 3: finished early.
        val early = planned - actual
        if (early >= EARLY_NOTICE_MS) {
            return ScheduleAdjustment(
                message = "Finished ${early / 60_000L} min ahead of plan, so the session will end earlier"
            )
        }
        return ScheduleAdjustment()
    }

    fun formatMs(ms: Long): String {
        val minutes = ms / 60_000L
        val seconds = (ms % 60_000L) / 1_000L
        return if (seconds == 0L) "$minutes min" else "$minutes min $seconds s"
    }
}
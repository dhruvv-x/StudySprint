package com.pookie.studysprint.domain.scheduler

import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.data.local.TaskEntity
import com.pookie.studysprint.domain.model.SegmentType

/** Ek task aur uske liye is session mein chuna gaya duration. */
data class PlannedTask(
    val task: TaskEntity,
    val minutes: Int
)

/**
 * Session ka plan banane ke saaf-suthre rules. Koi AI nahi, sirf simple steps:
 *  1. Tasks usi order mein jaate hain jis order mein list diya gaya (priority order).
 *  2. Break sirf do tasks ke BEECH mein aata hai, kabhi pehle ya aakhir mein nahi.
 */
object SessionPlanner {

    const val MINUTE_MS = 60_000L

    fun totalFocusMinutes(tasks: List<PlannedTask>): Int = tasks.sumOf { it.minutes }

    fun breakCount(taskCount: Int, includeBreaks: Boolean): Int =
        if (includeBreaks && taskCount > 1) taskCount - 1 else 0

    /**
     * SegmentEntity ki list banata hai. sessionId yahan 0 hai; asli id
     * SessionRepository.createSession insert karte waqt laga deta hai.
     */
    fun buildSegments(
        tasks: List<PlannedTask>,
        includeBreaks: Boolean,
        breakMinutes: Int
    ): List<SegmentEntity> {
        val result = mutableListOf<SegmentEntity>()
        tasks.forEachIndexed { index, planned ->
            if (includeBreaks && index > 0) {
                result += SegmentEntity(
                    sessionId = 0,
                    orderIndex = result.size,
                    type = SegmentType.BREAK,
                    label = "Break",
                    plannedMs = breakMinutes * MINUTE_MS
                )
            }
            result += SegmentEntity(
                sessionId = 0,
                orderIndex = result.size,
                type = SegmentType.TASK,
                taskId = planned.task.id,
                subjectId = planned.task.subjectId,
                label = planned.task.title,
                plannedMs = planned.minutes * MINUTE_MS
            )
        }
        return result
    }
}
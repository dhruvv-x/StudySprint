package com.pookie.studysprint.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pookie.studysprint.domain.model.Priority
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SegmentType
import com.pookie.studysprint.domain.model.SessionStatus

/** Saare timestamps epoch milliseconds mein hain (System.currentTimeMillis()). */

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 0..5, ui/theme ke SubjectColors list ka index */
    val colorIndex: Int,
    val createdAt: Long
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val priority: Priority,
    val estimatedMinutes: Int,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long
)

/**
 * Ek study session. Timer ka source of truth yahi row hai (loop nahi).
 *
 * Current segment ka elapsed time:
 *   accumulatedActiveMs + (agar lastResumedAt != null) (now - lastResumedAt)
 * Segment badalne par accumulatedActiveMs 0 ho jaata hai aur uska actual time
 * SegmentEntity.actualMs mein chala jaata hai.
 * lastResumedAt == null  ->  session paused hai.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val status: SessionStatus,
    val startedAt: Long,
    val endedAt: Long? = null,
    val currentSegmentIndex: Int = 0,
    val accumulatedActiveMs: Long = 0,
    val lastResumedAt: Long? = null,
    val plannedTotalMs: Long
)

/**
 * Session ka ek hissa: task ya break.
 * taskId / subjectId par foreign key jaan-bujhkar nahi hai: task ya subject delete
 * ho jaaye to bhi purani history bachi rahe (label mein naam saved hai).
 */
@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val orderIndex: Int,
    val type: SegmentType,
    val taskId: Long? = null,
    val subjectId: Long? = null,
    val label: String,
    val plannedMs: Long,
    val actualMs: Long = 0,
    val status: SegmentStatus = SegmentStatus.PENDING
)
package com.pookie.studysprint.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

/** Subject list ke liye: subject + uske pending/completed tasks ki ginti. */
data class SubjectWithCounts(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val pendingCount: Int,
    val completedCount: Int
)

/** Home aur History ke liye: finished session ka chhota hissaab (segments jod kar). */
data class SessionBrief(
    val id: Long,
    val status: SessionStatus,
    val startedAt: Long,
    val plannedTotalMs: Long,
    val focusedMs: Long
)

/** Analytics ke liye: finished sessions ke ek TASK segment ki ek row. */
data class AnalyticsRow(
    val sessionId: Long,
    val startedAt: Long,
    val subjectId: Long?,
    val plannedMs: Long,
    val actualMs: Long,
    val status: SegmentStatus
)

/** Time suggestion ke liye: ek task ke COMPLETED segments ka average. runs = kitni baar poora hua. */
data class TaskAverage(
    val taskId: Long,
    val runs: Int,
    val avgActualMs: Long
)

@Dao
interface SubjectDao {

    @Query(
        """
        SELECT s.id AS id, s.name AS name, s.colorIndex AS colorIndex,
               COALESCE(SUM(CASE WHEN t.isCompleted = 0 THEN 1 ELSE 0 END), 0) AS pendingCount,
               COALESCE(SUM(CASE WHEN t.isCompleted = 1 THEN 1 ELSE 0 END), 0) AS completedCount
        FROM subjects s
        LEFT JOIN tasks t ON t.subjectId = s.id
        GROUP BY s.id
        ORDER BY s.createdAt ASC
        """
    )
    fun observeWithCounts(): Flow<List<SubjectWithCounts>>

    @Query("SELECT * FROM subjects ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: Long): SubjectEntity?

    @Insert
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Delete
    suspend fun delete(subject: SubjectEntity)
}

@Dao
interface TaskDao {

    // Priority ke hisaab se: HIGH pehle, phir MEDIUM, phir LOW.
    @Query(
        """
        SELECT * FROM tasks WHERE subjectId = :subjectId
        ORDER BY isCompleted ASC,
                 CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
                 createdAt ASC
        """
    )
    fun observeBySubject(subjectId: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks WHERE isCompleted = 0
        ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
                 createdAt ASC
        """
    )
    fun observePending(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long?)
}

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    /** History se delete: sirf khatam/roke gaye session. Chalta session kabhi nahi. Segments cascade se jaate hain. */
    @Query("DELETE FROM sessions WHERE id = :id AND status IN ('COMPLETED', 'ABORTED')")
    suspend fun deleteFinishedById(id: Long)

    /** Khatam ya roke gaye sessions ka brief, naye pehle. focusedMs = sirf TASK segments ka actual time. */
    @Query(
        """
        SELECT s.id AS id, s.status AS status, s.startedAt AS startedAt,
               s.plannedTotalMs AS plannedTotalMs,
               COALESCE(SUM(CASE WHEN seg.type = 'TASK' THEN seg.actualMs ELSE 0 END), 0) AS focusedMs
        FROM sessions s
        LEFT JOIN segments seg ON seg.sessionId = s.id
        WHERE s.status IN ('COMPLETED', 'ABORTED')
        GROUP BY s.id
        ORDER BY s.startedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentBriefs(limit: Int): Flow<List<SessionBrief>>

    /** `since` ke baad shuru hue finished sessions ke saare TASK segments (Analytics ka kaccha data). */
    @Query(
        """
        SELECT s.id AS sessionId, s.startedAt AS startedAt, seg.subjectId AS subjectId,
               seg.plannedMs AS plannedMs, seg.actualMs AS actualMs, seg.status AS status
        FROM segments seg
        INNER JOIN sessions s ON s.id = seg.sessionId
        WHERE seg.type = 'TASK' AND s.status IN ('COMPLETED', 'ABORTED') AND s.startedAt >= :since
        ORDER BY s.startedAt ASC
        """
    )
    fun observeAnalyticsRows(since: Long): Flow<List<AnalyticsRow>>

    /** `since` ke baad shuru hue sessions ka total focused time (TASK segments). */
    @Query(
        """
        SELECT COALESCE(SUM(seg.actualMs), 0)
        FROM segments seg
        INNER JOIN sessions s ON s.id = seg.sessionId
        WHERE seg.type = 'TASK' AND s.startedAt >= :since
        """
    )
    fun observeFocusedSince(since: Long): Flow<Long>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    /** App restart par recovery ke liye: abhi chalta/paused session (agar koi ho). */
    @Query(
        """
        SELECT * FROM sessions
        WHERE status IN ('ACTIVE', 'PAUSED')
        ORDER BY startedAt DESC LIMIT 1
        """
    )
    suspend fun getActive(): SessionEntity?

    @Query(
        """
        SELECT * FROM sessions
        WHERE status IN ('ACTIVE', 'PAUSED')
        ORDER BY startedAt DESC LIMIT 1
        """
    )
    fun observeActive(): Flow<SessionEntity?>

    /** History screen: khatam ya beech mein roke gaye sessions, naye pehle. */
    @Query(
        """
        SELECT * FROM sessions
        WHERE status IN ('COMPLETED', 'ABORTED')
        ORDER BY startedAt DESC
        """
    )
    fun observeFinished(): Flow<List<SessionEntity>>

    @Delete
    suspend fun delete(session: SessionEntity)
}

@Dao
interface SegmentDao {

    @Insert
    suspend fun insertAll(segments: List<SegmentEntity>): List<Long>

    @Update
    suspend fun update(segment: SegmentEntity)

    @Update
    suspend fun updateAll(segments: List<SegmentEntity>)

    @Query("SELECT * FROM segments WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getBySession(sessionId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    fun observeBySession(sessionId: Long): Flow<List<SegmentEntity>>

    /**
     * Har taskId ke COMPLETED TASK segments ka average actualMs.
     * Sirf wahi task aate hain jo kam se kam `minRuns` baar poore hue (HAVING).
     * Task delete ho jaaye to uski purani rows bhi rehti hain, par wo pending list mein nahi aata.
     */
    @Query(
        """
        SELECT taskId AS taskId, COUNT(*) AS runs, CAST(AVG(actualMs) AS INTEGER) AS avgActualMs
        FROM segments
        WHERE type = 'TASK' AND status = 'COMPLETED' AND taskId IS NOT NULL
        GROUP BY taskId
        HAVING COUNT(*) >= :minRuns
        """
    )
    fun observeTaskAverages(minRuns: Int): Flow<List<TaskAverage>>
}
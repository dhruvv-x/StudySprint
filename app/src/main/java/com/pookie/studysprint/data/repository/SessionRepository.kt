package com.pookie.studysprint.data.repository

import androidx.room.withTransaction
import com.pookie.studysprint.data.local.SegmentDao
import com.pookie.studysprint.data.local.AnalyticsRow
import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.data.local.SessionBrief
import com.pookie.studysprint.data.local.SessionDao
import com.pookie.studysprint.data.local.SessionEntity
import com.pookie.studysprint.data.local.StudyDatabase
import com.pookie.studysprint.data.local.TaskAverage
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SessionStatus
import com.pookie.studysprint.domain.scheduler.TimeSuggestionRules
import kotlinx.coroutines.flow.Flow

class SessionRepository(
    private val database: StudyDatabase,
    private val sessionDao: SessionDao,
    private val segmentDao: SegmentDao
) {

    fun observeActive(): Flow<SessionEntity?> = sessionDao.observeActive()

    fun observeFinished(): Flow<List<SessionEntity>> = sessionDao.observeFinished()

    fun observeRecentBriefs(limit: Int): Flow<List<SessionBrief>> =
        sessionDao.observeRecentBriefs(limit)

    fun observeAnalyticsRows(since: Long): Flow<List<AnalyticsRow>> =
        sessionDao.observeAnalyticsRows(since)

    fun observeFocusedSince(since: Long): Flow<Long> = sessionDao.observeFocusedSince(since)

    fun observeSegments(sessionId: Long): Flow<List<SegmentEntity>> =
        segmentDao.observeBySession(sessionId)

    /** Time suggestion ka kaccha data: har task ka average, kam se kam MIN_RUNS baar poora hua ho. */
    fun observeTaskAverages(): Flow<List<TaskAverage>> =
        segmentDao.observeTaskAverages(TimeSuggestionRules.MIN_RUNS)

    suspend fun getActive(): SessionEntity? = sessionDao.getActive()

    suspend fun getSession(id: Long): SessionEntity? = sessionDao.getById(id)

    suspend fun getSegments(sessionId: Long): List<SegmentEntity> =
        segmentDao.getBySession(sessionId)

    /**
     * Session + uske saare segments ek hi transaction mein save hote hain.
     * Beech mein kuch fail ho to dono rollback ho jaate hain, aadha session nahi bachta.
     * Pehla segment ACTIVE ho jaata hai. Session ka id return hota hai.
     */
    suspend fun createSession(segments: List<SegmentEntity>, now: Long): Long =
        database.withTransaction {
            val plannedTotal = segments.sumOf { it.plannedMs }
            val sessionId = sessionDao.insert(
                SessionEntity(
                    status = SessionStatus.ACTIVE,
                    startedAt = now,
                    currentSegmentIndex = 0,
                    accumulatedActiveMs = 0,
                    lastResumedAt = now,
                    plannedTotalMs = plannedTotal
                )
            )
            val prepared = segments.mapIndexed { index, segment ->
                segment.copy(
                    id = 0,
                    sessionId = sessionId,
                    orderIndex = index,
                    actualMs = 0,
                    status = if (index == 0) SegmentStatus.ACTIVE else SegmentStatus.PENDING
                )
            }
            segmentDao.insertAll(prepared)
            sessionId
        }

    /**
     * Engine ka har state-change yahin se save hota hai: session row + badle hue segments
     * ek transaction mein. Process kill beech mein ho to ya sab save hoga ya kuch nahi.
     */
    suspend fun saveProgress(session: SessionEntity, changedSegments: List<SegmentEntity>) {
        database.withTransaction {
            sessionDao.update(session)
            if (changedSegments.isNotEmpty()) {
                segmentDao.updateAll(changedSegments)
            }
        }
    }

    suspend fun updateSession(session: SessionEntity) = sessionDao.update(session)

    suspend fun updateSegment(segment: SegmentEntity) = segmentDao.update(segment)

    suspend fun updateSegments(segments: List<SegmentEntity>) = segmentDao.updateAll(segments)

    suspend fun deleteSession(session: SessionEntity) = sessionDao.delete(session)

    suspend fun deleteFinishedById(id: Long) = sessionDao.deleteFinishedById(id)
}
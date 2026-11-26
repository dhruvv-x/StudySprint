package com.pookie.studysprint.engine

import com.pookie.studysprint.data.local.SegmentEntity
import com.pookie.studysprint.domain.model.SegmentStatus
import com.pookie.studysprint.domain.model.SessionStatus

/**
 * Engine ka live state. UI sirf isko dekhti hai (StateFlow ke through).
 * sessionId == null  ->  koi session chal nahi raha.
 * Session khatam hone par bhi (COMPLETED / ABORTED) state tab tak rehti hai
 * jab tak dismissFinished() na bulaya jaaye, taaki UI Summary par ja sake.
 */
data class EngineState(
    val sessionId: Long? = null,
    val status: SessionStatus? = null,
    val segments: List<SegmentEntity> = emptyList(),
    val currentIndex: Int = 0,
    /** Current segment ka ab tak ka active time. Ticker isko refresh karta hai. */
    val segmentElapsedMs: Long = 0L,
    /** true = app restart ke baad session wapas mila hai (PAUSED), user ko Resume/End poochna hai. */
    val recovered: Boolean = false
) {
    val isIdle: Boolean get() = sessionId == null
    val isRunning: Boolean get() = status == SessionStatus.ACTIVE
    val isPaused: Boolean get() = status == SessionStatus.PAUSED

    val current: SegmentEntity? get() = segments.getOrNull(currentIndex)

    val remainingMs: Long
        get() = TimerMath.remainingMs(current?.plannedMs ?: 0L, segmentElapsedMs)

    /** Planned time se aage nikal gaye (exactly 0 par abhi overrun nahi). */
    val isOverrun: Boolean get() = current != null && remainingMs < 0L

    val progress: Float
        get() = TimerMath.progress(current?.plannedMs ?: 0L, segmentElapsedMs)

    val completedCount: Int get() = segments.count { it.status == SegmentStatus.COMPLETED }
    val skippedCount: Int get() = segments.count { it.status == SegmentStatus.SKIPPED }

    /**
     * Poore session mein abhi kitna time bacha: current ka bacha hua (overrun mein 0)
     * plus agle saare segments ka planned time. Skip, jaldi khatam ya break kam hone par
     * ye khud badal jaata hai, kyunki hamesha taaza segments se nikalta hai.
     */
    val remainingSessionMs: Long
        get() = if (current == null) 0L
        else remainingMs.coerceAtLeast(0L) + upcoming.sumOf { it.plannedMs }

    /** Current ke baad ke segments (agle kya aa raha hai dikhane ke liye). */
    val upcoming: List<SegmentEntity>
        get() = if (currentIndex + 1 < segments.size) segments.subList(currentIndex + 1, segments.size)
        else emptyList()
}

/**
 * Ek-baar ke signals (SharedFlow se jaate hain): snackbar, haptic, navigation jaise kaam.
 * StateFlow mein ye rotation par dobara chal jaate, isliye alag rakhe hain.
 */
sealed interface SessionEvent {
    data class SegmentStarted(val index: Int, val label: String) : SessionEvent
    data class SegmentFinished(val index: Int, val auto: Boolean) : SessionEvent
    data class SegmentSkipped(val index: Int) : SessionEvent
    /** Auto-advance OFF hai aur planned time pura ho gaya: ab overrun shuru. */
    data class PlannedTimeReached(val index: Int) : SessionEvent
    /** Adaptive rule ne schedule badla (break chhota, break hata, ya jaldi khatam). */
    data class ScheduleAdjusted(val message: String) : SessionEvent
    data object Paused : SessionEvent
    data object Resumed : SessionEvent
    data class SessionFinished(val sessionId: Long) : SessionEvent
    data class SessionAborted(val sessionId: Long) : SessionEvent
    data class Error(val message: String) : SessionEvent
}
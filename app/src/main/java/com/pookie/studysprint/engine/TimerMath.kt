package com.pookie.studysprint.engine

/**
 * Timer ka saara hisaab yahin hai. Koi coroutine ya Android cheez nahi, isliye test aasaan hai.
 *
 * Asli formula (loop se count nahi hota):
 *   elapsed = accumulatedActiveMs + (agar chal raha hai) (now - lastResumedAt)
 * lastResumedAt == null  ->  session paused hai, elapsed badhta nahi.
 */
object TimerMath {

    fun elapsedMs(accumulatedActiveMs: Long, lastResumedAt: Long?, now: Long): Long {
        val running = if (lastResumedAt != null) (now - lastResumedAt).coerceAtLeast(0L) else 0L
        return accumulatedActiveMs + running
    }

    /** Negative = planned time se zyada ho chuka (overrun). */
    fun remainingMs(plannedMs: Long, elapsedMs: Long): Long = plannedMs - elapsedMs

    /** 0f..1f. Overrun mein bhi 1f par ruk jaata hai. */
    fun progress(plannedMs: Long, elapsedMs: Long): Float {
        if (plannedMs <= 0L) return 0f
        return (elapsedMs.toFloat() / plannedMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Clock text. Bacha hua time upar ki taraf round hota hai (59.9 s -> "01:00"),
     * taaki "00:00" sirf tab dikhe jab time sach mein khatam ho.
     * Negative (overrun) ho to aage "+" lagta hai, jaise "+02:15".
     */
    fun formatClock(ms: Long): String {
        val overrun = ms < 0L
        val totalSeconds = if (overrun) (-ms) / 1000L else (ms + 999L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val body = if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
        return if (overrun) "+$body" else body
    }
}
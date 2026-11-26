package com.pookie.studysprint.domain.scheduler

import com.pookie.studysprint.data.local.TaskAverage
import kotlin.math.roundToInt

/** Suggestion: itne minute rakho, ye `runs` pichhli baar ke average se nikla hai. */
data class TimeSuggestion(
    val minutes: Int,
    val runs: Int
)

/**
 * History se time suggestion ka saaf niyam. Koi AI nahi:
 *  1. Task kam se kam MIN_RUNS baar COMPLETED hua ho, warna suggestion nahi.
 *  2. Suggested minutes = average actual time, nearest minute tak round.
 *  3. Result MIN_MINUTES..MAX_MINUTES ke beech rakha jaata hai (Create Session ke stepper jaisa).
 */
object TimeSuggestionRules {

    const val MIN_RUNS = 2
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 480

    private const val MINUTE_MS = 60_000.0

    fun suggest(average: TaskAverage?): TimeSuggestion? {
        if (average == null || average.runs < MIN_RUNS) return null
        val minutes = (average.avgActualMs / MINUTE_MS)
            .roundToInt()
            .coerceIn(MIN_MINUTES, MAX_MINUTES)
        return TimeSuggestion(minutes = minutes, runs = average.runs)
    }
}
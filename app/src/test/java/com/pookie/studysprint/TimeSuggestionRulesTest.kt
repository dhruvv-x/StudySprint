package com.pookie.studysprint.domain.scheduler

import com.pookie.studysprint.data.local.TaskAverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimeSuggestionRulesTest {

    private fun avg(runs: Int, avgMs: Long) =
        TaskAverage(taskId = 1L, runs = runs, avgActualMs = avgMs)

    @Test
    fun noHistory_givesNoSuggestion() {
        assertNull(TimeSuggestionRules.suggest(null))
    }

    @Test
    fun singleRun_givesNoSuggestion() {
        assertNull(TimeSuggestionRules.suggest(avg(runs = 1, avgMs = 25 * 60_000L)))
    }

    @Test
    fun twoRuns_usesAverageMinutes() {
        val s = TimeSuggestionRules.suggest(avg(runs = 2, avgMs = 25 * 60_000L))
        assertNotNull(s)
        assertEquals(25, s!!.minutes)
        assertEquals(2, s.runs)
    }

    @Test
    fun average_roundsToNearestMinute() {
        // 25.6 min -> 26
        assertEquals(26, TimeSuggestionRules.suggest(avg(3, 1_536_000L))!!.minutes)
        // 25.4 min -> 25
        assertEquals(25, TimeSuggestionRules.suggest(avg(3, 1_524_000L))!!.minutes)
    }

    @Test
    fun tinyAverage_isRaisedToOneMinute() {
        assertEquals(1, TimeSuggestionRules.suggest(avg(2, 10_000L))!!.minutes)
    }

    @Test
    fun hugeAverage_isCappedAtMax() {
        val s = TimeSuggestionRules.suggest(avg(2, 900 * 60_000L))
        assertEquals(TimeSuggestionRules.MAX_MINUTES, s!!.minutes)
    }
}
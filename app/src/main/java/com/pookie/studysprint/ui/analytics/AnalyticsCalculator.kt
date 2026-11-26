package com.pookie.studysprint.ui.analytics

import com.pookie.studysprint.data.local.AnalyticsRow
import com.pookie.studysprint.data.local.SubjectEntity
import com.pookie.studysprint.domain.model.SegmentStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayBar(
    val dayStart: Long,
    val label: String,
    val focusedMs: Long
)

data class SubjectShare(
    val name: String,
    /** -1 = subject delete ho chuka hai (screen grey dikhata hai). */
    val colorIndex: Int,
    val focusedMs: Long,
    val fraction: Float
)

data class PaceTotals(
    val plannedMs: Long,
    val actualMs: Long,
    val completedTasks: Int,
    val skippedTasks: Int
)

/**
 * Analytics ka saara hisaab. Koi Android ya coroutine cheez nahi: pure functions,
 * isliye ViewModel inhe Dispatchers.Default par alag-alag async mein chala sakta hai.
 */
object AnalyticsCalculator {

    fun dayStart(epochMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = epochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Aaj ko milakar pichhle `days` din ki shuruaat. */
    fun windowStart(days: Int, now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = dayStart(now)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis

    /** Har din ka focused time (jis din session shuru hua us din ginte hain). */
    fun dailyBars(rows: List<AnalyticsRow>, days: Int, now: Long): List<DayBar> {
        val totals = rows
            .groupBy { dayStart(it.startedAt) }
            .mapValues { (_, list) -> list.sumOf { it.actualMs } }

        val weekdayFormat = SimpleDateFormat("EEEEE", Locale.getDefault())
        val dayOfMonthFormat = SimpleDateFormat("d", Locale.getDefault())
        val cal = Calendar.getInstance().apply { timeInMillis = windowStart(days, now) }

        return List(days) { index ->
            val day = cal.timeInMillis
            val label = when {
                days <= 7 -> weekdayFormat.format(Date(day))
                index % 5 == 0 -> dayOfMonthFormat.format(Date(day))
                else -> ""
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
            DayBar(dayStart = day, label = label, focusedMs = totals[day] ?: 0L)
        }
    }

    /** Subject-wise focused time, zyada se kam. Fraction sabse bade subject ke hisaab se hai. */
    fun subjectShares(rows: List<AnalyticsRow>, subjects: List<SubjectEntity>): List<SubjectShare> {
        val byId = subjects.associateBy { it.id }
        val totals = rows
            .filter { it.subjectId != null && it.actualMs > 0L }
            .groupBy { it.subjectId!! }
            .mapValues { (_, list) -> list.sumOf { it.actualMs } }
        val max = totals.values.maxOrNull() ?: return emptyList()

        return totals.entries
            .sortedByDescending { it.value }
            .map { (subjectId, focusedMs) ->
                val subject = byId[subjectId]
                SubjectShare(
                    name = subject?.name ?: "Deleted subject",
                    colorIndex = subject?.colorIndex ?: -1,
                    focusedMs = focusedMs,
                    fraction = focusedMs.toFloat() / max.toFloat()
                )
            }
    }

    /** Planned vs actual sirf COMPLETED tasks par (skipped adhoore hote hain, unka muqabla seedha nahi). */
    fun pace(rows: List<AnalyticsRow>): PaceTotals {
        val completed = rows.filter { it.status == SegmentStatus.COMPLETED }
        return PaceTotals(
            plannedMs = completed.sumOf { it.plannedMs },
            actualMs = completed.sumOf { it.actualMs },
            completedTasks = completed.size,
            skippedTasks = rows.count { it.status == SegmentStatus.SKIPPED }
        )
    }
}
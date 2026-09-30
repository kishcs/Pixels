package com.erohsik.pixels.domain

import com.erohsik.pixels.data.model.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class InsightsTest {

    private val t = InsightTemplates(
        dayOfWeek = "dow|%1\$s|%2\$s|%3\$s|%4\$s",
        month = "month|%1\$s|%2\$s|%3\$s|%4\$s",
        trendUp = "up|%1\$s|%2\$s",
        trendDown = "down|%1\$s|%2\$s",
        longestRun = "run|%1\$s|%2\$d|%3\$s",
        completeness = "done|%1\$d|%2\$d|%3\$d",
        locale = Locale.UK,
    )

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun e(dayIndex: Long, level: Int) = Entry(1, dayIndex, level, null, 0)

    /** Consecutive days from [start], one entry per level in [levels]. */
    private fun series(start: Long, levels: List<Int>) = levels.mapIndexed { i, l -> e(start + i, l) }

    // --- Rule 1: day of week -------------------------------------------------------------

    @Test
    fun `day of week reports best and worst when gap exceeds 0_4`() {
        val start = day(2026, 1, 5) // a Monday
        val entries = (0 until 70).map { i ->
            val dow = LocalDate.ofEpochDay(start + i).dayOfWeek
            e(start + i, when (dow) { DayOfWeek.SATURDAY -> 5; DayOfWeek.MONDAY -> 1; else -> 3 })
        }
        assertEquals("dow|Saturday|5.0|Monday|1.0", Insights.dayOfWeek(entries, t))
    }

    @Test
    fun `day of week is silent when gap is at most 0_4`() {
        val start = day(2026, 1, 5)
        val entries = (0 until 70).map { i ->
            val dow = LocalDate.ofEpochDay(start + i).dayOfWeek
            e(start + i, if (dow == DayOfWeek.SATURDAY && i < 28) 4 else 3) // Saturdays avg 3.4
        }
        assertNull(Insights.dayOfWeek(entries, t))
    }

    @Test
    fun `day of week ignores weekdays with too few samples`() {
        val start = day(2026, 1, 5)
        // Only three Saturdays, all 5: not enough to compare.
        val entries = listOf(e(start + 5, 5), e(start + 12, 5), e(start + 19, 5)) +
            (0 until 28).map { e(start + it * 7L, 3) } + (0 until 28).map { e(start + 1 + it * 7L, 3) }
        assertNull(Insights.dayOfWeek(entries, t))
    }

    // --- Rule 2: month -------------------------------------------------------------------

    @Test
    fun `month compares only months with at least 15 entries`() {
        val jan = series(day(2026, 1, 1), List(20) { 4 })
        val feb = series(day(2026, 2, 1), List(15) { 2 })
        val mar = series(day(2026, 3, 1), List(14) { 1 }) // excluded
        assertEquals("month|January 2026|4.0|February 2026|2.0", Insights.bestWorstMonth(jan + feb + mar, t))
    }

    @Test
    fun `month is silent with fewer than two qualifying months`() {
        val jan = series(day(2026, 1, 1), List(20) { 4 })
        val feb = series(day(2026, 2, 1), List(14) { 2 })
        assertNull(Insights.bestWorstMonth(jan + feb, t))
    }

    @Test
    fun `month is silent when means are equal`() {
        val jan = series(day(2026, 1, 1), List(20) { 3 })
        val feb = series(day(2026, 2, 1), List(20) { 3 })
        assertNull(Insights.bestWorstMonth(jan + feb, t))
    }

    // --- Rule 3: 30-day trend ------------------------------------------------------------

    @Test
    fun `trend reports up and down beyond 0_3`() {
        val today = day(2026, 6, 30)
        val previous = series(today - 59, List(30) { 3 })
        val upRecent = series(today - 29, List(30) { 4 })
        assertEquals("up|4.0|3.0", Insights.recentTrend(previous + upRecent, today, t))
        val downRecent = series(today - 29, List(30) { 2 })
        assertEquals("down|2.0|3.0", Insights.recentTrend(previous + downRecent, today, t))
    }

    @Test
    fun `trend is silent at or below 0_3 and with sparse windows`() {
        val today = day(2026, 6, 30)
        val previous = series(today - 59, List(30) { 3 })
        val similar = series(today - 29, List(8) { 4 } + List(22) { 3 }) // 3.27 vs 3.0
        assertNull(Insights.recentTrend(previous + similar, today, t))
        val sparse = series(today - 29, List(9) { 5 })
        assertNull(Insights.recentTrend(previous + sparse, today, t))
    }

    // --- Rule 4: longest run of level >= 4 ---------------------------------------------

    @Test
    fun `longest run is phrased with the tracker label and start month`() {
        val today = day(2026, 9, 30)
        val march = series(day(2026, 3, 10), List(9) { if (it % 2 == 0) 4 else 5 })
        val may = series(day(2026, 5, 1), listOf(4, 4, 4, 2, 5, 5))
        assertEquals("run|good|9|March", Insights.longestGoodRun(march + may, today, "Good", t))
    }

    @Test
    fun `longest run names the year when not the current year`() {
        val today = day(2026, 9, 30)
        val run = series(day(2025, 11, 28), List(5) { 4 })
        assertEquals("run|good|5|November 2025", Insights.longestGoodRun(run, today, "Good", t))
    }

    @Test
    fun `a missing day breaks a run and ties go to the most recent`() {
        val today = day(2026, 9, 30)
        val a = series(day(2026, 1, 1), List(3) { 5 })
        val b = series(day(2026, 1, 5), List(3) { 5 }) // Jan 4 missing
        val c = series(day(2026, 4, 1), List(3) { 4 })
        assertEquals("run|good|3|April", Insights.longestGoodRun(a + b + c, today, "Good", t))
    }

    @Test
    fun `longest run is silent without two consecutive good days`() {
        val today = day(2026, 9, 30)
        val entries = listOf(e(day(2026, 1, 1), 4), e(day(2026, 1, 2), 3), e(day(2026, 1, 3), 5))
        assertNull(Insights.longestGoodRun(entries, today, "Good", t))
    }

    // --- Rule 5: completeness ------------------------------------------------------------

    @Test
    fun `completeness counts this year's logged days`() {
        val today = day(2026, 3, 14) // day 73
        val entries = series(day(2026, 1, 1), List(20) { 3 }) + series(day(2025, 12, 1), List(5) { 3 })
        assertEquals("done|20|73|2026", Insights.yearCompleteness(entries, 2026, today, t))
        assertNull(Insights.yearCompleteness(entries, 2027, today, t))
        assertEquals("done|5|365|2025", Insights.yearCompleteness(entries, 2025, today, t))
    }

    // --- Aggregation ---------------------------------------------------------------------

    @Test
    fun `nothing below 60 entries`() {
        val today = day(2026, 6, 30)
        val entries = series(today - 58, List(59) { if (it % 2 == 0) 5 else 1 })
        assertTrue(Insights.all(entries, 2026, today, "Good", t).isEmpty())
    }

    @Test
    fun `all returns met rules in order`() {
        val today = day(2026, 6, 30)
        val entries = series(today - 59, List(30) { 2 } + List(30) { 4 })
        val all = Insights.all(entries, 2026, today, "Good", t)
        assertTrue(all.first { it.startsWith("up|") } == "up|4.0|2.0")
        assertTrue(all.any { it.startsWith("run|good|30|") })
        assertEquals("done|60|181|2026", all.last())
    }

    @Test
    fun `rotation shows at most two and advances daily`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "b"), Insights.pickRotating(list, 0))
        assertEquals(listOf("b", "c"), Insights.pickRotating(list, 1))
        assertEquals(listOf("d", "a"), Insights.pickRotating(list, 3))
        assertEquals(listOf("d", "a"), Insights.pickRotating(list, -1))
        assertEquals(listOf("x"), Insights.pickRotating(listOf("x"), 5))
    }
}

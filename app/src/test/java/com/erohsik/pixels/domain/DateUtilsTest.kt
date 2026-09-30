package com.erohsik.pixels.domain

import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class DateUtilsTest {

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun entry(y: Int, m: Int, d: Int, level: Int) = Entry(1, day(y, m, d), level, null, 0)

    @Test
    fun `leap day exists only in leap years`() {
        assertTrue(DateUtils.isValidDay(2024, 2, 29))
        assertFalse(DateUtils.isValidDay(2025, 2, 29))
        assertFalse(DateUtils.isValidDay(2100, 2, 29))
        assertTrue(DateUtils.isValidDay(2000, 2, 29))
        assertFalse(DateUtils.isValidDay(2024, 2, 30))
        assertFalse(DateUtils.isValidDay(2024, 4, 31))
        assertFalse(DateUtils.isValidDay(2024, 13, 1))
    }

    @Test
    fun `grid index is column-major by month`() {
        assertEquals(0, DateUtils.gridIndex(1, 1))
        assertEquals(30, DateUtils.gridIndex(1, 31))
        assertEquals(31, DateUtils.gridIndex(2, 1))
        assertEquals(371, DateUtils.gridIndex(12, 31))
    }

    @Test
    fun `grid hides non-existent dates and shows leap day in leap years`() {
        val endOf2024 = day(2024, 12, 31)
        val leap = DateUtils.buildLevelGrid(2024, emptyList(), endOf2024)
        assertEquals(Level.EMPTY, leap[DateUtils.gridIndex(2, 29)])
        assertEquals(DateUtils.CELL_HIDDEN, leap[DateUtils.gridIndex(2, 30)])
        assertEquals(DateUtils.CELL_HIDDEN, leap[DateUtils.gridIndex(4, 31)])
        assertEquals(366, leap.count { it >= 0 })

        val common = DateUtils.buildLevelGrid(2025, emptyList(), day(2025, 12, 31))
        assertEquals(DateUtils.CELL_HIDDEN, common[DateUtils.gridIndex(2, 29)])
        assertEquals(365, common.count { it >= 0 })
    }

    @Test
    fun `grid hides future days`() {
        val today = day(2026, 3, 15)
        val grid = DateUtils.buildLevelGrid(2026, listOf(entry(2026, 3, 16, 4)), today)
        assertEquals(Level.EMPTY, grid[DateUtils.gridIndex(3, 15)])
        assertEquals(DateUtils.CELL_HIDDEN, grid[DateUtils.gridIndex(3, 16)])
        assertEquals(DateUtils.CELL_HIDDEN, grid[DateUtils.gridIndex(12, 1)])
        assertEquals(31 + 28 + 15, grid.count { it >= 0 })
    }

    @Test
    fun `grid of a future year is entirely hidden`() {
        val grid = DateUtils.buildLevelGrid(2027, emptyList(), day(2026, 6, 1))
        assertTrue(grid.all { it == DateUtils.CELL_HIDDEN })
    }

    @Test
    fun `grid places entries and ignores other years`() {
        val entries = listOf(entry(2026, 1, 1, 5), entry(2026, 2, 28, 2), entry(2025, 12, 31, 3), entry(2027, 1, 1, 1))
        val grid = DateUtils.buildLevelGrid(2026, entries, day(2026, 12, 31))
        assertEquals(5, grid[DateUtils.gridIndex(1, 1)])
        assertEquals(2, grid[DateUtils.gridIndex(2, 28)])
        assertEquals(2, grid.count { it > 0 })
    }

    @Test
    fun `grid handles zero, one and 366 entries`() {
        val endOf2024 = day(2024, 12, 31)
        assertEquals(0, DateUtils.buildLevelGrid(2024, emptyList(), endOf2024).count { it > 0 })
        assertEquals(1, DateUtils.buildLevelGrid(2024, listOf(entry(2024, 7, 4, 3)), endOf2024).count { it > 0 })
        val full = (0 until 366).map { i -> Entry(1, day(2024, 1, 1) + i, i % 5 + 1, null, 0) }
        val grid = DateUtils.buildLevelGrid(2024, full, endOf2024)
        assertEquals(366, grid.count { it > 0 })
        assertEquals(DateUtils.GRID_SIZE - 366, grid.count { it == DateUtils.CELL_HIDDEN })
    }

    @Test
    fun `days elapsed counts up to and including today`() {
        assertEquals(1, DateUtils.daysElapsedInYear(2026, day(2026, 1, 1)))
        assertEquals(60, DateUtils.daysElapsedInYear(2024, day(2024, 2, 29)))
        assertEquals(365, DateUtils.daysElapsedInYear(2025, day(2026, 3, 1)))
        assertEquals(366, DateUtils.daysElapsedInYear(2024, day(2026, 3, 1)))
        assertEquals(0, DateUtils.daysElapsedInYear(2027, day(2026, 3, 1)))
    }

    @Test
    fun `today grid index only within the year`() {
        assertEquals(DateUtils.gridIndex(10, 14), DateUtils.todayGridIndex(2025, day(2025, 10, 14)))
        assertEquals(-1, DateUtils.todayGridIndex(2024, day(2025, 10, 14)))
    }

    @Test
    fun `count in year and month range`() {
        val entries = listOf(entry(2026, 1, 1, 1), entry(2026, 12, 31, 1), entry(2025, 12, 31, 1))
        assertEquals(2, DateUtils.countInYear(2026, entries))
        val feb = DateUtils.monthRange(YearMonth.of(2024, 2))
        assertEquals(29, (feb.last - feb.first + 1).toInt())
    }

    @Test
    fun `long date format`() {
        assertEquals("Tuesday, 14 October", DateUtils.formatLong(day(2025, 10, 14), Locale.UK))
        assertEquals("October 2025", DateUtils.formatMonth(YearMonth.of(2025, 10), Locale.UK))
    }

    @Test
    fun `epoch day round trip covers dates before 1970`() {
        val d = DateUtils.dayIndex(1965, 5, 1)
        assertTrue(d < 0)
        assertEquals(LocalDate.of(1965, 5, 1), DateUtils.toDate(d))
        assertEquals(1965, DateUtils.yearOf(d))
    }
}

package com.erohsik.pixels.domain

import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Level
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Day identity is `LocalDate.toEpochDay()` throughout. "Today" follows the device's current
 * zone; a user crossing time zones may see a day shift, which is accepted.
 */
object DateUtils {
    const val GRID_COLUMNS = 12
    const val GRID_ROWS = 31
    const val GRID_SIZE = GRID_COLUMNS * GRID_ROWS

    fun today(clock: Clock = Clock.systemDefaultZone()): Long = LocalDate.now(clock).toEpochDay()

    fun toDate(dayIndex: Long): LocalDate = LocalDate.ofEpochDay(dayIndex)

    fun dayIndex(year: Int, month: Int, dayOfMonth: Int): Long =
        LocalDate.of(year, month, dayOfMonth).toEpochDay()

    /** 29 Feb exists only in leap years; 30 Feb / 31 Apr never do. */
    fun isValidDay(year: Int, month: Int, dayOfMonth: Int): Boolean =
        month in 1..12 && YearMonth.of(year, month).isValidDay(dayOfMonth)

    /** Index into the 12 × 31 level grid: column-major by month, then day of month. */
    fun gridIndex(month: Int, dayOfMonth: Int): Int = (month - 1) * GRID_ROWS + (dayOfMonth - 1)

    fun yearOf(dayIndex: Long): Int = toDate(dayIndex).year

    fun firstDayOfYear(year: Int): Long = LocalDate.of(year, 1, 1).toEpochDay()

    fun lastDayOfYear(year: Int): Long = LocalDate.of(year, 12, 31).toEpochDay()

    fun daysInYear(year: Int): Int = if (java.time.Year.isLeap(year.toLong())) 366 else 365

    /**
     * Days of [year] up to and including [today]: all of them for a past year, none for a
     * future one.
     */
    fun daysElapsedInYear(year: Int, today: Long): Int {
        val first = firstDayOfYear(year)
        val last = lastDayOfYear(year)
        return when {
            today < first -> 0
            today > last -> daysInYear(year)
            else -> (today - first + 1).toInt()
        }
    }

    /** Grid value for a cell that is not drawn: a date that doesn't exist, or a future day. */
    const val CELL_HIDDEN = -1

    /**
     * Builds the grid consumed by the canvas: index = (month-1)*31 + (dom-1), value = level
     * 1..5, [Level.EMPTY] for an unlogged past day, [CELL_HIDDEN] for a non-existent or future
     * date. Built once per data change, so the canvas never does date maths or lookups per cell.
     *
     * ASSUMPTION: hidden cells are folded into the same IntArray (as -1) rather than a second
     * array, so the canvas reads exactly one value per cell.
     */
    fun buildLevelGrid(year: Int, entries: List<Entry>, today: Long): IntArray {
        val grid = IntArray(GRID_SIZE) { CELL_HIDDEN }
        for (month in 1..12) {
            val length = YearMonth.of(year, month).lengthOfMonth()
            val monthStart = LocalDate.of(year, month, 1).toEpochDay()
            for (dom in 1..length) {
                if (monthStart + dom - 1 <= today) grid[gridIndex(month, dom)] = Level.EMPTY
            }
        }
        val first = firstDayOfYear(year)
        val last = minOf(lastDayOfYear(year), today)
        for (e in entries) {
            if (e.dayIndex < first || e.dayIndex > last || !Level.isValid(e.level)) continue
            val d = toDate(e.dayIndex)
            grid[gridIndex(d.monthValue, d.dayOfMonth)] = e.level
        }
        return grid
    }

    /** Grid index of [today] if it falls in [year], else -1. */
    fun todayGridIndex(year: Int, today: Long): Int {
        val d = toDate(today)
        return if (d.year == year) gridIndex(d.monthValue, d.dayOfMonth) else -1
    }

    /** Number of entries whose day falls inside [year]. */
    fun countInYear(year: Int, entries: List<Entry>): Int {
        val first = firstDayOfYear(year)
        val last = lastDayOfYear(year)
        return entries.count { it.dayIndex in first..last }
    }

    fun monthRange(month: YearMonth): LongRange =
        month.atDay(1).toEpochDay()..month.atEndOfMonth().toEpochDay()

    /** "Tuesday, 14 October". */
    fun formatLong(dayIndex: Long, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(toDate(dayIndex))

    /** "Tue 14 Oct", for note lists. */
    fun formatShort(dayIndex: Long, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(toDate(dayIndex))

    /** "October 2026". */
    fun formatMonth(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(month)
}

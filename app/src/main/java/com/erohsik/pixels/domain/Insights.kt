package com.erohsik.pixels.domain

import com.erohsik.pixels.data.model.Entry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Format strings for insights, loaded from strings.xml on device and hard-coded in tests.
 * Keeping them as data keeps [Insights] pure while every user-visible word stays in resources.
 */
data class InsightTemplates(
    /** 1: best weekday, 2: its mean, 3: lowest weekday, 4: its mean. */
    val dayOfWeek: String,
    /** 1: best month, 2: its mean, 3: lowest month, 4: its mean. */
    val month: String,
    /** 1: recent 30-day mean, 2: previous 30-day mean. */
    val trendUp: String,
    val trendDown: String,
    /** 1: level-4 label, 2: run length in days, 3: month the run started in. */
    val longestRun: String,
    /** 1: days logged in the year, 2: days of the year so far, 3: the year. */
    val completeness: String,
    val locale: Locale = Locale.getDefault(),
)

/**
 * Deterministic, observational insights. Every rule is a pure function returning null when its
 * threshold isn't met. Phrasing is kept in [InsightTemplates]: observations only, no advice,
 * no goals, no judgement of low levels, no streaks.
 */
object Insights {
    const val MIN_ENTRIES = 60
    const val MAX_SHOWN = 2

    private const val DOW_MIN_GAP = 0.4
    private const val DOW_MIN_SAMPLES = 4 // ASSUMPTION: a weekday needs ≥4 entries to be compared
    private const val MONTH_MIN_ENTRIES = 15
    private const val TREND_WINDOW = 30
    private const val TREND_MIN_GAP = 0.3
    private const val TREND_MIN_SAMPLES = 10 // ASSUMPTION: each 30-day window needs ≥10 entries
    private const val GOOD_LEVEL = 4
    private const val RUN_MIN_LENGTH = 2

    /** Rule 1: best and worst day of the week, if their means differ by more than 0.4. */
    fun dayOfWeek(entries: List<Entry>, t: InsightTemplates): String? {
        val means = entries
            .groupBy { LocalDate.ofEpochDay(it.dayIndex).dayOfWeek }
            .filterValues { it.size >= DOW_MIN_SAMPLES }
            .mapValues { (_, list) -> list.meanLevel() }
        if (means.size < 2) return null
        val best = means.maxWith(compareBy<Map.Entry<DayOfWeek, Double>> { it.value }.thenBy { it.key })
        val worst = means.minWith(compareBy<Map.Entry<DayOfWeek, Double>> { it.value }.thenBy { it.key })
        if (best.value - worst.value <= DOW_MIN_GAP) return null
        return t.dayOfWeek.format(
            t.locale,
            best.key.getDisplayName(TextStyle.FULL, t.locale),
            fmt(best.value, t.locale),
            worst.key.getDisplayName(TextStyle.FULL, t.locale),
            fmt(worst.value, t.locale),
        )
    }

    /** Rule 2: best and worst calendar month, considering only months with ≥15 entries. */
    fun bestWorstMonth(entries: List<Entry>, t: InsightTemplates): String? {
        val means = entries
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.dayIndex)) }
            .filterValues { it.size >= MONTH_MIN_ENTRIES }
            .mapValues { (_, list) -> list.meanLevel() }
        if (means.size < 2) return null
        val best = means.maxWith(compareBy<Map.Entry<YearMonth, Double>> { it.value }.thenBy { it.key })
        val worst = means.minWith(compareBy<Map.Entry<YearMonth, Double>> { it.value }.thenBy { it.key })
        if (best.value == worst.value) return null
        return t.month.format(
            t.locale,
            DateUtils.formatMonth(best.key, t.locale),
            fmt(best.value, t.locale),
            DateUtils.formatMonth(worst.key, t.locale),
            fmt(worst.value, t.locale),
        )
    }

    /** Rule 3: last 30 days vs the 30 before, if the means differ by more than 0.3. */
    fun recentTrend(entries: List<Entry>, today: Long, t: InsightTemplates): String? {
        val recentStart = today - (TREND_WINDOW - 1)
        val previousStart = recentStart - TREND_WINDOW
        val recent = entries.filter { it.dayIndex in recentStart..today }
        val previous = entries.filter { it.dayIndex in previousStart until recentStart }
        if (recent.size < TREND_MIN_SAMPLES || previous.size < TREND_MIN_SAMPLES) return null
        val r = recent.meanLevel()
        val p = previous.meanLevel()
        if (abs(r - p) <= TREND_MIN_GAP) return null
        val template = if (r > p) t.trendUp else t.trendDown
        return template.format(t.locale, fmt(r, t.locale), fmt(p, t.locale))
    }

    /**
     * Rule 4: longest run of consecutive days at level ≥4. On a tie the most recent run wins.
     * [goodLabel] is the tracker's own level-4 label, so the phrasing matches what the user sees.
     */
    fun longestGoodRun(entries: List<Entry>, today: Long, goodLabel: String, t: InsightTemplates): String? {
        var bestLength = 0
        var bestStart = 0L
        var runLength = 0
        var runStart = 0L
        var previousDay = Long.MIN_VALUE
        for (e in entries.sortedBy { it.dayIndex }) {
            if (e.level >= GOOD_LEVEL) {
                if (runLength > 0 && e.dayIndex == previousDay + 1) {
                    runLength++
                } else {
                    runLength = 1
                    runStart = e.dayIndex
                }
                if (runLength >= bestLength) {
                    bestLength = runLength
                    bestStart = runStart
                }
            } else {
                runLength = 0
            }
            previousDay = e.dayIndex
        }
        if (bestLength < RUN_MIN_LENGTH) return null
        val start = LocalDate.ofEpochDay(bestStart)
        val monthName = if (start.year == LocalDate.ofEpochDay(today).year) {
            start.month.getDisplayName(TextStyle.FULL, t.locale)
        } else {
            DateUtils.formatMonth(YearMonth.from(start), t.locale)
        }
        return t.longestRun.format(t.locale, goodLabel.lowercase(t.locale), bestLength, monthName)
    }

    /** Rule 5: how many days of [year] have been logged so far. */
    fun yearCompleteness(entries: List<Entry>, year: Int, today: Long, t: InsightTemplates): String? {
        val elapsed = DateUtils.daysElapsedInYear(year, today)
        if (elapsed == 0) return null
        val last = minOf(today, DateUtils.lastDayOfYear(year))
        val logged = entries.count { it.dayIndex in DateUtils.firstDayOfYear(year)..last }
        return t.completeness.format(t.locale, logged, elapsed, year)
    }

    /**
     * Every insight whose threshold is met, in rule order. Empty below [MIN_ENTRIES] entries.
     * [entries] is the tracker's whole history; [year] is the year on screen.
     */
    fun all(entries: List<Entry>, year: Int, today: Long, goodLabel: String, t: InsightTemplates): List<String> {
        if (entries.size < MIN_ENTRIES) return emptyList()
        return listOfNotNull(
            dayOfWeek(entries, t),
            bestWorstMonth(entries, t),
            recentTrend(entries, today, t),
            longestGoodRun(entries, today, goodLabel, t),
            yearCompleteness(entries, year, today, t),
        )
    }

    /**
     * Picks at most [MAX_SHOWN] insights, rotating through the list once per day.
     * ASSUMPTION: "rotating" means the pair shown advances daily, not on a timer.
     */
    fun pickRotating(insights: List<String>, today: Long): List<String> {
        if (insights.size <= MAX_SHOWN) return insights
        val start = Math.floorMod(today, insights.size.toLong()).toInt()
        return List(MAX_SHOWN) { insights[(start + it) % insights.size] }
    }

    private fun List<Entry>.meanLevel(): Double = sumOf { it.level }.toDouble() / size

    private fun fmt(value: Double, locale: Locale): String = String.format(locale, "%.1f", value)
}

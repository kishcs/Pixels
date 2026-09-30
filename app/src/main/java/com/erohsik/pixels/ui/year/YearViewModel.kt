package com.erohsik.pixels.ui.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erohsik.pixels.data.EntryDao
import com.erohsik.pixels.data.SettingsStore
import com.erohsik.pixels.data.TrackerDao
import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Level
import com.erohsik.pixels.data.model.Tracker
import com.erohsik.pixels.data.resolveHomeTracker
import com.erohsik.pixels.domain.DateUtils
import com.erohsik.pixels.domain.InsightTemplates
import com.erohsik.pixels.domain.Insights
import com.erohsik.pixels.ui.log.EntryWriter
import com.erohsik.pixels.ui.palette.Palette
import com.erohsik.pixels.ui.palette.Palettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Per-month numbers for the grid's accessibility summary. */
data class MonthSummary(val logged: Int, val possible: Int, val commonLevel: Int)

data class YearUiState(
    val loading: Boolean = true,
    val trackers: List<Tracker> = emptyList(),
    val tracker: Tracker? = null,
    val palette: Palette = Palettes.Default,
    val year: Int = DateUtils.yearOf(DateUtils.today()),
    val today: Long = DateUtils.today(),
    /** See [DateUtils.buildLevelGrid]. Built here once; the canvas only indexes into it. */
    val levels: IntArray = IntArray(DateUtils.GRID_SIZE) { DateUtils.CELL_HIDDEN },
    val todayIndex: Int = -1,
    val entriesByDay: Map<Long, Entry> = emptyMap(),
    val todayEntry: Entry? = null,
    val loggedInYear: Int = 0,
    val daysElapsed: Int = 0,
    val totalEntries: Int = 0,
    val insights: List<String> = emptyList(),
    val months: List<MonthSummary> = emptyList(),
    val years: List<Int> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class YearViewModel(
    trackerDao: TrackerDao,
    private val entryDao: EntryDao,
    private val settings: SettingsStore,
    private val writer: EntryWriter,
    private val today: StateFlow<Long>,
    private val templates: InsightTemplates,
) : ViewModel() {

    private val year = MutableStateFlow(DateUtils.yearOf(today.value))

    private val trackers: Flow<List<Tracker>> = trackerDao.observeAll()

    private val homeTracker: Flow<Tracker?> =
        combine(trackers, settings.selectedTrackerId) { list, id -> resolveHomeTracker(list, id) }
            .distinctUntilChanged()

    /** Tagged with the tracker id so a stale list is never drawn against a newer tracker. */
    private val entries: Flow<Pair<Long, List<Entry>>> =
        homeTracker.map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(-1L to emptyList()) else entryDao.observeAllFor(id).map { id to it }
        }

    val state: StateFlow<YearUiState> =
        combine(trackers, homeTracker, entries, year, today) { all, tracker, tagged, y, t ->
            val list = if (tracker != null && tagged.first == tracker.id) tagged.second else emptyList()
            buildState(all.filter { !it.archived }, tracker, list, y, t)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YearUiState())

    private fun buildState(active: List<Tracker>, tracker: Tracker?, entries: List<Entry>, year: Int, today: Long): YearUiState {
        val palette = tracker?.let { Palettes.byId(it.paletteId) } ?: Palettes.Default
        val levels = DateUtils.buildLevelGrid(year, entries, today)
        val byDay = entries.associateBy { it.dayIndex }
        val currentYear = DateUtils.yearOf(today)
        val earliest = listOfNotNull(entries.firstOrNull()?.dayIndex, tracker?.createdDay).minOrNull()
        val firstYear = minOf(earliest?.let(DateUtils::yearOf) ?: currentYear, currentYear - YEARS_BACK)
        val insights = if (tracker != null && entries.size >= Insights.MIN_ENTRIES) {
            Insights.pickRotating(Insights.all(entries, year, today, tracker.label(4), templates), today)
        } else {
            emptyList()
        }
        return YearUiState(
            loading = false,
            trackers = active,
            tracker = tracker,
            palette = palette,
            year = year,
            today = today,
            levels = levels,
            todayIndex = DateUtils.todayGridIndex(year, today),
            entriesByDay = byDay,
            todayEntry = byDay[today],
            loggedInYear = DateUtils.countInYear(year, entries),
            daysElapsed = DateUtils.daysElapsedInYear(year, today),
            totalEntries = entries.size,
            insights = insights,
            months = summarise(levels),
            years = (currentYear downTo firstYear).toList(),
        )
    }

    private fun summarise(levels: IntArray): List<MonthSummary> = List(12) { m ->
        val counts = IntArray(Level.MAX + 1)
        var possible = 0
        for (row in 0 until DateUtils.GRID_ROWS) {
            val v = levels[m * DateUtils.GRID_ROWS + row]
            if (v == DateUtils.CELL_HIDDEN) continue
            possible++
            counts[v]++
        }
        val common = (Level.MIN..Level.MAX).maxByOrNull { counts[it] }?.takeIf { counts[it] > 0 } ?: 0
        MonthSummary(logged = possible - counts[Level.EMPTY], possible = possible, commonLevel = common)
    }

    fun setYear(value: Int) {
        year.value = value
    }

    fun selectTracker(id: Long) {
        viewModelScope.launch { settings.setSelectedTracker(id) }
    }

    /** The month the year screen's swipe opens: this month in the current year, else January. */
    fun defaultMonth(): YearMonth {
        val s = state.value
        val todayDate = DateUtils.toDate(s.today)
        return if (todayDate.year == s.year) YearMonth.from(todayDate) else YearMonth.of(s.year, 1)
    }

    fun log(dayIndex: Long, level: Int, note: String?) {
        val tracker = state.value.tracker ?: return
        viewModelScope.launch { writer.log(tracker.id, dayIndex, today.value, level, note) }
    }

    fun saveNote(dayIndex: Long, note: String?) {
        val tracker = state.value.tracker ?: return
        viewModelScope.launch { writer.saveNote(tracker.id, dayIndex, note) }
    }

    fun clear(dayIndex: Long) {
        val tracker = state.value.tracker ?: return
        viewModelScope.launch { writer.clear(tracker.id, dayIndex) }
    }

    private companion object {
        const val YEARS_BACK = 5
    }
}

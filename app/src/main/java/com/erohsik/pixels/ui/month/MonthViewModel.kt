package com.erohsik.pixels.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erohsik.pixels.data.EntryDao
import com.erohsik.pixels.data.SettingsStore
import com.erohsik.pixels.data.TrackerDao
import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Tracker
import com.erohsik.pixels.data.resolveHomeTracker
import com.erohsik.pixels.domain.DateUtils
import com.erohsik.pixels.ui.log.EntryWriter
import com.erohsik.pixels.ui.palette.Palette
import com.erohsik.pixels.ui.palette.Palettes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class MonthUiState(
    val loading: Boolean = true,
    val tracker: Tracker? = null,
    val palette: Palette = Palettes.Default,
    val month: YearMonth = YearMonth.now(),
    val today: Long = DateUtils.today(),
    val entriesByDay: Map<Long, Entry> = emptyMap(),
    /** Entries with a note, in day order, for the list beneath the calendar. */
    val notes: List<Entry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(
    trackerDao: TrackerDao,
    private val entryDao: EntryDao,
    settings: SettingsStore,
    private val writer: EntryWriter,
    private val today: StateFlow<Long>,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(DateUtils.toDate(today.value)))

    private val homeTracker =
        combine(trackerDao.observeAll(), settings.selectedTrackerId) { list, id -> resolveHomeTracker(list, id) }
            .distinctUntilChanged()

    val state: StateFlow<MonthUiState> =
        combine(homeTracker, month) { tracker, m -> tracker to m }
            .flatMapLatest { (tracker, m) ->
                if (tracker == null) {
                    flowOf(MonthUiState(loading = false, month = m))
                } else {
                    val range = DateUtils.monthRange(m)
                    combine(entryDao.observeRange(tracker.id, range.first, range.last), today) { entries, t ->
                        MonthUiState(
                            loading = false,
                            tracker = tracker,
                            palette = Palettes.byId(tracker.paletteId),
                            month = m,
                            today = t,
                            entriesByDay = entries.associateBy { it.dayIndex },
                            notes = entries.filter { !it.note.isNullOrBlank() },
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthUiState())

    fun setMonth(value: YearMonth) {
        month.value = value
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
}

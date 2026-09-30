package com.erohsik.pixels.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erohsik.pixels.data.SettingsStore
import com.erohsik.pixels.data.TrackerDao
import com.erohsik.pixels.data.model.Tracker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackersUiState(
    val loading: Boolean = true,
    val active: List<Tracker> = emptyList(),
    val archived: List<Tracker> = emptyList(),
)

class TrackersViewModel(
    private val trackerDao: TrackerDao,
    private val settings: SettingsStore,
) : ViewModel() {

    val state: StateFlow<TrackersUiState> = trackerDao.observeAll()
        .map { all -> TrackersUiState(false, all.filter { !it.archived }, all.filter { it.archived }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackersUiState())

    fun reorder(ids: List<Long>) {
        viewModelScope.launch { trackerDao.reorder(ids) }
    }

    fun archive(id: Long) {
        viewModelScope.launch { trackerDao.archive(id) }
    }

    fun unarchive(id: Long) {
        viewModelScope.launch { trackerDao.unarchive(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { trackerDao.delete(id) }
    }

    fun select(id: Long) {
        viewModelScope.launch { settings.setSelectedTracker(id) }
    }
}

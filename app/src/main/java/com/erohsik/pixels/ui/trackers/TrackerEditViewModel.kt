package com.erohsik.pixels.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erohsik.pixels.data.TrackerDao
import com.erohsik.pixels.data.model.Level
import com.erohsik.pixels.data.model.Tracker
import com.erohsik.pixels.ui.palette.Palettes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrackerEditState(
    val loadedId: Long? = null,
    val existing: Tracker? = null,
    val name: String = "",
    val paletteId: String = Palettes.Default.id,
    val labels: List<String> = List(Level.COUNT) { "" },
) {
    val isNew: Boolean get() = existing == null
    val canSave: Boolean get() = name.isNotBlank() && labels.all { it.isNotBlank() }
}

class TrackerEditViewModel(
    private val trackerDao: TrackerDao,
    private val today: StateFlow<Long>,
) : ViewModel() {

    private val _state = MutableStateFlow(TrackerEditState())
    val state: StateFlow<TrackerEditState> = _state.asStateFlow()

    /**
     * Loads tracker [id] (0 = new) unless it is already loaded, so a rotation keeps edits.
     * [defaultLabels] seeds a new tracker's labels.
     */
    fun load(id: Long, defaultLabels: List<String>) {
        if (_state.value.loadedId == id) return
        if (id == NEW) {
            _state.value = TrackerEditState(loadedId = NEW, labels = defaultLabels)
            return
        }
        _state.value = TrackerEditState(loadedId = id)
        viewModelScope.launch {
            val t = trackerDao.get(id) ?: return@launch
            _state.value = TrackerEditState(
                loadedId = id,
                existing = t,
                name = t.name,
                paletteId = t.paletteId,
                labels = t.labels,
            )
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value.take(MAX_NAME)) }

    fun setPalette(id: String) = _state.update { it.copy(paletteId = id) }

    fun setLabel(level: Int, value: String) = _state.update {
        it.copy(labels = it.labels.toMutableList().also { l -> l[level - 1] = value.take(MAX_LABEL) })
    }

    /** Saves and resets. Calls [onDone] with the tracker's id once the write lands. */
    fun save(onDone: (Long) -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val labels = s.labels.map { it.trim() }
            val existing = s.existing
            val id = if (existing == null) {
                trackerDao.insert(
                    Tracker(
                        name = s.name.trim(),
                        position = 0,
                        createdDay = today.value,
                        paletteId = s.paletteId,
                        labels = labels,
                    ),
                )
            } else {
                trackerDao.update(existing.copy(name = s.name.trim(), paletteId = s.paletteId, labels = labels))
                existing.id
            }
            reset()
            onDone(id)
        }
    }

    fun delete(onDone: () -> Unit) {
        val existing = _state.value.existing ?: return
        viewModelScope.launch {
            trackerDao.delete(existing.id)
            reset()
            onDone()
        }
    }

    /** Drops any unsaved edits so the next visit starts fresh. */
    fun reset() {
        _state.value = TrackerEditState()
    }

    companion object {
        const val NEW = 0L
        private const val MAX_NAME = 40
        private const val MAX_LABEL = 24
    }
}

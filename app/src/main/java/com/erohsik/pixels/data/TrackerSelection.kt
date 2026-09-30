package com.erohsik.pixels.data

import com.erohsik.pixels.data.model.Tracker

/**
 * The "home" tracker shown by the year screen, the widget, and the reminder: the selected one if
 * it is still active, otherwise the first active tracker by position, otherwise none.
 */
fun resolveHomeTracker(trackers: List<Tracker>, selectedId: Long?): Tracker? {
    val active = trackers.filter { !it.archived }
    return active.firstOrNull { it.id == selectedId } ?: active.firstOrNull()
}

/** Blocking lookup for receivers and widget callbacks that are already off the main thread. */
fun TrackerDao.homeTrackerBlocking(settings: SettingsStore): Tracker? =
    resolveHomeTracker(queryAll(), settings.currentSelectedTrackerId())

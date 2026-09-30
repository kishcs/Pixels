package com.erohsik.pixels.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.erohsik.pixels.di.ServiceLocator
import com.erohsik.pixels.ui.log.EntryWriter
import com.erohsik.pixels.ui.month.MonthViewModel
import com.erohsik.pixels.ui.settings.SettingsViewModel
import com.erohsik.pixels.ui.trackers.TrackerEditViewModel
import com.erohsik.pixels.ui.trackers.TrackersViewModel
import com.erohsik.pixels.ui.year.YearViewModel

/** The single factory for every ViewModel. Explicit constructors, no reflection. */
object PixelsViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val sl = ServiceLocator
        val vm: ViewModel = when (modelClass) {
            YearViewModel::class.java -> YearViewModel(
                sl.trackerDao, sl.entryDao, sl.settings, EntryWriter(sl.entryDao), sl.today, sl.insightTemplates,
            )
            MonthViewModel::class.java -> MonthViewModel(
                sl.trackerDao, sl.entryDao, sl.settings, EntryWriter(sl.entryDao), sl.today,
            )
            TrackersViewModel::class.java -> TrackersViewModel(sl.trackerDao, sl.settings)
            TrackerEditViewModel::class.java -> TrackerEditViewModel(sl.trackerDao, sl.today)
            SettingsViewModel::class.java -> SettingsViewModel(sl.app, sl.settings, sl.backup)
            else -> throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
        }
        return vm as T
    }
}

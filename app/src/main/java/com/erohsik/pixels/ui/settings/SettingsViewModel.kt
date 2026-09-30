package com.erohsik.pixels.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erohsik.pixels.R
import com.erohsik.pixels.data.SettingsStore
import com.erohsik.pixels.data.ThemeMode
import com.erohsik.pixels.data.export.BackupFormatException
import com.erohsik.pixels.data.export.BackupRepository
import com.erohsik.pixels.data.export.ImportPlan
import com.erohsik.pixels.notify.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = SettingsStore.DEFAULT_REMINDER_MINUTES,
)

class SettingsViewModel(
    private val app: Application,
    private val settings: SettingsStore,
    private val backup: BackupRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(settings.themeMode, settings.reminderEnabled, settings.reminderMinuteOfDay) { theme, enabled, minutes ->
            SettingsUiState(theme, enabled, minutes)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(
                settings.currentTheme(),
                settings.currentReminderEnabled(),
                settings.currentReminderMinuteOfDay(),
            ),
        )

    /** An import waiting for the user to confirm its summary. */
    private val _pendingImport = MutableStateFlow<ImportPlan?>(null)
    val pendingImport: StateFlow<ImportPlan?> = _pendingImport.asStateFlow()

    /** One-shot message for the snackbar; cleared by [messageShown]. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settings.setTheme(mode) }
    }

    /** Called only after POST_NOTIFICATIONS is granted (or not needed on this API level). */
    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setReminderEnabled(enabled)
            if (enabled) {
                ReminderScheduler.schedule(app, settings.currentReminderMinuteOfDay())
            } else {
                ReminderScheduler.cancel(app)
            }
        }
    }

    fun setReminderTime(minuteOfDay: Int) {
        viewModelScope.launch {
            settings.setReminderMinuteOfDay(minuteOfDay)
            if (settings.currentReminderEnabled()) ReminderScheduler.schedule(app, minuteOfDay)
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _message.value = runCatching { backup.exportTo(uri) }.fold(
                onSuccess = { app.getString(R.string.export_done) },
                onFailure = { app.getString(R.string.export_failed) },
            )
        }
    }

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            runCatching { backup.preview(uri) }
                .onSuccess { plan ->
                    if (plan.isEmpty) _message.value = app.getString(R.string.import_nothing)
                    else _pendingImport.value = plan
                }
                .onFailure { e ->
                    _message.value = app.getString(
                        if (e is BackupFormatException) R.string.import_invalid else R.string.import_failed,
                    )
                }
        }
    }

    fun confirmImport() {
        val plan = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            _message.value = runCatching { backup.apply(plan) }.fold(
                onSuccess = { app.getString(R.string.import_done) },
                onFailure = { app.getString(R.string.import_failed) },
            )
        }
    }

    fun cancelImport() {
        _pendingImport.value = null
    }

    fun messageShown() {
        _message.value = null
    }
}

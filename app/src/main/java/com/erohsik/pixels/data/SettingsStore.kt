package com.erohsik.pixels.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.erohsik.pixels.data.PixelsDbHelper.Companion.T_SETTINGS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Key/value settings backed by the `settings` table and mirrored in memory.
 *
 * ASSUMPTION: the table is read once, synchronously, on first use (from Application.onCreate).
 * It holds a handful of rows, and having the values ready before the first frame is what
 * keeps the theme and the selected tracker from flickering on cold start.
 */
class SettingsStore(private val helper: PixelsDbHelper) {

    private val values = MutableStateFlow(loadAll())

    val themeMode: Flow<ThemeMode> =
        values.map { parseTheme(it[KEY_THEME]) }.distinctUntilChanged()

    val selectedTrackerId: Flow<Long?> =
        values.map { it[KEY_SELECTED_TRACKER]?.toLongOrNull() }.distinctUntilChanged()

    val reminderEnabled: Flow<Boolean> =
        values.map { it[KEY_REMINDER_ENABLED] == "1" }.distinctUntilChanged()

    val reminderMinuteOfDay: Flow<Int> =
        values.map { parseMinutes(it[KEY_REMINDER_TIME]) }.distinctUntilChanged()

    fun currentTheme(): ThemeMode = parseTheme(values.value[KEY_THEME])
    fun currentSelectedTrackerId(): Long? = values.value[KEY_SELECTED_TRACKER]?.toLongOrNull()
    fun currentReminderEnabled(): Boolean = values.value[KEY_REMINDER_ENABLED] == "1"
    fun currentReminderMinuteOfDay(): Int = parseMinutes(values.value[KEY_REMINDER_TIME])

    suspend fun setTheme(mode: ThemeMode) = put(KEY_THEME, mode.name)
    suspend fun setSelectedTracker(id: Long) = put(KEY_SELECTED_TRACKER, id.toString())
    suspend fun setReminderEnabled(enabled: Boolean) = put(KEY_REMINDER_ENABLED, if (enabled) "1" else "0")
    suspend fun setReminderMinuteOfDay(minutes: Int) = put(KEY_REMINDER_TIME, minutes.toString())

    private suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        val row = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        helper.writableDatabase.insertWithOnConflict(T_SETTINGS, null, row, SQLiteDatabase.CONFLICT_REPLACE)
        values.update { it + (key to value) }
    }

    private fun loadAll(): Map<String, String> =
        helper.readableDatabase.query(T_SETTINGS, null, null, null, null, null, null).use { c ->
            val k = c.getColumnIndexOrThrow("key")
            val v = c.getColumnIndexOrThrow("value")
            buildMap { while (c.moveToNext()) put(c.getString(k), c.getString(v)) }
        }

    private fun parseTheme(raw: String?): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM

    private fun parseMinutes(raw: String?): Int =
        raw?.toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: DEFAULT_REMINDER_MINUTES

    companion object {
        const val DEFAULT_REMINDER_MINUTES = 21 * 60

        private const val KEY_THEME = "theme"
        private const val KEY_SELECTED_TRACKER = "selected_tracker"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_TIME = "reminder_minute_of_day"
    }
}

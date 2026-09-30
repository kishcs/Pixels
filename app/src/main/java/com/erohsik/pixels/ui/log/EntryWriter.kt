package com.erohsik.pixels.ui.log

import com.erohsik.pixels.data.EntryDao
import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Level

/** The three writes the log sheet can make, shared by the year and month screens. */
class EntryWriter(private val entryDao: EntryDao) {

    /** Future days are never loggable; the call is a no-op for them. */
    suspend fun log(trackerId: Long, dayIndex: Long, today: Long, level: Int, note: String?) {
        if (dayIndex > today || !Level.isValid(level)) return
        entryDao.upsert(Entry(trackerId, dayIndex, level, normaliseNote(note), System.currentTimeMillis()))
    }

    /**
     * Saves [note] onto an existing entry. A note typed on a day that has no level is dropped:
     * ASSUMPTION: an entry needs a level, so there is nothing to attach the note to.
     */
    suspend fun saveNote(trackerId: Long, dayIndex: Long, note: String?) {
        val existing = entryDao.getForDay(trackerId, dayIndex) ?: return
        val normalised = normaliseNote(note)
        if (existing.note == normalised) return
        entryDao.upsert(existing.copy(note = normalised, updatedAt = System.currentTimeMillis()))
    }

    suspend fun clear(trackerId: Long, dayIndex: Long) {
        entryDao.delete(trackerId, dayIndex)
    }

    private fun normaliseNote(note: String?): String? =
        note?.trim()?.take(Entry.MAX_NOTE_LENGTH)?.takeIf { it.isNotEmpty() }
}

package com.erohsik.pixels.data

import android.content.ContentValues
import android.database.Cursor
import com.erohsik.pixels.data.PixelsDbHelper.Companion.T_TRACKERS
import com.erohsik.pixels.data.model.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class TrackerDao(private val helper: PixelsDbHelper) {

    /**
     * All trackers, archived included, ordered by position.
     * ASSUMPTION: shares the helper's single change ticker rather than a tracker-only
     * MutableStateFlow; distinctUntilChanged drops re-emissions caused by entry writes.
     */
    fun observeAll(): Flow<List<Tracker>> =
        helper.changes
            .onStart { emit(Unit) }
            .conflate()
            .map { queryAll() }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun queryAll(): List<Tracker> =
        helper.readableDatabase.query(
            T_TRACKERS, null, null, null, null, null, "position ASC, id ASC",
        ).use { c -> buildList { while (c.moveToNext()) add(c.toTracker()) } }

    suspend fun getAll(): List<Tracker> = withContext(Dispatchers.IO) { queryAll() }

    suspend fun get(id: Long): Tracker? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            T_TRACKERS, null, "id = ?", arrayOf(id.toString()), null, null, null,
        ).use { c -> if (c.moveToFirst()) c.toTracker() else null }
    }

    /** Inserts [t] at the end of the list, ignoring its id and position. Returns the new id. */
    suspend fun insert(t: Tracker): Long = withContext(Dispatchers.IO) {
        val id = helper.writableDatabase.inTransaction { insertBlocking(this, t) }
        helper.notifyChanged()
        id
    }

    /** For callers already inside a transaction (import). Does not notify. */
    internal fun insertBlocking(db: android.database.sqlite.SQLiteDatabase, t: Tracker): Long {
        val nextPosition = db.rawQuery("SELECT COALESCE(MAX(position), -1) + 1 FROM $T_TRACKERS", null)
            .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        return db.insertOrThrow(T_TRACKERS, null, t.toValues().apply { put("position", nextPosition) })
    }

    suspend fun update(t: Tracker) = withContext(Dispatchers.IO) {
        // Plain UPDATE, never REPLACE: REPLACE deletes the row first, which would cascade
        // and wipe every entry for the tracker.
        helper.writableDatabase.update(T_TRACKERS, t.toValues(), "id = ?", arrayOf(t.id.toString()))
        helper.notifyChanged()
    }

    suspend fun archive(id: Long) = setArchived(id, true)

    suspend fun unarchive(id: Long) = setArchived(id, false)

    private suspend fun setArchived(id: Long, archived: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("archived", if (archived) 1 else 0) }
        helper.writableDatabase.update(T_TRACKERS, values, "id = ?", arrayOf(id.toString()))
        helper.notifyChanged()
    }

    /** Deletes the tracker; its entries go with it via ON DELETE CASCADE. */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(T_TRACKERS, "id = ?", arrayOf(id.toString()))
        helper.notifyChanged()
    }

    /** Sets position = index in [ids] for each tracker. */
    suspend fun reorder(ids: List<Long>) = withContext(Dispatchers.IO) {
        helper.writableDatabase.inTransaction {
            val values = ContentValues()
            ids.forEachIndexed { index, id ->
                values.put("position", index)
                update(T_TRACKERS, values, "id = ?", arrayOf(id.toString()))
            }
        }
        helper.notifyChanged()
    }

    private fun Tracker.toValues() = ContentValues().apply {
        put("name", name)
        put("position", position)
        put("created_day", createdDay)
        put("palette_id", paletteId)
        labels.forEachIndexed { i, label -> put("label_${i + 1}", label) }
        put("archived", if (archived) 1 else 0)
    }

    private fun Cursor.toTracker() = Tracker(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        position = getInt(getColumnIndexOrThrow("position")),
        createdDay = getLong(getColumnIndexOrThrow("created_day")),
        paletteId = getString(getColumnIndexOrThrow("palette_id")),
        labels = (1..5).map { getString(getColumnIndexOrThrow("label_$it")) },
        archived = getInt(getColumnIndexOrThrow("archived")) != 0,
    )
}

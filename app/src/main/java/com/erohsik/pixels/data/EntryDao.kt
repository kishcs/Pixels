package com.erohsik.pixels.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.erohsik.pixels.data.PixelsDbHelper.Companion.T_ENTRIES
import com.erohsik.pixels.data.model.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class EntryDao(private val helper: PixelsDbHelper) {

    /** Entries for [trackerId] with day_index in [fromDay]..[toDay] inclusive, ordered by day. */
    fun observeRange(trackerId: Long, fromDay: Long, toDay: Long): Flow<List<Entry>> =
        helper.changes
            .onStart { emit(Unit) }
            .conflate()
            .map { queryRange(trackerId, fromDay, toDay) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Every entry for [trackerId]; the tracker's whole history. */
    fun observeAllFor(trackerId: Long): Flow<List<Entry>> =
        observeRange(trackerId, Long.MIN_VALUE, Long.MAX_VALUE)

    fun queryRange(trackerId: Long, fromDay: Long, toDay: Long): List<Entry> =
        helper.readableDatabase.query(
            T_ENTRIES, null,
            "tracker_id = ? AND day_index BETWEEN ? AND ?",
            arrayOf(trackerId.toString(), fromDay.toString(), toDay.toString()),
            null, null, "day_index ASC",
        ).use { it.toEntries() }

    suspend fun getForDay(trackerId: Long, dayIndex: Long): Entry? = withContext(Dispatchers.IO) {
        getForDayBlocking(trackerId, dayIndex)
    }

    fun getForDayBlocking(trackerId: Long, dayIndex: Long): Entry? =
        helper.readableDatabase.query(
            T_ENTRIES, null,
            "tracker_id = ? AND day_index = ?",
            arrayOf(trackerId.toString(), dayIndex.toString()),
            null, null, null,
        ).use { c -> c.toEntries().firstOrNull() }

    /** Single REPLACE on the composite primary key. No read-then-write. */
    suspend fun upsert(e: Entry) = withContext(Dispatchers.IO) {
        upsertBlocking(helper.writableDatabase, e)
        helper.notifyChanged()
    }

    /** For callers already inside a transaction (import). Does not notify. */
    internal fun upsertBlocking(db: SQLiteDatabase, e: Entry) {
        val values = ContentValues().apply {
            put("tracker_id", e.trackerId)
            put("day_index", e.dayIndex)
            put("level", e.level)
            if (e.note.isNullOrBlank()) putNull("note") else put("note", e.note.take(Entry.MAX_NOTE_LENGTH))
            put("updated_at", e.updatedAt)
        }
        db.insertWithOnConflict(T_ENTRIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun delete(trackerId: Long, dayIndex: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(
            T_ENTRIES, "tracker_id = ? AND day_index = ?",
            arrayOf(trackerId.toString(), dayIndex.toString()),
        )
        helper.notifyChanged()
    }

    suspend fun countAll(trackerId: Long): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $T_ENTRIES WHERE tracker_id = ?", arrayOf(trackerId.toString()),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    suspend fun exportAll(): List<Entry> = withContext(Dispatchers.IO) { exportAllBlocking() }

    fun exportAllBlocking(): List<Entry> =
        helper.readableDatabase.query(
            T_ENTRIES, null, null, null, null, null, "tracker_id ASC, day_index ASC",
        ).use { it.toEntries() }

    private fun Cursor.toEntries(): List<Entry> {
        val tracker = getColumnIndexOrThrow("tracker_id")
        val day = getColumnIndexOrThrow("day_index")
        val level = getColumnIndexOrThrow("level")
        val note = getColumnIndexOrThrow("note")
        val updated = getColumnIndexOrThrow("updated_at")
        return buildList(count) {
            while (moveToNext()) {
                add(
                    Entry(
                        trackerId = getLong(tracker),
                        dayIndex = getLong(day),
                        level = getInt(level),
                        note = if (isNull(note)) null else getString(note),
                        updatedAt = getLong(updated),
                    ),
                )
            }
        }
    }
}

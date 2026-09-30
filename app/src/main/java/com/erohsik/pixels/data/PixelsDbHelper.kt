package com.erohsik.pixels.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.erohsik.pixels.R
import com.erohsik.pixels.domain.DateUtils
import com.erohsik.pixels.data.model.Tracker
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Raw SQLite. SQLite has no change notifications, so every DAO write calls [notifyChanged],
 * and every `observe*` query re-runs when [changes] ticks.
 */
class PixelsDbHelper(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Ticks once per committed write. Carries no payload; observers simply re-query. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        // ASSUMPTION: enableWriteAheadLogging() issues PRAGMA journal_mode=WAL and also tells the
        // framework's connection pool about it. A bare rawQuery("PRAGMA journal_mode=WAL") works
        // too, but leaves the pool thinking it is in rollback-journal mode.
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_TRACKERS (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                name            TEXT    NOT NULL,
                position        INTEGER NOT NULL,
                created_day     INTEGER NOT NULL,
                palette_id      TEXT    NOT NULL,
                label_1         TEXT    NOT NULL,
                label_2         TEXT    NOT NULL,
                label_3         TEXT    NOT NULL,
                label_4         TEXT    NOT NULL,
                label_5         TEXT    NOT NULL,
                archived        INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE $T_ENTRIES (
                tracker_id      INTEGER NOT NULL,
                day_index       INTEGER NOT NULL,
                level           INTEGER NOT NULL,
                note            TEXT,
                updated_at      INTEGER NOT NULL,
                PRIMARY KEY (tracker_id, day_index),
                FOREIGN KEY (tracker_id) REFERENCES $T_TRACKERS(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_entries_tracker_day ON $T_ENTRIES(tracker_id, day_index)")
        db.execSQL(
            """
            CREATE TABLE $T_SETTINGS (
                key             TEXT PRIMARY KEY,
                value           TEXT NOT NULL
            )
            """.trimIndent(),
        )
        seedDefaultTracker(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Step through every version in order so a user jumping several releases runs each
        // migration exactly once. Never DROP TABLE here: user data lives in these tables.
        var version = oldVersion
        while (version < newVersion) {
            when (version) {
                // 1 -> migrate1To2(db)
                else -> Unit
            }
            version++
        }
    }

    private fun seedDefaultTracker(db: SQLiteDatabase) {
        val labels = context.resources.getStringArray(R.array.default_mood_labels)
        val values = ContentValues().apply {
            put("name", context.getString(R.string.default_tracker_name))
            put("position", 0)
            put("created_day", DateUtils.today())
            put("palette_id", Tracker.DEFAULT_PALETTE_ID)
            labels.forEachIndexed { i, label -> put("label_${i + 1}", label) }
            put("archived", 0)
        }
        db.insertOrThrow(T_TRACKERS, null, values)
    }

    companion object {
        const val DB_NAME = "pixels.db"
        const val DB_VERSION = 1

        const val T_TRACKERS = "trackers"
        const val T_ENTRIES = "entries"
        const val T_SETTINGS = "settings"
    }
}

/** Runs [block] in a transaction and returns its result. */
inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}

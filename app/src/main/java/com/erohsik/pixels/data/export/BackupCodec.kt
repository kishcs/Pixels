package com.erohsik.pixels.data.export

import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Level
import com.erohsik.pixels.data.model.Tracker
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

data class Backup(
    val schema: Int,
    val exportedAt: Long,
    val trackers: List<BackupTracker>,
)

data class BackupTracker(
    val id: Long,
    val name: String,
    val paletteId: String,
    val labels: List<String>,
    val archived: Boolean,
    val entries: List<BackupEntry>,
)

data class BackupEntry(
    val day: Long,
    val level: Int,
    val note: String?,
    val updatedAt: Long,
)

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What an import will do to one tracker. [existing] is null when the tracker is new. */
data class TrackerImport(
    val existing: Tracker?,
    val source: BackupTracker,
    /** True when [existing] has no entries yet, so the backup's palette and labels are adopted. */
    val adoptAppearance: Boolean,
    /** Entries to write, already filtered down to adds and newer updates. */
    val writes: List<BackupEntry>,
)

data class ImportPlan(
    val trackers: List<TrackerImport>,
    val added: Int,
    val updated: Int,
    val skipped: Int,
) {
    val isEmpty: Boolean get() = added == 0 && updated == 0 && trackers.none { it.existing == null || it.adoptAppearance }
}

/**
 * JSON export/import via org.json (no Gson/Moshi). Pure: no Android or database access,
 * so round-tripping and merge planning are unit-testable on the JVM.
 */
object BackupCodec {
    const val SCHEMA = 1

    fun encode(trackers: List<Tracker>, entries: List<Entry>, exportedAt: Long): String {
        val byTracker = entries.groupBy { it.trackerId }
        val trackerArray = JSONArray()
        for (t in trackers.sortedWith(compareBy({ it.position }, { it.id }))) {
            val entryArray = JSONArray()
            for (e in byTracker[t.id].orEmpty().sortedBy { it.dayIndex }) {
                entryArray.put(
                    JSONObject()
                        .put("day", e.dayIndex)
                        .put("level", e.level)
                        .put("note", e.note ?: JSONObject.NULL)
                        .put("updatedAt", e.updatedAt),
                )
            }
            trackerArray.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("paletteId", t.paletteId)
                    .put("labels", JSONArray(t.labels))
                    .put("archived", t.archived)
                    .put("entries", entryArray),
            )
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("exportedAt", exportedAt)
            .put("trackers", trackerArray)
            .toString(2)
    }

    /** Parses [json]. Entries with an out-of-range level are dropped; notes are clipped to 140. */
    fun decode(json: String): Backup {
        try {
            val root = JSONObject(json)
            val schema = root.getInt("schema")
            if (schema > SCHEMA) throw BackupFormatException("Unsupported backup schema $schema")
            val trackersJson = root.getJSONArray("trackers")
            val trackers = List(trackersJson.length()) { i -> decodeTracker(trackersJson.getJSONObject(i)) }
            return Backup(schema, root.optLong("exportedAt", 0L), trackers)
        } catch (e: JSONException) {
            throw BackupFormatException("Not a Pixels backup", e)
        }
    }

    private fun decodeTracker(o: JSONObject): BackupTracker {
        val name = o.getString("name").trim()
        if (name.isEmpty()) throw BackupFormatException("Tracker without a name")
        val labelsJson = o.getJSONArray("labels")
        if (labelsJson.length() != Level.COUNT) throw BackupFormatException("Tracker '$name' needs ${Level.COUNT} labels")
        val entriesJson = o.optJSONArray("entries") ?: JSONArray()
        val entries = ArrayList<BackupEntry>(entriesJson.length())
        for (i in 0 until entriesJson.length()) {
            val e = entriesJson.getJSONObject(i)
            val level = e.getInt("level")
            if (!Level.isValid(level)) continue
            val note = if (e.isNull("note")) null else e.getString("note").take(Entry.MAX_NOTE_LENGTH)
            entries += BackupEntry(
                day = e.getLong("day"),
                level = level,
                note = note?.takeIf { it.isNotBlank() },
                updatedAt = e.optLong("updatedAt", 0L),
            )
        }
        return BackupTracker(
            id = o.optLong("id", 0L),
            name = name,
            paletteId = o.optString("paletteId", Tracker.DEFAULT_PALETTE_ID),
            labels = List(Level.COUNT) { labelsJson.getString(it) },
            archived = o.optBoolean("archived", false),
            entries = entries,
        )
    }

    /**
     * Merge, not replace. Trackers match by name (trimmed, case-insensitive); entries match by
     * (tracker, day); a conflict goes to the newer `updatedAt`. Identical rows count as skipped.
     */
    fun plan(backup: Backup, existingTrackers: List<Tracker>, existingEntries: List<Entry>): ImportPlan {
        val trackersByName = existingTrackers.associateBy { normalise(it.name) }
        val entriesByTracker = existingEntries.groupBy { it.trackerId }
            .mapValues { (_, list) -> list.associateBy { it.dayIndex } }

        // Two backup trackers with the same name are merged into one.
        val sources = backup.trackers.groupBy { normalise(it.name) }.map { (_, group) ->
            group.first().copy(
                entries = group.flatMap { it.entries }
                    .groupBy { it.day }
                    .map { (_, sameDay) -> sameDay.maxBy { it.updatedAt } }
                    .sortedBy { it.day },
            )
        }

        var added = 0
        var updated = 0
        var skipped = 0
        val imports = sources.map { source ->
            val existing = trackersByName[normalise(source.name)]
            val local = existing?.let { entriesByTracker[it.id] }.orEmpty()
            val writes = source.entries.filter { incoming ->
                val current = local[incoming.day]
                when {
                    current == null -> { added++; true }
                    incoming.updatedAt > current.updatedAt &&
                        (incoming.level != current.level || incoming.note != current.note) -> { updated++; true }
                    else -> { skipped++; false }
                }
            }
            TrackerImport(
                existing = existing,
                source = source,
                adoptAppearance = existing != null && local.isEmpty() &&
                    (existing.paletteId != source.paletteId || existing.labels != source.labels),
                writes = writes,
            )
        }
        return ImportPlan(imports, added, updated, skipped)
    }

    private fun normalise(name: String) = name.trim().lowercase(Locale.ROOT)
}

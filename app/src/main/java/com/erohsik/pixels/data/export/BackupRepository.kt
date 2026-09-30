package com.erohsik.pixels.data.export

import android.content.ContentValues
import android.content.ContentResolver
import android.net.Uri
import com.erohsik.pixels.data.EntryDao
import com.erohsik.pixels.data.PixelsDbHelper
import com.erohsik.pixels.data.TrackerDao
import com.erohsik.pixels.data.inTransaction
import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Tracker
import com.erohsik.pixels.domain.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Moves backups between SAF documents and the database. */
class BackupRepository(
    private val helper: PixelsDbHelper,
    private val trackerDao: TrackerDao,
    private val entryDao: EntryDao,
    private val resolver: ContentResolver,
) {

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val json = BackupCodec.encode(
            trackers = trackerDao.queryAll(),
            entries = entryDao.exportAllBlocking(),
            exportedAt = System.currentTimeMillis(),
        )
        // "wt" truncates an existing file; some providers only support plain "w".
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri)
            ?: error("Cannot open $uri for writing")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }

    /** Reads and plans an import without writing anything, so the user can confirm the summary. */
    suspend fun preview(uri: Uri): ImportPlan = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri) ?: error("Cannot open $uri for reading")
        val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        BackupCodec.plan(BackupCodec.decode(json), trackerDao.queryAll(), entryDao.exportAllBlocking())
    }

    /** Applies [plan] in a single transaction: either everything lands or nothing does. */
    suspend fun apply(plan: ImportPlan) = withContext(Dispatchers.IO) {
        val today = DateUtils.today()
        helper.writableDatabase.inTransaction {
            for (item in plan.trackers) {
                val source = item.source
                val trackerId = item.existing?.id ?: trackerDao.insertBlocking(
                    this,
                    Tracker(
                        name = source.name,
                        position = 0,
                        createdDay = source.entries.minOfOrNull { it.day }?.coerceAtMost(today) ?: today,
                        paletteId = source.paletteId,
                        labels = source.labels,
                        archived = source.archived,
                    ),
                )
                if (item.adoptAppearance) {
                    val values = ContentValues().apply {
                        put("palette_id", source.paletteId)
                        source.labels.forEachIndexed { i, label -> put("label_${i + 1}", label) }
                    }
                    update(PixelsDbHelper.T_TRACKERS, values, "id = ?", arrayOf(trackerId.toString()))
                }
                for (e in item.writes) {
                    entryDao.upsertBlocking(this, Entry(trackerId, e.day, e.level, e.note, e.updatedAt))
                }
            }
        }
        helper.notifyChanged()
    }
}

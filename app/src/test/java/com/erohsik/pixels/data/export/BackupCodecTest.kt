package com.erohsik.pixels.data.export

import com.erohsik.pixels.data.model.Entry
import com.erohsik.pixels.data.model.Tracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private val mood = Tracker(1, "Mood", 0, 20000, "warmth", listOf("Rough", "Low", "Okay", "Good", "Great"))
    private val sleep = Tracker(2, "Sleep", 1, 20100, "mono", listOf("1", "2", "3", "4", "5"), archived = true)
    private val entries = listOf(
        Entry(1, 20385, 4, null, 1_791_820_800_000),
        Entry(1, 20386, 2, "Rain all day — \"quiet\" ☔", 1_791_820_900_000),
        Entry(2, 20385, 5, "slept well", 1_791_821_000_000),
    )

    @Test
    fun `encode then decode round-trips every field`() {
        val json = BackupCodec.encode(listOf(sleep, mood), entries, exportedAt = 123L)
        val backup = BackupCodec.decode(json)
        assertEquals(BackupCodec.SCHEMA, backup.schema)
        assertEquals(123L, backup.exportedAt)
        assertEquals(listOf("Mood", "Sleep"), backup.trackers.map { it.name }) // position order
        val m = backup.trackers[0]
        assertEquals("warmth", m.paletteId)
        assertEquals(mood.labels, m.labels)
        assertFalse(m.archived)
        assertEquals(
            listOf(BackupEntry(20385, 4, null, 1_791_820_800_000), BackupEntry(20386, 2, "Rain all day — \"quiet\" ☔", 1_791_820_900_000)),
            m.entries,
        )
        assertTrue(backup.trackers[1].archived)
        assertEquals("slept well", backup.trackers[1].entries.single().note)
    }

    @Test
    fun `decode matches the documented format`() {
        val json = """
            { "schema": 1, "exportedAt": 1791820800000,
              "trackers": [ { "id": 1, "name": "Mood", "paletteId": "calm",
                "labels": ["Rough","Low","Okay","Good","Great"],
                "entries": [ { "day": 20385, "level": 4, "note": null, "updatedAt": 1791820800000 } ] } ] }
        """.trimIndent()
        val t = BackupCodec.decode(json).trackers.single()
        assertEquals("calm", t.paletteId)
        assertEquals(BackupEntry(20385, 4, null, 1791820800000), t.entries.single())
    }

    @Test
    fun `decode drops invalid levels and clips notes`() {
        val long = "x".repeat(200)
        val json = """
            { "schema": 1, "trackers": [ { "name": "Mood", "paletteId": "calm",
              "labels": ["a","b","c","d","e"],
              "entries": [ { "day": 1, "level": 0, "updatedAt": 1 }, { "day": 2, "level": 6, "updatedAt": 1 },
                           { "day": 3, "level": 3, "note": "$long", "updatedAt": 1 },
                           { "day": 4, "level": 3, "note": "   ", "updatedAt": 1 } ] } ] }
        """.trimIndent()
        val t = BackupCodec.decode(json).trackers.single()
        assertEquals(listOf(3L, 4L), t.entries.map { it.day })
        assertEquals(Entry.MAX_NOTE_LENGTH, t.entries[0].note!!.length)
        assertNull(t.entries[1].note)
    }

    @Test(expected = BackupFormatException::class)
    fun `decode rejects non-json`() {
        BackupCodec.decode("not json")
    }

    @Test(expected = BackupFormatException::class)
    fun `decode rejects wrong label count`() {
        BackupCodec.decode("""{ "schema": 1, "trackers": [ { "name": "M", "labels": ["a"], "entries": [] } ] }""")
    }

    @Test(expected = BackupFormatException::class)
    fun `decode rejects a newer schema`() {
        BackupCodec.decode("""{ "schema": 2, "trackers": [] }""")
    }

    @Test
    fun `plan merges by name and newer updatedAt`() {
        val local = listOf(
            Entry(7, 20385, 3, null, 100), // older than backup -> update
            Entry(7, 20386, 2, "mine", 999_999_999_999_999), // newer locally -> skip
        )
        val localMood = mood.copy(id = 7, name = " mood ")
        val backup = BackupCodec.decode(BackupCodec.encode(listOf(mood, sleep), entries, 0))
        val plan = BackupCodec.plan(backup, listOf(localMood), local)

        assertEquals(1, plan.added) // Sleep is new, with one entry
        assertEquals(1, plan.updated)
        assertEquals(1, plan.skipped)
        val moodImport = plan.trackers.first { it.source.name == "Mood" }
        assertEquals(localMood, moodImport.existing)
        assertFalse(moodImport.adoptAppearance)
        assertEquals(listOf(20385L), moodImport.writes.map { it.day })
        val sleepImport = plan.trackers.first { it.source.name == "Sleep" }
        assertNull(sleepImport.existing)
        assertEquals(1, sleepImport.writes.size)
    }

    @Test
    fun `identical rows are skipped even when newer`() {
        val local = listOf(Entry(1, 20385, 4, null, 1))
        val backup = BackupCodec.decode(BackupCodec.encode(listOf(mood), entries.take(1), 0))
        val plan = BackupCodec.plan(backup, listOf(mood), local)
        assertEquals(0, plan.added)
        assertEquals(0, plan.updated)
        assertEquals(1, plan.skipped)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `export, wipe, import reproduces the data`() {
        val json = BackupCodec.encode(listOf(mood, sleep), entries, 0)
        // After a wipe the app has only the seeded, empty Mood tracker with default looks.
        val seeded = Tracker(1, "Mood", 0, 20500, Tracker.DEFAULT_PALETTE_ID, listOf("Rough", "Low", "Okay", "Good", "Great"))
        val plan = BackupCodec.plan(BackupCodec.decode(json), listOf(seeded), emptyList())
        assertEquals(entries.size, plan.added)
        assertEquals(0, plan.updated)
        assertEquals(0, plan.skipped)
        val moodImport = plan.trackers.first { it.existing != null }
        assertTrue("empty local tracker adopts the backup's palette", moodImport.adoptAppearance)
        assertEquals("warmth", moodImport.source.paletteId)

        // Simulate applying the plan and re-exporting: same trackers, same entries.
        var nextId = 10L
        val appliedTrackers = plan.trackers.map { item ->
            val base = item.existing ?: Tracker(nextId++, item.source.name, 1, 0, item.source.paletteId, item.source.labels, item.source.archived)
            if (item.adoptAppearance) base.copy(paletteId = item.source.paletteId, labels = item.source.labels) else base
        }
        val appliedEntries = plan.trackers.zip(appliedTrackers).flatMap { (item, t) ->
            item.writes.map { Entry(t.id, it.day, it.level, it.note, it.updatedAt) }
        }
        val again = BackupCodec.decode(BackupCodec.encode(appliedTrackers, appliedEntries, 0))
        val original = BackupCodec.decode(json)
        assertEquals(
            original.trackers.map { it.copy(id = 0) },
            again.trackers.map { it.copy(id = 0) },
        )
    }

    @Test
    fun `duplicate tracker names in a backup merge into one`() {
        val a = mood.copy(id = 1)
        val b = mood.copy(id = 2, position = 1)
        val json = BackupCodec.encode(
            listOf(a, b),
            listOf(Entry(1, 5, 1, null, 10), Entry(2, 5, 3, null, 20), Entry(2, 6, 2, null, 1)),
            0,
        )
        val plan = BackupCodec.plan(BackupCodec.decode(json), emptyList(), emptyList())
        val only = plan.trackers.single()
        assertEquals(listOf(5L to 3, 6L to 2), only.writes.map { it.day to it.level })
    }
}

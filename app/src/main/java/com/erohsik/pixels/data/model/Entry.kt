package com.erohsik.pixels.data.model

data class Entry(
    val trackerId: Long,
    /** `LocalDate.toEpochDay()`. Never a formatted string, never wall-clock millis. */
    val dayIndex: Long,
    val level: Int,
    val note: String?,
    /** Epoch millis of the last write; used to resolve import conflicts. */
    val updatedAt: Long,
) {
    companion object {
        const val MAX_NOTE_LENGTH = 140
    }
}

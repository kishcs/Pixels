package com.erohsik.pixels.data.model

data class Tracker(
    val id: Long = 0,
    val name: String,
    val position: Int,
    val createdDay: Long,
    val paletteId: String,
    /** Exactly five labels, ordered level 1..5. */
    val labels: List<String>,
    val archived: Boolean = false,
) {
    init {
        require(labels.size == Level.COUNT) { "A tracker needs exactly ${Level.COUNT} labels" }
    }

    fun label(level: Int): String = labels[level - 1]

    companion object {
        /** Key of the palette new trackers start with; must exist in `Palettes`. */
        const val DEFAULT_PALETTE_ID = "calm"
    }
}

package com.erohsik.pixels.ui.palette

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.erohsik.pixels.R
import com.erohsik.pixels.data.model.Level
import com.erohsik.pixels.data.model.Tracker

/**
 * Five colours ordered level 1 → 5. Grid colours are fixed: identical in light and dark theme
 * and never touched by dynamic colour. Only the background around them changes.
 *
 * ASSUMPTION: the display name is a string resource rather than a raw String, so it can live
 * in strings.xml like every other user-visible word.
 */
data class Palette(
    val id: String,
    @param:StringRes val nameRes: Int,
    val colors: List<Color>,
) {
    init {
        require(colors.size == Level.COUNT) { "Palette $id needs exactly ${Level.COUNT} colours" }
    }

    fun color(level: Int): Color = colors[level - 1]
}

/**
 * Built-in palettes. Each was designed in CIE LCh so adjacent levels differ by at least 15 in
 * L* (checked in PalettesTest); a grid where two neighbouring levels look alike is useless.
 */
object Palettes {
    /** Default for mood. Low levels are cool blues, never red. L*: 36, 55, 88, 72, 57. */
    val Calm = Palette(
        id = "calm",
        nameRes = R.string.palette_calm,
        colors = listOf(Color(0xFF375870), Color(0xFF6B8899), Color(0xFFECDAC3), Color(0xFFD8A86A), Color(0xFFC1773B)),
    )

    /** Deep indigo → cream → terracotta. L*: 26, 46, 92, 72, 52. */
    val Warmth = Palette(
        id = "warmth",
        nameRes = R.string.palette_warmth,
        colors = listOf(Color(0xFF2D3A6E), Color(0xFF6C6A8B), Color(0xFFF2E7D1), Color(0xFFE3A285), Color(0xFFB9654E)),
    )

    /** Slate → sage → deep green. L*: 44, 61, 79, 59, 38. */
    val Forest = Palette(
        id = "forest",
        nameRes = R.string.palette_forest,
        colors = listOf(Color(0xFF5C6A74), Color(0xFF809896), Color(0xFFB8C9AA), Color(0xFF689A6D), Color(0xFF316440)),
    )

    /** Five steps of one blue, light to dark, for workout/habit trackers. L*: 86, 70, 54, 38, 22. */
    val Mono = Palette(
        id = "mono",
        nameRes = R.string.palette_mono,
        colors = listOf(Color(0xFFCCD7F1), Color(0xFF96ACD6), Color(0xFF6082B8), Color(0xFF2F5B90), Color(0xFF0C365E)),
    )

    /** The conventional red → green ramp. Available, never the default. L*: 46, 65, 86, 70, 51. */
    val Classic = Palette(
        id = "classic",
        nameRes = R.string.palette_classic,
        colors = listOf(Color(0xFFBF413F), Color(0xFFDF8948), Color(0xFFF0D75D), Color(0xFF86B963), Color(0xFF478845)),
    )

    val all: List<Palette> = listOf(Calm, Warmth, Forest, Mono, Classic)

    val Default: Palette = all.first { it.id == Tracker.DEFAULT_PALETTE_ID }

    /** Unknown ids (for example from a newer backup) fall back to the default palette. */
    fun byId(id: String): Palette = all.firstOrNull { it.id == id } ?: Default
}

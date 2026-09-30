package com.erohsik.pixels.ui.palette

import androidx.compose.ui.graphics.toArgb
import com.erohsik.pixels.data.model.Tracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow

class PalettesTest {

    /** CIE L* (D65) of an sRGB colour packed as ARGB. */
    private fun lightness(argb: Int): Double {
        fun lin(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val y = 0.2126 * lin(argb shr 16 and 0xFF) + 0.7152 * lin(argb shr 8 and 0xFF) + 0.0722 * lin(argb and 0xFF)
        val f = if (y > 216.0 / 24389) cbrt(y) else (24389.0 / 27 * y + 16) / 116
        return 116 * f - 16
    }

    @Test
    fun `adjacent levels differ by at least 15 in lightness`() {
        for (p in Palettes.all) {
            val l = p.colors.map { lightness(it.toArgb()) }
            for (i in 0 until l.size - 1) {
                assertTrue("${p.id}: levels ${i + 1} and ${i + 2} differ by ${abs(l[i + 1] - l[i])}", abs(l[i + 1] - l[i]) >= 15.0)
            }
        }
    }

    @Test
    fun `every palette has five opaque colours and a unique id`() {
        assertTrue(Palettes.all.size in 5..6)
        assertEquals(Palettes.all.size, Palettes.all.map { it.id }.toSet().size)
        for (p in Palettes.all) {
            assertEquals(5, p.colors.size)
            assertTrue(p.colors.all { it.alpha == 1f })
        }
    }

    @Test
    fun `calm is the default and does not start with red`() {
        assertEquals(Tracker.DEFAULT_PALETTE_ID, Palettes.Default.id)
        assertEquals("calm", Palettes.Default.id)
        val low = Palettes.Calm.colors[0].toArgb()
        assertTrue("level 1 should be cool: blue > red", (low and 0xFF) > (low shr 16 and 0xFF))
    }

    @Test
    fun `unknown ids fall back to the default`() {
        assertEquals(Palettes.Default, Palettes.byId("from-the-future"))
        assertEquals(Palettes.Forest, Palettes.byId("forest"))
    }
}

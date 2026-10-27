package com.prirai.android.nira.theme

import org.junit.Assert.*
import org.junit.Test

class TabGroupTonesTest {
    @Test fun ungroupedIsAlwaysBlueAndOpaque() {
        val ungrouped = TabGroupTones.forGroup(null)
        val blue = TabGroupTones.forGroup(0xFF2196F3.toInt())
        assertEquals(blue, ungrouped)
        assertEquals(0xFF2196F3.toInt(), ungrouped.accent)
        assertEquals(0xFF, (ungrouped.tabStart ushr 24) and 0xFF)
        assertEquals(0xFF, (ungrouped.headerStart ushr 24) and 0xFF)
    }

    @Test fun chosenYellowDrivesHeaderEveryTabAndSelection() {
        val tones = TabGroupTones.forGroup(0xFFFFC107.toInt())
        assertEquals(0xFFFFC107.toInt(), tones.accent)
        assertNotEquals(tones.headerStart, tones.tabStart)
        assertNotEquals(tones.tabStart, tones.activeStart)
        assertTrue(((tones.tabStart ushr 16) and 0xFF) > (tones.tabStart and 0xFF))
    }

    @Test fun changingColorChangesEveryDerivedBackground() {
        val before = TabGroupTones.forGroup(0xFFF44336.toInt())
        val after = TabGroupTones.forGroup(0xFF4CAF50.toInt())
        assertNotEquals(before.accent, after.accent)
        assertNotEquals(before.headerStart, after.headerStart)
        assertNotEquals(before.headerEnd, after.headerEnd)
        assertNotEquals(before.tabStart, after.tabStart)
        assertNotEquals(before.tabEnd, after.tabEnd)
        assertNotEquals(before.activeStart, after.activeStart)
        assertNotEquals(before.activeEnd, after.activeEnd)
    }

    @Test fun fullPaletteUsesOpaqueBackgroundsAndPreservesAccents() {
        for (color in listOf(
            0xFF2196F3.toInt(), 0xFFF44336.toInt(), 0xFF4CAF50.toInt(),
            0xFFFF9800.toInt(), 0xFF9C27B0.toInt(), 0xFFE91E63.toInt(),
            0xFF009688.toInt(), 0xFFFFC107.toInt(), 0xFF3F51B5.toInt(),
            0xFF00BCD4.toInt(), 0xFFCDDC39.toInt(), 0xFF795548.toInt()
        )) {
            val tone = TabGroupTones.forGroup(color)
            assertEquals(color, tone.accent)
            for (background in listOf(tone.headerStart, tone.headerEnd,
                tone.tabStart, tone.tabEnd, tone.activeStart, tone.activeEnd)) {
                assertEquals(0xFF, (background ushr 24) and 0xFF)
            }
        }
    }
}

package com.prirai.android.nira.browser.tabs

import com.prirai.android.nira.browser.tabs.compose.*
import org.junit.Assert.*
import org.junit.Test

class DragPlacementTest {
    @Test fun droppingOutsideViewportCancelsRatherThanMovingToNearestCard() {
        val rows = listOf(DropRow("single_tab_a", -20, 46), DropRow("single_tab_b", 26, 46))
        assertNull(DragPlacement.sideTarget(-1f, 0, 70, rows))
        assertNull(DragPlacement.sideTarget(70f, 0, 70, rows))
        assertNull(DragPlacement.sideTarget(Float.NaN, 0, 70, rows))
        assertNull(DragPlacement.sideTarget(20f, 0, 70, rows, x = -1f, viewportWidth = 300f))
        assertNull(DragPlacement.sideTarget(20f, 0, 70, rows, x = 300f, viewportWidth = 300f))
    }
    @Test fun scrolledPartiallyVisibleRowsUseViewportOffsetsAndBothHalves() {
        val rows = listOf(DropRow("group_tab_a", -20, 46), DropRow("group_tab_b", 26, 46))
        assertEquals(ResolvedDrop("group_tab_a", false), DragPlacement.sideTarget(1f, 0, 70, rows))
        assertEquals(ResolvedDrop("group_tab_a", true), DragPlacement.sideTarget(20f, 0, 70, rows))
        assertEquals(ResolvedDrop("group_tab_b", false), DragPlacement.sideTarget(30f, 0, 70, rows))
        assertEquals(ResolvedDrop("group_tab_b", true), DragPlacement.sideTarget(69f, 0, 70, rows))
    }
    @Test fun toolbarAndGroupMovesLandBeforeTheIndicatedAnchorInBothDirections() {
        val items = listOf("new", "next", "previous", "settings", "refresh")
        for (from in items.indices) for (boundary in 0..items.size) {
            val moved = items[from]
            val anchor = items.getOrNull(boundary)
            val result = items.toMutableList().apply {
                removeAt(from)
                add(DragPlacement.destination(from, boundary, items.size), moved)
            }
            val expected = items.toMutableList().apply {
                remove(moved)
                val position = if (anchor == moved) from else anchor?.let { indexOf(it) } ?: size
                add(position.coerceIn(0, size), moved)
            }
            assertEquals("from=$from boundary=$boundary", expected, result)
        }
    }
}

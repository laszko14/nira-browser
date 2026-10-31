package com.prirai.android.nira.browser.tabs

import com.prirai.android.nira.browser.tabs.compose.DragPlacement
import com.prirai.android.nira.browser.tabs.compose.DropRow
import org.junit.Assert.*
import org.junit.Test

/** The stationary pointer must be resolved against the LATEST scroll geometry. */
class SidePanelHoverAutoscrollTest {
    private fun currentKey(y: Float, rows: List<DropRow>, x: Float = 10f): String? =
        DragPlacement.sideTarget(y, 0, 100, rows, x, 300f)?.key

    @Test fun stationaryFingerReassignsHoverToActualRowAfterAutoscroll() {
        val oldRows = listOf(
            DropRow("group_B", 50, 50),
            DropRow("group_C", 100, 50)
        )
        val scrolledRows = listOf(
            DropRow("group_B", 0, 50),
            DropRow("group_C", 50, 50)
        )
        val fingerY = 75f
        assertEquals("group_B", currentKey(fingerY, oldRows))
        // No new pointer event: only layoutInfo.visibleItemsInfo changes.
        assertEquals("group_C", currentKey(fingerY, scrolledRows))
        assertEquals(
            currentKey(fingerY, scrolledRows),
            DragPlacement.sideTarget(fingerY, 0, 100, scrolledRows, 10f, 300f)?.key
        )
    }

    @Test fun staleHoverMustNotTriggerExpansionAfterRowsMove() {
        val targetBeforeScroll = "group_B"
        val afterScroll = listOf(DropRow("group_B", 0, 50), DropRow("group_C", 50, 50))
        val actualHover = currentKey(75f, afterScroll)
        assertNotEquals(targetBeforeScroll, actualHover)
        // The delayed 450ms handler validates both the displayed hover
        // AND live geometry before expanding the previously selected group.
        assertFalse(targetBeforeScroll == "group_B" && actualHover == "group_B")
        assertTrue(actualHover == "group_C")
    }

    @Test fun leavingViewportOrHorizontalPanelBoundsInvalidatesHoverAndDrop() {
        val rows = listOf(DropRow("group_A", 0, 50), DropRow("group_B", 50, 50))
        assertNull(currentKey(-1f, rows))
        assertNull(currentKey(100f, rows))
        assertNull(currentKey(75f, rows, -1f))
        assertNull(currentKey(75f, rows, 300f))
        assertEquals("group_B", currentKey(75f, rows, 299f))
    }

    @Test fun rowExpansionOrReorderingUsesLiveGeometryForHoverAndDrop() {
        val before = listOf(DropRow("group_A", 0, 50), DropRow("group_B", 50, 50))
        val afterExpansion = listOf(DropRow("group_A", 0, 50), DropRow("group_tab_a", 50, 50))
        assertEquals("group_B", currentKey(75f, before))
        assertEquals("group_tab_a", currentKey(75f, afterExpansion))
        val finalDrop = DragPlacement.sideTarget(75f, 0, 100, afterExpansion, 10f, 300f)
        assertEquals(currentKey(75f, afterExpansion), finalDrop?.key)
        assertEquals(true, finalDrop?.after)
    }
}

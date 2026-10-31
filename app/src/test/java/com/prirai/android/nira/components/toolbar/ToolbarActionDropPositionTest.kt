package com.prirai.android.nira.components.toolbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarActionDropPositionTest {
    private val original = listOf("new", "close", "settings", "back")

    @Test fun droppingBelowLastRowMovesDirectlyToEnd() {
        assertEquals(listOf("close", "settings", "back", "new"),
            ToolbarActionDropPosition.move(original, "new", "back", true))
    }

    @Test fun droppingBelowAdjacentRowMovesOnePositionDown() {
        assertEquals(listOf("close", "new", "settings", "back"),
            ToolbarActionDropPosition.move(original, "new", "close", true))
    }

    @Test fun droppingAboveFirstRowAndMovingBackwards() {
        assertEquals(listOf("back", "new", "close", "settings"),
            ToolbarActionDropPosition.move(original, "back", "new", false))
        assertEquals(listOf("new", "settings", "close", "back"),
            ToolbarActionDropPosition.move(original, "settings", "close", false))
    }

    @Test fun noOpOnSelfDropInvalidIdsAndAlreadyAdjacentPlacement() {
        assertEquals(original, ToolbarActionDropPosition.move(original, "close", "close", true))
        assertEquals(original, ToolbarActionDropPosition.move(original, "unknown", "back", true))
        assertEquals(original, ToolbarActionDropPosition.move(original, "close", "unknown", false))
        assertEquals(original, ToolbarActionDropPosition.move(original, "close", "new", true))
        assertEquals(original, ToolbarActionDropPosition.move(original, "new", "close", false))
    }

    @Test fun midpointIsIncludedInAfterHalfAndZeroHeightIsSafe() {
        assertFalse(ToolbarActionDropPosition.isAfter(22f, 46))
        assertTrue(ToolbarActionDropPosition.isAfter(23f, 46))
        assertTrue(ToolbarActionDropPosition.isAfter(46f, 46))
        assertFalse(ToolbarActionDropPosition.isAfter(0f, 0))
    }
}

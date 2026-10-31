package com.prirai.android.nira.browser.tabs.compose

import org.junit.Assert.*
import org.junit.Test

class SwipePillGestureOwnerTest {
    private fun owner() = SwipePillGestureOwner(10f, 40f, 80f)

    @Test fun upwardSwipeClosesExactlyOnceAfterRelease() {
        val g = owner()
        g.begin(7)
        assertFalse(g.move(7, 0f, -5f))
        assertTrue(g.move(7, 0f, -60f))
        assertEquals(-60f, g.offsetY)
        assertEquals(SwipePillOutcome.CLOSE, g.release(7))
        assertEquals(0f, g.offsetY)
        assertEquals(SwipePillOutcome.NONE, g.release(7))
    }

    @Test fun downwardSwipeOpensMenuAndShortSwipeDoesNothing() {
        val g = owner()
        g.begin(1)
        assertTrue(g.move(1, 0f, 60f))
        assertEquals(SwipePillOutcome.MENU, g.release(1))
        g.begin(2)
        assertTrue(g.move(2, 0f, 20f))
        assertEquals(SwipePillOutcome.NONE, g.release(2))
    }

    @Test fun dndTakeoverCancelsClaimedSwipeWithoutAnyAction() {
        val g = owner()
        g.begin(3)
        assertTrue(g.move(3, 0f, -60f))
        assertFalse(g.move(3, 0f, -5f, dragInProgress = true))
        assertFalse(g.isActive)
        assertEquals(0f, g.offsetY)
        assertEquals(SwipePillOutcome.NONE, g.release(3))
    }

    @Test fun alreadyConsumedMoveAndReleaseNeverClose() {
        val g = owner()
        g.begin(4)
        assertFalse(g.move(4, 0f, -60f, consumed = true))
        assertEquals(SwipePillOutcome.NONE, g.release(4))
        g.begin(5)
        assertTrue(g.move(5, 0f, -60f))
        assertEquals(SwipePillOutcome.NONE, g.release(5, consumed = true))
    }

    @Test fun secondFingerPoisonsEntireGestureUntilFreshDown() {
        val g = owner()
        g.begin(6)
        assertTrue(g.move(6, 0f, -60f))
        assertFalse(g.move(6, 0f, -1f, otherPointerPressed = true))
        assertFalse(g.move(6, 0f, -60f))
        assertEquals(SwipePillOutcome.NONE, g.release(6))
        g.begin(8)
        assertTrue(g.move(8, 0f, -60f))
        assertEquals(SwipePillOutcome.CLOSE, g.release(8))
    }

    @Test fun pointerExchangeCannotStealOwnership() {
        val g = owner()
        g.begin(9)
        assertFalse(g.move(10, 0f, 60f))
        assertEquals(SwipePillOutcome.NONE, g.release(10))
        assertFalse(g.isActive)
        assertEquals(0f, g.offsetY)
    }

    @Test fun horizontalDragDoesNotTriggerVerticalAction() {
        val g = owner()
        g.begin(11)
        assertFalse(g.move(11, 15f, 2f))
        assertFalse(g.isActive)
        assertEquals(SwipePillOutcome.NONE, g.release(11))
    }

    @Test fun explicitCancellationClearsOffsetAndRelease() {
        val g = owner()
        g.begin(12)
        assertTrue(g.move(12, 0f, -70f))
        g.cancel()
        assertEquals(0f, g.offsetY)
        assertEquals(SwipePillOutcome.NONE, g.release(12))
    }

    @Test fun currentDndOwnerForbidsDropTimeActionAndNewGesturesWork() {
        val g = owner()
        g.begin(13)
        assertTrue(g.move(13, 0f, -70f))
        assertEquals(SwipePillOutcome.NONE, g.release(13, dragInProgress = true))
        g.begin(14)
        assertTrue(g.move(14, 0f, 70f))
        assertEquals(SwipePillOutcome.MENU, g.release(14))
    }
}

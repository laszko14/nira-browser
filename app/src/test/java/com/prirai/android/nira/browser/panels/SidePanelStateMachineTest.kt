package com.prirai.android.nira.browser.panels

import org.junit.Assert.*
import org.junit.Test

class SidePanelStateMachineTest {
    @Test fun onePanelOwnsTheInteractionAndDuplicateStartsAreRejected() {
        val state = SidePanelStateMachine()
        assertEquals(SidePanelPhase.CLOSED, state.snapshot.phase)
        assertNull(state.snapshot.side)
        assertTrue(state.startDrag(SidePanelSide.LEFT))
        assertFalse(state.startDrag(SidePanelSide.LEFT))
        assertFalse(state.startDrag(SidePanelSide.RIGHT))
        assertFalse(state.startSettle(SidePanelSide.RIGHT, true))
        assertEquals(SidePanelSide.LEFT, state.snapshot.side)
    }

    @Test fun dragIsClampedAndInvalidValuesNeverContaminateState() {
        val state = SidePanelStateMachine()
        assertFalse(state.dragTo(0.5f))
        assertTrue(state.startDrag(SidePanelSide.RIGHT))
        assertTrue(state.dragTo(-100f))
        assertEquals(0f, state.snapshot.progress)
        assertTrue(state.dragTo(100f))
        assertEquals(1f, state.snapshot.progress)
        assertFalse(state.dragTo(Float.NaN))
        assertFalse(state.dragTo(Float.POSITIVE_INFINITY))
        assertEquals(1f, state.snapshot.progress)
    }

    @Test fun settlingMayBeInterruptedWithoutPositionJump() {
        val state = SidePanelStateMachine()
        assertTrue(state.startDrag(SidePanelSide.LEFT))
        assertTrue(state.dragTo(0.36f))
        assertTrue(state.startSettle(SidePanelSide.LEFT, true))
        assertTrue(state.settleTo(0.62f))
        assertTrue(state.startDrag(SidePanelSide.LEFT))
        assertEquals(SidePanelPhase.DRAGGING, state.snapshot.phase)
        assertEquals(0.62f, state.snapshot.progress)
        assertNull(state.snapshot.targetOpen)
        assertFalse(state.finishSettle(SidePanelSide.LEFT))
        assertTrue(state.startSettle(SidePanelSide.LEFT, false))
        assertTrue(state.finishSettle(SidePanelSide.LEFT))
        assertEquals(SidePanelPhase.CLOSED, state.snapshot.phase)
        assertNull(state.snapshot.side)
        assertEquals(0f, state.snapshot.progress)
    }

    @Test fun completionOnlyOnceAndOnlyForCorrectSide() {
        val state = SidePanelStateMachine()
        assertTrue(state.startSettle(SidePanelSide.RIGHT, true))
        assertFalse(state.startSettle(SidePanelSide.RIGHT, true))
        assertFalse(state.finishSettle(SidePanelSide.LEFT))
        assertTrue(state.settleTo(1f))
        assertTrue(state.finishSettle(SidePanelSide.RIGHT))
        assertFalse(state.finishSettle(SidePanelSide.RIGHT))
        assertEquals(SidePanelPhase.OPEN, state.snapshot.phase)
        assertEquals(1f, state.snapshot.progress)
        assertFalse(state.startSettle(SidePanelSide.RIGHT, true))
        assertTrue(state.startSettle(SidePanelSide.RIGHT, false))
        assertTrue(state.finishSettle(SidePanelSide.RIGHT))
        assertEquals(SidePanelPhase.CLOSED, state.snapshot.phase)
    }

    @Test fun resetClearsOwnershipEvenDuringIncompleteDragOrSettle() {
        val state = SidePanelStateMachine()
        assertTrue(state.startDrag(SidePanelSide.LEFT))
        assertTrue(state.dragTo(0.4f))
        state.reset()
        assertNull(state.snapshot.side)
        assertEquals(SidePanelPhase.CLOSED, state.snapshot.phase)
        assertTrue(state.startSettle(SidePanelSide.RIGHT, true))
        state.reset()
        assertFalse(state.finishSettle(SidePanelSide.RIGHT))
        assertTrue(state.startDrag(SidePanelSide.LEFT))
    }
}

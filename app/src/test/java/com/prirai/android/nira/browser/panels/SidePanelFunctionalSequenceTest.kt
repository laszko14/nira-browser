package com.prirai.android.nira.browser.panels

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure Kotlin end-to-end state sequences with an instantly controllable animation
 * driver. No Android SDK or mock GeckoView needed to validate ownership and Back.
 */
class SidePanelFunctionalSequenceTest {
    private class Driver : SidePanelAnimationDriver {
        data class Run(val from: Float, val to: Float, val frame: (Float) -> Unit,
                       val end: () -> Unit)
        var run: Run? = null
        var cancelled = 0
        override fun animate(from: Float, to: Float, durationMs: Long,
                             onFrame: (Float) -> Unit, onEnd: () -> Unit) {
            run = Run(from, to, onFrame, onEnd)
        }
        override fun cancel() {
            if (run != null) cancelled++
            run = null
        }
        fun finish() {
            val active = run ?: error("No pending animation")
            active.frame(active.to)
            active.end()
            run = null
        }
    }

    @Test fun eachSideOpensAndClosesWithoutRetainingOwnership() {
        val d = Driver()
        val c = SidePanelMotionController(d, 2f) { }
        for (side in listOf(SidePanelSide.LEFT, SidePanelSide.RIGHT, SidePanelSide.LEFT)) {
            assertTrue(c.beginDrag(side, 320))
            assertTrue(c.dragBy(if (side == SidePanelSide.LEFT) 230f else -230f))
            assertTrue(c.release(0f))
            assertEquals(true, c.snapshot.targetOpen)
            d.finish()
            assertEquals(SidePanelPhase.OPEN, c.snapshot.phase)
            assertEquals(side, c.snapshot.side)
            assertTrue(c.command(side, 320, false))
            d.finish()
            assertEquals(SidePanelPhase.CLOSED, c.snapshot.phase)
            assertNull(c.snapshot.side)
        }
    }

    @Test fun reverseClosingDoesNotHandOwnershipToOppositePanelMidAnimation() {
        val d = Driver()
        val c = SidePanelMotionController(d, 2f) { }
        assertTrue(c.command(SidePanelSide.LEFT, 300, true))
        d.run!!.frame(0.6f)
        val stale = d.run!!
        assertTrue(c.command(SidePanelSide.LEFT, 300, false))
        assertFalse(c.beginDrag(SidePanelSide.RIGHT, 300))
        stale.frame(1f)
        stale.end()
        assertEquals(SidePanelPhase.SETTLING, c.snapshot.phase)
        assertEquals(SidePanelSide.LEFT, c.snapshot.side)
        d.finish()
        assertTrue(c.beginDrag(SidePanelSide.RIGHT, 300))
    }

    @Test fun cancellingOpenAndClosedGesturesRestoresPriorTarget() {
        for (side in SidePanelSide.entries) {
            val d = Driver()
            val c = SidePanelMotionController(d, 2f) { }
            assertTrue(c.beginDrag(side, 400))
            c.dragBy(if (side == SidePanelSide.LEFT) 320f else -320f)
            assertTrue(c.cancelDrag())
            assertEquals(false, c.snapshot.targetOpen)
            d.finish()
            assertEquals(SidePanelPhase.CLOSED, c.snapshot.phase)
            assertTrue(c.command(side, 400, true))
            d.finish()
            assertTrue(c.beginDrag(side, 400))
            c.dragBy(if (side == SidePanelSide.LEFT) -300f else 300f)
            assertTrue(c.cancelDrag())
            assertEquals(true, c.snapshot.targetOpen)
            d.finish()
            assertEquals(SidePanelPhase.OPEN, c.snapshot.phase)
        }
    }

    @Test fun rotationAndFullscreenResetDoNotResurrectPreviousAnimation() {
        val d = Driver()
        val c = SidePanelMotionController(d, 3f) { }
        assertTrue(c.command(SidePanelSide.RIGHT, 400, true))
        d.run!!.frame(0.4f)
        val stale = d.run!!
        c.reset()
        assertEquals(SidePanelPhase.CLOSED, c.snapshot.phase)
        stale.frame(1f)
        stale.end()
        assertEquals(SidePanelPhase.CLOSED, c.snapshot.phase)
        assertNull(c.snapshot.side)
        assertTrue(c.beginDrag(SidePanelSide.LEFT, 250))
        c.dragBy(200f)
        c.reset()
        assertNull(c.snapshot.side)
        assertFalse(c.release(0f))
    }

    @Test fun widthResizingWhileOpenNeverChangesTheFractionOfVisibility() {
        val d = Driver()
        val c = SidePanelMotionController(d, 2f) { }
        assertTrue(c.command(SidePanelSide.LEFT, 290, true))
        d.finish()
        assertTrue(c.beginDrag(SidePanelSide.LEFT, 360))
        c.dragBy(-90f)
        assertEquals(0.75f, c.snapshot.progress, 0.0001f)
        assertTrue(c.release(0f))
        assertEquals(true, c.snapshot.targetOpen)
        d.finish()
        assertEquals(1f, c.snapshot.progress, 0.0001f)
    }
}

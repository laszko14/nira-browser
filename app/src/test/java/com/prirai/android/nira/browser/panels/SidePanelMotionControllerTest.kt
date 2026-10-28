package com.prirai.android.nira.browser.panels

import org.junit.Assert.*
import org.junit.Test

class SidePanelMotionControllerTest {
    private class Driver : SidePanelAnimationDriver {
        data class Run(val from: Float, val to: Float, val duration: Long,
                       val onFrame: (Float) -> Unit, val onEnd: () -> Unit)
        var current: Run? = null
        var previous: Run? = null
        var cancellations = 0
        override fun animate(from: Float, to: Float, durationMs: Long,
                             onFrame: (Float) -> Unit, onEnd: () -> Unit) {
            current = Run(from, to, durationMs, onFrame, onEnd)
        }
        override fun cancel() { current?.let { previous = it; cancellations++ }; current = null }
        fun frame(fraction: Float) { current!!.onFrame(fraction) }
        fun finish() { val run = current!!; run.onFrame(run.to); run.onEnd() }
    }

    @Test fun dragsTrackFingerInBothDirectionsAndRejectInvalidSizes() {
        for (side in SidePanelSide.entries) {
            val driver = Driver()
            val states = mutableListOf<SidePanelSnapshot>()
            val controller = SidePanelMotionController(driver, 2f, states::add)
            assertFalse(controller.beginDrag(side, 0))
            assertTrue(controller.beginDrag(side, 240))
            assertTrue(controller.dragBy(if (side == SidePanelSide.LEFT) 60f else -60f))
            assertEquals(0.25f, controller.snapshot.progress, 0.0001f)
            assertEquals(SidePanelPhase.DRAGGING, controller.snapshot.phase)
            assertTrue(controller.dragBy(if (side == SidePanelSide.LEFT) 400f else -400f))
            assertEquals(1f, controller.snapshot.progress, 0f)
            assertTrue(controller.dragBy(if (side == SidePanelSide.LEFT) -100f else 100f))
            assertEquals(0f, controller.snapshot.progress, 0f)
            assertFalse(controller.dragBy(Float.NaN))
            assertEquals(0f, controller.snapshot.progress, 0f)
            assertTrue(states.isNotEmpty())
        }
    }

    @Test fun slowReleaseUsesHalfwayThresholdAndFastFlingUsesDirection() {
        for (side in SidePanelSide.entries) {
            for ((progress, velocity, openExpected) in listOf(
                Triple(0.49f, 0f, false), Triple(0.50f, 0f, true),
                Triple(0.1f, if (side == SidePanelSide.LEFT) 900f else -900f, true),
                Triple(0.9f, if (side == SidePanelSide.LEFT) -900f else 900f, false)
            )) {
                val driver = Driver()
                val controller = SidePanelMotionController(driver, 2f) { }
                assertTrue(controller.beginDrag(side, 200))
                controller.dragBy(progress * 200f * (if (side == SidePanelSide.LEFT) 1f else -1f))
                assertTrue(controller.release(velocity))
                assertEquals(openExpected, controller.snapshot.targetOpen)
                driver.finish()
                assertEquals(if (openExpected) SidePanelPhase.OPEN else SidePanelPhase.CLOSED, controller.snapshot.phase)
                assertFalse(controller.release(0f))
            }
        }
    }

    @Test fun cancelRevertsToOriginalTargetAndNeverUsesFling() {
        val driver = Driver()
        val controller = SidePanelMotionController(driver, 2f) { }
        assertTrue(controller.beginDrag(SidePanelSide.LEFT, 200))
        controller.dragBy(190f)
        assertTrue(controller.cancelDrag())
        assertEquals(false, controller.snapshot.targetOpen)
        driver.finish()
        assertEquals(SidePanelPhase.CLOSED, controller.snapshot.phase)
        assertTrue(controller.command(SidePanelSide.LEFT, 200, true))
        driver.finish()
        assertTrue(controller.beginDrag(SidePanelSide.LEFT, 200))
        controller.dragBy(-170f)
        assertTrue(controller.cancelDrag())
        assertEquals(true, controller.snapshot.targetOpen)
        driver.finish()
        assertEquals(SidePanelPhase.OPEN, controller.snapshot.phase)
    }

    @Test fun interruptKeepsLastPixelAndStaleCallbacksCannotFinishNewTransition() {
        val driver = Driver()
        val controller = SidePanelMotionController(driver, 2f) { }
        assertTrue(controller.command(SidePanelSide.LEFT, 200, true))
        driver.frame(0.42f)
        val stale = driver.current!!
        assertTrue(controller.beginDrag(SidePanelSide.LEFT, 200))
        assertEquals(0.42f, controller.snapshot.progress, 0.00001f)
        controller.dragBy(-30f)
        assertEquals(0.27f, controller.snapshot.progress, 0.00001f)
        stale.onFrame(1f)
        stale.onEnd()
        assertEquals(SidePanelPhase.DRAGGING, controller.snapshot.phase)
        assertEquals(0.27f, controller.snapshot.progress, 0.00001f)
        assertTrue(controller.release(0f))
        val next = driver.current!!
        stale.onEnd()
        assertEquals(SidePanelPhase.SETTLING, controller.snapshot.phase)
        next.onEnd()
        assertEquals(SidePanelPhase.CLOSED, controller.snapshot.phase)
        next.onEnd()
        assertEquals(SidePanelPhase.CLOSED, controller.snapshot.phase)
        assertTrue(driver.cancellations > 0)
    }

    @Test fun programmaticReverseAndCompetingSideOwnershipAreDeterministic() {
        val driver = Driver()
        val controller = SidePanelMotionController(driver, 2f) { }
        assertTrue(controller.command(SidePanelSide.RIGHT, 250, true))
        driver.frame(0.4f)
        val stale = driver.current!!
        assertFalse(controller.command(SidePanelSide.LEFT, 250, true))
        assertTrue(controller.command(SidePanelSide.RIGHT, 250, false))
        assertEquals(0.4f, controller.snapshot.progress, 0.001f)
        stale.onEnd()
        assertEquals(SidePanelPhase.SETTLING, controller.snapshot.phase)
        driver.finish()
        assertEquals(SidePanelPhase.CLOSED, controller.snapshot.phase)
        assertTrue(controller.command(SidePanelSide.LEFT, 250, true))
        driver.finish()
        assertEquals(SidePanelPhase.OPEN, controller.snapshot.phase)
        assertFalse(controller.command(SidePanelSide.RIGHT, 250, true))
    }

    @Test fun resetCancelsAndGuardsCallbacksAgainstResurrection() {
        val driver = Driver()
        val states = mutableListOf<SidePanelSnapshot>()
        val controller = SidePanelMotionController(driver, 2f, states::add)
        assertTrue(controller.command(SidePanelSide.LEFT, 200, true))
        val stale = driver.current!!
        controller.reset()
        stale.onFrame(0.95f)
        stale.onEnd()
        assertEquals(SidePanelPhase.CLOSED, controller.snapshot.phase)
        assertNull(controller.snapshot.side)
        assertTrue(controller.beginDrag(SidePanelSide.RIGHT, 300))
        assertTrue(controller.dragBy(-150f))
        controller.reset()
        assertEquals(SidePanelPhase.CLOSED, controller.snapshot.phase)
        assertFalse(controller.release(0f))
        assertTrue(states.last().phase == SidePanelPhase.CLOSED)
    }

    @Test fun policyHonorsDensityBoundsAndEasingEndpoints() {
        assertFalse(SidePanelMotionPolicy.shouldOpen(SidePanelSide.LEFT, 0.1f, 799f, 2f))
        assertTrue(SidePanelMotionPolicy.shouldOpen(SidePanelSide.LEFT, 0.1f, 800f, 2f))
        assertTrue(SidePanelMotionPolicy.shouldOpen(SidePanelSide.RIGHT, 0.1f, -800f, 2f))
        assertTrue(SidePanelMotionPolicy.shouldOpen(SidePanelSide.RIGHT, 0.6f, Float.NaN, 2f))
        assertEquals(0L, SidePanelMotionPolicy.durationMs(0f, 200, 0f, 2f))
        assertEquals(224L, SidePanelMotionPolicy.durationMs(1f, 200, 0f, 2f))
        assertTrue(SidePanelMotionPolicy.durationMs(0.5f, 200, 0f, 2f) in 90L..223L)
        assertTrue(SidePanelMotionPolicy.durationMs(0.5f, 200, 1400f, 2f) <=
            SidePanelMotionPolicy.durationMs(0.5f, 200, 0f, 2f))
        assertEquals(0f, SidePanelMotionPolicy.easeOutCubic(0f), 0.0001f)
        assertEquals(1f, SidePanelMotionPolicy.easeOutCubic(1f), 0.0001f)
        assertTrue(SidePanelMotionPolicy.easeOutCubic(0.5f) > 0.5f)
    }

    @Test fun commandCanCloseDuringDragWithoutSnappingToZero() {
        val driver = Driver()
        val controller = SidePanelMotionController(driver, 2f) { }
        assertTrue(controller.beginDrag(SidePanelSide.LEFT, 300))
        assertTrue(controller.dragBy(180f))
        assertEquals(0.6f, controller.snapshot.progress, 0.0001f)
        assertTrue(controller.command(SidePanelSide.LEFT, 300, false))
        assertEquals(SidePanelPhase.SETTLING, controller.snapshot.phase)
        assertEquals(0.6f, driver.current!!.from, 0.0001f)
        assertEquals(0f, driver.current!!.to, 0.0001f)
        driver.finish()
        assertEquals(SidePanelPhase.CLOSED, controller.snapshot.phase)
    }


    @Test fun easingIsClampedMonotonicAndLessAbruptThanQuintic() {
        val points = (0..120).map { SidePanelMotionPolicy.easeOutCubic(it / 120f) }
        assertEquals(0f, points.first(), 0.000001f)
        assertEquals(1f, points.last(), 0.000001f)
        assertTrue(points.all { it in 0f..1f })
        assertTrue(points.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0.875f, SidePanelMotionPolicy.easeOutCubic(0.5f), 0.000001f)
        assertEquals(0f, SidePanelMotionPolicy.easeOutCubic(Float.NaN), 0f)
        assertEquals(0f, SidePanelMotionPolicy.easeOutCubic(-10f), 0f)
        assertEquals(1f, SidePanelMotionPolicy.easeOutCubic(10f), 0f)
    }

    @Test fun durationsScaleWithTravelWithoutAVisibleOnePixelTail() {
        for (width in listOf(220, 360, 800)) {
            val distances = listOf(0.05f, 0.1f, 0.25f, 0.5f, 1f)
            val durations = distances.map { SidePanelMotionPolicy.durationMs(it, width, 0f, 2f) }
            assertTrue(durations.zipWithNext().all { (a, b) -> b >= a })
            assertTrue(durations.all { it in 90L..280L })
            assertEquals(224L, durations.last())
            assertEquals(0L, SidePanelMotionPolicy.durationMs(1f / width, width, 0f, 2f))
        }
        assertEquals(0L, SidePanelMotionPolicy.durationMs(0f, 300, 0f, 2f))
    }

    @Test fun fasterFlingsAlwaysShortenOrPreserveSettleTimeForBothDirections() {
        for (width in listOf(240, 800)) {
            val slow = SidePanelMotionPolicy.durationMs(0.8f, width, 0f, 3f)
            val medium = SidePanelMotionPolicy.durationMs(0.8f, width, 1_500f, 3f)
            val fast = SidePanelMotionPolicy.durationMs(0.8f, width, 6_000f, 3f)
            assertTrue(medium <= slow)
            assertTrue(fast <= medium)
            assertEquals(fast, SidePanelMotionPolicy.durationMs(0.8f, width, -6_000f, 3f))
            assertTrue(fast in 90L..280L)
        }
    }

    @Test fun invalidGeometryIsRejectedAndNonFiniteReleaseSpeedIsSafe() {
        try {
            SidePanelMotionPolicy.durationMs(Float.NaN, 300, 0f, 2f)
            fail("Expected IllegalArgumentException for NaN distance")
        } catch (_: IllegalArgumentException) { }
        try {
            SidePanelMotionPolicy.durationMs(0.5f, 0, 0f, 2f)
            fail("Expected IllegalArgumentException for zero width")
        } catch (_: IllegalArgumentException) { }
        val driver = Driver()
        val controller = SidePanelMotionController(driver, 2f) { }
        assertTrue(controller.beginDrag(SidePanelSide.RIGHT, 320))
        assertTrue(controller.dragBy(-210f))
        assertTrue(controller.release(Float.NaN))
        driver.finish()
        assertEquals(SidePanelPhase.OPEN, controller.snapshot.phase)
    }

    @Test fun onePixelProgrammaticTailCompletesWithoutSchedulingAnAnimator() {
        val driver = Driver()
        val controller = SidePanelMotionController(driver, 2f) { }
        assertTrue(controller.beginDrag(SidePanelSide.LEFT, 250))
        assertTrue(controller.dragBy(249.5f))
        assertTrue(controller.release(0f))
        assertEquals(SidePanelPhase.OPEN, controller.snapshot.phase)
        assertNull(driver.current)
        assertTrue(controller.command(SidePanelSide.LEFT, 250, false))
        driver.finish()
        assertEquals(SidePanelPhase.CLOSED, controller.snapshot.phase)
    }

}

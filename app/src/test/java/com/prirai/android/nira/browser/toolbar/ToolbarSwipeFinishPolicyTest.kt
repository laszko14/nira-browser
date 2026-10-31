package com.prirai.android.nira.browser.toolbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ZC-AUD-049: stable physical direction and threshold for WWW and Home. */
class ToolbarSwipeFinishPolicyTest {
    private fun finish(left: Boolean, velocity: Float, progress: Double): Boolean =
        ToolbarSwipeFinishPolicy.completes(left, velocity, 1000, progress)

    @Test fun naturalFlingCompletesAtAndAboveMinimumInBothDirections() {
        for (progress in listOf(0.0, 0.01, 0.24, 0.25, 0.9)) {
            for (speed in listOf(1000f, 1500f)) {
                assertTrue(finish(true, -speed, progress))
                assertTrue(finish(false, speed, progress))
            }
        }
    }

    @Test fun reverseFlingCancelsAtAndAboveMinimumEvenWithLargeDrag() {
        for (progress in listOf(0.0, 0.24, 0.25, 0.9, 1.0)) {
            for (speed in listOf(1000f, 1500f)) {
                assertFalse(finish(true, speed, progress))
                assertFalse(finish(false, -speed, progress))
            }
        }
    }

    @Test fun slowReleaseUsesFractionalQuarterProgressAndAllowsSlowReversal() {
        for (left in listOf(true, false)) {
            for (velocity in listOf(0f, -999f, 999f)) {
                assertFalse(finish(left, velocity, 0.249))
                assertTrue(finish(left, velocity, 0.25))
                assertTrue(finish(left, velocity, 0.6))
            }
        }
    }

    @Test fun rtlChangesTabDestinationButNotPhysicalSign() {
        for (rtl in listOf(false, true)) {
            for (left in listOf(true, false)) {
                val velocity = if (left) -1200f else 1200f
                assertTrue("rtl=$rtl left=$left", finish(left, velocity, 0.01))
                assertFalse("rtl=$rtl left=$left", finish(left, -velocity, 0.90))
            }
        }
    }
}

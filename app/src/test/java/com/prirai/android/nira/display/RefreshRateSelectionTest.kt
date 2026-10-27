package com.prirai.android.nira.display

import org.junit.Assert.*
import org.junit.Test

class RefreshRateSelectionTest {
    private fun mode(rate: Float, width: Int = 1080, height: Int = 2400) =
        RefreshRateMode(width, height, rate)

    @Test fun prefersSupported120AtCurrentResolution() {
        assertEquals(120f, RefreshRateSelection.preferredRate(listOf(mode(60f), mode(120f)), 1080, 2400), 0f)
    }
    @Test fun fractional120UsesExactSupportedValue() {
        assertEquals(119.88f, RefreshRateSelection.preferredRate(listOf(mode(60f), mode(119.88f)), 1080, 2400), 0f)
    }
    @Test fun unsupported120LeavesSelectionToSystem() {
        assertEquals(0f, RefreshRateSelection.preferredRate(listOf(mode(60f), mode(90f), mode(144f)), 1080, 2400), 0f)
    }
    @Test fun neverRequestsRateThatRequiresResolutionChange() {
        assertEquals(0f, RefreshRateSelection.preferredRate(listOf(mode(60f), mode(120f, 720, 1600)), 1080, 2400), 0f)
    }
    @Test fun displayChangeUsesItsOwnModes() {
        assertEquals(119.9f, RefreshRateSelection.preferredRate(listOf(mode(119.9f, 1920, 1080)), 1920, 1080), 0f)
        assertEquals(0f, RefreshRateSelection.preferredRate(listOf(mode(60f, 1920, 1080)), 1920, 1080), 0f)
    }
    @Test fun powerSaverRelinquishesPreferenceWithoutForcing60() {
        assertEquals(0f, RefreshRateSelection.preferredRate(listOf(mode(120f)), 1080, 2400, powerSave = true), 0f)
    }
    @Test fun rejectsInvalidAndDistantRates() {
        assertEquals(0f, RefreshRateSelection.preferredRate(listOf(mode(Float.NaN), mode(Float.POSITIVE_INFINITY), mode(0f), mode(118f), mode(122f)), 1080, 2400), 0f)
        assertEquals(0f, RefreshRateSelection.preferredRate(emptyList(), 1080, 2400), 0f)
        assertEquals(0f, RefreshRateSelection.preferredRate(listOf(mode(120f, 0, 0)), 0, 0), 0f)
    }
    @Test fun closestRateIsDeterministicRegardlessOfModeOrdering() {
        val modes = listOf(mode(119.88f), mode(120f), mode(120.1f))
        assertEquals(120f, RefreshRateSelection.preferredRate(modes, 1080, 2400), 0f)
        assertEquals(120f, RefreshRateSelection.preferredRate(modes.reversed(), 1080, 2400), 0f)
    }
}

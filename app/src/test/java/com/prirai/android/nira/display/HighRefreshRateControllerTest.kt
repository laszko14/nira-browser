package com.prirai.android.nira.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighRefreshRateControllerTest {
    @Test fun selects120HzWhenSupported() {
        assertEquals(
            120f,
            HighRefreshRateController.choose120Hz(listOf(60f, 90f, 120f)),
            0.001f,
        )
    }

    @Test fun acceptsFractional120HzPanelTiming() {
        assertEquals(
            119.88f,
            HighRefreshRateController.choose120Hz(listOf(60f, 119.88f)),
            0.001f,
        )
    }

    @Test fun doesNotForceMissing120HzMode() {
        assertNull(HighRefreshRateController.choose120Hz(listOf(60f, 90f, 144f)))
    }

    @Test fun ignoresInvalidRatesAndSelectsClosest120HzMode() {
        assertEquals(
            120f,
            HighRefreshRateController.choose120Hz(listOf(Float.NaN, Float.POSITIVE_INFINITY, 119.8f, 120f)),
            0.001f,
        )
    }
}

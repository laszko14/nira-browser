package com.prirai.android.nira.display

import org.junit.Assert.*
import org.junit.Test

class HighRefreshRateControllerTest {
    @Test fun selectsNative120() {
        assertEquals(120f, HighRefreshRateController.choose120Hz(listOf(60f, 90f, 120f))!!, 0.001f)
    }
    @Test fun supportsFractional120() {
        assertEquals(119.88f, HighRefreshRateController.choose120Hz(listOf(60f, 119.88f))!!, 0.001f)
    }
    @Test fun noUnsupportedHzPreference() {
        assertNull(HighRefreshRateController.choose120Hz(listOf(60f, 90f, 144f)))
    }
    @Test fun ignoresInvalidRates() {
        assertEquals(120f, HighRefreshRateController.choose120Hz(listOf(Float.NaN, Float.POSITIVE_INFINITY, 119.9f, 120f))!!, 0.001f)
    }
}
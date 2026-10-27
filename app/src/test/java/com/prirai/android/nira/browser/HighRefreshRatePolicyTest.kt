package com.prirai.android.nira.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HighRefreshRatePolicyTest {
    @Test fun prefersReal119Point88Over60Or90() {
        assertEquals(119.88f, HighRefreshRatePolicy.choose(listOf(60f, 90f, 119.88f, 144f))!!, 0.01f)
    }

    @Test fun uses90OnA90HzPanel() {
        assertEquals(90f, HighRefreshRatePolicy.choose(listOf(60f, 90f))!!, 0.01f)
    }

    @Test fun leaves60HzAndInvalidPanelsAlone() {
        assertNull(HighRefreshRatePolicy.choose(listOf(60f, 30f, Float.NaN)))
    }
}

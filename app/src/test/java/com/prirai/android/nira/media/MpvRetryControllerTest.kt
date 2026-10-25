package com.prirai.android.nira.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvRetryControllerTest {
    @Test fun replacementEndIsConsumedExactlyOnce() {
        val state = MpvRetryController()
        state.prepare(true, 60.0, 300.0)
        assertTrue(state.consumeReplacementEnd())
        assertFalse(state.consumeReplacementEnd())
        assertEquals(60.0, state.onFileLoaded()!!, 0.0)
        assertNull(state.onFileLoaded())
    }

    @Test fun failedInitialLoadDoesNotMaskARealError() {
        val state = MpvRetryController()
        state.prepare(false, 42.0, 300.0)
        assertFalse(state.consumeReplacementEnd())
        assertNull(state.onFileLoaded())
    }

    @Test fun liveOrNearEndStreamsDoNotSeekOnRetry() {
        val state = MpvRetryController()
        state.prepare(true, 10.0, Double.NaN)
        assertNull(state.onFileLoaded())
        state.prepare(true, 299.0, 300.0)
        assertNull(state.onFileLoaded())
        state.prepare(true, Double.POSITIVE_INFINITY, 300.0)
        assertNull(state.onFileLoaded())
    }

    @Test fun resetDropsOldPendingStopAndResume() {
        val state = MpvRetryController()
        state.prepare(true, 60.0, 300.0)
        state.clear()
        assertFalse(state.consumeReplacementEnd())
        assertNull(state.onFileLoaded())
    }
}

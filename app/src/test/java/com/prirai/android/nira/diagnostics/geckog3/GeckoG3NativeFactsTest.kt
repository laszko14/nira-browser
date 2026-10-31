package com.prirai.android.nira.diagnostics.geckog3

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoG3NativeFactsTest {
    @Test fun differentiateSettingsMatchMismatchAndUnobserved() {
        val c = GeckoG3NativeFacts
        assertEquals(c.Comparison.MATCH, c.context(null, null, true))
        assertEquals(c.Comparison.MATCH, c.context("test", "test", true))
        assertEquals(c.Comparison.MISMATCH, c.context("test", null, true))
        assertEquals(c.Comparison.MISMATCH, c.context(null, "test", true))
        assertEquals(c.Comparison.NOT_OBSERVED, c.context("test", null, false))
        assertEquals(c.Comparison.MATCH, c.privacy(true, true))
        assertEquals(c.Comparison.MISMATCH, c.privacy(true, false))
        assertEquals(c.Comparison.NOT_OBSERVED, c.privacy(false, null))
    }

    @Test fun distinguishSyntheticMediaFromMissingAndStaleMetadata() {
        val c = GeckoG3NativeFacts
        assertEquals(c.MediaLabel.A, c.mediaLabel("/media/a.mp4"))
        assertEquals(c.MediaLabel.B, c.mediaLabel("/media/b.mp4"))
        assertEquals(c.MediaLabel.OTHER, c.mediaLabel("/media/invalid-b.mp4"))
        assertEquals(c.Comparison.MATCH, c.metadata(c.MediaLabel.A, 160, 90, 2))
        assertEquals(c.Comparison.MATCH, c.metadata(c.MediaLabel.B, 320, 180, 5))
        assertEquals(c.Comparison.MISMATCH, c.metadata(c.MediaLabel.B, 160, 90, 2))
        assertEquals(c.Comparison.NOT_OBSERVED, c.metadata(c.MediaLabel.B, null, 180, 5))
        assertEquals(c.Comparison.NOT_OBSERVED, c.metadata(c.MediaLabel.OTHER, 160, 90, 2))
    }
}

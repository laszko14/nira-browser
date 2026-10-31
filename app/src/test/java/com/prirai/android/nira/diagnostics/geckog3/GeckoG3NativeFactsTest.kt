package com.prirai.android.nira.diagnostics.geckog3

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoG3NativeFactsTest {
    @Test fun differentiateSettingsMatchMismatchAndUnobserved() {
        val c = GeckoG3NativeFacts
        assertEquals(GeckoG3NativeFacts.Comparison.MATCH, c.context(null, null, true))
        assertEquals(GeckoG3NativeFacts.Comparison.MATCH, c.context("test", "test", true))
        assertEquals(GeckoG3NativeFacts.Comparison.MISMATCH, c.context("test", null, true))
        assertEquals(GeckoG3NativeFacts.Comparison.MISMATCH, c.context(null, "test", true))
        assertEquals(GeckoG3NativeFacts.Comparison.NOT_OBSERVED, c.context("test", null, false))
        assertEquals(GeckoG3NativeFacts.Comparison.MATCH, c.privacy(true, true))
        assertEquals(GeckoG3NativeFacts.Comparison.MISMATCH, c.privacy(true, false))
        assertEquals(GeckoG3NativeFacts.Comparison.NOT_OBSERVED, c.privacy(false, null))
    }

    @Test fun distinguishSyntheticMediaFromMissingAndStaleMetadata() {
        val c = GeckoG3NativeFacts
        assertEquals(GeckoG3NativeFacts.MediaLabel.A, c.mediaLabel("/media/a.mp4"))
        assertEquals(GeckoG3NativeFacts.MediaLabel.B, c.mediaLabel("/media/b.mp4"))
        assertEquals(GeckoG3NativeFacts.MediaLabel.OTHER, c.mediaLabel("/media/invalid-b.mp4"))
        assertEquals(GeckoG3NativeFacts.Comparison.MATCH, c.metadata(GeckoG3NativeFacts.MediaLabel.A, 160, 90, 2))
        assertEquals(GeckoG3NativeFacts.Comparison.MATCH, c.metadata(GeckoG3NativeFacts.MediaLabel.B, 320, 180, 5))
        assertEquals(GeckoG3NativeFacts.Comparison.MISMATCH, c.metadata(GeckoG3NativeFacts.MediaLabel.B, 160, 90, 2))
        assertEquals(GeckoG3NativeFacts.Comparison.NOT_OBSERVED, c.metadata(GeckoG3NativeFacts.MediaLabel.B, null, 180, 5))
        assertEquals(GeckoG3NativeFacts.Comparison.NOT_OBSERVED, c.metadata(GeckoG3NativeFacts.MediaLabel.OTHER, 160, 90, 2))
    }
}

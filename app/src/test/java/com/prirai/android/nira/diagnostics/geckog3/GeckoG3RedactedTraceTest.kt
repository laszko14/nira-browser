package com.prirai.android.nira.diagnostics.geckog3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoG3RedactedTraceTest {
    @Test fun sameSessionRetainsLabelWhileDifferentSessionChangesIt() {
        val trace = GeckoG3RedactedTrace()
        val original = Any()
        val other = Any()
        val a = trace.record(GeckoG3RedactedTrace.Kind.SESSION_BOUND, 1L, original,
            "tab-secret", "profile_secret", false)
        val b = trace.record(GeckoG3RedactedTrace.Kind.SESSION_STABLE, 2L, original,
            "tab-secret", "profile_secret", false)
        val c = trace.record(GeckoG3RedactedTrace.Kind.SESSION_REPLACED, 3L, other,
            "tab-secret", "profile_secret", false)
        assertEquals("S1", a.sessionLabel)
        assertEquals(a.sessionLabel, b.sessionLabel)
        assertEquals("S2", c.sessionLabel)
        assertEquals("T1", c.tabLabel)
        assertEquals("C1", c.contextLabel)
        assertEquals(listOf(1, 2, 3), trace.entries().map { it.sequence })
    }

    @Test fun privatePartitionNeverExportsRawContextAndDefaultIsDistinct() {
        val trace = GeckoG3RedactedTrace()
        val privateEntry = trace.record(GeckoG3RedactedTrace.Kind.SESSION_BOUND, 10L, Any(),
            "my-tab-cookie", "private_real_id", true)
        val normalEntry = trace.record(GeckoG3RedactedTrace.Kind.SESSION_BOUND, 11L, Any(),
            "other-tab", null, false)
        assertEquals("PRIVATE", privateEntry.contextLabel)
        assertEquals("DEFAULT", normalEntry.contextLabel)
        val report = trace.entries().joinToString("\n") { it.redactedLine() }
        for (sensitive in listOf("my-tab-cookie", "private_real_id", "other-tab")) {
            assertFalse(report.contains(sensitive))
        }
        assertTrue(report.contains("documentKey=NOT_OBSERVED"))
        assertTrue(report.contains("accepted=NOT_OBSERVED"))
    }

    @Test fun documentDecisionMustBeObservedNotInferred() {
        val trace = GeckoG3RedactedTrace()
        val synthetic = trace.record(GeckoG3RedactedTrace.Kind.DOCUMENT_REJECTED_OBSERVED,
            7L, Any(), "test-only", "profile_test", false, 1L, false)
        assertTrue(synthetic.redactedLine().contains("documentKey=1 accepted=false"))
        assertFalse(synthetic.redactedLine().contains("profile_test"))
        assertThrows(IllegalArgumentException::class.java) {
            trace.record(GeckoG3RedactedTrace.Kind.DOCUMENT_ACCEPTED_OBSERVED,
                8L, null, null, null, false, 0L, true)
        }
    }

    @Test fun boundedBufferDropsOldEntriesWithoutReusingLabels() {
        val trace = GeckoG3RedactedTrace(2)
        val a = trace.record(GeckoG3RedactedTrace.Kind.SESSION_BOUND, 1L,
            Any(), "tab-a", "ctx-a", false)
        trace.record(GeckoG3RedactedTrace.Kind.SESSION_BOUND, 2L,
            Any(), "tab-b", "ctx-b", false)
        trace.record(GeckoG3RedactedTrace.Kind.SESSION_BOUND, 3L,
            Any(), "tab-c", "ctx-c", false)
        assertEquals(listOf(2, 3), trace.entries().map { it.sequence })
        assertEquals("S1", a.sessionLabel)
        assertEquals("S3", trace.entries().last().sessionLabel)
    }
}

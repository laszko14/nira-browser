package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

class VideoSearchDiagnosticDrainPolicyTest {
    private val d = VideoSearchDiagnosticDrainPolicy
    @Test fun normalReadIsNeverMistakenForStop() {
        assertEquals(VideoSearchDiagnosticDrainPolicy.Command.START, d.command("start", false))
        assertEquals(VideoSearchDiagnosticDrainPolicy.Command.START, d.command("start", true))
        assertEquals("reading", d.afterAck("start", "active"))
        assertEquals(VideoSearchDiagnosticDrainPolicy.Command.READ, d.command("reading", false))
        assertEquals("reading", d.afterRead("reading", true))
        assertFalse(d.terminal("reading"))
    }
    @Test fun stopMustAckBeforeDrainAndEmptyReadMustProveEnd() {
        assertEquals(VideoSearchDiagnosticDrainPolicy.Command.STOP, d.command("reading", true))
        assertNull(d.command("stopping", true))
        assertNull(d.afterAck("stopping", "active"))
        assertEquals("draining", d.afterAck("stopping", "stopped"))
        assertEquals(VideoSearchDiagnosticDrainPolicy.Command.READ, d.command("draining", true))
        assertEquals("draining", d.afterRead("draining", false))
        assertFalse(d.terminal("draining"))
        assertEquals("drained", d.afterRead("draining", true))
        assertTrue(d.terminal("drained"))
    }
    @Test fun neverTurnTimeoutFailureOrStaleAckIntoSuccess() {
        assertNull(d.afterAck("reading", "stopped"))
        assertNull(d.afterAck("draining", "stopped"))
        assertNull(d.afterAck("start", "rejected"))
        assertNull(d.afterRead("start", true))
        assertNull(d.command("failed", true))
        assertNull(d.command("drained", true))
        assertTrue(d.terminal("failed"))
        assertFalse(d.terminal("start"))
        assertFalse(d.terminal("stopping"))
    }
}

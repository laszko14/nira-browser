package com.prirai.android.nira.webapp

import org.junit.Assert.*
import org.junit.Test

class WebAppLaunchGateTest {
    @Test fun intentBeforeFirstFragmentAlwaysWinsEvenIfOldLookupFinishesLast() {
        val gate=WebAppLaunchGate()
        val initial=gate.accept("https://a.example")
        val last=gate.accept("https://b.example")
        assertFalse(gate.isCurrent(initial))
        assertTrue(gate.isCurrent(last))
        assertEquals("https://b.example",last.url)
    }

    @Test fun threeQuickIntentsCannotCommitStaleProfileOrIcon() {
        val gate=WebAppLaunchGate()
        val a=gate.accept("https://a.example")
        val b=gate.accept("https://b.example")
        val c=gate.accept("https://c.example")
        assertFalse(gate.isCurrent(b))
        assertFalse(gate.isCurrent(a))
        assertTrue(gate.isCurrent(c))
    }

    @Test fun repeatedUrlStillAdvancesTicketAndRejectsOldIcon() {
        val gate=WebAppLaunchGate()
        val a=gate.accept("https://a.example")
        val repeated=gate.accept("https://a.example")
        assertFalse(gate.isCurrent(a))
        assertTrue(gate.isCurrent(repeated))
    }
}

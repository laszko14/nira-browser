package com.prirai.android.nira

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenChromeRestoreGateTest {
    @Test fun queuedExitIsInvalidAfterNewEnter() {
        val gate = FullscreenChromeRestoreGate()
        gate.newView()
        gate.modeChanged(true)
        val exit = gate.modeChanged(false)
        gate.modeChanged(true)
        assertTrue(gate.isFullscreen)
        assertFalse(gate.canRestore(exit))
    }
    @Test fun latestExitMayRestoreNormalChrome() {
        val gate = FullscreenChromeRestoreGate()
        gate.newView()
        gate.modeChanged(true)
        val exit = gate.modeChanged(false)
        assertTrue(gate.canRestore(exit))
    }
    @Test fun olderExitDoesNotWinAfterAnotherExit() {
        val gate = FullscreenChromeRestoreGate()
        gate.newView()
        gate.modeChanged(true)
        val first = gate.modeChanged(false)
        gate.modeChanged(true)
        val last = gate.modeChanged(false)
        assertFalse(gate.canRestore(first))
        assertTrue(gate.canRestore(last))
    }
    @Test fun viewDestroyAndRecreationRejectOldCallbacks() {
        val gate = FullscreenChromeRestoreGate()
        gate.newView()
        val old = gate.modeChanged(false)
        gate.invalidateView()
        assertFalse(gate.canRestore(old))
        gate.newView()
        assertFalse(gate.canRestore(old))
        assertTrue(gate.canRestore(gate.modeChanged(false)))
    }
    @Test fun duplicateModeNotificationsStillInvalidateOlderRestores() {
        val gate = FullscreenChromeRestoreGate()
        gate.newView()
        val first = gate.modeChanged(false)
        val last = gate.modeChanged(false)
        assertFalse(gate.canRestore(first))
        assertTrue(gate.canRestore(last))
    }
}

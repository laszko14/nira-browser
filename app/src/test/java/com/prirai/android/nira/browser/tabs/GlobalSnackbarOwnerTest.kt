package com.prirai.android.nira.browser.tabs

import com.prirai.android.nira.browser.tabs.compose.GlobalSnackbarManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSnackbarOwnerTest {
    @Test fun disposingPanelDoesNotCancelUndo() = runTest {
        val manager = GlobalSnackbarManager(backgroundScope)
        val disposablePanel = CoroutineScope(coroutineContext + Job())
        var restored = 0
        manager.showUndoSnackbar("Closed", { restored++ }, {})
        runCurrent()
        assertNotNull(manager.snackbarHostState.currentSnackbarData)
        disposablePanel.cancel()
        runCurrent()
        manager.snackbarHostState.currentSnackbarData!!.performAction()
        runCurrent()
        assertEquals(1, restored)
    }

    @Test fun noHostTimeoutCannotLeaveStaleUndo() = runTest {
        val manager = GlobalSnackbarManager(backgroundScope)
        var confirmed = 0
        manager.showUndoSnackbar("Closed", {}, { confirmed++ })
        runCurrent()
        advanceTimeBy(5001L)
        runCurrent()
        assertEquals(1, confirmed)
        assertNull(manager.snackbarHostState.currentSnackbarData)
    }

    @Test fun replacingMessageDoesNotConfirmTheSupersededOne() = runTest {
        val manager = GlobalSnackbarManager(backgroundScope)
        var cancelledConfirm = 0
        var currentUndo = 0
        manager.showUndoSnackbar("Old", {}, { cancelledConfirm++ })
        runCurrent()
        manager.showUndoSnackbar("Current", { currentUndo++ }, {})
        runCurrent()
        assertEquals(0, cancelledConfirm)
        manager.snackbarHostState.currentSnackbarData!!.performAction()
        runCurrent()
        assertEquals(1, currentUndo)
    }
}

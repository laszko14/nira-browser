package com.prirai.android.nira.browser

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test

/** ZC-AUD-026: explicit finish may never outrun the acknowledged session write. */
class TaskExitSaveBarrierTest {
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Test fun pendingSaveCannotKillUntilWriteAcknowledges() {
        val diskAck = CompletableDeferred<Boolean>()
        var currentState = "S0"
        var persistedState = "S0"
        var terminated = 0
        var errors = 0
        val coordinator = TaskExitSaveBarrier(
            scope = scope(),
            persist = {
                val committed = diskAck.await()
                if (committed) persistedState = currentState
                committed
            },
            onCommitted = { terminated++ },
            onFailure = { errors++ },
        )

        currentState = "S1-new-tab"
        coordinator.request()
        assertEquals("S0", persistedState)
        assertEquals(0, terminated)
        diskAck.complete(true)
        assertEquals("S1-new-tab", persistedState)
        assertEquals(1, terminated)
        assertEquals(0, errors)
    }

    @Test fun waitForRestoreThenPersistDoesNotWriteEmptyStartupState() {
        val restored = CompletableDeferred<Boolean>()
        var inMemory = "empty-before-restore"
        var onDisk = "S0"
        var terminated = 0
        val barrier = TaskExitSaveBarrier(
            scope = scope(),
            persist = {
                if (!restored.await()) false else {
                    onDisk = inMemory
                    true
                }
            },
            onCommitted = { terminated++ },
            onFailure = { },
        )
        barrier.request()
        assertEquals("S0", onDisk)
        assertEquals(0, terminated)
        inMemory = "S1-restored-plus-new-tab"
        restored.complete(true)
        assertEquals("S1-restored-plus-new-tab", onDisk)
        assertEquals(1, terminated)
    }

    @Test fun failedRestoreBlocksWriteAndForcedTermination() {
        val restored = CompletableDeferred<Boolean>()
        var saved = false
        var killed = false
        var failures = 0
        val barrier = TaskExitSaveBarrier(
            scope = scope(),
            persist = {
                if (!restored.await()) false else {
                    saved = true
                    true
                }
            },
            onCommitted = { killed = true },
            onFailure = { failures++ },
        )
        barrier.request()
        restored.complete(false)
        assertFalse(saved)
        assertFalse(killed)
        assertEquals(1, failures)
    }

    @Test fun unsuccessfulDiskCommitNeverCallsTermination() {
        var killed = false
        var errors = 0
        TaskExitSaveBarrier(scope(), { false }, { killed = true }, { errors++ }).request()
        assertFalse(killed)
        assertEquals(1, errors)
    }

    @Test fun throwingStorageDoesNotKillAndReportsFailure() {
        var killed = false
        var actualError: Throwable? = null
        TaskExitSaveBarrier(
            scope(), { throw java.io.IOException("Disk full") },
            { killed = true }, { actualError = it },
        ).request()
        assertFalse(killed)
        assertTrue(actualError is java.io.IOException)
    }

    @Test fun duplicatedExitRequestsShareOneFinalWrite() {
        val acknowledge = CompletableDeferred<Boolean>()
        var saves = 0
        var kills = 0
        val barrier = TaskExitSaveBarrier(
            scope(),
            persist = {
                saves++
                acknowledge.await()
            },
            onCommitted = { kills++ },
            onFailure = { },
        )
        repeat(10) { barrier.request() }
        assertEquals(1, saves)
        assertEquals(0, kills)
        acknowledge.complete(true)
        assertEquals(1, kills)
    }
}

package com.prirai.android.nira.audit

import android.app.Application
import com.prirai.android.nira.downloads.DownloadProbeGate
import com.prirai.android.nira.downloads.isEligibleForProbeHandoff
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.state.content.DownloadState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class DownloadProbeGateTest {
    private fun state(
        id: String = "download",
        url: String = "https://example.org/file",
        status: DownloadState.Status = DownloadState.Status.DOWNLOADING,
        private: Boolean = false,
    ) = DownloadState(id = id, url = url, status = status, private = private)

    @Test fun lateProbeAfterPauseCannotStartParallelOrChangeRecord() = runTest {
        val gate = DownloadProbeGate()
        val started = state()
        val downloads = mutableMapOf(started.id to started)
        val ticket = gate.open(started.id)
        val release = CompletableDeferred<Long>()
        var starts = 0
        val probe = backgroundScope.launch {
            val total = withContext(NonCancellable) { release.await() } // uncooperative fetch
            gate.claim(ticket) {
                if (downloads[started.id].isEligibleForProbeHandoff(started)) {
                    downloads[started.id] = downloads.getValue(started.id).copy(contentLength = total)
                    starts++
                }
            }
        }
        gate.attach(ticket, probe)
        runCurrent()
        gate.invalidate(started.id)
        downloads[started.id] = started.copy(status = DownloadState.Status.PAUSED)
        release.complete(16L * 1024 * 1024)
        runCurrent()
        assertEquals(0, starts)
        assertEquals(DownloadState.Status.PAUSED, downloads[started.id]?.status)
        assertNull(downloads[started.id]?.contentLength)
    }

    @Test fun lateProbeAfterCancelAndRemovalCannotResurrectDownload() = runTest {
        val gate = DownloadProbeGate()
        val original = state()
        val store = mutableMapOf(original.id to original)
        val ticket = gate.open(original.id)
        gate.invalidate(original.id)
        store.remove(original.id)
        var started = false
        assertFalse(gate.claim(ticket) {
            if (store[original.id].isEligibleForProbeHandoff(original)) {
                store[original.id] = original.copy(contentLength = 16L * 1024 * 1024)
                started = true
            }
        })
        assertFalse(started)
        assertTrue(store.isEmpty())
    }

    @Test fun completionAndTerminalStatusesCannotTriggerHandoff() {
        val old = state()
        assertFalse((null as DownloadState?).isEligibleForProbeHandoff(old))
        for (status in listOf(
            DownloadState.Status.PAUSED, DownloadState.Status.CANCELLED,
            DownloadState.Status.COMPLETED, DownloadState.Status.FAILED,
        )) assertFalse(old.copy(status = status).isEligibleForProbeHandoff(old))
        assertTrue(old.copy(status = DownloadState.Status.INITIATED).isEligibleForProbeHandoff(old))
        assertTrue(old.copy(status = DownloadState.Status.DOWNLOADING).isEligibleForProbeHandoff(old))
    }

    @Test fun sameIdNewGenerationAndDifferentResourceDoNotInheritOldProbe() {
        val gate = DownloadProbeGate()
        val first = gate.open("x")
        val next = gate.open("x")
        var starts = 0
        assertFalse(gate.claim(first) { starts++ })
        assertTrue(gate.claim(next) { starts++ })
        assertFalse(gate.claim(next) { starts++ })
        assertEquals(1, starts)
        val original = state(id = "x")
        assertFalse(original.copy(url = "https://example.org/other").isEligibleForProbeHandoff(original))
        assertFalse(original.copy(private = true).isEligibleForProbeHandoff(original))
        assertFalse(original.copy(id = "y").isEligibleForProbeHandoff(original))
    }

    @Test fun independentlyRunningDownloadsDoNotInvalidateEachOther() {
        val gate = DownloadProbeGate()
        val a = gate.open("a")
        val b = gate.open("b")
        gate.invalidate("a")
        assertFalse(gate.runIfCurrent(a) { fail("a started") })
        var calls = 0
        assertTrue(gate.runIfCurrent(b) { calls++ })
        assertTrue(gate.claim(b) { calls++ })
        assertEquals(2, calls)
    }

    @Test fun invalidationBeforeFirstDispatchCancelsAttachedJob() = runTest {
        val gate = DownloadProbeGate()
        val ticket = gate.open("x")
        gate.invalidate("x")
        var ran = false
        val job = backgroundScope.launch { ran = true }
        gate.attach(ticket, job)
        runCurrent()
        assertTrue(job.isCancelled)
        assertFalse(ran)
    }
}

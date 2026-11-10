package com.prirai.android.nira.browser

import android.app.Application
import android.os.SystemClock
import com.prirai.android.nira.diagnostics.testlab.DiagnosticOwner
import com.prirai.android.nira.diagnostics.testlab.DiagnosticSessionGate
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MpvPlayDiagnosticsTest {
    private fun archiveEntries(bytes: ByteArray): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return entries
    }

    @Test fun boundedNativeSnapshotsAndArchiveCarryExplicitStage1Limitations() {
        val ledger = MpvPlayDiagnosticLedger(
            "synthetic-session", "profile-A", "tab-A", "example.org",
            "synthetic-build", 1_000L, 100L
        )
        repeat(MpvPlayDiagnosticLedger.MAX_MARKS) { index ->
            assertTrue(ledger.mark(index.toLong(), "N".repeat(30_000)))
        }
        assertFalse(ledger.mark(9, "no extra"))
        assertEquals(MpvPlayDiagnosticLedger.MAX_MARKS, ledger.markCount)
        ledger.finish(50, 1_050, "STOPPED")
        assertFalse(ledger.mark(55, "after stop"))
        val entries = archiveEntries(ledger.toZipBytes())
        assertEquals(setOf("summary.json", "timeline.json", "native-overlay.txt"), entries.keys)
        val summary = JSONObject(entries.getValue("summary.json"))
        assertEquals("APP_DECOR_ONLY", summary.getString("snapshot_scope"))
        assertEquals("NOT_IMPLEMENTED_STAGE1", summary.getString("dom_capture"))
        assertEquals("NOT_SUPPORTED_STAGE1", summary.getString("private_tabs"))
        assertFalse(summary.getBoolean("automatic_upload"))
        assertFalse(entries.getValue("native-overlay.txt").contains("N".repeat(15_000)))
        assertEquals(10, JSONArray(entries.getValue("timeline.json")).length())
    }

    @Test fun privateTabBlockedAndProfileIsolationAndGateConflict() {
        MpvPlayDiagnostics.discard()
        try {
            assertEquals(MpvPlayDiagnostics.Start.PRIVATE_TAB,
                MpvPlayDiagnostics.start("p1", "t1", "example.org", true))
            assertFalse(MpvPlayDiagnostics.state().running)
            assertTrue(DiagnosticSessionGate.shared.acquire(DiagnosticOwner.TESTLAB))
            assertEquals(MpvPlayDiagnostics.Start.BUSY,
                MpvPlayDiagnostics.start("p1", "t1", "example.org", false))
            DiagnosticSessionGate.shared.release(DiagnosticOwner.TESTLAB)
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start("p1", "t1", "example.org", false))
            assertFalse(MpvPlayDiagnostics.mark("p2", "t1", "native"))
            assertFalse(MpvPlayDiagnostics.mark("p1", "t2", "native"))
            assertTrue(MpvPlayDiagnostics.mark("p1", "t1", "native"))
            assertFalse(MpvPlayDiagnostics.stop("p2"))
            assertTrue(MpvPlayDiagnostics.state().running)
            assertTrue(MpvPlayDiagnostics.stop("p1"))
            assertFalse(MpvPlayDiagnostics.state().running)
            assertTrue(MpvPlayDiagnostics.state().reportReady)
            assertEquals(null, DiagnosticSessionGate.shared.owner)
        } finally {
            MpvPlayDiagnostics.discard()
            DiagnosticSessionGate.shared.release(DiagnosticOwner.TESTLAB)
        }
    }

    @Test fun cannotArchiveBeforeStopAndFinishIsIdempotent() {
        val ledger = MpvPlayDiagnosticLedger("id", "p", "t", null, "v", 10L,
            SystemClock.elapsedRealtime())
        try {
            ledger.toZipBytes()
            fail("Active session must not be exportable")
        } catch (_: IllegalStateException) {
            // Correct: only stopped sessions are exportable.
        }
        ledger.finish(15L, 25L, "STOPPED")
        ledger.finish(20L, 30L, "TIMED_OUT")
        val summary = JSONObject(archiveEntries(ledger.toZipBytes()).getValue("summary.json"))
        assertEquals("STOPPED", summary.getString("status"))
        assertEquals(25L, summary.getLong("stopped_utc_ms"))
    }
}

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

    @Test fun emptyMarkedSessionReportsNoPortInsteadOfSuccessfulPageCapture() {
        val ledger = MpvPlayDiagnosticLedger("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "p", "t", null, "v", 1, 0)
        assertTrue(ledger.mark(10, "native only"))
        assertTrue(ledger.mark(20, "native only"))
        ledger.refreshTransport(8_001)
        assertEquals(MpvPlayDiagnosticLedger.Transport.NO_PORT, ledger.transport)
        ledger.finish(71_000, 71_001, "STOPPED")
        val entries = archiveEntries(ledger.toZipBytes())
        assertEquals(0, JSONObject(entries.getValue("summary.json")).getInt("page_events"))
        assertEquals("NO_PORT", JSONObject(entries.getValue("transport.json")).getString("failure"))
        assertFalse(JSONObject(entries.getValue("analysis.json")).getBoolean("diagnostics_complete"))
    }

    @Test fun ackRequiresSentStartAndBaselineSnapshotBeforeCaptureIsConfirmed() {
        val ledger = MpvPlayDiagnosticLedger("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "p", "t", null, "v", 1, 0)
        val doc = "0123456789abcdef0123456789abcdef"
        val ack = JSONObject().put("schemaVersion", 2).put("event", "start_ack")
            .put("sessionId", ledger.sessionId).put("documentId", doc).put("frame", "TOP")
        assertFalse(ledger.startAck(ack))
        ledger.connected()
        ledger.startSent(100)
        assertTrue(ledger.startAck(ack))
        assertEquals(MpvPlayDiagnosticLedger.Transport.START_ACKNOWLEDGED, ledger.transport)
        assertTrue(ledger.startAck(ack))
        ledger.refreshTransport(8_101)
        assertEquals(MpvPlayDiagnosticLedger.Transport.NO_PAGE_EVENTS, ledger.transport)
        ledger.finish(9_000, 9_001, "STOPPED")
        val transport = JSONObject(archiveEntries(ledger.toZipBytes()).getValue("transport.json"))
        assertEquals(1, transport.getInt("start_acknowledgements"))
        assertEquals(1, transport.getInt("packets_rejected"))
    }

    @Test fun connectedWithoutReadyIsDistinctFromNoAck() {
        val ledger = MpvPlayDiagnosticLedger("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "p", "t", null, "v", 1, 0)
        ledger.connected()
        ledger.refreshTransport(8_001)
        assertEquals(MpvPlayDiagnosticLedger.Transport.NO_READY, ledger.transport)
        ledger.finish(8_002, 8_003, "TIMED_OUT")
        val report = JSONObject(archiveEntries(ledger.toZipBytes()).getValue("transport.json"))
        assertEquals("zavro-mpv-play-transport-v2", report.getString("schema"))
        assertEquals("NO_READY", report.getString("failure"))
        assertEquals(0, report.getInt("messages_received"))
        assertEquals(0, report.getInt("ports_ready"))
        assertEquals("NOT_PROVEN_BY_POST", report.getString("start_delivery"))
    }

    @Test fun transportRejectionsAreTypedBoundedAndExcludeRawPayload() {
        val ledger = MpvPlayDiagnosticLedger("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "p", "t", null, "v", 1, 0)
        ledger.connected()
        ledger.ready(10)
        ledger.received()
        ledger.drop(MpvPlayDiagnosticLedger.Drop.INVALID_TYPE)
        ledger.drop(MpvPlayDiagnosticLedger.Drop.PARSE_ERROR)
        ledger.drop(MpvPlayDiagnosticLedger.Drop.SESSION_MISMATCH)
        ledger.startSent(12)
        ledger.finish(20, 25, "STOPPED")
        val report = JSONObject(archiveEntries(ledger.toZipBytes()).getValue("transport.json"))
        assertEquals(1, report.getInt("messages_received"))
        assertEquals(1, report.getInt("ports_ready"))
        assertEquals(1, report.getInt("invalid_type"))
        assertEquals(1, report.getInt("parse_errors"))
        assertEquals(1, report.getInt("session_mismatch"))
        assertEquals(3, report.getInt("packets_rejected"))
        assertFalse(report.toString().contains("private-cookie"))
    }

    @Test fun sentStartWithoutAckTimesOutAndLatePacketsCannotRestartIt() {
        val ledger = MpvPlayDiagnosticLedger("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "p", "t", null, "v", 1, 0)
        ledger.connected()
        ledger.startSent(10)
        ledger.refreshTransport(8_011)
        assertEquals(MpvPlayDiagnosticLedger.Transport.NO_ACK, ledger.transport)
        val late = JSONObject().put("schemaVersion", 2).put("event", "start_ack")
            .put("sessionId", ledger.sessionId)
            .put("documentId", "0123456789abcdef0123456789abcdef").put("frame", "TOP")
        assertFalse(ledger.startAck(late))
        ledger.finish(9_000, 9_001, "TIMED_OUT")
        assertFalse(ledger.startAck(late))
        val report = JSONObject(archiveEntries(ledger.toZipBytes()).getValue("summary.json"))
        assertEquals("NO_ACK", report.getString("transport_failure"))
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
        assertEquals(setOf("summary.json", "timeline.json", "native-overlay.txt",
            "page-events.json", "page-coverage.json", "addon-inventory.json",
            "analysis.json", "analysis.txt", "transport.json"), entries.keys)
        val summary = JSONObject(entries.getValue("summary.json"))
        assertEquals("zavro-mpv-play-stage4-v2", summary.getString("schema"))
        assertEquals("zavro-preview-triage-stage4-v2",
            JSONObject(entries.getValue("analysis.json")).getString("schema"))
        assertEquals("APP_DECOR_ONLY", summary.getString("snapshot_scope"))
        assertEquals("BOUNDED_STRUCTURE_ONLY", summary.getString("dom_capture"))
        assertEquals("NOT_SUPPORTED_STAGE1", summary.getString("private_tabs"))
        assertFalse(summary.getBoolean("automatic_upload"))
        assertFalse(entries.getValue("native-overlay.txt").contains("N".repeat(15_000)))
        assertEquals(10, JSONArray(entries.getValue("timeline.json")).length())
    }

    @Test fun pagePacketsMustBeOwnedBoundedAndNonSensitive() {
        val ledger = MpvPlayDiagnosticLedger(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "p", "t", "example.org", "v", 1L, 0L)
        val doc = "0123456789abcdef0123456789abcdef"
        ledger.connected()
        ledger.startSent(1)
        assertTrue(ledger.startAck(JSONObject().put("schemaVersion", 2)
            .put("event", "start_ack").put("sessionId", ledger.sessionId)
            .put("documentId", doc).put("frame", "TOP")))
        fun packet(sequence: Int, token: String = ledger.sessionId): JSONObject =
            JSONObject().apply {
                put("schemaVersion", 2)
                put("event", "observation")
                put("sessionId", token)
                put("documentId", doc)
                put("sequence", sequence)
                put("kind", "MEDIA")
                put("target", "VIDEO")
                put("detail", "BASELINE")
                put("metrics", JSONObject().put("frame", "TOP")
                    .put("readyState", 3).put("paused", true))
            }
        assertTrue(ledger.appendPageEvent(packet(1), 12))
        assertFalse(ledger.appendPageEvent(packet(1), 13))
        assertFalse(ledger.appendPageEvent(packet(2, "invalid"), 14))
        assertFalse(ledger.appendPageEvent(packet(2).put(
            "metrics", JSONObject().put("token", "secret-value")), 15))
        assertFalse(ledger.appendPageEvent(packet(2).put(
            "detail", "user@email.com"), 16))
        assertFalse(ledger.appendPageEvent(packet(2).put(
            "detail", "API_SECRET_TOKEN"), 16))
        assertFalse(ledger.appendPageEvent(packet(2).put(
            "metrics", JSONObject().put("frame", "PRIVATE_PASSWORD")), 16))
        assertFalse(ledger.appendPageEvent(packet(2).put(
            "metrics", JSONObject().put("controls", "YES")), 16))
        assertTrue(ledger.appendPageEvent(packet(2), 17))
        ledger.finish(20, 40, "STOPPED")
        assertFalse(ledger.appendPageEvent(packet(3), 21))
        val entries = archiveEntries(ledger.toZipBytes())
        val events = JSONArray(entries.getValue("page-events.json"))
        assertEquals(2, events.length())
        assertEquals(1, events.getJSONObject(0).getInt("sequence"))
        assertEquals(2, events.getJSONObject(1).getInt("sequence"))
        assertFalse(entries.getValue("page-events.json").contains("secret-value"))
        val summary = JSONObject(entries.getValue("summary.json"))
        assertEquals(2, summary.getInt("page_events"))
        assertTrue(summary.getInt("page_rejected") >= 4)
    }

    @Test fun addonInventoryCannotConflateInstalledWithExecutedOrLeakExtensionIds() {
        val ledger = MpvPlayDiagnosticLedger(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "profile-A", "tab-A", "example.org", "v", 100, 1)
        assertTrue(ledger.recordAddons(setOf(
            "nira-media-detector@prirai.android.nira",
            "zavro-preview-diagnostics@prirai.android.nira",
            "third-party.private-addon@example.test"
        ), false))
        ledger.finish(5, 105, "STOPPED")
        val json = JSONObject(archiveEntries(ledger.toZipBytes())
            .getValue("addon-inventory.json"))
        assertEquals("INSTALLED", json.getString("media_detector"))
        assertEquals("NOT_INSTALLED", json.getString("bg_play"))
        assertEquals("INSTALLED", json.getString("page_diagnostics"))
        assertEquals("NOT_ALLOWED", json.getString("bgplay_current_origin_permission"))
        assertEquals("NOT_CONFIRMED", json.getString("execution_on_page"))
        assertEquals("NOT_OBSERVED", json.getString("bgplay_page_patch"))
        assertFalse(json.getBoolean("raw_extension_ids_exported"))
        assertFalse(json.toString().contains("third-party.private-addon"))
        assertFalse(ledger.recordAddons(emptySet(), true))
        val unknown = MpvPlayAddonEvidence.unknown()
        assertEquals("UNKNOWN", unknown.getString("bg_play"))
        assertEquals("NOT_OBSERVED", unknown.getString("evidence"))
    }

    @Test fun lateInventoryCallbacksCannotModifyNewSessions() {
        MpvPlayDiagnostics.discard()
        try {
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start("p", "t", "example.org", false))
            val first = MpvPlayDiagnostics.sessionFor("p", "t")!!
            assertFalse(MpvPlayDiagnostics.addons("another", "t", first, emptySet(), true))
            assertFalse(MpvPlayDiagnostics.addons("p", "other", first, emptySet(), true))
            assertFalse(MpvPlayDiagnostics.addons("p", "t", "forged", emptySet(), true))
            assertTrue(MpvPlayDiagnostics.stop("p"))
            assertFalse(MpvPlayDiagnostics.addons("p", "t", first, emptySet(), true))
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start("p", "t", "example.org", false))
            assertFalse(MpvPlayDiagnostics.addons("p", "t", first, emptySet(), true))
            assertTrue(MpvPlayDiagnostics.addons("p", "t",
                MpvPlayDiagnostics.sessionFor("p", "t")!!, emptySet(), false))
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }


    @Test fun missingSessionAndOwnerHaveTypedFailureStageInAllZipReports() {
        val expected = mapOf(
            MpvPlayDiagnosticLedger.Failure.TAB_MISSING to "TAB_LOOKUP",
            MpvPlayDiagnosticLedger.Failure.ENGINE_SESSION_MISSING to "ENGINE_SESSION",
            MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH to "SESSION_OWNER",
            MpvPlayDiagnosticLedger.Failure.INSTALL_FAILED to "EXTENSION_INSTALL",
            MpvPlayDiagnosticLedger.Failure.HANDLER_FAILED to "MESSAGE_HANDLER",
            MpvPlayDiagnosticLedger.Failure.POST_FAILED to "COMMAND_SEND",
            MpvPlayDiagnosticLedger.Failure.SCRIPT_VERSION_MISMATCH to "SCRIPT_VERSION"
        )
        expected.forEach { (failure, stage) ->
            val ledger = MpvPlayDiagnosticLedger(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "owner", "tab", null,
                "app-version", 0, 0)
            ledger.transportFailed(failure)
            ledger.finish(1, 1, "STOPPED")
            val files = archiveEntries(ledger.toZipBytes())
            val transport = JSONObject(files.getValue("transport.json"))
            assertEquals(failure.name, transport.getString("failure"))
            assertEquals(stage, transport.getString("failure_stage"))
            assertEquals("FAILED", transport.getString("state"))
            assertEquals(0, transport.getInt("ports_open"))
            assertEquals(false, transport.getBoolean("top_frame_acknowledged"))
            assertEquals(0, transport.getInt("page_events_accepted"))
            assertEquals(stage, JSONObject(files.getValue("summary.json"))
                .getString("transport_stage"))
            assertEquals(stage, JSONObject(files.getValue("analysis.json"))
                .getString("transport_stage"))
            assertEquals(stage, JSONObject(files.getValue("page-coverage.json"))
                .getString("transport_stage"))
            assertFalse(files.getValue("transport.json").contains("example.org"))
        }
    }

    @Test fun fatalOwnerMismatchIsNotMaskedByLateConnectReadyOrDisconnect() {
        val ledger = MpvPlayDiagnosticLedger(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "owner", "tab", null, "v", 0, 0)
        ledger.connected(1)
        ledger.transportFailed(MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH)
        ledger.ready(2)
        ledger.connected(3)
        ledger.startSent(4)
        ledger.disconnected(5)
        assertEquals(MpvPlayDiagnosticLedger.Transport.FAILED, ledger.transport)
        assertEquals(MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH, ledger.failure)
        ledger.finish(6, 7, "STOPPED")
        val report = JSONObject(archiveEntries(ledger.toZipBytes())
            .getValue("transport.json"))
        assertEquals("SESSION_OWNER", report.getString("failure_stage"))
        assertEquals(1, report.getInt("ports_connected"))
        assertEquals(1, report.getInt("ports_disconnected"))
        assertEquals(0, report.getInt("start_attempts"))
        assertEquals(0, report.getInt("ports_open"))
    }

    @Test fun transportZipCountsOutstandingPortsAndAcknowledgedFrameClasses() {
        val ledger = MpvPlayDiagnosticLedger(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "owner", "tab", null, "v", 0, 0)
        val topDocument = "0123456789abcdef0123456789abcdef"
        val subDocument = "abcdefabcdefabcdefabcdefabcdefab"
        ledger.connected()
        ledger.connected()
        ledger.ready(1)
        ledger.startSent(2)
        fun ack(doc: String, frame: String) = JSONObject()
            .put("schemaVersion", 2).put("event", "start_ack")
            .put("sessionId", ledger.sessionId).put("documentId", doc)
            .put("frame", frame)
        assertTrue(ledger.startAck(ack(topDocument, "TOP"), 3))
        assertTrue(ledger.startAck(ack(subDocument, "SUBFRAME"), 4))
        ledger.disconnected(5)
        ledger.finish(6, 7, "STOPPED")
        val files = archiveEntries(ledger.toZipBytes())
        val transport = JSONObject(files.getValue("transport.json"))
        assertEquals(2, transport.getInt("ports_connected"))
        assertEquals(1, transport.getInt("ports_disconnected"))
        assertEquals(1, transport.getInt("ports_open"))
        assertEquals(2, transport.getInt("start_acknowledgements"))
        assertEquals(true, transport.getBoolean("top_frame_acknowledged"))
        assertEquals(1, transport.getInt("subframes_acknowledged"))
        assertEquals("PAGE_EVENTS", transport.getString("failure_stage"))
        assertEquals("NO_PAGE_EVENTS", transport.getString("failure"))
        assertEquals(false, JSONObject(files.getValue("analysis.json"))
            .getBoolean("diagnostics_complete"))
    }

    @Test fun privateTabBlockedAndProfileIsolationAndGateConflict() {
        MpvPlayDiagnostics.discard()
        try {
            assertEquals(MpvPlayDiagnostics.Start.PRIVATE_TAB,
                MpvPlayDiagnostics.start("p1", "t1", "example.org", true))
            assertEquals(MpvPlayDiagnostics.Start.UNSUPPORTED_PAGE,
                MpvPlayDiagnostics.start("p1", "t1", null, false, false))
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

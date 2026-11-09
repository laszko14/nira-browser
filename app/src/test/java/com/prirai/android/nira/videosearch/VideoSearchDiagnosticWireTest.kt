package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDiagnosticWireTest {
    private val doc = "a".repeat(32)
    private val oldDoc = "b".repeat(32)
    private val req = "c".repeat(32)
    private val session = "11111111-1111-4111-8111-111111111111"

    private fun event(
        sequence: Int = 1, documentId: String = doc,
        sourceRequest: String? = req,
    ) = JSONObject()
        .put("schemaVersion", 1).put("sequence", sequence)
        .put("timeMs", 1800000000000L)
        .put("documentId", documentId).put("navigationRevision", 0)
        .put("requestId", sourceRequest ?: JSONObject.NULL)
        .put("stage", "CARDS").put("claim", "OBSERVATION")
        .put("decision", "ACCEPTED").put("reason", "BATCH_EXTRACTED")
        .put("counts", JSONObject().put("VISIBLE_CARDS", 2)
            .put("ACCEPTED_FILMS", 1))
        .put("fields", JSONObject().put("FILM_URL",
            "https://films.example/watch?id=2&token=hidden")
            .put("TITLE", "Episode 2"))

    private fun packet(
        rows: JSONArray = JSONArray().put(event()),
        attempts: Int = 1, dropped: Int = 0,
        docId: String = doc, sessionId: String = session,
    ) = JSONObject().put("schemaVersion", 1).put("event", "diag_events")
        .put("documentId", docId).put("requestId", req)
        .put("batch", JSONObject()
            .put("schemaVersion", 1).put("sessionId", sessionId)
            .put("attempted", attempts).put("captured", attempts - dropped)
            .put("dropped", dropped).put("limited", dropped > 0)
            .put("oldestSequence", if (attempts > dropped) 1 else JSONObject.NULL)
            .put("latestSequence", attempts)
            .put("stopped", false).put("stopReason", JSONObject.NULL)
            .put("events", rows))

    private fun decoded(value: JSONObject): VideoSearchDiagnosticWire.Reply.Batch? =
        VideoSearchDiagnosticWire.decode(value) as?
            VideoSearchDiagnosticWire.Reply.Batch

    @Test fun validPacketKeepsOnlyTypedObservationWithoutOwner() {
        val packet = decoded(packet())!!
        assertEquals(doc, packet.documentId)
        assertEquals(session, packet.sessionId)
        assertEquals(1L, packet.latestSequence)
        assertEquals(VideoSearchDiagnosticStage.CARDS, packet.observations.single().stage)
        assertEquals(1, packet.observations.single().counts[
            VideoSearchDiagnosticCount.ACCEPTED_FILMS])
        assertFalse(packet.toString().contains("token=hidden"))
    }

    @Test fun strictNativeCommandsNeverIncludeSiteOrProfile() {
        val start = VideoSearchDiagnosticWire.start(doc, req, session)
        val read = VideoSearchDiagnosticWire.read(doc, req, 7)
        val stop = VideoSearchDiagnosticWire.stop(doc, req, session)
        val sample = VideoSearchDiagnosticWire.snapshot(doc, req)
        assertEquals("diag_start", start.getString("event"))
        assertEquals("diag_read", read.getString("event"))
        assertEquals(7L, read.getLong("cursor"))
        assertEquals("diag_stop", stop.getString("event"))
        assertEquals("diag_snapshot", sample.getString("event"))
        for (command in listOf(start, read, stop, sample)) {
            assertFalse(command.has("profileId"))
            assertFalse(command.has("tabId"))
            assertFalse(command.has("siteId"))
            assertFalse(command.has("runId"))
        }
    }

    @Test fun rejectExtraOwnerDataInvalidCountsAndSecretFields() {
        assertNull(decoded(packet().put("owner", "forged")))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").put("profileId", "foreign")
        }))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").getJSONArray("events")
                .getJSONObject(0).put("siteId", "spoofed")
        }))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").getJSONArray("events").getJSONObject(0)
                .getJSONObject("counts").put("COOKIE", 1)
        }))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").getJSONArray("events").getJSONObject(0)
                .getJSONObject("fields").put("password", "secret")
        }))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").getJSONArray("events").getJSONObject(0)
                .getJSONObject("fields").put("DOM_FRAGMENT", "x".repeat(769))
        }))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").getJSONArray("events").getJSONObject(0)
                .put("claim", "INFERENCE")
        }))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").put("attempted", 5)
        }))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").put("dropped", -1)
        }))
    }

    @Test fun rejectDuplicateUnorderedAndOversizedEvents() {
        assertNull(decoded(packet(JSONArray().put(event(2)).put(event(1)),
            attempts = 2)))
        assertNull(decoded(packet(JSONArray().put(event(1)).put(event(1)),
            attempts = 2)))
        val five = JSONArray()
        for (n in 1..5) five.put(event(n))
        assertNull(decoded(packet(five, attempts = 5)))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").getJSONArray("events").getJSONObject(0)
                .put("documentId", oldDoc.uppercase())
        }))
        assertNull(decoded(packet().apply {
            getJSONObject("batch").getJSONArray("events").getJSONObject(0)
                .put("requestId", "0".repeat(31))
        }))
    }

    @Test fun ackCannotSpoofSessionOwnerOrUseExtraKeys() {
        val ack = JSONObject().put("schemaVersion", 1).put("event", "diag_ack")
            .put("documentId", doc).put("requestId", req).put("status", "active")
        assertTrue(VideoSearchDiagnosticWire.decode(ack)
            is VideoSearchDiagnosticWire.Reply.Ack)
        assertNull(VideoSearchDiagnosticWire.decode(
            JSONObject(ack.toString()).put("profileId", "other")))
        assertNull(VideoSearchDiagnosticWire.decode(
            JSONObject(ack.toString()).put("status", "completed")))
    }

    @Test fun exactNativeTabAndDocumentBindFilmToOnlyItsOwner() {
        val owner = VideoSearchScope.Private("profile-A", "epoch-A")
        val recorder = VideoSearchDiagnosticRecorder(owner, null, sessionId = session)
        val native = VideoSearchDiagnosticNativeSession(recorder)
        native.registerRequest("tab-A", doc, req, "site-A", "worker-A",
            runId = "run-A", batchId = "batch-A")
        assertNull(native.accept("tab-A", setOf(oldDoc), 0, decoded(packet())!!))
        assertEquals(1L, native.accept("tab-A", setOf(doc), 0, decoded(packet())!!))
        // Same requestId on a different tab never acquires site-A attribution.
        assertEquals(1L, native.accept("tab-B", setOf(doc), 0, decoded(packet())!!))
        native.stop("USER_STOP")
        assertTrue(recorder.awaitStopped())
        val list = recorder.privateLines(owner).map(::JSONObject)
            .filter { it.optString("kind") == "event" &&
                it.optString("reason") == "BATCH_EXTRACTED" }
        assertEquals(2, list.size)
        val a = list[0].getJSONObject("trace")
        assertEquals("site-A", a.getString("siteId"))
        assertEquals("worker-A", a.getString("workerId"))
        assertEquals("run-A", a.getString("runId"))
        val b = list[1].getJSONObject("trace")
        assertTrue(b.isNull("siteId"))
        val raw = recorder.privateLines(owner).joinToString("\n")
        assertFalse(raw.contains("token=hidden"))
        assertTrue(raw.contains("watch?id=2"))
    }

    @Test fun staleDocumentAndDuplicateCursorDoNotCreateExtraEvents() {
        val owner = VideoSearchScope.Private("profile-A", "epoch-A")
        val recorder = VideoSearchDiagnosticRecorder(owner, null, sessionId = session)
        val native = VideoSearchDiagnosticNativeSession(recorder)
        native.registerRequest("tab-A", doc, req, "site-A", null)
        val valid = decoded(packet())!!
        assertEquals(1L, native.accept("tab-A", setOf(doc), 0, valid))
        assertNull(native.accept("tab-A", setOf(doc), 1, valid))
        assertNull(native.accept("tab-A", setOf(doc), 0,
            decoded(packet(docId = oldDoc))!!))
        native.stop("USER_STOP")
        assertTrue(recorder.awaitStopped())
        assertEquals(1, recorder.privateLines(owner).map(::JSONObject).count {
            it.optString("kind") == "event"
        })
    }

    @Test fun lostJsRecordsBecomeExplicitInterruptionAndPrivateEpochClearsRam() {
        val owner = VideoSearchScope.Private("profile-A", "epoch-A")
        val other = VideoSearchScope.Private("profile-A", "epoch-B")
        val recorder = VideoSearchDiagnosticRecorder(owner, null, sessionId = session)
        val native = VideoSearchDiagnosticNativeSession(recorder)
        val batch = decoded(packet(JSONArray().put(event(1)),
            attempts = 3, dropped = 2))!!
        assertEquals(1L, native.accept("tab-A", setOf(doc), 0, batch))
        assertNull(native.accept("tab-A", setOf(doc), 1, batch))
        native.stop("USER_STOP")
        assertTrue(recorder.awaitStopped())
        val lines = recorder.privateLines(owner).joinToString("\n")
        assertTrue(lines.contains("JS_EVENTS_DROPPED"))
        assertFalse(native.retirePrivate(other))
        assertTrue(native.retirePrivate(owner))
        assertTrue(recorder.privateLines(owner).isEmpty())
        assertNull(native.accept("tab-A", setOf(doc), 0, batch))
    }

    @Test fun newDocumentBufferCanRestartJsDropCounterForSameTab() {
        val owner = VideoSearchScope.Private("profile-A", "epoch")
        val recorder = VideoSearchDiagnosticRecorder(owner, null, sessionId = session)
        val native = VideoSearchDiagnosticNativeSession(recorder)
        val overflow = decoded(packet(JSONArray().put(event(1)),
            attempts = 3, dropped = 2))!!
        assertEquals(1L, native.accept("tab-A", setOf(doc), 0, overflow))
        native.newPageBuffer("tab-A")
        val newPort = decoded(packet())!!
        assertEquals(1L, native.accept("tab-A", setOf(doc), 0, newPort))
        native.queueStatus("run-1", "site-1", VideoSearchQueueStatus.DONE)
        native.stop("USER_STOP")
        assertTrue(recorder.awaitStopped())
        val raw = recorder.privateLines(owner).joinToString("\n")
        assertTrue(raw.contains("JS_EVENTS_DROPPED"))
        assertTrue(raw.contains("QUEUE_DONE"))
        assertTrue(raw.contains("run-1"))
        assertTrue(raw.contains("site-1"))
    }

    @Test fun sameOriginSpaCanRetainOlderVerifiedDocumentWithoutSiteForgery() {
        val owner = VideoSearchScope.Private("profile-A", "epoch")
        val recorder = VideoSearchDiagnosticRecorder(owner, null, sessionId = session)
        val native = VideoSearchDiagnosticNativeSession(recorder)
        native.registerRequest("tab-A", oldDoc, req, "site-A", null)
        val batch = decoded(packet(
            rows = JSONArray().put(event(1, oldDoc)),
            docId = doc))!!
        assertNull(native.accept("tab-A", setOf(doc), 0, batch))
        assertEquals(1L, native.accept("tab-A", setOf(doc, oldDoc), 0, batch))
        native.stop("USER_STOP")
        assertTrue(recorder.awaitStopped())
        val records = recorder.privateLines(owner).map(::JSONObject)
        assertTrue(records.any {
            it.optString("kind") == "event" &&
                it.getJSONObject("trace").optString("siteId") == "site-A"
        })
    }
}

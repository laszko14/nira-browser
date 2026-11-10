package com.prirai.android.nira.browser

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MpvPlayEvidenceReportTest {
    private val token = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    private val doc = "abcdefabcdefabcdefabcdefabcdefab"
    private val subDoc = "0123456789abcdef0123456789abcdef"

    private fun ready(ledger: MpvPlayDiagnosticLedger, subframe: Boolean = false) {
        ledger.connected()
        ledger.startSent(0)
        assertTrue(ledger.startAck(JSONObject().put("schemaVersion", 2)
            .put("event", "start_ack").put("sessionId", token)
            .put("documentId", doc).put("frame", "TOP")))
        if (subframe) assertTrue(ledger.startAck(JSONObject().put("schemaVersion", 2)
            .put("event", "start_ack").put("sessionId", token)
            .put("documentId", subDoc).put("frame", "SUBFRAME")))
    }

    private fun packet(seq: Int, kind: String, target: String, detail: String,
                       metrics: JSONObject = JSONObject()): JSONObject =
        JSONObject().apply {
            put("schemaVersion", 2)
            put("event", "observation")
            put("sessionId", token)
            val frame = metrics.optString("frame", "TOP")
            put("documentId", if (frame == "SUBFRAME") subDoc else doc)
            put("sequence", seq)
            put("kind", kind)
            put("target", target)
            put("detail", detail)
            put("metrics", if (metrics.has("frame")) metrics else metrics.put("frame", "TOP"))
        }

    private fun zip(ledger: MpvPlayDiagnosticLedger): Map<String, String> {
        val parts = mutableMapOf<String,String>()
        ZipInputStream(ByteArrayInputStream(ledger.toZipBytes())).use { stream ->
            while (true) {
                val next = stream.nextEntry ?: break
                parts[next.name] = stream.readBytes().toString(Charsets.UTF_8)
                stream.closeEntry()
            }
        }
        return parts
    }

    @Test fun emptyCaptureNeverClaimsPlayOwner() {
        val ledger = MpvPlayDiagnosticLedger(token, "p", "t", null, "v", 0, 0)
        ledger.finish(10, 10, "STOPPED")
        val entries = zip(ledger)
        val report = JSONObject(entries.getValue("analysis.json"))
        assertEquals("UNDETERMINED", report.getString("diagnosis"))
        assertEquals("NOT_ESTABLISHED", report.getString("attribution_confidence"))
        assertEquals("UNKNOWN", report.getString("play_owner"))
        assertEquals("NONE", report.getString("evidence_level"))
        assertEquals(0, report.getInt("page_events"))
        val gaps = report.getJSONArray("missing_evidence").toString()
        assertTrue(gaps.contains("NO_PAGE_EVENTS"))
        assertTrue(gaps.contains("NO_SELECTED_CONTROL"))
        assertTrue(gaps.contains("ADDON_INVENTORY_UNAVAILABLE"))
        assertTrue(gaps.contains("REAL_SITE_REPRO_NOT_VERIFIED"))
        assertTrue(entries.getValue("analysis.txt").contains("NIE USTALONO"))
        assertFalse(entries.getValue("analysis.txt").contains("example.test"))
    }

    @Test fun typedHintsCannotBeConflatedWithVerifiedAttribution() {
        val ledger = MpvPlayDiagnosticLedger(token, "p", "t", "example.test", "v", 100, 0)
        ready(ledger, subframe = true)
        assertTrue(ledger.mark(10, "native AppDecOr sensitive-tag=NEVER-EXPORT-AS-HINT"))
        assertTrue(ledger.appendPageEvent(packet(1,"DOM","BUTTON","PICKED_ELEMENT",
            JSONObject().put("playHint",true).put("frame","TOP")),1))
        assertTrue(ledger.appendPageEvent(packet(2,"MEDIA","VIDEO","VIDEO_STATE",
            JSONObject().put("helperMarker",true).put("controls",true)
                .put("poster",true).put("frame","SUBFRAME")),2))
        assertTrue(ledger.appendPageEvent(packet(3,"PICK","BUTTON","USER_SELECTED",
            JSONObject().put("node",3)),3))
        assertTrue(ledger.appendPageEvent(packet(4,"MEDIA","VIDEO","ON_PLAY",
            JSONObject().put("paused",false)),4))
        assertTrue(ledger.appendPageEvent(packet(5,"ERROR","OTHER","JS_ERROR"),5))
        assertTrue(ledger.recordAddons(setOf("nira-bg-play@prirai.android.nira",
            "third-party-secret@domain.invalid"), true))
        ledger.finish(20, 120, "STOPPED")
        val parts = zip(ledger)
        val report = JSONObject(parts.getValue("analysis.json"))
        val hints = report.getJSONArray("hints").toString()
        for (name in listOf("HTML5_CONTROLS_PRESENT","PREVIEW_ASSIST_MARKER_PRESENT",
            "BGPLAY_PATCH_POSSIBLE","PLAY_EVENT_OBSERVED","JAVASCRIPT_ERROR_OBSERVED",
            "SUBFRAME_OBSERVED","VIDEO_POSTER_PRESENT","SITE_PLAY_LABEL_HINT")) {
            assertTrue("Missing hint: $name", hints.contains(name))
        }
        assertEquals("PASSIVE_HINTS_ONLY", report.getString("evidence_level"))
        assertEquals("UNKNOWN", report.getString("play_owner"))
        assertEquals(1, report.getInt("selected_controls"))
        assertEquals(2, report.getInt("video_records"))
        assertEquals(2, report.getInt("documents_observed"))
        assertFalse(parts.getValue("analysis.json").contains("third-party-secret"))
        assertFalse(parts.getValue("analysis.txt").contains("NEVER-EXPORT-AS-HINT"))
        assertEquals("UNDETERMINED", report.getString("diagnosis"))
    }

    @Test fun rejectedMalformedStringsAndDataLossAreReportedAsGaps() {
        val ledger = MpvPlayDiagnosticLedger(token, "p", "t", null, "v", 0, 0)
        ready(ledger)
        assertFalse(ledger.appendPageEvent(packet(1,"DOM","BUTTON","PRIVATE_SECRET"), 1))
        assertFalse(ledger.appendPageEvent(packet(1,"DOM","BUTTON","BASELINE",
            JSONObject().put("frame","SESSION_TOKEN")), 1))
        assertFalse(ledger.appendPageEvent(packet(1,"DOM","BUTTON","BASELINE",
            JSONObject().put("paused", "YES")), 1))
        assertTrue(ledger.appendPageEvent(packet(1,"GAP","OTHER","EVENT_LIMIT",
            JSONObject().put("dropped",1)),2))
        ledger.finish(4, 4, "STOPPED")
        val analysis = JSONObject(zip(ledger).getValue("analysis.json"))
        val gaps = analysis.getJSONArray("missing_evidence").toString()
        assertTrue(gaps.contains("REJECTED_PAGE_PACKETS"))
        assertTrue(gaps.contains("PAGE_EVENT_LIMIT_REACHED"))
        assertTrue(analysis.getBoolean("event_limit"))
        assertEquals(3, analysis.getInt("rejected_packets"))
        assertFalse(analysis.toString().contains("PRIVATE_SECRET"))
        assertFalse(analysis.toString().contains("SESSION_TOKEN"))
    }

    @Test fun portMembershipRequiresSamePortObjectNotSameUrlOrName() {
        data class SyntheticPort(val name: String, val url: String)
        val connected = SyntheticPort("diagnostics", "https://example.test/frame")
        val stale = SyntheticPort("diagnostics", "https://example.test/frame")
        assertEquals(connected, stale)
        assertTrue(MpvPlayPortIdentity.matches(listOf(connected), connected))
        assertFalse(MpvPlayPortIdentity.matches(listOf(connected), stale))
        assertFalse(MpvPlayPortIdentity.matches(emptyList(), connected))
    }

    @Test fun nativeTimelineIsTimeOrderedEvenWhenCallbacksArriveOutOfOrder() {
        val ledger = MpvPlayDiagnosticLedger(token, "p", "t", null, "v", 0, 0)
        ready(ledger)
        assertTrue(ledger.appendPageEvent(packet(1,"DOM","VIDEO","BASELINE"), 7))
        assertTrue(ledger.mark(3, "native"))
        ledger.finish(10, 10, "STOPPED")
        val timeline = JSONArray(zip(ledger).getValue("timeline.json"))
        assertEquals(0L, timeline.getJSONObject(0).getLong("elapsed_ms"))
        assertEquals(3L, timeline.getJSONObject(1).getLong("elapsed_ms"))
        assertEquals(7L, timeline.getJSONObject(2).getLong("elapsed_ms"))
        assertEquals(10L, timeline.getJSONObject(3).getLong("elapsed_ms"))
    }

    @Test fun final5BoundedSaturatedCaptureNeverExceedsZipLimit() {
        val ledger = MpvPlayDiagnosticLedger(
            token, "normal", "tab-a", "example.test", "v-stage5", 100L, 0L)
        ready(ledger, subframe = true)
        repeat(MpvPlayDiagnosticLedger.MAX_MARKS) { index ->
            assertTrue(ledger.mark(index.toLong() + 1L, "N".repeat(24_000)))
        }
        assertFalse(ledger.mark(9L, "extra"))
        repeat(MpvPlayDiagnosticLedger.MAX_PAGE_EVENTS) { index ->
            assertTrue("Could not accept event $index",
                ledger.appendPageEvent(packet(index + 1, "MEDIA", "VIDEO", "BASELINE",
                    JSONObject().put("frame", "SUBFRAME").put("readyState", 3)
                        .put("networkState", 2).put("controls", true)
                        .put("helperMarker", true).put("paused", false)
                        .put("poster", true).put("currentTime", 14)
                        .put("duration", 80).put("node", index + 1)), index.toLong()))
        }
        assertFalse(ledger.appendPageEvent(packet(161, "MEDIA", "VIDEO", "BASELINE"), 161))
        ledger.finish(200L, 300L, "STOPPED")
        val bytes = ledger.toZipBytes()
        assertTrue("Archive must stay within limit",
            bytes.size <= MpvPlayDiagnosticLedger.MAX_ARCHIVE_BYTES)
        val parts = zip(ledger)
        assertEquals(8, JSONObject(parts.getValue("summary.json")).getInt("marks"))
        assertEquals(160, JSONArray(parts.getValue("page-events.json")).length())
        assertEquals(160, JSONObject(parts.getValue("analysis.json")).getInt("page_events"))
        assertTrue(JSONObject(parts.getValue("analysis.json")).getJSONArray("missing_evidence")
            .toString().contains("PAGE_EVENT_LIMIT_REACHED"))
        assertFalse(parts.getValue("page-events.json").contains("example.test"))
        assertEquals(9, parts.keys.size)
    }

    @Test fun final5RejectsUntrustedMetadataEvenWhenItLooksLikeAnEnum() {
        val ledger = MpvPlayDiagnosticLedger(
            token, "normal", "tab-a", null, "v-stage5", 100L, 0L)
        ready(ledger)
        for ((key,value) in listOf(
            "frame" to "SESSION_TOKEN",
            "display" to "SECRET",
            "visibility" to "PASSWORD",
            "pointerEvents" to "PRIVATE",
            "preload" to "AUTH_TOKEN",
            "muted" to "TRUE",
            "node" to "TOKEN"
        )) {
            assertFalse("Accepted invalid metric $key=$value",
                ledger.appendPageEvent(packet(1,"DOM","BUTTON","BASELINE",
                    JSONObject().put(key,value)), 1L))
        }
        assertFalse(ledger.appendPageEvent(packet(1,"MEDIA","VIDEO","PRIVATE"),1L))
        assertTrue(ledger.appendPageEvent(packet(1,"MEDIA","VIDEO","BASELINE",
            JSONObject().put("frame","TOP").put("muted",true).put("node",1)),2L))
        ledger.finish(3L,103L,"STOPPED")
        val parts = zip(ledger)
        val report = parts.getValue("analysis.json")
        assertEquals(8,JSONObject(report).getInt("rejected_packets"))
        assertFalse(parts.getValue("page-events.json").contains("PASSWORD"))
        assertFalse(parts.getValue("page-events.json").contains("TOKEN"))
        assertEquals("UNKNOWN", JSONObject(report).getString("play_owner"))
    }

}

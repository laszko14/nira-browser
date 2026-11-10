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

    private fun packet(seq: Int, kind: String, target: String, detail: String,
                       metrics: JSONObject = JSONObject()): JSONObject =
        JSONObject().apply {
            put("schemaVersion", 2)
            put("event", "observation")
            put("sessionId", token)
            put("documentId", doc)
            put("sequence", seq)
            put("kind", kind)
            put("target", target)
            put("detail", detail)
            put("metrics", metrics)
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
        assertEquals(1, report.getInt("documents_observed"))
        assertFalse(parts.getValue("analysis.json").contains("third-party-secret"))
        assertFalse(parts.getValue("analysis.txt").contains("NEVER-EXPORT-AS-HINT"))
        assertEquals("UNDETERMINED", report.getString("diagnosis"))
    }

    @Test fun rejectedMalformedStringsAndDataLossAreReportedAsGaps() {
        val ledger = MpvPlayDiagnosticLedger(token, "p", "t", null, "v", 0, 0)
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
        assertTrue(ledger.appendPageEvent(packet(1,"DOM","VIDEO","BASELINE"), 7))
        assertTrue(ledger.mark(3, "native"))
        ledger.finish(10, 10, "STOPPED")
        val timeline = JSONArray(zip(ledger).getValue("timeline.json"))
        assertEquals(0L, timeline.getJSONObject(0).getLong("elapsed_ms"))
        assertEquals(3L, timeline.getJSONObject(1).getLong("elapsed_ms"))
        assertEquals(7L, timeline.getJSONObject(2).getLong("elapsed_ms"))
        assertEquals(10L, timeline.getJSONObject(3).getLong("elapsed_ms"))
    }
}

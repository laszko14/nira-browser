package com.prirai.android.nira.browser

import org.json.JSONArray
import org.json.JSONObject

/**
 * Stage 4 deterministic triage of already allowlisted metadata. A collection of
 * UI hints cannot identify the owner of a particular Play button. No raw URLs,
 * DOM text, token values, selectors, media source or third-party addon IDs.
 */
internal object MpvPlayEvidenceReport {
    private val hintOrder = listOf(
        "SITE_PLAY_LABEL_HINT", "HTML5_CONTROLS_PRESENT",
        "PREVIEW_ASSIST_MARKER_PRESENT", "BGPLAY_PATCH_POSSIBLE",
        "PLAY_EVENT_OBSERVED", "JAVASCRIPT_ERROR_OBSERVED",
        "SUBFRAME_OBSERVED", "VIDEO_POSTER_PRESENT",
        "OPEN_SHADOW_OBSERVED", "VIDEO_PLAYING_EVENT", "PREVIEW_WAITING_EVENT",
        "DECLARED_PREVIEW_ATTRIBUTE_OBSERVED"
    )

    fun build(pageEvents: List<JSONObject>, marks: Int, rejected: Int,
              addonInventory: JSONObject): JSONObject {
        val hints = linkedSetOf<String>()
        val frames = linkedSetOf<String>()
        var picked = 0
        var video = 0
        var errors = 0
        var eventLimit = false
        var shadowLimit = false
        var scanLimit = false
        var manualSnapshots = 0
        val documents = linkedSetOf<String>()
        pageEvents.forEach { item ->
            val kind = item.optString("kind")
            val detail = item.optString("detail")
            val target = item.optString("target")
            val m = item.optJSONObject("metrics") ?: JSONObject()
            val documentId = item.optString("document_id")
            if (documentId.matches(Regex("[0-9a-f]{32}"))) documents.add(documentId)
            val frame = m.optString("frame")
            if (frame == "TOP" || frame == "SUBFRAME") frames.add(frame)
            if (frame == "SUBFRAME") hints.add("SUBFRAME_OBSERVED")
            if (m.optBoolean("shadow", false)) hints.add("OPEN_SHADOW_OBSERVED")
            if (m.optBoolean("previewHint", false))
                hints.add("DECLARED_PREVIEW_ATTRIBUTE_OBSERVED")
            if (detail == "ON_PLAYING" && target == "VIDEO")
                hints.add("VIDEO_PLAYING_EVENT")
            if (detail == "ON_WAITING" && target == "VIDEO")
                hints.add("PREVIEW_WAITING_EVENT")
            if (kind == "PICK" && detail == "USER_SELECTED") picked++
            if (detail == "USER_MARK") manualSnapshots++
            if (kind == "MEDIA" && target == "VIDEO") video++
            if (kind == "ERROR") {
                errors++
                hints.add("JAVASCRIPT_ERROR_OBSERVED")
            }
            if (kind == "GAP" && detail == "EVENT_LIMIT") eventLimit = true
            if (kind == "GAP" && detail == "SHADOW_LIMIT") shadowLimit = true
            if (kind == "GAP" && detail == "SCAN_LIMIT") scanLimit = true
            if (kind == "DOM" && target in setOf("BUTTON", "A") &&
                m.optBoolean("playHint", false)) hints.add("SITE_PLAY_LABEL_HINT")
            if (kind == "MEDIA" && target == "VIDEO") {
                if (m.optBoolean("controls", false)) hints.add("HTML5_CONTROLS_PRESENT")
                if (m.optBoolean("helperMarker", false)) hints.add("PREVIEW_ASSIST_MARKER_PRESENT")
                if (m.optBoolean("poster", false)) hints.add("VIDEO_POSTER_PRESENT")
                if (detail == "ON_PLAY") hints.add("PLAY_EVENT_OBSERVED")
            }
        }
        if (addonInventory.optString("bg_play") == "INSTALLED" &&
            addonInventory.optString("bgplay_current_origin_permission") == "ALLOWED") {
            hints.add("BGPLAY_PATCH_POSSIBLE")
        }
        val gaps = linkedSetOf<String>()
        if (pageEvents.isEmpty()) gaps.add("NO_PAGE_EVENTS")
        if (marks == 0) gaps.add("NO_NATIVE_MARKER")
        if (picked == 0) gaps.add("NO_SELECTED_CONTROL")
        if (video == 0) gaps.add("NO_VIDEO_METADATA")
        if (addonInventory.optString("evidence") != "GECKO_INSTALLED_LIST")
            gaps.add("ADDON_INVENTORY_UNAVAILABLE")
        if (rejected > 0) gaps.add("REJECTED_PAGE_PACKETS")
        if (shadowLimit) gaps.add("OPEN_SHADOW_ROOT_LIMIT_REACHED")
        if (scanLimit) gaps.add("BOUNDED_DOM_SCAN_LIMIT_REACHED")
        if (eventLimit || pageEvents.size >= MpvPlayDiagnosticLedger.MAX_PAGE_EVENTS)
            gaps.add("PAGE_EVENT_LIMIT_REACHED")
        // Coverage of closed Shadow DOM, permission-restricted frames, and site-
        // handled play() rejections cannot be proven from this protocol.
        gaps.add("INACCESSIBLE_DOM_NOT_EXCLUDED")
        gaps.add("CLOSED_SHADOW_ROOTS_NOT_OBSERVABLE")
        gaps.add("CROSS_ORIGIN_FRAME_COVERAGE_UNVERIFIED")
        gaps.add("CAUGHT_PLAY_REJECTIONS_NOT_OBSERVED")
        gaps.add("REAL_SITE_REPRO_NOT_VERIFIED")
        val actions = linkedSetOf<String>()
        if (pageEvents.isEmpty()) actions.add("RELOAD_PAGE_AND_REPEAT")
        if (marks == 0 || picked == 0) actions.add("MARK_AND_SELECT_PLAY_CONTROL")
        if (video == 0) actions.add("TRIGGER_PREVIEW_AND_CAPTURE_VIDEO")
        if (eventLimit) actions.add("SHORTEN_CAPTURE_TO_AVOID_TRUNCATION")
        actions.add("VERIFY_EXPORT_ON_REAL_SITE_AND_DEVICE")
        return JSONObject().apply {
            put("schema", "zavro-preview-triage-stage4-v2")
            put("diagnosis", "UNDETERMINED")
            put("attribution_confidence", "NOT_ESTABLISHED")
            put("play_owner", "UNKNOWN")
            put("evidence_level", if (pageEvents.isEmpty()) "NONE" else "PASSIVE_HINTS_ONLY")
            put("page_events", pageEvents.size)
            put("documents_observed", documents.size)
            put("frames_observed", JSONArray(frames.sorted()))
            put("native_markers", marks)
            put("page_mark_snapshots", manualSnapshots)
            put("selected_controls", picked)
            put("video_records", video)
            put("script_errors", errors)
            put("rejected_packets", rejected)
            put("event_limit", eventLimit)
            put("open_shadow_limit", shadowLimit)
            put("dom_scan_limit", scanLimit)
            put("observation_isolation", "EXTENSION_ISOLATED_WORLD")
            put("causal_attribution", "NOT_PROVEN_BY_EVENT_TYPES")
            put("hints", JSONArray(hintOrder.filter { it in hints }))
            put("missing_evidence", JSONArray(gaps.toList()))
            put("recommended_checks", JSONArray(actions.toList()))
            put("interpretation", "HINTS_ARE_NOT_CAUSAL_PROOF")
            put("external_mpv", "SEPARATE_INTENT_ACTION_NOT_REMOVED")
        }
    }

    fun explanation(data: JSONObject): String {
        fun values(key: String): String {
            val items = data.getJSONArray(key)
            return (0 until items.length()).joinToString(", ") { items.getString(it) }
                .ifEmpty { "BRAK" }
        }
        return listOf(
            "ZAVRO CLEAN — RAPORT DIAGNOSTYCZNY PLAY (ETAP 5/6)",
            "WYNIK: NIE USTALONO ŹRÓDŁA PRZYCISKU PLAY.",
            "Wskazówki są obserwacjami, nie dowodami przyczynowymi.",
            "Stan zbierania: ${data.optString("transport_state", "UNKNOWN")}; przyczyna: ${data.optString("transport_failure", "NONE")}.",
            "Zdarzenia strony: ${data.getInt("page_events")}; wskazane elementy: ${data.getInt("selected_controls")}.",
            "Zaobserwowane sygnały: ${values("hints")}",
            "Brakujące dowody i ograniczenia: ${values("missing_evidence")}",
            "Następne kroki: ${values("recommended_checks")}",
            "Zainstalowany dodatek nie oznacza jego uruchomienia na stronie.",
            "Obserwacja dostępnych ramek i otwartych Shadow DOM jest ograniczona; zamknięte korzenie i nieuprawnione ramki pozostają poza zasięgiem.",
            "Ruchome miniatury, ich odtwarzanie i zewnętrzne MPV Alpha nie zostały zmienione.",
            "Szczegółowe, filtrowane zdarzenia znajdują się w page-events.json."
        ).joinToString("\n", postfix = "\n")
    }
}

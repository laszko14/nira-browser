package com.prirai.android.nira.browser

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.prirai.android.nira.BuildConfig
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.diagnostics.testlab.DiagnosticOwner
import com.prirai.android.nira.diagnostics.testlab.DiagnosticSessionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Opt-in, bounded stage 4 in-memory diagnostic session. Content data is limited
 * to typed categories and numeric/boolean metadata. This ledger neither proves
 * the cause of Play UI nor records raw site markup, source URLs or credentials.
 */
internal class MpvPlayDiagnosticLedger(
    val sessionId: String,
    val ownerProfile: String,
    val ownerTab: String,
    val host: String?,
    val version: String,
    val startedWallMs: Long,
    val startedElapsedMs: Long,
) {
    enum class Transport { NOT_READY, CONNECTED, PORT_READY, START_SENT, START_ACKNOWLEDGED,
        CAPTURING, NO_PORT, NO_READY, NO_START, NO_ACK, NO_PAGE_EVENTS, NO_BASELINE,
        DISCONNECTED, FAILED }
    enum class Failure { NONE, NO_PORT, NO_READY, NO_START, NO_ACK, NO_PAGE_EVENTS,
        NO_BASELINE, PORT_DISCONNECTED, INSTALL_FAILED, HANDLER_FAILED, POST_FAILED,
        INVALID_ACK }
    enum class Drop { INVALID_TYPE, PARSE_ERROR, BAD_SCHEMA, SESSION_MISMATCH,
        DOCUMENT_MISMATCH, STALE_PORT, UNEXPECTED_EVENT }
    companion object {
        const val MAX_MARKS = 8
        const val MAX_SNAPSHOT_CHARS = 12_000
        const val MAX_ARCHIVE_BYTES = 512 * 1024
        const val MAX_PAGE_EVENTS = 160
        const val MAX_PAGE_PACKET_CHARS = 2_300
    }

    private data class Entry(val elapsedMs: Long, val kind: String, val note: String)
    private val entries = mutableListOf(Entry(0L, "started", "User-initiated bounded diagnostics"))
    private val snapshots = mutableListOf<String>()
    private val pageEvents = mutableListOf<JSONObject>()
    private val lastSequence = mutableMapOf<String, Int>()
    private var pageRejected = 0
    // Real Gecko installed-addon snapshot, collected only during an explicit
    // session. It is NOT proof that a script executed on the target website.
    private var addonInventory = MpvPlayAddonEvidence.unknown()
    var transport = Transport.NOT_READY
        private set
    var failure = Failure.NONE
        private set
    private var portsConnected = 0
    private var portsDisconnected = 0
    private var openPorts = 0
    private var startAttempts = 0
    private var retryAttempts = 0
    private var startAcknowledgements = 0
    private var transportRejected = 0
    private var messagesReceived = 0
    private var portsReady = 0
    private var invalidAcks = 0
    private val drops = Drop.entries.associateWith { 0 }.toMutableMap()
    private var baselineSnapshots = 0
    private var lastTransportProgressMs = 0L
    private val acknowledgedFrames = mutableMapOf<String, String>()
    private val transportTimeoutMs = 8_000L

    private var result = "RUNNING"
    private var stoppedWallMs: Long? = null

    val active: Boolean get() = result == "RUNNING"
    val markCount: Int get() = snapshots.size

    fun connected(elapsedMs: Long = 0) {
        if (!active || transport in setOf(Transport.NO_PORT, Transport.NO_READY, Transport.NO_START, Transport.NO_ACK,
                Transport.NO_PAGE_EVENTS, Transport.NO_BASELINE)) return
        portsConnected++
        openPorts++
        lastTransportProgressMs = elapsedMs.coerceAtLeast(0)
        if (transport in setOf(Transport.NOT_READY, Transport.DISCONNECTED, Transport.FAILED)) {
            transport = Transport.CONNECTED
            failure = Failure.NONE
        }
    }

    fun ready(elapsedMs: Long) {
        if (!active) return
        portsReady = (portsReady + 1).coerceAtMost(10_000)
        lastTransportProgressMs = elapsedMs.coerceAtLeast(0)
        if (transport in setOf(Transport.NOT_READY, Transport.CONNECTED, Transport.DISCONNECTED)) {
            transport = Transport.PORT_READY
            failure = Failure.NONE
        }
    }

    fun received() {
        if (active) messagesReceived = (messagesReceived + 1).coerceAtMost(10_000)
    }

    fun drop(cause: Drop) {
        if (!active) return
        transportRejected = (transportRejected + 1).coerceAtMost(10_000)
        drops[cause] = ((drops[cause] ?: 0) + 1).coerceAtMost(10_000)
    }

    fun invalidAck() {
        if (active) invalidAcks = (invalidAcks + 1).coerceAtMost(10_000)
        // startAck already counts this rejection.
    }

    fun startSent(elapsedMs: Long, retry: Boolean = false) {
        if (!active || transport in setOf(Transport.NO_PORT, Transport.NO_READY, Transport.NO_START, Transport.NO_ACK,
                Transport.NO_PAGE_EVENTS, Transport.NO_BASELINE)) return
        startAttempts++
        if (retry) retryAttempts++
        lastTransportProgressMs = elapsedMs.coerceAtLeast(0)
        if (transport != Transport.CAPTURING && transport != Transport.START_ACKNOWLEDGED) {
            transport = Transport.START_SENT
            failure = Failure.NONE
        }
    }

    fun startAck(packet: JSONObject, elapsedMs: Long = 0): Boolean {
        if (!active || transport in setOf(Transport.NO_PORT, Transport.NO_READY, Transport.NO_START, Transport.NO_ACK,
                Transport.NO_PAGE_EVENTS, Transport.NO_BASELINE) || packet.length() != 5 ||
            packet.optInt("schemaVersion") != 2 ||
            packet.optString("event") != "start_ack" ||
            packet.optString("sessionId") != sessionId || startAttempts == 0) {
            transportRejected++
            return false
        }
        val doc = packet.optString("documentId")
        val frame = packet.optString("frame")
        if (!doc.matches(Regex("[0-9a-f]{32}")) || frame !in setOf("TOP", "SUBFRAME") ||
            (acknowledgedFrames.size >= 24 && doc !in acknowledgedFrames) ||
            (acknowledgedFrames[doc] != null && acknowledgedFrames[doc] != frame)) {
            transportRejected++
            return false
        }
        if (doc !in acknowledgedFrames) startAcknowledgements++
        acknowledgedFrames[doc] = frame
        lastTransportProgressMs = elapsedMs.coerceAtLeast(0)
        if (transport != Transport.CAPTURING) transport = Transport.START_ACKNOWLEDGED
        failure = Failure.NONE
        return true
    }

    fun disconnected(elapsedMs: Long = 0) {
        if (!active) return
        portsDisconnected++
        openPorts = (openPorts - 1).coerceAtLeast(0)
        lastTransportProgressMs = elapsedMs.coerceAtLeast(0)
        if (openPorts == 0) {
            transport = Transport.DISCONNECTED
            failure = Failure.PORT_DISCONNECTED
        }
    }

    fun transportFailed(cause: Failure) {
        if (!active) return
        if (cause !in setOf(Failure.INSTALL_FAILED, Failure.HANDLER_FAILED,
                Failure.POST_FAILED, Failure.INVALID_ACK)) return
        transport = Transport.FAILED
        failure = cause
    }

    fun refreshTransport(elapsedMs: Long, terminal: Boolean = false) {
        if (!active) return
        if (transport in setOf(Transport.CAPTURING, Transport.FAILED,
                Transport.NO_PORT, Transport.NO_READY, Transport.NO_START, Transport.NO_ACK, Transport.NO_PAGE_EVENTS,
                Transport.NO_BASELINE)) return
        val elapsed = elapsedMs.coerceAtLeast(0)
        when {
            transport == Transport.DISCONNECTED &&
                (terminal || elapsed - lastTransportProgressMs >= transportTimeoutMs) -> {
                transport = Transport.FAILED; failure = Failure.PORT_DISCONNECTED
            }
            transport == Transport.DISCONNECTED -> Unit
            startAcknowledgements > 0 && (terminal ||
                elapsed - lastTransportProgressMs >= transportTimeoutMs) -> {
                if (pageEvents.isEmpty()) {
                    transport = Transport.NO_PAGE_EVENTS; failure = Failure.NO_PAGE_EVENTS
                } else {
                    transport = Transport.NO_BASELINE; failure = Failure.NO_BASELINE
                }
            }
            startAttempts > 0 && (terminal ||
                elapsed - lastTransportProgressMs >= transportTimeoutMs) -> {
                transport = Transport.NO_ACK; failure = Failure.NO_ACK
            }
            portsConnected == 0 && (terminal || elapsed >= transportTimeoutMs) -> {
                transport = Transport.NO_PORT; failure = Failure.NO_PORT
            }
            portsConnected > 0 && portsReady == 0 &&
                (terminal || elapsed - lastTransportProgressMs >= transportTimeoutMs) -> {
                transport = Transport.NO_READY; failure = Failure.NO_READY
            }
            portsConnected > 0 && startAttempts == 0 &&
                (terminal || elapsed - lastTransportProgressMs >= transportTimeoutMs) -> {
                transport = Transport.NO_START; failure = Failure.NO_START
            }
        }
    }

    fun mark(elapsedMs: Long, nativeSnapshot: String): Boolean {
        if (!active || snapshots.size >= MAX_MARKS) return false
        val capped = nativeSnapshot.take(MAX_SNAPSHOT_CHARS)
        snapshots.add("MARK " + snapshots.size + " (" + elapsedMs.coerceAtLeast(0L) +
            "ms; APP_DECOR_ONLY)\n" + capped)
        entries.add(Entry(elapsedMs.coerceAtLeast(0L), "manual_problem_marker",
            "Native app-decor sample; not DOM or site JS"))
        return true
    }

    /**
     * Allowlisted schema: no free-form text, URLs, selectors, DOM values, scripts,
     * cookies or network payloads. The content extension is NOT a trust boundary.
     */
    fun appendPageEvent(message: JSONObject, receivedMs: Long): Boolean {
        if (!active || transport in setOf(Transport.NO_PORT, Transport.NO_READY, Transport.NO_START, Transport.NO_ACK,
                Transport.NO_PAGE_EVENTS, Transport.NO_BASELINE)) return false
        fun reject(): Boolean { pageRejected++; return false }
        if (message.toString().length > MAX_PAGE_PACKET_CHARS ||
            message.length() != 9 || message.optInt("schemaVersion") != 2 ||
            message.optString("event") != "observation" ||
            message.optString("sessionId") != sessionId) return reject()
        val documentId = message.optString("documentId")
        if (!documentId.matches(Regex("[0-9a-f]{32}"))) return reject()
        val expectedFrame = acknowledgedFrames[documentId] ?: return reject()
        val sequence = message.optInt("sequence", -1)
        if (sequence <= 0 || sequence > 160 ||
            sequence <= (lastSequence[documentId] ?: 0)) return reject()
        val kind = message.optString("kind")
        val target = message.optString("target")
        val detail = message.optString("detail")
        // Reject arbitrary uppercase strings as well as malformed text: both
        // can accidentally contain confidential website state.
        if (kind !in setOf("DOM", "MEDIA", "EVENT", "MUTATION", "ERROR", "PICK", "GAP") ||
            target !in setOf("VIDEO", "SOURCE", "BUTTON", "A", "IFRAME",
                "DIV", "SPAN", "IMG", "PICTURE", "SECTION", "ARTICLE",
                "OTHER", "UNKNOWN") ||
            detail !in setOf("BASELINE", "USER_MARK", "PICKED_ELEMENT",
                "ATTRIBUTE_STATE", "SNAPSHOT_FINISHED", "VIDEO_STATE",
                "ON_POINTERDOWN", "ON_POINTEROVER", "ON_CLICK",
                "ON_PLAY", "ON_PAUSE", "ON_LOADEDMETADATA", "ON_ERROR",
                "DOCUMENT_VISIBILITY", "ATTRIBUTE_STYLE", "ATTRIBUTE_CLASS",
                "ATTRIBUTE_HIDDEN", "ATTRIBUTE_POSTER", "ATTRIBUTE_SRC",
                "ATTRIBUTE_CONTROLS", "ATTRIBUTE_AUTOPLAY", "ATTRIBUTE_ROLE",
                "ATTRIBUTE_BATCH", "NODES_ADDED", "PROMISE_REJECTION",
                "JS_ERROR", "USER_SELECTED", "DOM_UNAVAILABLE",
                "SCAN_LIMIT", "EVENT_LIMIT")) return reject()
        val metrics = message.optJSONObject("metrics") ?: return reject()
        if (metrics.optString("frame") != expectedFrame) return reject()
        if (metrics.length() > 16) return reject()
        val names = setOf("frame", "shadow", "readyState", "networkState", "paused",
            "muted", "autoplay", "controls", "loop", "duration", "currentTime", "errorCode",
            "width", "height", "x", "y", "z", "display", "visibility", "pointerEvents",
            "poster", "preload", "playsInline", "dropped", "count", "node", "parent",
            "hasId", "hasClass", "hasAria", "playHint", "helperMarker")
        val bools = setOf("shadow", "paused", "muted", "autoplay", "controls",
            "loop", "poster", "playsInline", "hasId", "hasClass", "hasAria",
            "playHint", "helperMarker")
        val enums = mapOf(
            "frame" to setOf("TOP", "SUBFRAME"),
            "display" to setOf("NONE", "BLOCK", "INLINE", "INLINE_BLOCK",
                "FLEX", "GRID", "CONTENTS", "OTHER"),
            "visibility" to setOf("VISIBLE", "HIDDEN", "COLLAPSE", "OTHER"),
            "pointerEvents" to setOf("AUTO", "NONE", "OTHER"),
            "preload" to setOf("NONE", "AUTO", "METADATA", "OTHER"),
        )
        val filtered = JSONObject()
        for (name in metrics.keys()) {
            if (name !in names) return reject()
            val value = metrics.get(name)
            when {
                name in bools && value is Boolean -> filtered.put(name, value)
                name in enums && value is String && value in enums.getValue(name) ->
                    filtered.put(name, value)
                name !in bools && name !in enums && value is Number &&
                    value.toDouble().isFinite() && value.toDouble() in 0.0..32768.0 ->
                    filtered.put(name, value)
                else -> return reject()
            }
        }
        if (pageEvents.size >= MAX_PAGE_EVENTS) return reject()
        lastSequence[documentId] = sequence
        val received = receivedMs.coerceAtLeast(0L)
        pageEvents.add(JSONObject().apply {
            put("received_elapsed_ms", received)
            put("document_id", documentId)
            put("sequence", sequence)
            put("kind", kind)
            put("target", target)
            put("detail", detail)
            put("metrics", filtered)
        })
        entries.add(Entry(received, "page_" + kind.lowercase(), detail))
        if (detail == "SNAPSHOT_FINISHED") {
            baselineSnapshots++
            transport = Transport.CAPTURING
            failure = Failure.NONE
        }
        return true
    }

    fun recordAddons(installed: Set<String>?, originAllowed: Boolean?): Boolean {
        if (!active) return false
        addonInventory = MpvPlayAddonEvidence.snapshot(installed, originAllowed)
        return true
    }

    fun finish(elapsedMs: Long, wallMs: Long, reason: String) {
        if (!active) return
        refreshTransport(elapsedMs, terminal = true)
        result = reason
        stoppedWallMs = wallMs
        entries.add(Entry(elapsedMs.coerceAtLeast(0L), "finished", reason))
    }

    fun toZipBytes(): ByteArray {
        check(!active) { "Stop diagnostics before exporting" }
        val summary = JSONObject().apply {
            put("schema", "zavro-mpv-play-stage4-v2")
            put("session", sessionId)
            put("status", result)
            put("started_utc_ms", startedWallMs)
            put("stopped_utc_ms", stoppedWallMs)
            put("app_version", version)
            put("site_host_only", host ?: JSONObject.NULL)
            put("snapshot_scope", "APP_DECOR_ONLY")
            put("dom_capture", "BOUNDED_STRUCTURE_ONLY")
            put("js_capture", "EVENT_TYPES_AND_ERROR_CLASSES_ONLY")
            put("page_events", pageEvents.size)
            put("page_rejected", pageRejected)
            put("iframe_capture", "PERMITTED_HTTP_FRAMES_ONLY")
            put("shadow_dom", "OPEN_ROOTS_BOUNDED")
            put("network_capture", "NOT_CAPTURED_STAGE4")
            put("addon_inventory_scope", "INSTALLED_ADDONS_NOT_PAGE_SCRIPT_EXECUTION")
            put("play_origin", "NOT_CONFIRMED_WITHOUT_REAL_PAGE_EVIDENCE")
            put("play_promise_rejections", "UNHANDLED_ONLY")
            put("private_tabs", "NOT_SUPPORTED_STAGE1")
            put("automatic_upload", false)
            put("marks", snapshots.size)
            put("transport_state", transport.name)
            put("transport_failure", failure.name)
        }
        val timeline = JSONArray()
        entries.sortedBy { it.elapsedMs }.forEach { entry ->
            timeline.put(JSONObject().apply {
                put("elapsed_ms", entry.elapsedMs)
                put("kind", entry.kind)
                put("note", entry.note)
            })
        }
        val analysis = MpvPlayEvidenceReport.build(pageEvents, markCount,
            pageRejected, addonInventory)
        analysis.put("transport_state", transport.name)
        analysis.put("transport_failure", failure.name)
        analysis.put("diagnostics_complete", transport == Transport.CAPTURING)
        if (transport != Transport.CAPTURING) {
            analysis.getJSONArray("missing_evidence").put("DIAGNOSTIC_TRANSPORT_INCOMPLETE")
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, data: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            add("summary.json", summary.toString(2))
            add("timeline.json", timeline.toString(2))
            add("native-overlay.txt", snapshots.joinToString("\n\n--- NEXT MARK ---\n\n"))
            add("page-events.json", JSONArray(pageEvents).toString(2))
            add("addon-inventory.json", addonInventory.toString(2))
            add("analysis.json", analysis.toString(2))
            add("analysis.txt", MpvPlayEvidenceReport.explanation(analysis))
            add("page-coverage.json", JSONObject().apply {
                put("schema", "zavro-mpv-play-coverage-v2")
                put("snapshot", "STRUCTURE_AND_STATE_NOT_RAW_HTML")
                put("frame_access", "HTTP_FRAMES_WITH_EXTENSION_ACCESS")
                put("shadow_dom", "OPEN_ROOTS_ONLY")
                put("javascript", "EVENT_TYPES_NO_SOURCE_OR_STACKS")
                put("video_play_promise", "UNHANDLED_REJECTION_ONLY")
                put("network", "NOT_RECORDED")
                put("credentials", "NOT_RECORDED")
                put("missing_page_events", pageEvents.isEmpty())
                put("rejected_events", pageRejected)
                put("transport_state", transport.name)
                put("transport_failure", failure.name)
            }.toString(2))
            add("transport.json", JSONObject().apply {
                put("schema", "zavro-mpv-play-transport-v2")
                put("state", transport.name)
                put("failure", failure.name)
                put("ports_connected", portsConnected)
                put("ports_disconnected", portsDisconnected)
                put("start_attempts", startAttempts)
                put("start_acknowledgements", startAcknowledgements)
                put("frames_acknowledged", acknowledgedFrames.size)
                put("packets_rejected", transportRejected)
                put("messages_received", messagesReceived)
                put("ports_ready", portsReady)
                put("start_delivery", "NOT_PROVEN_BY_POST")
                put("invalid_ack", invalidAcks)
                put("baseline_snapshots", baselineSnapshots)
                put("invalid_type", drops.getValue(Drop.INVALID_TYPE))
                put("parse_errors", drops.getValue(Drop.PARSE_ERROR))
                put("bad_schema", drops.getValue(Drop.BAD_SCHEMA))
                put("session_mismatch", drops.getValue(Drop.SESSION_MISMATCH))
                put("document_mismatch", drops.getValue(Drop.DOCUMENT_MISMATCH))
                put("stale_port", drops.getValue(Drop.STALE_PORT))
                put("unexpected_event", drops.getValue(Drop.UNEXPECTED_EVENT))
                put("page_rejected", pageRejected)
                put("retry_count", retryAttempts)
            }.toString(2))
        }
        return out.toByteArray().also { check(it.size <= MAX_ARCHIVE_BYTES) }
    }
}


/**
 * Packaged built-in add-on identifiers, never arbitrary Gecko extension names
 * or user-configured hostnames. A session inventory proves installation only;
 * page-world modifications and the actual Play owner remain unverified.
 */
internal object MpvPlayAddonEvidence {
    private val known = linkedMapOf(
        "media_detector" to "nira-media-detector@prirai.android.nira",
        "bg_play" to "nira-bg-play@prirai.android.nira",
        "page_diagnostics" to "zavro-preview-diagnostics@prirai.android.nira",
    )

    fun unknown(): JSONObject = snapshot(null, null)

    fun snapshot(installed: Set<String>?, allowedOrigin: Boolean?): JSONObject =
        JSONObject().apply {
            put("schema", "zavro-preview-addons-stage3-v1")
            put("evidence", if (installed == null) "NOT_OBSERVED" else "GECKO_INSTALLED_LIST")
            put("execution_on_page", "NOT_CONFIRMED")
            put("play_owner", "UNKNOWN")
            for ((key, id) in known) {
                put(key, if (installed == null) "UNKNOWN" else
                    if (id in installed) "INSTALLED" else "NOT_INSTALLED")
            }
            put("bgplay_current_origin_permission", when (allowedOrigin) {
                true -> "ALLOWED"
                false -> "NOT_ALLOWED"
                null -> "UNKNOWN"
            })
            put("bgplay_page_patch", "NOT_OBSERVED")
            put("preview_assist_declared", "PACKAGED_WITH_MEDIA_DETECTOR")
            put("preview_assist_executed", "NOT_OBSERVED")
            put("raw_extension_ids_exported", false)
        }
}

internal object MpvPlayDiagnostics {
    internal enum class Start { STARTED, PRIVATE_TAB, UNSUPPORTED_PAGE, BUSY }
    internal data class State(val running: Boolean, val reportReady: Boolean,
        val ownerProfile: String?, val ownerTab: String?,
        val transport: MpvPlayDiagnosticLedger.Transport?,
        val failure: MpvPlayDiagnosticLedger.Failure?)
    private const val MAX_DURATION_MS = 15 * 60 * 1000L
    private val lock = Any()
    private var recording: MpvPlayDiagnosticLedger? = null
    private var finished: MpvPlayDiagnosticLedger? = null

    private fun expireIfNeeded() {
        val old = recording ?: return
        val elapsed = SystemClock.elapsedRealtime() - old.startedElapsedMs
        old.refreshTransport(elapsed)
        if (elapsed < MAX_DURATION_MS && old.transport !in setOf(
                MpvPlayDiagnosticLedger.Transport.NO_PORT,
                MpvPlayDiagnosticLedger.Transport.NO_READY,
                MpvPlayDiagnosticLedger.Transport.NO_START,
                MpvPlayDiagnosticLedger.Transport.NO_ACK,
                MpvPlayDiagnosticLedger.Transport.NO_PAGE_EVENTS,
                MpvPlayDiagnosticLedger.Transport.NO_BASELINE) &&
                !(old.transport == MpvPlayDiagnosticLedger.Transport.FAILED && elapsed >= 8_000L)) return
        old.finish(elapsed, System.currentTimeMillis(), "TIMED_OUT")
        recording = null
        finished = old
        DiagnosticSessionGate.shared.release(DiagnosticOwner.VIDEO_PREVIEW_DIAGNOSTICS)
    }

    fun state(): State = synchronized(lock) {
        expireIfNeeded()
        val current = recording
        State(current != null, finished != null, current?.ownerProfile ?: finished?.ownerProfile,
            current?.ownerTab ?: finished?.ownerTab, current?.transport ?: finished?.transport,
            current?.failure ?: finished?.failure)
    }

    fun start(profile: String, tab: String, host: String?, privateTab: Boolean,
              eligiblePage: Boolean = true): Start = synchronized(lock) {
        expireIfNeeded()
        if (privateTab) return@synchronized Start.PRIVATE_TAB
        if (!eligiblePage) return@synchronized Start.UNSUPPORTED_PAGE
        if (recording != null || !DiagnosticSessionGate.shared.acquire(DiagnosticOwner.VIDEO_PREVIEW_DIAGNOSTICS)) {
            return@synchronized Start.BUSY
        }
        try {
            finished = null
            recording = MpvPlayDiagnosticLedger(
                UUID.randomUUID().toString(), profile, tab,
                host?.takeIf { it.length <= 255 && it.matches(Regex("[a-zA-Z0-9.-]+")) },
                BuildConfig.VERSION_NAME, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
            )
            Start.STARTED
        } catch (error: Exception) {
            DiagnosticSessionGate.shared.release(DiagnosticOwner.VIDEO_PREVIEW_DIAGNOSTICS)
            throw error
        }
    }

    fun sessionFor(profile: String, tab: String): String? = synchronized(lock) {
        expireIfNeeded()
        recording?.takeIf { it.ownerProfile == profile && it.ownerTab == tab }?.sessionId
    }

    private fun withSession(profile: String, tab: String, nonce: String,
                            block: (MpvPlayDiagnosticLedger) -> Unit): Boolean = synchronized(lock) {
        expireIfNeeded()
        val ledger = recording ?: return@synchronized false
        if (ledger.ownerProfile != profile || ledger.ownerTab != tab ||
            ledger.sessionId != nonce) return@synchronized false
        block(ledger)
        true
    }

    fun connected(profile: String, tab: String, nonce: String): Boolean =
        withSession(profile, tab, nonce) {
            it.connected(SystemClock.elapsedRealtime() - it.startedElapsedMs)
        }

    fun ready(profile: String, tab: String, nonce: String): Boolean =
        withSession(profile, tab, nonce) {
            it.ready(SystemClock.elapsedRealtime() - it.startedElapsedMs)
        }

    fun received(profile: String, tab: String, nonce: String): Boolean =
        withSession(profile, tab, nonce) { it.received() }

    fun drop(profile: String, tab: String, nonce: String,
             cause: MpvPlayDiagnosticLedger.Drop): Boolean =
        withSession(profile, tab, nonce) { it.drop(cause) }

    fun invalidAck(profile: String, tab: String, nonce: String): Boolean =
        withSession(profile, tab, nonce) { it.invalidAck() }

    fun startSent(profile: String, tab: String, nonce: String,
                  retry: Boolean = false): Boolean =
        withSession(profile, tab, nonce) {
            it.startSent(SystemClock.elapsedRealtime() - it.startedElapsedMs, retry)
        }

    fun disconnected(profile: String, tab: String, nonce: String): Boolean =
        withSession(profile, tab, nonce) {
            it.disconnected(SystemClock.elapsedRealtime() - it.startedElapsedMs)
        }

    fun failed(profile: String, tab: String, nonce: String,
               cause: MpvPlayDiagnosticLedger.Failure): Boolean =
        withSession(profile, tab, nonce) { it.transportFailed(cause) }

    fun ack(profile: String, tab: String, nonce: String, packet: JSONObject): Boolean {
        var result = false
        withSession(profile, tab, nonce) {
            result = it.startAck(packet, SystemClock.elapsedRealtime() - it.startedElapsedMs)
        }
        return result
    }

    fun page(profile: String, tab: String, packet: JSONObject): Boolean = synchronized(lock) {
        expireIfNeeded()
        val ledger = recording ?: return@synchronized false
        if (ledger.ownerProfile != profile || ledger.ownerTab != tab) return@synchronized false
        ledger.appendPageEvent(packet, SystemClock.elapsedRealtime() - ledger.startedElapsedMs)
    }

    /**
     * A delayed Gecko callback must not modify a stopped or newer session.
     * Only known IDs become booleans; no third-party extension list is stored.
     */
    fun addons(profile: String, tab: String, nonce: String,
               installed: Set<String>?, originAllowed: Boolean?): Boolean = synchronized(lock) {
        expireIfNeeded()
        val ledger = recording ?: return@synchronized false
        if (ledger.ownerProfile != profile || ledger.ownerTab != tab ||
            ledger.sessionId != nonce) return@synchronized false
        ledger.recordAddons(installed, originAllowed)
    }

    fun mark(profile: String, tab: String, snapshot: String): Boolean = synchronized(lock) {
        expireIfNeeded()
        val ledger = recording ?: return@synchronized false
        if (ledger.ownerProfile != profile || ledger.ownerTab != tab) return@synchronized false
        ledger.mark(SystemClock.elapsedRealtime() - ledger.startedElapsedMs, snapshot)
    }

    fun stop(profile: String): Boolean = synchronized(lock) {
        expireIfNeeded()
        val ledger = recording ?: return@synchronized false
        if (ledger.ownerProfile != profile) return@synchronized false
        ledger.finish(SystemClock.elapsedRealtime() - ledger.startedElapsedMs,
            System.currentTimeMillis(), "STOPPED")
        recording = null
        finished = ledger
        DiagnosticSessionGate.shared.release(DiagnosticOwner.VIDEO_PREVIEW_DIAGNOSTICS)
        true
    }

    /** Only our ephemeral diagnostic memory is discarded, never browser data. */
    fun discard() = synchronized(lock) {
        if (recording != null) DiagnosticSessionGate.shared.release(DiagnosticOwner.VIDEO_PREVIEW_DIAGNOSTICS)
        recording = null
        finished = null
    }

    /** User-visible CLEAR also removes our own previously exported cache ZIPs. */
    suspend fun clearExports(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, "mpv_play_diagnostics").listFiles()?.filter {
            it.isFile && it.name.startsWith("mpv-play-") && it.name.endsWith(".zip")
        }?.forEach { it.delete() }
    }

    suspend fun export(context: Context, profile: String): Uri? {
        val ledger = synchronized(lock) {
            expireIfNeeded()
            finished?.takeIf { it.ownerProfile == profile }
        } ?: return null
        val manager = ProfileManager.getInstance(context)
        if (manager.isPrivateMode() || manager.getActiveProfile().id != profile) return null
        val bytes = ledger.toZipBytes()
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "mpv_play_diagnostics")
            check(dir.isDirectory || dir.mkdirs()) { "Diagnostic cache unavailable" }
            val file = File(dir, "mpv-play-" + ledger.sessionId + ".zip")
            check(file.canonicalFile.parentFile == dir.canonicalFile)
            // Clean only old files generated by this diagnostic component.
            dir.listFiles()?.filter {
                it.isFile && it.name.startsWith("mpv-play-") &&
                    it.name.endsWith(".zip") && it.name != file.name
            }?.forEach { it.delete() }
            file.outputStream().use { it.write(bytes) }
            if (manager.isPrivateMode() || manager.getActiveProfile().id != profile) {
                file.delete()
                return@withContext null
            }
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }
    }
}

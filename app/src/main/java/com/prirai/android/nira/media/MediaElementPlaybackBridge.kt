package com.prirai.android.nira.media

import android.os.Handler
import android.os.Looper
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.Port
import org.json.JSONObject
import java.util.UUID
import java.util.IdentityHashMap

/**
 * Manual, opt-in and fail-closed WebExtension <-> native MPV element handoff.
 *
 * A port belongs to a real Gecko EngineSession and frame URL, never a
 * page-provided tab/profile id. Only an unambiguous exact-source video is
 * paused. If a frame/element cannot be proven, the caller falls back to the
 * existing media-session pause strategy (or keeps Gecko playback).
 *
 * All state is process-local and main-thread confined; signed URLs and nonces
 * are never persisted, put into Intents or logged.
 */
internal object MediaElementPlaybackBridge {
    const val NATIVE_APP = "nira.media.playback"
    private val main = Handler(Looper.getMainLooper())
    private const val ACK_TIMEOUT_MS = 1_000L
    private const val MAX_PORTS = 96
    private val UUID_PATTERN = Regex("[0-9a-f-]{36}")

    /** Created by our isolated Gecko content script, not a web page tab ID. */
    data class AutoOffer(
        val port: Port,
        val frameKey: String,
        val elementKey: String,
        val pageUrl: String,
        val mediaUrl: String,
        val topFrame: Boolean = false,
        val x: Double = 0.0, val y: Double = 0.0,
        val w: Double = 0.0, val h: Double = 0.0,
        val dpr: Double = 1.0,
    ) {
        override fun toString() = "AutoOffer(<redacted>)"
    }
    /** Registered only by the current foreground browser view. */
    var onAutoOffer: ((AutoOffer) -> Unit)? = null
    data class InlineRect(val x: Double, val y: Double, val w: Double, val h: Double, val dpr: Double)
    var onInlineLayout: ((String, InlineRect) -> Unit)? = null
    private fun rectOf(data: JSONObject): InlineRect? {
        val x=data.optDouble("x",Double.NaN)
        val y=data.optDouble("y",Double.NaN)
        val w=data.optDouble("w",Double.NaN)
        val h=data.optDouble("h",Double.NaN)
        val d=data.optDouble("dpr",Double.NaN)
        if (!listOf(x,y,w,h,d).all { it.isFinite() } ||
            kotlin.math.abs(x)>20000 || kotlin.math.abs(y)>20000 ||
            w !in 0.0..10000.0 || h !in 0.0..10000.0 || d !in .5..5.0) return null
        return InlineRect(x,y,w,h,d)
    }


    fun busy(): Boolean = pending != null || lease != null

    private data class Pending(
        val port: Port,
        val binding: MediaSessionBinding,
        val pageUrl: String,
        val frameUrl: String,
        val nonce: String,
        val callback: (String?) -> Unit,
        val target: AutoOffer?,
        val timeout: Runnable,
    )
    private data class Lease(
        val port: Port,
        val binding: MediaSessionBinding,
        val pageUrl: String,
        val frameUrl: String,
        val nonce: String,
        val target: AutoOffer?,
    )

    private val ports = LinkedHashSet<Port>()
    // The challenge survives acknowledgement for per-frame one-shot fallback.
    private val probes = IdentityHashMap<Port, String>()
    private val verifiedPorts = LinkedHashSet<Port>()
    private var pending: Pending? = null
    private var lease: Lease? = null

    fun connected(port: Port) {
        main.post {
            if (port.engineSession == null) return@post
            if (ports.size >= MAX_PORTS) {
                runCatching { port.disconnect() }
                return@post
            }
            ports.add(port)
            MpvDebugTrace.note(MpvDebugTrace.Section.WEB, "port_connected", port.engineSession)
            val nonce = UUID.randomUUID().toString()
            probes[port] = nonce
            runCatching {
                port.postMessage(JSONObject()
                    .put("version", 1)
                    .put("event", "probe")
                    .put("nonce", nonce))
            }.onFailure {
                MpvDebugTrace.note(MpvDebugTrace.Section.WEB, "probe_send_failed", port.engineSession)
            }
            main.postDelayed({
                if (probes[port] == nonce && !verifiedPorts.contains(port)) {
                    MpvDebugTrace.note(MpvDebugTrace.Section.WEB, "probe_no_ack", port.engineSession)
                }
            }, 2_000L)
        }
    }

    /**
     * Native one-shot fallback when Gecko's content port delivers the challenge
     * but does not deliver extension-to-Android port messages. A cryptographic
     * port-specific challenge and the real EngineSession are both required.
     * URL and cookies are never logged or stored outside this process.
     */
    fun receivedOneShot(source: EngineSession, message: JSONObject): Boolean {
        if (message.optInt("version") != 1) return false
        val event = message.optString("event")
        if (event !in setOf("probe_ack", "auto_offer", "handoff_ack", "inline_layout")) return false
        val challenge = message.optString("probeNonce")
        if (!UUID_PATTERN.matches(challenge)) return false
        main.post {
            val matching = ports.filter {
                it.engineSession === source && probes[it] == challenge
            }
            val port = matching.singleOrNull() ?: return@post
            if (event == "probe_ack") {
                if (verifiedPorts.add(port)) {
                    MpvDebugTrace.note(MpvDebugTrace.Section.WEB, "probe_ack_oneshot", source)
                }
            } else if (verifiedPorts.contains(port)) {
                MpvDebugTrace.note(MpvDebugTrace.Section.WEB, "oneshot_protocol_received", source)
                received(port, message)
            }
        }
        return true
    }

    fun disconnected(port: Port) {
        main.post {
            ports.remove(port)
            probes.remove(port)
            verifiedPorts.remove(port)
            MpvDebugTrace.note(MpvDebugTrace.Section.WEB, "port_disconnected", port.engineSession)
            val p = pending
            if (p?.port === port) {
                pending = null
                main.removeCallbacks(p.timeout)
                p.callback(null)
            }
            if (lease?.port === port) {
                val token=lease?.nonce
                lease=null
                if (token != null)
                    onInlineLayout?.invoke(token,InlineRect(0.0,0.0,0.0,0.0,1.0))
            }
        }
    }

    private fun matches(a: MediaSessionBinding, b: MediaSessionBinding): Boolean =
        a.session === b.session && a.tabId == b.tabId &&
            a.contextId == b.contextId && a.isPrivate == b.isPrivate

    fun prepare(
        tab: MediaTabState,
        sourceUrl: String,
        preferredFrame: String?,
        target: AutoOffer? = null,
        completed: (String?) -> Unit,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (pending != null || lease != null || sourceUrl.length !in 8..8192) {
            MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "prepare_rejected", tab.binding.session)
            completed(null)
            return
        }
        val frame = preferredFrame ?: tab.pageUrl
        if (target != null && (target.pageUrl != frame || target.mediaUrl != sourceUrl ||
            !ports.contains(target.port))) {
            completed(null)
            return
        }
        if (!frame.startsWith("https://") && !frame.startsWith("http://")) {
            completed(null)
            return
        }
        // Multiple same-URL frames are ambiguous. Refuse rather than pausing
        // a random ad player. A frame with no port also refuses safely.
        val candidates = ports.filter {
            (target == null || it === target.port) &&
                it.engineSession === tab.binding.session &&
                runCatching { it.senderUrl() }.getOrNull() == frame
        }
        val port = candidates.singleOrNull()
        if (port == null) {
            MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "prepare_rejected", tab.binding.session)
            completed(null)
            return
        }
        val nonce = UUID.randomUUID().toString()
        val timeout = Runnable {
            val p = pending
            if (p?.nonce == nonce) {
                pending = null
                MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "ack_timeout", p.binding.session)
                // ACK might have arrived after timeout; restore defensively.
                send(port, "restore", nonce)
                p.callback(null)
            }
        }
        pending = Pending(port, tab.binding, tab.pageUrl, frame, nonce, completed, target, timeout)
        MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "prepare_sent", tab.binding.session)
        main.postDelayed(timeout, ACK_TIMEOUT_MS)
        try {
            port.postMessage(JSONObject()
                .put("version", 1)
                .put("event", "prepare")
                .put("nonce", nonce)
                .put("pageUrl", frame)
                .put("mediaUrl", sourceUrl)
                .apply {
                    if (target != null) {
                        put("frameKey", target.frameKey)
                        put("elementKey", target.elementKey)
                    }
                })
        } catch (_: RuntimeException) {
            main.removeCallbacks(timeout)
            pending = null
            completed(null)
        }
    }

    fun received(port: Port, message: Any) {
        main.post {
            if (!ports.contains(port)) return@post
            val data = when (message) {
                is JSONObject -> message
                is String -> if (message.length <= 16384) {
                    runCatching { JSONObject(message) }.getOrNull()
                } else null
                else -> null
            } ?: run {
                MpvDebugTrace.note(MpvDebugTrace.Section.WEB, "message_not_json", port.engineSession)
                return@post
            }
            if (data.optInt("version") != 1) return@post
            if (data.optString("event") == "probe_ack") {
                if (probes[port] == data.optString("nonce")) {
                    verifiedPorts.add(port)
                    MpvDebugTrace.note(MpvDebugTrace.Section.WEB, "probe_ack", port.engineSession)
                }
                return@post
            }
            if (!verifiedPorts.contains(port)) return@post
            if (data.optString("event") == "diagnostic") {
                val code = data.optString("code")
                if (code.length <= 36) {
                    MpvDebugTrace.note(MpvDebugTrace.Section.WEB, code, port.engineSession)
                }
                return@post
            }
            if (data.optString("event") == "auto_offer") {
                MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "auto_offer_received", port.engineSession)
                if (busy() || onAutoOffer == null) {
                    MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "auto_busy", port.engineSession)
                    return@post
                }
                val frameKey = data.optString("frameKey")
                val elementKey = data.optString("elementKey")
                val page = data.optString("pageUrl")
                val url = data.optString("mediaUrl")
                val parsed = runCatching { android.net.Uri.parse(url) }.getOrNull()
                if (!UUID_PATTERN.matches(frameKey) || !UUID_PATTERN.matches(elementKey) ||
                    page.length !in 8..8192 || url.length !in 8..8192 ||
                    parsed?.scheme !in listOf("http", "https") || parsed?.host.isNullOrBlank() ||
                    runCatching { port.senderUrl() }.getOrNull() != page) {
                    MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "prepare_rejected", port.engineSession)
                    return@post
                }
                val rect=rectOf(data)
                onAutoOffer?.invoke(AutoOffer(port,frameKey,elementKey,page,url,
                    data.optBoolean("topFrame",false) && rect != null,
                    rect?.x?:0.0,rect?.y?:0.0,rect?.w?:0.0,rect?.h?:0.0,rect?.dpr?:1.0))
                return@post
            }
            if (data.optString("event") == "inline_layout") {
                val l=lease ?: return@post
                val t=l.target ?: return@post
                val r=rectOf(data) ?: return@post
                if (l.port === port && l.nonce == data.optString("nonce") &&
                    t.frameKey == data.optString("frameKey") &&
                    t.elementKey == data.optString("elementKey") &&
                    runCatching { port.senderUrl() }.getOrNull() == l.frameUrl)
                    onInlineLayout?.invoke(l.nonce,r)
                return@post
            }
            val p = pending ?: return@post
            if (p.port !== port || data.optString("event") != "handoff_ack" ||
                data.optString("nonce") != p.nonce) return@post
            main.removeCallbacks(p.timeout)
            pending = null
            val ok = data.optBoolean("ok", false) &&
                p.port.engineSession === p.binding.session &&
                runCatching { p.port.senderUrl() }.getOrNull() == p.frameUrl
            if (ok) {
                MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "ack_ok", p.binding.session)
                lease = Lease(p.port, p.binding, p.pageUrl, p.frameUrl, p.nonce, p.target)
                p.callback(p.nonce)
            } else {
                MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "ack_failed", p.binding.session)
                // Even a rejected ACK may have raced a document change.
                send(p.port, "restore", p.nonce)
                p.callback(null)
            }
        }
    }

    fun activateInline(token: String, actual: MediaTabState): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val l=lease ?: return false
        val t=l.target ?: return false
        if (l.nonce != token || !t.topFrame ||
            !matches(l.binding,actual.binding) || l.pageUrl != actual.pageUrl ||
            !ports.contains(l.port) ||
            runCatching { l.port.senderUrl() }.getOrNull() != l.frameUrl) return false
        return runCatching {
            l.port.postMessage(JSONObject().put("version",1)
                .put("event","inline_start").put("nonce",token))
            true
        }.getOrDefault(false)
    }

    /** Return true only if the same EngineSession/profile/document was verified. */
    fun restore(token: String, actual: MediaTabState): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        val l = lease ?: return false
        if (l.nonce != token) return false
        lease = null
        val safe = ports.contains(l.port) &&
            l.port.engineSession === actual.binding.session &&
            runCatching { l.port.senderUrl() }.getOrNull() == l.frameUrl &&
            matches(l.binding, actual.binding) &&
            l.pageUrl == actual.pageUrl
        if (safe) {
            MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "restore_sent", l.binding.session)
            send(l.port, "restore", token)
        }
        return safe
    }

    /** A closed/stale tab must clear its lease without resuming old playback. */
    fun discard(token: String) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val l = lease ?: return
        if (l.nonce != token) return
        lease = null
        MpvDebugTrace.note(MpvDebugTrace.Section.HANDOFF, "lease_discarded", l.binding.session)
        if (ports.contains(l.port)) send(l.port, "cancel", token)
    }

    private fun send(port: Port, event: String, nonce: String) {
        runCatching {
            port.postMessage(JSONObject()
                .put("version", 1)
                .put("event", event)
                .put("nonce", nonce))
        }
    }
}

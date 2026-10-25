package com.prirai.android.nira.media

import android.os.Handler
import android.os.Looper
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.Port
import org.json.JSONObject
import java.util.UUID

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

    private data class Pending(
        val port: Port,
        val binding: MediaSessionBinding,
        val pageUrl: String,
        val frameUrl: String,
        val nonce: String,
        val callback: (String?) -> Unit,
        val timeout: Runnable,
    )
    private data class Lease(
        val port: Port,
        val binding: MediaSessionBinding,
        val pageUrl: String,
        val frameUrl: String,
        val nonce: String,
    )

    private val ports = LinkedHashSet<Port>()
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
        }
    }

    fun disconnected(port: Port) {
        main.post {
            ports.remove(port)
            val p = pending
            if (p?.port === port) {
                pending = null
                main.removeCallbacks(p.timeout)
                p.callback(null)
            }
            if (lease?.port === port) lease = null
        }
    }

    private fun matches(a: MediaSessionBinding, b: MediaSessionBinding): Boolean =
        a.session === b.session && a.tabId == b.tabId &&
            a.contextId == b.contextId && a.isPrivate == b.isPrivate

    fun prepare(
        tab: MediaTabState,
        sourceUrl: String,
        preferredFrame: String?,
        completed: (String?) -> Unit,
    ) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (pending != null || lease != null || sourceUrl.length !in 8..8192) {
            completed(null)
            return
        }
        val frame = preferredFrame ?: tab.pageUrl
        if (!frame.startsWith("https://") && !frame.startsWith("http://")) {
            completed(null)
            return
        }
        // Multiple same-URL frames are ambiguous. Refuse rather than pausing
        // a random ad player. A frame with no port also refuses safely.
        val candidates = ports.filter {
            it.engineSession === tab.binding.session &&
                runCatching { it.senderUrl() }.getOrNull() == frame
        }
        val port = candidates.singleOrNull()
        if (port == null) {
            completed(null)
            return
        }
        val nonce = UUID.randomUUID().toString()
        val timeout = Runnable {
            val p = pending
            if (p?.nonce == nonce) {
                pending = null
                // ACK might have arrived after timeout; restore defensively.
                send(port, "restore", nonce)
                p.callback(null)
            }
        }
        pending = Pending(port, tab.binding, tab.pageUrl, frame, nonce, completed, timeout)
        main.postDelayed(timeout, ACK_TIMEOUT_MS)
        try {
            port.postMessage(JSONObject()
                .put("version", 1)
                .put("event", "prepare")
                .put("nonce", nonce)
                .put("pageUrl", frame)
                .put("mediaUrl", sourceUrl))
        } catch (_: RuntimeException) {
            main.removeCallbacks(timeout)
            pending = null
            completed(null)
        }
    }

    fun received(port: Port, message: Any) {
        main.post {
            val p = pending ?: return@post
            if (p.port !== port || !ports.contains(port)) return@post
            val data = message as? JSONObject ?: return@post
            if (data.optInt("version") != 1 ||
                data.optString("event") != "handoff_ack" ||
                data.optString("nonce") != p.nonce) return@post
            main.removeCallbacks(p.timeout)
            pending = null
            val ok = data.optBoolean("ok", false) &&
                p.port.engineSession === p.binding.session &&
                runCatching { p.port.senderUrl() }.getOrNull() == p.frameUrl
            if (ok) {
                lease = Lease(p.port, p.binding, p.pageUrl, p.frameUrl, p.nonce)
                p.callback(p.nonce)
            } else {
                // Even a rejected ACK may have raced a document change.
                send(p.port, "restore", p.nonce)
                p.callback(null)
            }
        }
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
        if (safe) send(l.port, "restore", token)
        return safe
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

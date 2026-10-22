package com.prirai.android.nira.media

import android.os.SystemClock
import android.util.Log
import com.prirai.android.nira.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.lib.state.ext.flowScoped
import org.json.JSONObject
import java.util.IdentityHashMap

/** Stage 3.1: transient HTML5 MP4/WebM detection, without repository, UI or playback. */
internal class MediaDetectorController(private val engine: Engine, private val store: BrowserStore) {
    private val attached = IdentityHashMap<EngineSession, Unit>()
    private val rate = IdentityHashMap<EngineSession, Window>()
    private var observer: kotlinx.coroutines.CoroutineScope? = null
    private var started = false

    private class Window(var since: Long, var count: Int)

    fun start() {
        if (started) return
        started = true
        try {
            engine.installBuiltInWebExtension(
                id = EXTENSION_ID,
                url = "resource://android/assets/extensions/nira-media-detector/",
                onSuccess = { extension ->
                    try { attach(extension) } catch (_: Exception) { diagnostic("attach_failed") }
                },
                onError = { diagnostic("install_failed") },
            )
        } catch (_: Exception) {
            diagnostic("install_failed")
        }
    }

    private fun bindings(): List<MediaSessionBinding> = store.state.tabs.mapNotNull { tab ->
        tab.engineState.engineSession?.let { MediaSessionBinding(it, tab.id, tab.contextId, tab.content.private) }
    }

    private fun attach(extension: WebExtension) {
        // Preserve the Stage 3.0 public-fixture-only webRequest compatibility probe.
        extension.registerBackgroundMessageHandler(NATIVE_APP, object : MessageHandler {
            override fun onMessage(message: Any, source: EngineSession?): Any {
                val accepted = (message as? JSONObject)?.toString()?.let(MediaMessageProtocol::decode) === MediaDetectorMessage.NetworkProbe
                diagnostic(if (accepted) "webrequest_callback_received" else "background_rejected")
                return accepted
            }
        })
        observer = store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.map { state -> state.tabs.mapNotNull { it.engineState.engineSession } }
                .distinctUntilChanged()
                .collect { sessions ->
                    attached.keys.removeAll { old -> sessions.none { it === old } }
                    rate.keys.removeAll { old -> sessions.none { it === old } }
                    sessions.forEach { session ->
                        if (!attached.containsKey(session)) {
                            try {
                                extension.registerContentMessageHandler(session, NATIVE_APP, handler(session))
                                attached[session] = Unit
                            } catch (_: Exception) { diagnostic("session_attach_failed") }
                        }
                    }
                }
        }
        diagnostic("installed")
        diagnostic(if (extension.isAllowedInPrivateBrowsing()) "private_allowed" else "private_disabled")
    }

    private fun handler(registeredSession: EngineSession) = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any {
            if (source !== registeredSession) return false
            val context = resolveMediaSession(source, bindings()) ?: return false
            val candidate = (message as? JSONObject)?.toString()?.let(MediaMessageProtocol::decode)
            if (candidate !is MediaDetectorMessage.Candidate) {
                diagnostic("message_rejected")
                return false
            }
            // Bound native work without dropping two different videos in the same second.
            val now = SystemClock.elapsedRealtime()
            val window = rate[registeredSession]
            if (window == null || now - window.since >= 5000L) {
                rate[registeredSession] = Window(now, 1)
            } else if (window.count >= 24) {
                return false
            } else {
                window.count++
            }
            val media = MediaClassifier.fromDom(candidate, context)
            if (media == null) {
                diagnostic("candidate_unsupported")
                return false
            }
            // No durable state until Stage 3.3. URLs remain transient in this local object.
            diagnostic("media_detected format=${media.format} tab=${context.tabId} private=${context.isPrivate}")
            return true
        }
    }

    private fun diagnostic(event: String) {
        if (BuildConfig.DEBUG) Log.d("NiraMediaDetector", event)
    }

    companion object {
        const val EXTENSION_ID = "nira-media-detector@prirai.android.nira"
        private const val NATIVE_APP = "nira.media.detector"
    }
}

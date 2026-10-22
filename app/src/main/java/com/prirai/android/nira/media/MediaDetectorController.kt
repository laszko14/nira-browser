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

/** Stage 3.0: transient DOM event reception; no media list, storage or player launch. */
internal class MediaDetectorController(private val engine: Engine, private val store: BrowserStore) {
    private val attached = IdentityHashMap<EngineSession, Unit>()
    private val lastReceived = IdentityHashMap<EngineSession, Long>()
    private var observer: kotlinx.coroutines.CoroutineScope? = null
    private var started = false

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
        // webRequest probe is background-only and deliberately cannot produce a media candidate.
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
                    lastReceived.keys.removeAll { old -> sessions.none { it === old } }
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
        // Private browsing permission remains controlled by Gecko; do not silently enable it.
        diagnostic(if (extension.isAllowedInPrivateBrowsing()) "private_allowed" else "private_disabled")
    }

    private fun handler(registeredSession: EngineSession) = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any {
            if (source !== registeredSession) return false
            val context = resolveMediaSession(source, bindings()) ?: return false
            // Bound native work/log volume as well as content-script emission.
            val now = SystemClock.elapsedRealtime()
            if (lastReceived[registeredSession]?.let { now - it < 1000 } == true) return false
            lastReceived[registeredSession] = now
            val candidate = (message as? JSONObject)?.toString()?.let(MediaMessageProtocol::decode)
            if (candidate !is MediaDetectorMessage.Candidate) {
                diagnostic("message_rejected")
                return false
            }
            // URL has reached Kotlin intact. Retaining/classifying it belongs to Stage 3.1.
            diagnostic("candidate_received tab=${context.tabId} context=${context.contextId ?: "default"} private=${context.isPrivate}")
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

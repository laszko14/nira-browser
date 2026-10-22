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

/** Stage 3.2: transient DOM and passive network candidates; no persistent repository. */
internal class MediaDetectorController(private val engine: Engine, private val store: BrowserStore) {
    private val attached = IdentityHashMap<EngineSession, Unit>()
    private val rate = IdentityHashMap<EngineSession, Window>()
    private val deduplicator = MediaRecentDeduplicator()
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
                onError = { MediaDetectorDiagnostics.installFailed(); diagnostic("install_failed") },
            )
        } catch (_: Exception) {
            MediaDetectorDiagnostics.installFailed()
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
                // Background messages have no native EngineSession: never guess the active tab.
                if (source != null) {
                    MediaDetectorDiagnostics.rejected()
                    return false
                }
                val decoded = (message as? JSONObject)?.toString()?.let(MediaMessageProtocol::decode)
                when (decoded) {
                    MediaDetectorMessage.NetworkProbe -> {
                        MediaDetectorDiagnostics.networkProbe()
                        diagnostic("webrequest_callback_received")
                    }
                    is MediaDetectorMessage.NetworkUnassigned ->
                        MediaDetectorDiagnostics.networkUnassigned(decoded.isPrivate)
                    is MediaDetectorMessage.NetworkSegments ->
                        MediaDetectorDiagnostics.networkSegments(decoded.isPrivate, decoded.count)
                    else -> {
                        MediaDetectorDiagnostics.rejected()
                        diagnostic("background_rejected")
                        return false
                    }
                }
                return true
            }
        })
        observer = store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.map { state -> state.tabs.mapNotNull { it.engineState.engineSession } }
                .distinctUntilChanged()
                .collect { sessions ->
                    attached.keys.removeAll { old -> sessions.none { it === old } }
                    rate.keys.removeAll { old -> sessions.none { it === old } }
                    deduplicator.retainSessions(sessions)
                    MediaDetectorDiagnostics.retainOpenTabs(store.state.tabs.map { it.id }.toSet())
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
        MediaDetectorDiagnostics.installed()
        diagnostic("installed")
        val privateAllowed = extension.isAllowedInPrivateBrowsing()
        MediaDetectorDiagnostics.setPrivateAllowed(privateAllowed)
        diagnostic(if (privateAllowed) "private_allowed" else "private_disabled")
    }

    private fun handler(registeredSession: EngineSession) = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any {
            if (source !== registeredSession) return false
            val context = resolveMediaSession(source, bindings()) ?: return false
            val candidate = (message as? JSONObject)?.toString()?.let(MediaMessageProtocol::decode)
            if (candidate !is MediaDetectorMessage.Candidate) {
                MediaDetectorDiagnostics.validationError(context)
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
            val media = when (candidate.source) {
                MediaSource.DOM -> MediaClassifier.fromDom(candidate, context)
                MediaSource.NETWORK -> MediaClassifier.fromNetwork(candidate, context)
            }
            if (media == null) {
                if (candidate.source == MediaSource.NETWORK && MediaClassifier.isSegment(candidate.url)) {
                    MediaDetectorDiagnostics.networkSegments(context.isPrivate, 1)
                } else {
                    MediaDetectorDiagnostics.rejected()
                }
                diagnostic("candidate_unsupported")
                return false
            }
            // Signed URL is kept only within this bounded short-lived session filter.
            if (!deduplicator.accept(context, media, now)) {
                MediaDetectorDiagnostics.duplicate(context)
                return true // acknowledge network path even when DOM already found the URL
            }
            MediaDetectorDiagnostics.detected(context, media.format, candidate.source)
            diagnostic("media_detected format=${media.format} source=${candidate.source} private=${context.isPrivate}")
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

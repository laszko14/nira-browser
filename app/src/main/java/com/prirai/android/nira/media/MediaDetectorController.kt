package com.prirai.android.nira.media

import android.os.SystemClock
import android.util.Log
import com.prirai.android.nira.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Stage 3.3: RAM-only, native Store-bound media state; no UI or downloader. */
internal class MediaDetectorController(private val engine: Engine, private val store: BrowserStore) {
    private val attached = IdentityHashMap<EngineSession, Unit>()
    private val rate = IdentityHashMap<EngineSession, Window>()
    private val repository = MediaRepository()
    private var observer: kotlinx.coroutines.CoroutineScope? = null
    private var started = false

    private class Window(var since: Long, var count: Int)

    fun start() {
        if (started) return
        started = true
        MediaDetectorDiagnostics.setRepository(repository)
        mutableUiRepository.value = repository
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

    private fun tabs(): List<MediaTabState> = store.state.tabs.mapNotNull { tab ->
        tab.engineState.engineSession?.let {
            MediaTabState(MediaSessionBinding(it, tab.id, tab.contextId, tab.content.private),
                tab.content.url, tab.content.loading)
        }
    }

    private fun reconcile(current: List<MediaTabState>) {
        repository.observe(current).forEach { change ->
            if (change.navigation) MediaDetectorDiagnostics.navigationRemoved(change.binding, change.removed)
        }
        MediaDetectorDiagnostics.retainOpenTabs(current.map { it.binding.tabId }.toSet())
    }

    private fun attach(extension: WebExtension) {
        extension.registerBackgroundMessageHandler(NATIVE_APP, object : MessageHandler {
            override fun onMessage(message: Any, source: EngineSession?): Any {
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
            flow.map { state -> state.tabs.mapNotNull { tab ->
                tab.engineState.engineSession?.let {
                    MediaTabState(MediaSessionBinding(it, tab.id, tab.contextId, tab.content.private),
                        tab.content.url, tab.content.loading)
                }
            } }.distinctUntilChanged().collect { current ->
                val sessions = current.map { it.binding.session }
                attached.keys.removeAll { old -> sessions.none { it === old } }
                rate.keys.removeAll { old -> sessions.none { it === old } }
                reconcile(current)
                current.forEach { tab ->
                    val session = tab.binding.session as EngineSession
                    if (!attached.containsKey(session)) {
                        try {
                            extension.registerContentMessageHandler(session, NATIVE_APP, handler(session))
                            // Independent native port for per-element pause ACK and
                            // restore; detector candidate protocol is unchanged.
                            extension.registerContentMessageHandler(
                                session, MediaElementPlaybackBridge.NATIVE_APP,
                                object : MessageHandler {
                                    override fun onPortConnected(
                                        port: mozilla.components.concept.engine.webextension.Port
                                    ) { MediaElementPlaybackBridge.connected(port) }
                                    override fun onPortDisconnected(
                                        port: mozilla.components.concept.engine.webextension.Port
                                    ) { MediaElementPlaybackBridge.disconnected(port) }
                                    override fun onPortMessage(
                                        message: Any,
                                        port: mozilla.components.concept.engine.webextension.Port
                                    ) { MediaElementPlaybackBridge.received(port, message) }
                                }
                            )
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
            val current = tabs()
            reconcile(current)
            val tab = current.singleOrNull { it.binding.session === source } ?: return false
            val context = tab.binding
            val decoded = (message as? JSONObject)?.toString()?.let(MediaMessageProtocol::decode)
            if (decoded is MediaDetectorMessage.Document) {
                val result = repository.registerDocument(tab, decoded.pageUrl, decoded.documentKey)
                if (!result.accepted) {
                    MediaDetectorDiagnostics.stale(context)
                    return false
                }
                if (result.removed > 0) MediaDetectorDiagnostics.navigationRemoved(context, result.removed)
                return true
            }
            val candidate = decoded as? MediaDetectorMessage.Candidate
            if (candidate == null) {
                MediaDetectorDiagnostics.validationError(context)
                diagnostic("message_rejected")
                return false
            }
            if (candidate.source == MediaSource.NETWORK && candidate.privateHint != context.isPrivate) {
                MediaDetectorDiagnostics.validationError(context)
                return false
            }
            if (candidate.documentKey == null || candidate.pageUrl == null ||
                candidate.pageUrl != tab.pageUrl) {
                MediaDetectorDiagnostics.stale(context)
                return false
            }
            val now = SystemClock.elapsedRealtime()
            val window = rate[registeredSession]
            if (window == null || now - window.since >= 5000L) {
                rate[registeredSession] = Window(now, 1)
            } else if (window.count >= 96) {
                return false
            } else {
                window.count++
            }
            val media = when (candidate.source) {
                MediaSource.DOM -> MediaClassifier.fromDom(candidate, context)
                MediaSource.PAGE -> MediaClassifier.fromPage(candidate, context)
                MediaSource.NETWORK -> MediaClassifier.fromNetwork(candidate, context)
            }
            if (media == null) {
                if (candidate.source == MediaSource.NETWORK && MediaClassifier.isSegment(candidate.url))
                    MediaDetectorDiagnostics.networkSegments(context.isPrivate, 1)
                else MediaDetectorDiagnostics.rejected()
                diagnostic("candidate_unsupported")
                return false
            }
            return when (repository.add(tab, candidate.pageUrl, candidate.documentKey, media, candidate.source, candidate.groupId, candidate)) {
                MediaRepository.InsertResult.STALE -> {
                    MediaDetectorDiagnostics.stale(context)
                    false
                }
                MediaRepository.InsertResult.DUPLICATE -> {
                    MediaDetectorDiagnostics.duplicate(context)
                    true
                }
                MediaRepository.InsertResult.ADDED -> {
                    MediaDetectorDiagnostics.detected(context, media.format, candidate.source)
                    diagnostic("media_detected format=${media.format} source=${candidate.source} private=${context.isPrivate}")
                    true
                }
            }
        }
    }

    private fun diagnostic(event: String) {
        if (BuildConfig.DEBUG) Log.d("NiraMediaDetector", event)
    }

    companion object {
        private val mutableUiRepository = MutableStateFlow<MediaRepository?>(null)
        val uiRepository: StateFlow<MediaRepository?> = mutableUiRepository.asStateFlow()

        const val EXTENSION_ID = "nira-media-detector@prirai.android.nira"
        private const val NATIVE_APP = "nira.media.detector"
    }
}

package com.prirai.android.nira.videosearch

import android.content.Context
import android.os.SystemClock
import com.prirai.android.nira.browser.profile.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.lib.state.ext.flowScoped
import java.util.IdentityHashMap

/**
 * Separate, opt-in GeckoView bridge for VIDEO SEARCH only.
 *
 * Never touches MediaDetectorController or the media WebExtension. Installed
 * lazily when the user opens their configured-site management screen, rather
 * than injecting a content script into every tab during browser cold-start.
 *
 * In stage 3A the ONLY accepted payloads are document hello and pong.
 * No DOM access, search submission, results, network capture or persistent
 * writes are performed by the bridge. Commands for site interaction belong
 * to 3B+ and MUST verify the requested site/profile/tab again.
 */
internal class VideoSearchBridgeController(
    private val applicationContext: Context,
    private val engine: Engine,
    private val store: BrowserStore,
) {
    private data class DocumentPort(
        val port: Port,
        var documentId: String? = null,
        var pageUrl: String? = null,
        var profileId: String? = null,
        var lastPong: Boolean = false,
    )
    private data class TabSnapshot(
        val tabId: String,
        val pageUrl: String,
        val owner: VideoSearchScope.Normal,
    )
    private class Window(var start: Long, var count: Int)

    private val attached = IdentityHashMap<EngineSession, Unit>()
    private val ports = IdentityHashMap<EngineSession, DocumentPort>()
    private val windows = IdentityHashMap<EngineSession, Window>()
    private var observer: kotlinx.coroutines.CoroutineScope? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        try {
            engine.installBuiltInWebExtension(
                id = VideoSearchBridgeProtocol.EXTENSION_ID,
                url = VideoSearchBridgeProtocol.EXTENSION_URL,
                onSuccess = { extension ->
                    try { attach(extension) }
                    catch (_: Exception) {
                        started = false
                        // Intentionally no URLs, titles, hosts or tokens in logs.
                    }
                },
                onError = {
                    started = false
                },
            )
        } catch (_: Exception) {
            started = false
        }
    }

    private fun currentTab(session: EngineSession): TabSnapshot? {
        val tab = store.state.tabs.singleOrNull {
            it.engineState.engineSession === session
        } ?: return null
        if (tab.content.private) return null

        // Derive owner from trusted BrowserStore + registered profiles,
        // never a JS-provided profile ID or the currently active UI profile.
        val profile = ProfileManager.getInstance(applicationContext)
            .getAllProfiles()
            .firstOrNull { candidate ->
                VideoSearchScope.Normal(candidate.id).ownsTab(tab.contextId, tab.content.private)
            } ?: return null
        val owner = VideoSearchScope.Normal(profile.id)
        val url = tab.content.url
        try { VideoSearchIntegrity.requirePageUrl(url) }
        catch (_: IllegalArgumentException) { return null }
        return TabSnapshot(tab.id, url, owner)
    }

    private fun isFresh(session: EngineSession, entry: DocumentPort): Boolean {
        val tab = currentTab(session) ?: return false
        return tab.owner.profileId == entry.profileId &&
            tab.pageUrl == entry.pageUrl &&
            entry.documentId != null
    }

    private fun withinRate(session: EngineSession): Boolean {
        val now = SystemClock.elapsedRealtime()
        val window = windows[session]
        if (window == null || now - window.start >= 5_000L) {
            windows[session] = Window(now, 1)
            return true
        }
        if (window.count >= 12) return false
        window.count++
        return true
    }

    private fun attach(extension: WebExtension) {
        observer = store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.map { state ->
                state.tabs.mapNotNull { tab ->
                    tab.engineState.engineSession?.let { session ->
                        Triple(session, tab.contextId, tab.content.url to tab.content.private)
                    }
                }
            }.distinctUntilChanged().collect { live ->
                val sessions = live.map { it.first }
                val obsolete = attached.keys.filter { old -> sessions.none { it === old } }
                obsolete.forEach { old ->
                    attached.remove(old)
                    windows.remove(old)
                    ports.remove(old)?.port?.let {
                        try { it.disconnect() } catch (_: Exception) {}
                    }
                }
                live.forEach { (session, _, _) ->
                    if (!attached.containsKey(session)) {
                        try {
                            extension.registerContentMessageHandler(
                                session, VideoSearchBridgeProtocol.NATIVE_APP, handler(session)
                            )
                            attached[session] = Unit
                        } catch (_: Exception) {
                            // Don't log the page or exception text.
                        }
                    } else {
                        ports[session]?.let { connection ->
                            // Navigation or profile moves invalidate the old document.
                            if (!isFresh(session, connection)) {
                                ports.remove(session)
                                try { connection.port.disconnect() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handler(session: EngineSession) = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any = false

        override fun onPortConnected(port: Port) {
            if (currentTab(session) == null) {
                try { port.disconnect() } catch (_: Exception) {}
                return
            }
            val old = ports.put(session, DocumentPort(port))
            if (old != null && old.port !== port) {
                try { old.port.disconnect() } catch (_: Exception) {}
            }
        }

        override fun onPortMessage(message: Any, port: Port) {
            val entry = ports[session] ?: return
            // Gecko's PortDelegate may wrap the SAME native port in a fresh
            // GeckoPort on every message. Compare stable sender metadata,
            // not the Kotlin wrapper object identity.
            if (entry.port.name() != port.name() ||
                entry.port.senderUrl() != port.senderUrl() ||
                port.name() != VideoSearchBridgeProtocol.NATIVE_APP) return
            if (!withinRate(session)) return
            val parsed = VideoSearchBridgeProtocol.decode(message) ?: return
            val tab = currentTab(session) ?: return
            when (parsed) {
                is VideoSearchBridgeMessage.Hello -> {
                    if (parsed.pageUrl != tab.pageUrl) return
                    entry.documentId = parsed.documentId
                    entry.pageUrl = parsed.pageUrl
                    entry.profileId = tab.owner.profileId
                    entry.lastPong = false
                    try {
                        port.postMessage(VideoSearchBridgeProtocol.bound(parsed.documentId))
                    } catch (_: Exception) {
                        ports.remove(session)
                    }
                }
                is VideoSearchBridgeMessage.Pong -> {
                    if (isFresh(session, entry) &&
                        parsed.documentId == entry.documentId) entry.lastPong = true
                }
            }
        }

        override fun onPortDisconnected(port: Port) {
            if (ports[session]?.port === port) ports.remove(session)
        }
    }

    /**
     * Developer-only handshake probe, not a search command. This method
     * cannot target a tab of another profile or a private session.
     * A subsequent PONG is kept RAM-only (never written to Room).
     */
    fun ping(scope: VideoSearchScope.Normal, tabId: String): Boolean {
        val tab = store.state.tabs.singleOrNull { it.id == tabId } ?: return false
        val session = tab.engineState.engineSession ?: return false
        val current = currentTab(session) ?: return false
        if (current.owner != scope || current.tabId != tabId) return false
        val entry = ports[session] ?: return false
        if (!isFresh(session, entry)) return false
        val docId = entry.documentId ?: return false
        entry.lastPong = false
        return try {
            entry.port.postMessage(VideoSearchBridgeProtocol.ping(docId))
            true
        } catch (_: Exception) { false }
    }

    fun hasPong(scope: VideoSearchScope.Normal, tabId: String): Boolean {
        val tab = store.state.tabs.singleOrNull { it.id == tabId } ?: return false
        val session = tab.engineState.engineSession ?: return false
        if (currentTab(session)?.owner != scope) return false
        val entry = ports[session] ?: return false
        return isFresh(session, entry) && entry.lastPong
    }
}

/** Starts only on user access to the feature; never during app cold-start. */
internal object VideoSearchBridgeHost {
    private var controller: VideoSearchBridgeController? = null

    fun start(context: Context, engine: Engine, store: BrowserStore) {
        val instance = controller ?: VideoSearchBridgeController(
            context.applicationContext, engine, store
        ).also { controller = it }
        instance.start()
    }
}

package com.prirai.android.nira.videosearch

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.UUID

/**
 * Separate, opt-in GeckoView bridge for VIDEO SEARCH only.
 *
 * Never touches MediaDetectorController or the media WebExtension. Installed
 * lazily when the user opens their configured-site management screen, rather
 * than injecting a content script into every tab during browser cold-start.
 *
 * In stage 3B site inspection / GET search are permitted only after explicit
 * user action on an already open exact-site tab. No automatic DOM polling,
 * pagination, result harvesting, private browsing or persistent writes here.
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

    private data class Pending(
        val requestId: String,
        val documentId: String,
        val owner: VideoSearchScope.Normal,
        val siteId: String,
        val pageUrl: String,
        val kind: String,
        val result: CompletableDeferred<VideoSearchBridgeMessage>,
    )
    private data class Proposal(
        val session: EngineSession,
        val documentId: String,
        val createdMs: Long,
        val inspection: VideoSearchBridgeMessage.Inspection,
    )
    private val manualScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var manualBusy = false
    private val pending = IdentityHashMap<EngineSession, Pending>()
    private val proposals = mutableMapOf<Pair<String, String>, Proposal>()
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
                    pending.remove(old)?.result?.cancel()
                    proposals.entries.removeAll { it.value.session === old }
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
                                pending.remove(session)?.result?.cancel()
                                proposals.entries.removeAll { it.value.session === session }
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
            pending.remove(session)?.result?.cancel()
            proposals.entries.removeAll { it.value.session === session }
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
                is VideoSearchBridgeMessage.Inspection,
                is VideoSearchBridgeMessage.Submitted,
                is VideoSearchBridgeMessage.SearchFailed,
                is VideoSearchBridgeMessage.Picked,
                is VideoSearchBridgeMessage.PickSkipped,
                is VideoSearchBridgeMessage.PickCancelled,
                is VideoSearchBridgeMessage.Validation,
                is VideoSearchBridgeMessage.Results -> {
                    val job = pending[session] ?: return
                    val req = when (parsed) {
                        is VideoSearchBridgeMessage.Inspection -> parsed.requestId
                        is VideoSearchBridgeMessage.Submitted -> parsed.requestId
                        is VideoSearchBridgeMessage.SearchFailed -> parsed.requestId
                        is VideoSearchBridgeMessage.Picked -> parsed.requestId
                        is VideoSearchBridgeMessage.PickSkipped -> parsed.requestId
                        is VideoSearchBridgeMessage.PickCancelled -> parsed.requestId
                        is VideoSearchBridgeMessage.Validation -> parsed.requestId
                        is VideoSearchBridgeMessage.Results -> parsed.requestId
                        else -> return
                    }
                    if (job.requestId != req || job.documentId != parsed.documentId ||
                        !isFresh(session, entry) || job.pageUrl != tab.pageUrl ||
                        tab.owner != job.owner || job.kind != when (parsed) {
                            is VideoSearchBridgeMessage.Inspection -> "inspect"
                            is VideoSearchBridgeMessage.Validation -> "validate"
                            is VideoSearchBridgeMessage.Results -> "extract"
                            is VideoSearchBridgeMessage.Submitted,
                            is VideoSearchBridgeMessage.SearchFailed -> "search"
                            is VideoSearchBridgeMessage.Picked -> "pick:" + parsed.pickKind
                            is VideoSearchBridgeMessage.PickSkipped -> "pick:" + parsed.pickKind
                            is VideoSearchBridgeMessage.PickCancelled -> "pick:" + parsed.pickKind
                            else -> return
                        }) return
                    pending.remove(session)
                    if (parsed is VideoSearchBridgeMessage.Inspection &&
                        parsed.inputSelector != null && parsed.submission != null) {
                        proposals[job.owner.profileId to job.siteId] =
                            Proposal(session, parsed.documentId, SystemClock.elapsedRealtime(), parsed)
                    }
                    job.result.complete(parsed)
                }
            }
        }

        override fun onPortDisconnected(port: Port) {
            val old = ports[session]
            if (old != null && old.port.name() == port.name() &&
                old.port.senderUrl() == port.senderUrl()) {
                ports.remove(session)
                pending.remove(session)?.result?.cancel()
                proposals.entries.removeAll { it.value.session === session }
            }
        }
    }

    private fun existingSiteTab(site: VideoSearchSite): Pair<EngineSession, DocumentPort>? {
        val scope = site.scope as? VideoSearchScope.Normal ?: return null
        if (!site.enabled) return null
        val matching = store.state.tabs.mapNotNull { tab ->
            val session = tab.engineState.engineSession ?: return@mapNotNull null
            val state = currentTab(session) ?: return@mapNotNull null
            // Exact saved page, except its optional final slash. Never act
            // on unrelated pages of the same origin.
            if (state.owner != scope ||
                state.pageUrl.trimEnd('/') != site.siteUrl.trimEnd('/')) return@mapNotNull null
            val entry = ports[session] ?: return@mapNotNull null
            if (!isFresh(session, entry)) return@mapNotNull null
            session to entry
        }
        return matching.singleOrNull() // no guessing among duplicate tabs
    }

    /**
     * Only the currently displayed normal tab on the saved site's exact
     * origin is eligible. Unlike searching, manual identification may need
     * the /results path of that origin, not just site.siteUrl.
     */
    private fun manualSiteTab(site: VideoSearchSite): Pair<EngineSession, DocumentPort>? {
        val scope = site.scope as? VideoSearchScope.Normal ?: return null
        if (!site.enabled) return null
        val selected = store.state.tabs.singleOrNull {
            it.id == store.state.selectedTabId
        } ?: return null
        val session = selected.engineState.engineSession ?: return null
        val state = currentTab(session) ?: return null
        if (state.owner != scope ||
            !VideoSearchBridgeProtocol.sameOrigin(state.pageUrl, site.siteUrl)) return null
        val entry = ports[session] ?: return null
        return if (isFresh(session, entry)) session to entry else null
    }

    private suspend fun request(
        site: VideoSearchSite,
        kind: String,
        packet: (String, String) -> org.json.JSONObject,
        timeoutMs: Long = 8_000L,
    ): VideoSearchBridgeMessage? {
        val scope = site.scope as? VideoSearchScope.Normal ?: return null
        val (session, entry) = (if (kind.startsWith("pick:") ||
            kind == "validate" || kind == "extract")
            manualSiteTab(site) else existingSiteTab(site)) ?: return null
        val documentId = entry.documentId ?: return null
        if (pending.containsKey(session)) return null
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val result = CompletableDeferred<VideoSearchBridgeMessage>()
        val work = Pending(requestId, documentId, scope, site.id,
            entry.pageUrl ?: return null, kind, result)
        pending[session] = work
        try {
            entry.port.postMessage(packet(documentId, requestId))
            return withTimeoutOrNull(timeoutMs) { result.await() }
        } catch (_: Exception) {
            return null
        } finally {
            if (pending[session] === work) pending.remove(session)
        }
    }

    /** Inspection is read-only and only runs when user taps Rozpoznaj. */
    suspend fun inspectSite(site: VideoSearchSite): VideoSearchBridgeMessage.Inspection? =
        request(site, "inspect") { document, request ->
            VideoSearchBridgeProtocol.inspect(document, request)
        } as? VideoSearchBridgeMessage.Inspection

    /**
     * Explicit read-only integrity check. It can inspect the selected normal
     * tab on the saved site's origin (including /results), but never a private
     * tab or any other site/profile. No automatic polling from the host.
     */
    suspend fun validateSite(site: VideoSearchSite): VideoSearchBridgeMessage.Validation? {
        val configuration = site.configuration ?: return null
        return request(site, "validate") { document, request ->
            VideoSearchBridgeProtocol.validate(document, request, configuration)
        } as? VideoSearchBridgeMessage.Validation
    }

    /**
     * A bounded, transient snapshot from the currently selected normal tab's
     * result cards. No run ID is invented and nothing is persisted or fetched
     * from the network in stage 4A. Future stages own task/run persistence.
     */
    suspend fun extractSite(site: VideoSearchSite): VideoSearchExtractedPage? {
        val selector = site.configuration?.resultsCardSelector
            ?.takeIf(VideoSearchBridgeProtocol::validSelector) ?: return null
        if (site.scope !is VideoSearchScope.Normal || !site.enabled) return null
        val source = manualSiteTab(site) ?: return null
        val originPage = currentTab(source.first)?.pageUrl ?: return null
        val result = request(site, "extract") { document, req ->
            VideoSearchBridgeProtocol.extract(document, req, selector)
        } as? VideoSearchBridgeMessage.Results ?: return null
        // Result links are film PAGE links, not arbitrary external adverts.
        if (result.page.films.any {
                !VideoSearchBridgeProtocol.sameOrigin(it.filmPageUrl, originPage)
            }) return null
        return result.page
    }

    private suspend fun pickSite(site: VideoSearchSite, kind: String): VideoSearchBridgeMessage? =
        request(site, "pick:$kind", { document, req ->
            VideoSearchBridgeProtocol.pick(document, req, kind)
        }, timeoutMs = 101_000L)

    /** The picker must target the page currently visible behind My Sites. */
    fun canStartManual(site: VideoSearchSite): Boolean {
        if (manualBusy) return false
        val pair = manualSiteTab(site) ?: return false
        val matched = currentTab(pair.first) ?: return false
        return store.state.selectedTabId == matched.tabId && pair.second.documentId != null
    }

    /**
     * Start a user-directed wizard and return to Gecko. Its coroutine is owned
     * by the bridge, not by MyVideoSitesActivity (which finishes immediately).
     * Nothing is saved until the wizard ends without cancellation.
     */
    fun startManual(site: VideoSearchSite, repository: VideoSearchRepository): Boolean {
        if (!canStartManual(site)) return false
        manualBusy = true
        manualScope.launch {
            var feedbackSent = false
            try {
                val original = manualSiteTab(site) ?: return@launch
                val originalDocument = original.second.documentId ?: return@launch
                val input = pickSite(site, "input") as? VideoSearchBridgeMessage.Picked
                    ?: return@launch
                if (input.pickKind != "input") return@launch
                if (manualSiteTab(site)?.let {
                        it.first === original.first && it.second.documentId == originalDocument
                    } != true) return@launch

                val submit = pickSite(site, "submit")
                if (submit is VideoSearchBridgeMessage.PickCancelled || submit == null)
                    return@launch
                val submitSelector = (submit as? VideoSearchBridgeMessage.Picked)?.selector
                // Skipping a button is legal only for an actual same-origin GET
                // form. For an SPA field the user must choose a safe button.
                if (submitSelector == null && !input.formSafe) return@launch
                val submission = if (submitSelector != null)
                    VideoSearchSubmission.CLICK_SUBMIT else VideoSearchSubmission.FORM_SUBMIT

                val cards = pickSite(site, "cards")
                if (cards is VideoSearchBridgeMessage.PickCancelled || cards == null)
                    return@launch
                val cardSelector = (cards as? VideoSearchBridgeMessage.Picked)?.selector

                val next = pickSite(site, "next")
                if (next is VideoSearchBridgeMessage.PickCancelled || next == null)
                    return@launch
                if (manualSiteTab(site)?.let {
                        it.first === original.first && it.second.documentId == originalDocument
                    } != true) return@launch

                val nextPicked = next as? VideoSearchBridgeMessage.Picked
                val configuration = VideoSiteConfiguration(
                    searchInputSelector = input.selector,
                    resultsCardSelector = cardSelector,
                    submission = submission,
                    submitSelector = submitSelector,
                    pagination = nextPicked?.pagination ?: VideoSearchPagination.NONE,
                    nextPageSelector = nextPicked?.selector,
                )
                // Optimistic CAS: a deleted/renamed/reconfigured site cannot
                // be resurrected or overwritten by an old picker session.
                val saved = withContext(Dispatchers.IO) {
                    repository.applyManualConfiguration(site, configuration)
                }
                val message = when {
                    !saved -> "Nie zapisano: witryna zmieniła się. Ponów konfigurację."
                    cardSelector != null -> "Ręczna konfiguracja wyszukiwarki zapisana."
                    else -> "Zapisano pole wyszukiwania. Karty filmów wymagają wskazania."
                }
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                feedbackSent = true
            } catch (_: Exception) {
                Toast.makeText(applicationContext,
                    "Nie zapisano konfiguracji. Witryna pozostała na liście.",
                    Toast.LENGTH_LONG).show()
                feedbackSent = true
            } finally {
                if (!feedbackSent) Toast.makeText(applicationContext,
                    "Wskazywanie przerwane. Poprzednia konfiguracja pozostaje bez zmian.",
                    Toast.LENGTH_LONG).show()
                manualBusy = false
            }
        }
        return true
    }

    /**
     * Search stays on the site's own page: no Google, no navigation of
     * unrelated tabs. A partial detection can run a one-off search but is
     * not marked CONFIGURED until result cards are recognized.
     */
    suspend fun searchSite(site: VideoSearchSite, query: String): Boolean {
        if (!VideoSearchBridgeProtocol.validQuery(query)) return false
        val scoped = site.scope as? VideoSearchScope.Normal ?: return false
        val (session, entry) = existingSiteTab(site) ?: return false
        val previous = proposals[scoped.profileId to site.id]
        val setup = if (previous != null && previous.session === session &&
            previous.documentId == entry.documentId &&
            SystemClock.elapsedRealtime() - previous.createdMs <= 120_000L) {
            previous.inspection
        } else null
        // Persisted user-selected controls take precedence over transient
        // automatic hints; failed auto-detection must not shadow manual picks.
        val input = site.configuration?.searchInputSelector ?: setup?.inputSelector ?: return false
        val method = site.configuration?.submission ?: setup?.submission ?: return false
        val button = site.configuration?.submitSelector ?: setup?.submitSelector
        return request(site, "search") { document, request ->
            VideoSearchBridgeProtocol.search(document, request, query, input, method, button)
        } is VideoSearchBridgeMessage.Submitted
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

    fun instance(): VideoSearchBridgeController? = controller

    fun start(context: Context, engine: Engine, store: BrowserStore) {
        val instance = controller ?: VideoSearchBridgeController(
            context.applicationContext, engine, store
        ).also { controller = it }
        instance.start()
    }
}

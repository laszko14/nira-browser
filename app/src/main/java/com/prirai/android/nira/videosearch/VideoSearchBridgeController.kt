package com.prirai.android.nira.videosearch

import android.content.Context
import android.os.SystemClock
import android.app.ActivityManager
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import android.widget.Toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
 * Stage 5C adds explicit, normal-profile multi-site search workers.
 * Each newly created worker tab is verified independently, its search-result
 * document must actually change and expose film cards, and bounded page
 * extraction checkpoints are stored transactionally in normal-profile Room.
 * No private browsing persistence, passive page scanning or media playback.
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
        var owner: VideoSearchScope? = null,
        var lastPong: Boolean = false,
        var navigationRevision: Int = -1,
    )
    private data class TabSnapshot(
        val tabId: String,
        val pageUrl: String,
        val owner: VideoSearchScope,
    )
    private class Window(var start: Long, var count: Int)

    private data class Pending(
        val requestId: String,
        val documentId: String,
        val owner: VideoSearchScope,
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
    private var crawlBusy = false
    private val normalRunGate = VideoSearchNormalRunGate()
    private var privateJob: Job? = null
    private val privateResults = VideoSearchPrivateRunAccess()
    private val privateGroup = VideoSearchPrivateGroupAccess()
    // Only the current background-created private tab can hold a worker lease.
    // A user selecting it latches permanent adoption (even after switching away).
    private var privateWorkerLease: VideoSearchPrivateWorkerLease? = null
    private val lastMulti = LinkedHashMap<String, VideoSearchQueueSnapshot>()
    private val lastMultiRunIds = LinkedHashMap<String, String>()
    // At most eight temporary result lists; never grow a hidden unlimited cache.
    private val recentCrawls =
        LinkedHashMap<Pair<String, String>, VideoSearchPaginationSnapshot>(8, 0.75f, true)
    private val pending = IdentityHashMap<EngineSession, Pending>()
    private val proposals = mutableMapOf<Pair<String, String>, Proposal>()
    private val attached = IdentityHashMap<EngineSession, Unit>()
    private val ports = IdentityHashMap<EngineSession, DocumentPort>()
    private val windows = IdentityHashMap<EngineSession, Window>()
    private var observer: kotlinx.coroutines.CoroutineScope? = null
    private var started = false
    private var extensionReady = false

    fun start() {
        if (started) return
        started = true
        try {
            engine.installBuiltInWebExtension(
                id = VideoSearchBridgeProtocol.EXTENSION_ID,
                url = VideoSearchBridgeProtocol.EXTENSION_URL,
                onSuccess = { extension ->
                    try {
                        attach(extension)
                        extensionReady = true
                    } catch (_: Exception) {
                        started = false
                        extensionReady = false
                        // Intentionally no URLs, titles, hosts or tokens in logs.
                    }
                },
                onError = {
                    started = false
                    extensionReady = false
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
        // Private owner is assigned by the process-only tab registry, not JS.
        val manager = ProfileManager.getInstance(applicationContext)
        val owner: VideoSearchScope = if (tab.content.private) {
            VideoSearchPrivateSessionHost.registry.ownerForTab(
                VideoSearchPrivateTab(tab.id, tab.contextId, true),
                manager.getActiveProfile().id
            ) ?: return null
        } else {
            val profile = manager.getAllProfiles().firstOrNull { candidate ->
                VideoSearchScope.Normal(candidate.id).ownsTab(tab.contextId, false)
            } ?: return null
            VideoSearchScope.Normal(profile.id)
        }
        val url = tab.content.url
        try { VideoSearchIntegrity.requirePageUrl(url) }
        catch (_: IllegalArgumentException) { return null }
        return TabSnapshot(tab.id, url, owner)
    }

    private fun isFresh(session: EngineSession, entry: DocumentPort): Boolean {
        val tab = currentTab(session) ?: return false
        return tab.owner == entry.owner &&
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
                // Include selection: a focus change must not be suppressed just
                // because Gecko's session inventory is otherwise unchanged.
                state.selectedTabId to state.tabs.mapNotNull { tab ->
                    tab.engineState.engineSession?.let { session ->
                        Triple(session, tab.contextId, tab.content.url to tab.content.private)
                    }
                }
            }.distinctUntilChanged().collect { (selectedId, live) ->
                privateWorkerLease?.observeSelection(selectedId)
                VideoSearchNormalWorkerSelectionHost.registry.observeSelection(selectedId)
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
                                connection.lastPong = false
                                val current = currentTab(session)
                                if (VideoSearchSpaBinding.maySync(
                                        connection.pageUrl, connection.owner,
                                        current?.pageUrl, current?.owner
                                    )) {
                                    // Keep the *same* Gecko document port; request
                                    // new nonce/revision. Old replies are cancelled.
                                    try {
                                        connection.port.postMessage(
                                            VideoSearchBridgeProtocol.syncUrl()
                                        )
                                    } catch (_: Exception) {
                                        ports.remove(session)
                                        try { connection.port.disconnect() } catch (_: Exception) {}
                                    }
                                } else {
                                    ports.remove(session)
                                    try { connection.port.disconnect() } catch (_: Exception) {}
                                }
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
                    if (!VideoSearchSpaBinding.acceptHello(
                            entry.pageUrl, entry.owner, entry.navigationRevision,
                            tab.pageUrl, tab.owner, parsed
                        )) return
                    // A valid newer same-origin route revokes every previous
                    // request/proposal, even when the underlying port survives.
                    pending.remove(session)?.result?.cancel()
                    proposals.entries.removeAll { it.value.session === session }
                    entry.documentId = parsed.documentId
                    entry.pageUrl = parsed.pageUrl
                    entry.owner = tab.owner
                    entry.navigationRevision = parsed.navigationRevision
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
                is VideoSearchBridgeMessage.Results,
                is VideoSearchBridgeMessage.PageAction -> {
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
                        is VideoSearchBridgeMessage.PageAction -> parsed.requestId
                        else -> return
                    }
                    if (job.requestId != req || job.documentId != parsed.documentId ||
                        !isFresh(session, entry) || job.pageUrl != tab.pageUrl ||
                        tab.owner != job.owner || job.kind != when (parsed) {
                            is VideoSearchBridgeMessage.Inspection -> "inspect"
                            is VideoSearchBridgeMessage.Validation -> "validate"
                            is VideoSearchBridgeMessage.Results -> when {
                                job.kind == "crawl" &&
                                    parsed.sourceEvent == "crawl_results" -> "crawl"
                                job.kind == "extract_batch" &&
                                    parsed.sourceEvent == "results" -> "extract_batch"
                                job.kind == "extract" &&
                                    parsed.sourceEvent == "results" -> "extract"
                                else -> "invalid"
                            }
                            is VideoSearchBridgeMessage.PageAction -> "paginate"
                            is VideoSearchBridgeMessage.Submitted,
                            is VideoSearchBridgeMessage.SearchFailed -> "search"
                            is VideoSearchBridgeMessage.Picked -> "pick:" + parsed.pickKind
                            is VideoSearchBridgeMessage.PickSkipped -> "pick:" + parsed.pickKind
                            is VideoSearchBridgeMessage.PickCancelled -> "pick:" + parsed.pickKind
                            else -> return
                        }) return
                    pending.remove(session)
                    if (job.owner is VideoSearchScope.Normal &&
                        parsed is VideoSearchBridgeMessage.Inspection &&
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


    /**
     * Explicit, single-site, currently selected PRIVATE Gecko tab.
     * The normal worker path remains strictly typed VideoSearchScope.Normal.
     * No private Room writes, disk image cache, background private worker tabs.
     */
    private fun privateSiteTab(
        site: VideoSearchSite, tabId: String, exactPage: Boolean,
        requireSelected: Boolean = true,
    ): Pair<EngineSession, DocumentPort>? {
        val owner = site.scope as? VideoSearchScope.Private ?: return null
        val manager = ProfileManager.getInstance(applicationContext)
        if (!manager.isPrivateMode() || manager.getActiveProfile().id != owner.profileId)
            return null
        val tab = store.state.tabs.singleOrNull {
            it.id == tabId && (!requireSelected || it.id == store.state.selectedTabId)
        } ?: return null
        val session = tab.engineState.engineSession ?: return null
        privateWorkerLease?.takeIf { it.tabId == tabId }?.let { lease ->
            if (!lease.canUse(store.state.selectedTabId) ||
                !lease.acceptsSession(session)) return null
        }
        val state = currentTab(session) ?: return null
        if (!site.enabled || state.tabId != tabId || state.owner != owner ||
            !VideoSearchBridgeProtocol.sameOrigin(state.pageUrl, site.siteUrl) ||
            (exactPage && state.pageUrl.trimEnd('/') != site.siteUrl.trimEnd('/')))
            return null
        val port = ports[session] ?: return null
        return if (isFresh(session, port)) session to port else null
    }

    private suspend fun privateRequest(
        site: VideoSearchSite, tabId: String,
        kind: String, exactPage: Boolean,
        requireSelected: Boolean = true,
        packet: (String, String) -> org.json.JSONObject,
    ): VideoSearchBridgeMessage? {
        // Low-memory can arise during a 120s crawl, not only before leasing.
        if (!requireSelected) {
            val manager = applicationContext.getSystemService(
                Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
            val info = ActivityManager.MemoryInfo()
            try { manager.getMemoryInfo(info) }
            catch (_: Exception) { return null }
            if (VideoSearchWorkerBudget.permits(info.availMem, info.lowMemory) < 1)
                return null
        }
        val (session, entry) = privateSiteTab(
            site, tabId, exactPage, requireSelected
        ) ?: return null
        val document = entry.documentId ?: return null
        if (pending.containsKey(session)) return null
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val result = CompletableDeferred<VideoSearchBridgeMessage>()
        val work = Pending(requestId, document, site.scope, site.id,
            entry.pageUrl ?: return null, kind, result)
        pending[session] = work
        try {
            entry.port.postMessage(packet(document, requestId))
            return withTimeoutOrNull(6_000L) { result.await() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        } finally {
            if (pending[session] === work) pending.remove(session)
        }
    }

    /**
     * Explicit private single-site execution. Same bounded Gecko protocol
     * is also reused by the sequential multiple-site path. All new state
     * lives on the original private epoch; never open normal tabs.
     */
    private suspend fun executePrivateSiteSearch(
        site: VideoSearchSite, tabId: String, profileId: String,
        query: String, requireSelected: Boolean,
        deadlineMs: Long = Long.MAX_VALUE,
        hasAggregateCapacity: () -> Boolean = { true },
        onProgress: suspend (VideoSearchPaginationSnapshot) -> Unit = {},
    ): VideoSearchPaginationSnapshot? {
        if (SystemClock.elapsedRealtime() >= deadlineMs) return null
        val tab = VideoSearchPrivateTab(tabId, "private", true)
        val before = privateSiteTab(site, tabId, true, requireSelected) ?: return null
        val originalSession = before.first
        val originalDocument = before.second.documentId ?: return null
        val detected = privateRequest(
            site, tabId, "inspect", true, requireSelected
        ) { doc, id -> VideoSearchBridgeProtocol.inspect(doc, id) }
            as? VideoSearchBridgeMessage.Inspection ?: return null
        if (detected.inputSelector == null || detected.submission == null) return null
        // Snapshot BEFORE the search. Only a read-only, validated crawl of
        // the exact same selector can prove an SPA's new film generation.
        // A missing/invalid baseline allows only a new Gecko document.
        val baselineSelector = site.configuration?.resultsCardSelector
            ?: detected.resultsCardSelector
        val beforePage = baselineSelector?.let { selector ->
            (privateRequest(site, tabId, "crawl", true, requireSelected) { doc, id ->
                VideoSearchBridgeProtocol.extractCrawl(doc, id,
                    VideoSiteConfiguration(
                        searchInputSelector = detected.inputSelector,
                        resultsCardSelector = selector,
                        submission = detected.submission,
                        submitSelector = detected.submitSelector,
                    ), 0)
            } as? VideoSearchBridgeMessage.Results)?.page
        }?.takeIf { it.status != "stale" }
        val submitted = privateRequest(site, tabId, "search", true, requireSelected) {
            doc, id -> VideoSearchBridgeProtocol.search(
                doc, id, query, detected.inputSelector, detected.submission,
                detected.submitSelector
            )
        } is VideoSearchBridgeMessage.Submitted
        if (!submitted) return null

        // ACK is only dispatch, not proof. Even if cards remain visible,
        // don't attribute them to this query until a NEW document or a
        // verified change of film URLs in the SAME document is observed.
        var config: VideoSiteConfiguration? = null
        var confirmed = false
        for (attempt in 0 until 9) {
            if (SystemClock.elapsedRealtime() >= deadlineMs) return null
            delay(900)
            val live = privateSiteTab(site, tabId, false, requireSelected)
                ?: return null
            if (live.first !== originalSession) return null
            if (privateResults.selectedSite(tab, profileId, site.id) != site) return null
            val inspected = privateRequest(
                site, tabId, "inspect", false, requireSelected
            ) { doc, id -> VideoSearchBridgeProtocol.inspect(doc, id) }
                as? VideoSearchBridgeMessage.Inspection
            val selector = inspected?.resultsCardSelector
                ?: site.configuration?.resultsCardSelector ?: detected.resultsCardSelector
            if (selector == null) continue
            val candidate = VideoSiteConfiguration(
                searchInputSelector = inspected?.inputSelector ?: detected.inputSelector,
                resultsCardSelector = selector,
                submission = inspected?.submission ?: detected.submission,
                submitSelector = inspected?.submitSelector ?: detected.submitSelector,
                pagination = inspected?.pagination ?: site.configuration?.pagination
                    ?: VideoSearchPagination.NONE,
                nextPageSelector = inspected?.nextPageSelector
                    ?: site.configuration?.nextPageSelector,
            )
            val page = (privateRequest(site, tabId, "crawl", false, requireSelected) { doc, id ->
                VideoSearchBridgeProtocol.extractCrawl(doc, id, candidate, 0)
            } as? VideoSearchBridgeMessage.Results)?.page
            // SPA baseline is only comparable for the same configured selector.
            val beforeForSelector = beforePage?.takeIf {
                baselineSelector == candidate.resultsCardSelector
            }
            if (VideoSearchWorkerVerification.confirmed(
                    originalDocument, live.second.documentId, page, beforeForSelector
                )) {
                config = candidate
                confirmed = true
                break
            }
        }
        if (!confirmed) return null
        val snapshot = executePrivatePagination(site, tabId, config!!,
            requireSelected, deadlineMs, hasAggregateCapacity, onProgress)
        val manager = ProfileManager.getInstance(applicationContext)
        return snapshot.takeIf {
            manager.isPrivateMode() && manager.getActiveProfile().id == profileId &&
                privateSiteTab(site, tabId, false, requireSelected) != null &&
                privateResults.selectedSite(tab, profileId, site.id) == site
        }
    }

    fun startPrivateSiteSearch(
        tab: VideoSearchPrivateTab, activeProfileId: String,
        siteId: String, query: String,
    ): Boolean {
        if (!extensionReady || !VideoSearchPrivateJobGate.canStart(privateJob) ||
            !VideoSearchBridgeProtocol.validQuery(query)) return false
        val manager = ProfileManager.getInstance(applicationContext)
        if (!manager.isPrivateMode() || manager.getActiveProfile().id != activeProfileId)
            return false
        val site = privateResults.selectedSite(tab, activeProfileId, siteId)
            ?: return false
        if (privateSiteTab(site, tab.id, exactPage = true) == null) return false
        val privateRunId = privateResults.beginSingle(
            tab, activeProfileId, siteId, query
        ) ?: return false
        privateJob = manualScope.launch {
            try {
                val snapshot = executePrivateSiteSearch(
                    site, tab.id, activeProfileId, query, requireSelected = true,
                    onProgress = { partial ->
                        privateResults.checkpointSingle(
                            tab, activeProfileId, privateRunId, site.id, partial
                        )
                    },
                )
                val saved = snapshot != null && privateResults.checkpointSingle(
                    tab, activeProfileId, privateRunId, site.id, snapshot, terminal = true
                )
                if (saved) Toast.makeText(applicationContext,
                    "Prywatne wyniki zapisano w RAM. Otwórz Moje witryny.",
                    Toast.LENGTH_LONG).show()
                else Toast.makeText(applicationContext,
                    "Nie udało się zebrać wyników na tej prywatnej karcie.",
                    Toast.LENGTH_LONG).show()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(applicationContext,
                    "Prywatne wyszukiwanie przerwane.", Toast.LENGTH_LONG).show()
            } finally {
                privateResults.finishSingle(tab, activeProfileId, privateRunId)
                if (privateJob === coroutineContext[Job]) privateJob = null
            }
        }
        privateJob?.invokeOnCompletion {
            privateResults.finishSingle(tab, activeProfileId, privateRunId)
        }
        return true
    }

    /**
     * Private multi-site searches are sequential. A missing site gets a
     * background-created private worker tab; existing user tabs are NEVER
     * owned or reclaimed. The 180s total budget still applies. Worker
     * ownership, session identity, adoption and private epoch are checked.
     */
    fun startPrivateMultiSite(
        anchor: VideoSearchPrivateTab, activeProfileId: String, query: String,
    ): Boolean {
        if (!extensionReady || !VideoSearchPrivateJobGate.canStart(privateJob) ||
            !VideoSearchBridgeProtocol.validQuery(query)) return false
        val manager = ProfileManager.getInstance(applicationContext)
        if (!manager.isPrivateMode() || manager.getActiveProfile().id != activeProfileId)
            return false
        val plan = privateResults.prepareMulti(anchor, activeProfileId, query) ?: return false
        val groupRunId = privateGroup.start(anchor, activeProfileId, plan) ?: return false
        privateJob = manualScope.launch {
            var visited = 0
            var saved = 0
            var missing = 0
            var lowMemory = false
            val deadline = SystemClock.elapsedRealtime() + 180_000L
            try {
                for (site in plan.sites) {
                    if (!privateGroup.hasCapacity(anchor, activeProfileId, groupRunId)) break
                    if (SystemClock.elapsedRealtime() >= deadline) break
                    val liveManager = ProfileManager.getInstance(applicationContext)
                    if (!liveManager.isPrivateMode() ||
                        liveManager.getActiveProfile().id != activeProfileId ||
                        VideoSearchPrivateSessionHost.registry.ownerForTab(
                            anchor, activeProfileId) != plan.owner) break
                    if (privateResults.selectedSite(anchor, activeProfileId, site.id) != site)
                        continue
                    val memoryInfo = ActivityManager.MemoryInfo()
                    val service = applicationContext.getSystemService(
                        Context.ACTIVITY_SERVICE) as? ActivityManager
                    try { service?.getMemoryInfo(memoryInfo) }
                    catch (_: Exception) { memoryInfo.lowMemory = true }
                    if (service == null || VideoSearchWorkerBudget.permits(
                        memoryInfo.availMem, memoryInfo.lowMemory
                    ) < 1) {
                        lowMemory = true
                        break
                    }
                    val candidates = store.state.tabs.filter {
                        it.content.private && it.contextId == "private" &&
                        it.content.url.trimEnd('/') == site.siteUrl.trimEnd('/') &&
                        VideoSearchPrivateSessionHost.registry.ownerForTab(
                            VideoSearchPrivateTab(it.id, it.contextId, true),
                            activeProfileId
                        ) == plan.owner
                    }
                    // Do not guess which user tab to operate on. Only zero
                    // matches may create a dedicated background private tab.
                    if (candidates.size > 1) {
                        missing++
                        privateGroup.checkpoint(
                            anchor, activeProfileId, groupRunId, site, null
                        )
                        continue
                    }
                    var createdTabId: String? = null
                    var lease: VideoSearchPrivateWorkerLease? = null
                    try {
                        val tabId = if (candidates.size == 1) candidates.single().id else {
                            val newId = applicationContext.components.tabsUseCases
                                .addTab.invoke(
                                    site.siteUrl, selectTab = false, private = true,
                                    contextId = "private",
                                )
                            createdTabId = newId
                            VideoSearchPrivateWorkerLease(newId, plan.owner).also {
                                lease = it
                                privateWorkerLease = it
                                it.observeSelection(store.state.selectedTabId)
                            }
                            newId
                        }
                        // Gecko may suspend newly created background sessions.
                        // Never interpret timeout as success, and never fall
                        // back to a non-private tab/engine.
                        val ready = withTimeoutOrNull(8_000L) {
                            while (true) {
                                if (SystemClock.elapsedRealtime() >= deadline ||
                                    VideoSearchPrivateSessionHost.registry.ownerForTab(
                                        anchor, activeProfileId) != plan.owner) break
                                val managerNow = ProfileManager.getInstance(applicationContext)
                                if (!managerNow.isPrivateMode() ||
                                    managerNow.getActiveProfile().id != activeProfileId) break
                                if (privateSiteTab(site, tabId, true,
                                        requireSelected = false) != null) return@withTimeoutOrNull true
                                if (store.state.tabs.none { it.id == tabId }) break
                                delay(160)
                            }
                            false
                        } ?: false
                        if (!ready || lease?.canUse(store.state.selectedTabId) == false) {
                            missing++
                            privateGroup.checkpoint(
                                anchor, activeProfileId, groupRunId, site, null
                            )
                            continue
                        }
                        val boundLease = lease
                        if (boundLease != null) {
                            val readySession = store.state.tabs.singleOrNull {
                                it.id == tabId
                            }?.engineState?.engineSession
                            if (!boundLease.bindSession(readySession)) {
                                missing++
                                privateGroup.checkpoint(
                                    anchor, activeProfileId, groupRunId, site, null
                                )
                                continue
                            }
                        }
                        visited++
                        val snapshot = executePrivateSiteSearch(
                            site, tabId, activeProfileId, query,
                            requireSelected = false, deadlineMs = deadline,
                            hasAggregateCapacity = {
                                privateGroup.hasCapacity(anchor, activeProfileId, groupRunId)
                            },
                            onProgress = { partial ->
                                val activeLease = lease
                                if (activeLease == null || (
                                    activeLease.canUse(store.state.selectedTabId) &&
                                    activeLease.acceptsSession(store.state.tabs.singleOrNull {
                                        it.id == tabId
                                    }?.engineState?.engineSession))) {
                                    privateGroup.checkpoint(
                                        anchor, activeProfileId, groupRunId, site, partial,
                                        terminal = false
                                    )
                                }
                            },
                        )
                        // A worker selected/adopted while crawling cannot
                        // checkpoint its possibly outdated private payload.
                        val finalLease = lease
                        val safeSnapshot = snapshot?.takeIf {
                            finalLease == null || (finalLease.canUse(store.state.selectedTabId) &&
                                finalLease.acceptsSession(store.state.tabs.singleOrNull {
                                    it.id == tabId
                                }?.engineState?.engineSession))
                        }
                        if (privateGroup.checkpoint(
                                anchor, activeProfileId, groupRunId, site, safeSnapshot
                            ) && safeSnapshot != null) saved++
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        privateGroup.checkpoint(
                            anchor, activeProfileId, groupRunId, site, null
                        )
                    } finally {
                        // Fail closed on selection (including earlier focus),
                        // epoch/profile switch, replaced Gecko session or
                        // navigation outside the owned site's origin.
                        createdTabId?.let { id ->
                            val tab = store.state.tabs.singleOrNull { it.id == id }
                            val currentOwner = tab?.let {
                                VideoSearchPrivateSessionHost.registry.ownerForTab(
                                    VideoSearchPrivateTab(
                                        it.id, it.contextId, it.content.private
                                    ), activeProfileId
                                )
                            }
                            val safeDocument = tab?.content?.url?.let { url ->
                                if (lease?.hasBoundSession == true)
                                    VideoSearchBridgeProtocol.sameOrigin(url, site.siteUrl)
                                else url.trimEnd('/') == site.siteUrl.trimEnd('/')
                            } ?: false
                            val cleanupLease = lease
                            if (cleanupLease != null && cleanupLease.mayReclaim(
                                    tab?.let {
                                        VideoSearchPrivateTab(
                                            it.id, it.contextId, it.content.private
                                        )
                                    },
                                    store.state.selectedTabId, currentOwner,
                                    sameEngine = cleanupLease.acceptsSession(
                                        tab?.engineState?.engineSession
                                    ),
                                    safeDocument = safeDocument,
                                )) {
                                try {
                                    applicationContext.components.tabsUseCases.removeTab(id)
                                } catch (_: Exception) { }
                            }
                        }
                        if (privateWorkerLease === lease) privateWorkerLease = null
                    }
                    delay(40)
                }
                if (VideoSearchPrivateSessionHost.registry.ownerForTab(
                        anchor, activeProfileId) == plan.owner) {
                    Toast.makeText(applicationContext,
                        "Prywatne witryny: przetworzono $visited, " +
                            "wyniki zapisane: $saved, niedostępne karty: $missing." +
                            (if (lowMemory) " Zatrzymano przez małą pamięć." else ""),
                        Toast.LENGTH_LONG).show()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } finally {
                // Even cancellation/timeout completes queued group tasks as
                // STOPPED, never as a fabricated successful search.
                try {
                    privateGroup.finish(anchor, activeProfileId, groupRunId)
                } catch (_: Exception) { }
                if (privateJob === coroutineContext[Job]) privateJob = null
            }
        }
        // A cancelled job may complete BEFORE entering its body on Main.
        // In that case the in-body finally never runs; still finalize RAM
        // tasks. finish() is idempotent when the normal finally ran first.
        // No durable data or application-wide background worker is created.
        privateJob?.invokeOnCompletion {
            try {
                privateGroup.finish(anchor, activeProfileId, groupRunId)
            } catch (_: Exception) { }
        }
        return true
    }

    /** Only the initiating private lifetime can cancel its queued work. */
    fun stopPrivateSearch(anchor: VideoSearchPrivateTab, profileId: String): Boolean {
        val owner = VideoSearchPrivateSessionHost.registry.ownerForTab(anchor, profileId)
            ?: return false
        val job = privateJob ?: return false
        val active = ProfileManager.getInstance(applicationContext)
        if (!active.isPrivateMode() || active.getActiveProfile().id != owner.profileId ||
            !job.isActive) return false
        job.cancel()
        return true
    }


    /**
     * Stage 10D: bounded private SAME-TAB pagination. No worker tab, Room,
     * history disk, cache or background search beyond this user action.
     * Abort when the selected Gecko EngineSession/profile/lifetime changes.
     * The existing collector verifies same-origin URLs and film identity.
     */
    private suspend fun executePrivatePagination(
        site: VideoSearchSite, tabId: String, config: VideoSiteConfiguration,
        requireSelected: Boolean = true,
        deadlineMs: Long = Long.MAX_VALUE,
        hasAggregateCapacity: () -> Boolean = { true },
        onProgress: suspend (VideoSearchPaginationSnapshot) -> Unit = {},
    ): VideoSearchPaginationSnapshot? {
        val first = privateSiteTab(
            site, tabId, exactPage = false, requireSelected = requireSelected
        ) ?: return null
        val originalSession = first.first
        val firstUrl = currentTab(originalSession)?.pageUrl ?: return null
        val collector = VideoSearchPaginationCollector(site.siteUrl, 200)
        if (!collector.beginDocument(firstUrl)) return null
        val deadline = minOf(
            SystemClock.elapsedRealtime() + 120_000L, deadlineMs
        )
        var stagnantPages = 0

        fun authorized(): Boolean {
            val live = privateSiteTab(site, tabId, false, requireSelected) ?: return false
            if (live.first !== originalSession) return false
            return privateResults.selectedSite(
                VideoSearchPrivateTab(tabId, "private", true),
                (site.scope as VideoSearchScope.Private).profileId, site.id
            ) == site
        }

        fun stop(reason: VideoSearchPaginationStop) = collector.snapshot(reason)
        while (true) {
            if (!hasAggregateCapacity())
                return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)
            if (SystemClock.elapsedRealtime() >= deadline)
                return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)
            if (!authorized()) return stop(VideoSearchPaginationStop.CONFIGURATION_CHANGED)

            var newInDocument = 0
            while (collector.shouldReadCardWindow) {
                if (!hasAggregateCapacity())
                    return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)
                if (SystemClock.elapsedRealtime() >= deadline)
                    return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)
                if (!authorized()) return stop(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
                val cursor = collector.nextCardIndex
                val result = privateRequest(site, tabId, "crawl", false, requireSelected) { doc, request ->
                    VideoSearchBridgeProtocol.extractCrawl(doc, request, config, cursor)
                } as? VideoSearchBridgeMessage.Results
                    ?: return stop(VideoSearchPaginationStop.ERROR)
                if (result.page.status == "no_results") {
                    return if (collector.uniqueCount == 0 && collector.nextCardIndex == 0 &&
                        collector.visitedPages == 1)
                        stop(VideoSearchPaginationStop.NO_RESULTS)
                    else stop(VideoSearchPaginationStop.ERROR)
                }
                try {
                    val added = collector.appendWindow(cursor, result.page)
                    newInDocument += added
                    if (added > 0 && authorized())
                        onProgress(stop(VideoSearchPaginationStop.RESOURCE_LIMIT))
                } catch (_: IllegalArgumentException) {
                    return stop(VideoSearchPaginationStop.ERROR)
                }
                if (collector.reachedLimit)
                    return stop(VideoSearchPaginationStop.LIMIT_REACHED)
                delay(24)
            }

            if (collector.reachedLimit)
                return stop(VideoSearchPaginationStop.LIMIT_REACHED)
            if (collector.exhaustedCardBudget)
                return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)
            if (config.pagination == VideoSearchPagination.NONE)
                return stop(VideoSearchPaginationStop.UNSUPPORTED)
            if (collector.isAtActionLimit ||
                (collector.isAtDocumentLimit &&
                    config.pagination == VideoSearchPagination.NEXT_LINK))
                return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)

            val before = privateSiteTab(site, tabId, false, requireSelected)
                ?: return stop(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
            val beforeDocument = before.second.documentId
                ?: return stop(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
            val beforeUrl = currentTab(originalSession)?.pageUrl
                ?: return stop(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
            var dispatched = false
            // A temporarily absent or disabled pagination button must NOT
            // turn a partially collected run into a successful terminal run.
            for (attempt in 0 until 4) {
                if (attempt > 0) delay(750)
                if (SystemClock.elapsedRealtime() >= deadline)
                    return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)
                if (!authorized()) return stop(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
                val action = privateRequest(site, tabId, "paginate", false, requireSelected) { doc, request ->
                    VideoSearchBridgeProtocol.paginate(doc, request, config)
                } as? VideoSearchBridgeMessage.PageAction
                    ?: return stop(VideoSearchPaginationStop.ERROR)
                if (action.pagination != config.pagination)
                    return stop(VideoSearchPaginationStop.ERROR)
                when (action.status) {
                    "unsafe" -> return stop(VideoSearchPaginationStop.UNSAFE)
                    "end" -> return stop(if (collector.uniqueCount > 0)
                        VideoSearchPaginationStop.NO_MORE_RESULTS
                        else VideoSearchPaginationStop.STALLED)
                    "pending", "unavailable" -> Unit
                    "dispatched" -> {
                        if (!collector.recordAction())
                            return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)
                        dispatched = true
                        break
                    }
                    else -> return stop(VideoSearchPaginationStop.ERROR)
                }
            }
            if (!dispatched) return stop(VideoSearchPaginationStop.STALLED)

            if (config.pagination == VideoSearchPagination.NEXT_LINK) {
                val nextUrl = withTimeoutOrNull(7_000L) {
                    var destination: String? = null
                    while (destination == null) {
                        delay(160)
                        if (!authorized()) break
                        val live = privateSiteTab(site, tabId, false, requireSelected) ?: break
                        val url = currentTab(originalSession)?.pageUrl ?: break
                        if (url != beforeUrl &&
                            live.second.documentId != beforeDocument &&
                            VideoSearchBridgeProtocol.sameOrigin(url, site.siteUrl))
                            destination = url
                    }
                    destination
                } ?: return stop(VideoSearchPaginationStop.STALLED)
                if (!collector.beginDocument(nextUrl))
                    return stop(VideoSearchPaginationStop.STALLED)
                stagnantPages = if (newInDocument == 0) stagnantPages + 1 else 0
                if (stagnantPages >= 2) return stop(VideoSearchPaginationStop.STALLED)
            } else {
                if (!authorized()) return stop(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
                collector.armSameDocumentContinuation()
                var grew = false
                for (attempt in 0..2) {
                    delay(if (attempt == 0) 400L else 650L)
                    if (SystemClock.elapsedRealtime() >= deadline)
                        return stop(VideoSearchPaginationStop.RESOURCE_LIMIT)
                    if (!authorized()) return stop(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
                    val cursor = collector.nextCardIndex
                    val result = privateRequest(site, tabId, "crawl", false, requireSelected) { doc, request ->
                        VideoSearchBridgeProtocol.extractCrawl(doc, request, config, cursor)
                    } as? VideoSearchBridgeMessage.Results
                        ?: return stop(VideoSearchPaginationStop.ERROR)
                    if (result.page.status == "stale")
                        return stop(VideoSearchPaginationStop.ERROR)
                    if (result.page.inspectedCards == 0) continue
                    try {
                        val added = collector.appendWindow(cursor, result.page)
                        if (added > 0 && authorized())
                            onProgress(stop(VideoSearchPaginationStop.RESOURCE_LIMIT))
                    } catch (_: IllegalArgumentException) {
                        return stop(VideoSearchPaginationStop.ERROR)
                    }
                    grew = true
                    break
                }
                if (!grew) return stop(VideoSearchPaginationStop.STALLED)
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

    /** Worker may visit /results and /page2, but never another origin or tab. */
    private fun workerOwnedTab(
        site: VideoSearchSite, workerTabId: String,
    ): Pair<EngineSession, DocumentPort>? {
        val owner = site.scope as? VideoSearchScope.Normal ?: return null
        val tab = store.state.tabs.singleOrNull { it.id == workerTabId } ?: return null
        val session = tab.engineState.engineSession ?: return null
        val lease = VideoSearchNormalWorkerSelectionHost.registry.forTab(workerTabId) ?: return null
        if (lease.owner != owner || !lease.bindSession(session, store.state.selectedTabId))
            return null
        val state = currentTab(session) ?: return null
        if (!site.enabled || state.owner != owner || state.tabId != workerTabId ||
            !VideoSearchBridgeProtocol.sameOrigin(state.pageUrl, site.siteUrl)) return null
        val port = ports[session] ?: return null
        return if (isFresh(session, port)) session to port else null
    }

    private fun workerSiteTab(
        site: VideoSearchSite, workerTabId: String,
    ): Pair<EngineSession, DocumentPort>? {
        val owner = site.scope as? VideoSearchScope.Normal ?: return null
        if (!site.enabled) return null
        val tab = store.state.tabs.singleOrNull { it.id == workerTabId } ?: return null
        val session = tab.engineState.engineSession ?: return null
        val lease = VideoSearchNormalWorkerSelectionHost.registry.forTab(workerTabId) ?: return null
        if (lease.owner != owner || !lease.bindSession(session, store.state.selectedTabId))
            return null
        val state = currentTab(session) ?: return null
        if (state.owner != owner || state.tabId != workerTabId ||
            state.pageUrl.trimEnd('/') != site.siteUrl.trimEnd('/')) return null
        val entry = ports[session] ?: return null
        return if (isFresh(session, entry)) session to entry else null
    }

    private suspend fun request(
        site: VideoSearchSite,
        kind: String,
        timeoutMs: Long = 8_000L,
        workerTabId: String? = null,
        packet: (String, String) -> org.json.JSONObject,
    ): VideoSearchBridgeMessage? {
        val scope = site.scope as? VideoSearchScope.Normal ?: return null
        // Backpressure below existing 12 messages/5s per-session rate cap.
        if (kind == "crawl" || kind == "paginate") delay(540)
        val target = if (workerTabId != null) {
            if (kind == "search") workerSiteTab(site, workerTabId)
            else workerOwnedTab(site, workerTabId)
        }
            else if (kind.startsWith("pick:") ||
                kind == "validate" || kind == "extract" ||
                kind == "extract_batch" || kind == "crawl" ||
                kind == "paginate") manualSiteTab(site)
            else existingSiteTab(site)
        val (session, entry) = target ?: return null
        val documentId = entry.documentId ?: return null
        if (pending.containsKey(session)) return null
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val result = CompletableDeferred<VideoSearchBridgeMessage>()
        val work = Pending(requestId, documentId, scope, site.id,
            entry.pageUrl ?: return null, kind, result)
        pending[session] = work
        try {
            entry.port.postMessage(packet(documentId, requestId))
            val reply = withTimeoutOrNull(timeoutMs) { result.await() }
            if (workerTabId != null) {
                val lease = VideoSearchNormalWorkerSelectionHost.registry.forTab(workerTabId) ?: return null
                val live = store.state.tabs.singleOrNull { it.id == workerTabId }
                    ?: return null
                if (lease.owner != scope || !lease.canUse(store.state.selectedTabId) ||
                    !lease.acceptsSession(live.engineState.engineSession) ||
                    !scope.ownsTab(live.contextId, live.content.private) ||
                    !VideoSearchBridgeProtocol.sameOrigin(live.content.url, site.siteUrl))
                    return null
            }
            return reply
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
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
        val config = site.configuration ?: return null
        if (!VideoSearchBridgeProtocol.validSelector(config.resultsCardSelector)) return null
        if (site.scope !is VideoSearchScope.Normal || !site.enabled) return null
        val source = manualSiteTab(site) ?: return null
        val originPage = currentTab(source.first)?.pageUrl ?: return null
        val result = request(site, "extract") { document, req ->
            VideoSearchBridgeProtocol.extract(document, req, config)
        } as? VideoSearchBridgeMessage.Results ?: return null
        // Result links are film PAGE links, not arbitrary external adverts.
        if (result.page.films.any {
                !VideoSearchBridgeProtocol.sameOrigin(it.filmPageUrl, originPage)
            }) return null
        // A second deterministic guard preserves one row per exact URL,
        // never correlating on title similarity or changing URL parameters.
        return result.page.copy(
            films = VideoSearchResultCorrelator.correlate(result.page.films)
        )
    }

    /**
     * Read up to five short 16-card windows from the SAME current document.
     * This is an on-demand preview, not cross-page pagination or a search run.
     * Never persist URLs, open tabs, or invoke next-page controls here.
     */
    suspend fun collectVisiblePage(site: VideoSearchSite): VideoSearchVisiblePage? {
        val config = site.configuration ?: return null
        if (!VideoSearchBridgeProtocol.validSelector(config.resultsCardSelector) ||
            !site.enabled || site.scope !is VideoSearchScope.Normal) return null
        val initial = manualSiteTab(site) ?: return null
        val document = initial.second.documentId ?: return null
        val url = currentTab(initial.first)?.pageUrl ?: return null
        val accumulator = VideoSearchVisiblePageCollector(
            url, site.resultLimit, config.pagination
        )
        while (accumulator.shouldReadAnotherBatch) {
            // Abort on navigation, changed profile, or replacement of the
            // active tab between adjacent 16-card reads.
            val live = manualSiteTab(site) ?: return null
            if (live.first !== initial.first || live.second.documentId != document ||
                currentTab(live.first)?.pageUrl != url) return null
            val offset = accumulator.nextCardIndex
            val result = request(site, "extract_batch") { d, req ->
                VideoSearchBridgeProtocol.extractBatch(d, req, config, offset)
            } as? VideoSearchBridgeMessage.Results ?: return null
            try {
                accumulator.append(offset, result.page)
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        return accumulator.snapshot()
    }

    /** Most recent completed *attempt*, RAM-only and profile-scoped. */
    fun lastPagination(scope: VideoSearchScope.Normal, siteId: String)
        : VideoSearchPaginationSnapshot? = recentCrawls[scope.profileId to siteId]

    fun canStartPagination(site: VideoSearchSite): Boolean =
        !crawlBusy && !manualBusy && !isMultiSiteActive() && site.enabled &&
            site.scope is VideoSearchScope.Normal &&
            site.configuration?.resultsCardSelector?.let(
                VideoSearchBridgeProtocol::validSelector
            ) == true && manualSiteTab(site) != null

    /** User action only; continues after MyVideoSitesActivity returns to Gecko. */
    fun startPagination(site: VideoSearchSite, repository: VideoSearchRepository): Boolean {
        if (!canStartPagination(site)) return false
        crawlBusy = true
        manualScope.launch {
            try {
                val result = executePagination(site, repository)
                if (result != null) {
                    val scope = site.scope as VideoSearchScope.Normal
                    recentCrawls[scope.profileId to site.id] = result
                    if (recentCrawls.size > 8)
                        recentCrawls.remove(recentCrawls.keys.first())
                    val label = when (result.reason) {
                        VideoSearchPaginationStop.LIMIT_REACHED -> "Osiągnięto limit"
                        VideoSearchPaginationStop.NO_MORE_RESULTS -> "Brak kolejnych stron"
                VideoSearchPaginationStop.NO_RESULTS -> "Potwierdzony brak filmów"
                        VideoSearchPaginationStop.UNSUPPORTED -> "Brak ustawionej paginacji"
                        VideoSearchPaginationStop.STALLED -> "Brak postępu"
                        VideoSearchPaginationStop.UNSAFE -> "Niebezpieczna paginacja"
                        VideoSearchPaginationStop.RESOURCE_LIMIT -> "Limit zasobów"
                        VideoSearchPaginationStop.CONFIGURATION_CHANGED -> "Zmiana sesji/witryny"
                        VideoSearchPaginationStop.ERROR -> "Błąd komunikacji"
                    }
                    Toast.makeText(applicationContext,
                        "Zavro: ${result.films.size} unikalnych filmów. $label.",
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(applicationContext,
                        "Zavro: nie można rozpocząć paginacji na tej karcie.",
                        Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(applicationContext,
                    "Zavro: paginacja przerwana. Sprawdź konfigurację.",
                    Toast.LENGTH_LONG).show()
            } finally {
                crawlBusy = false
            }
        }
        return true
    }

    private suspend fun executePagination(
        site: VideoSearchSite, repository: VideoSearchRepository,
        workerTabId: String? = null,
        hasAggregateCapacity: () -> Boolean = { true },
        onProgress: suspend (VideoSearchPaginationSnapshot) -> Unit = {},
    ): VideoSearchPaginationSnapshot? {
        val config = site.configuration ?: return null
        val scope = site.scope as? VideoSearchScope.Normal ?: return null
        fun matchingTab() = if (workerTabId == null) manualSiteTab(site)
            else workerOwnedTab(site, workerTabId)
        val first = matchingTab() ?: return null
        val session = first.first
        val startingUrl = currentTab(session)?.pageUrl ?: return null
        val collector = VideoSearchPaginationCollector(site.siteUrl, site.resultLimit)
        if (!collector.beginDocument(startingUrl)) return null
        val deadline = SystemClock.elapsedRealtime() + 120_000L
        var noNewPages = 0

        suspend fun stillAuthorized(): Boolean {
            val live = matchingTab() ?: return false
            if (workerTabId != null) {
                val manager = ProfileManager.getInstance(applicationContext)
                if (manager.isPrivateMode() ||
                    manager.getActiveProfile().id != scope.profileId) return false
            }
            if (live.first !== session) return false
            val stored = withContext(Dispatchers.IO) {
                repository.site(scope, site.id)
            }
            return stored == site
        }

        while (true) {
            if (!hasAggregateCapacity())
                return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)
            if (SystemClock.elapsedRealtime() >= deadline)
                return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)
            if (!stillAuthorized())
                return collector.snapshot(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
            var newInDocument = 0
            while (collector.shouldReadCardWindow) {
                if (!hasAggregateCapacity())
                    return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)
                if (SystemClock.elapsedRealtime() >= deadline)
                    return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)
                if (!stillAuthorized())
                    return collector.snapshot(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
                val offset = collector.nextCardIndex
                val reply = request(site, "crawl", workerTabId = workerTabId) { doc, id ->
                    VideoSearchBridgeProtocol.extractCrawl(doc, id, config, offset)
                } as? VideoSearchBridgeMessage.Results
                    ?: return collector.snapshot(VideoSearchPaginationStop.ERROR)
                if (reply.page.status == "no_results") {
                    return collector.snapshot(if (collector.uniqueCount == 0 &&
                        collector.nextCardIndex == 0 && collector.visitedPages == 1)
                        VideoSearchPaginationStop.NO_RESULTS
                        else VideoSearchPaginationStop.ERROR)
                }
                try {
                    val added = collector.appendWindow(offset, reply.page)
                    newInDocument += added
                    if (added > 0 && stillAuthorized())
                        onProgress(collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT))
                } catch (_: IllegalArgumentException) {
                    return collector.snapshot(VideoSearchPaginationStop.ERROR)
                }
                if (collector.reachedLimit)
                    return collector.snapshot(VideoSearchPaginationStop.LIMIT_REACHED)
                delay(24) // Yield across bounded, at-most-16-card DOM reads.
            }

            if (collector.reachedLimit)
                return collector.snapshot(VideoSearchPaginationStop.LIMIT_REACHED)
            if (collector.exhaustedCardBudget)
                return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)

            if (config.pagination == VideoSearchPagination.NONE)
                return collector.snapshot(VideoSearchPaginationStop.UNSUPPORTED)
            if ((collector.isAtDocumentLimit &&
                    config.pagination == VideoSearchPagination.NEXT_LINK) ||
                collector.isAtActionLimit)
                return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)

            // NEXT_LINK must actually produce a new document on the SAME
            // EngineSession and origin. LOAD_MORE/SCROLL must append new cards.
            val before = matchingTab() ?: return collector.snapshot(
                VideoSearchPaginationStop.CONFIGURATION_CHANGED
            )
            val beforeDoc = before.second.documentId ?: return collector.snapshot(
                VideoSearchPaginationStop.CONFIGURATION_CHANGED
            )
            val beforeUrl = currentTab(session)?.pageUrl ?: return collector.snapshot(
                VideoSearchPaginationStop.CONFIGURATION_CHANGED
            )
            var dispatched = false
            for (attempt in 0 until 4) {
                if (attempt > 0) delay(750)
                if (SystemClock.elapsedRealtime() >= deadline)
                    return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)
                if (!stillAuthorized())
                    return collector.snapshot(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
                val action = request(site, "paginate", workerTabId = workerTabId) { doc, id ->
                    VideoSearchBridgeProtocol.paginate(doc, id, config)
                } as? VideoSearchBridgeMessage.PageAction
                    ?: return collector.snapshot(VideoSearchPaginationStop.ERROR)
                if (action.pagination != config.pagination)
                    return collector.snapshot(VideoSearchPaginationStop.ERROR)
                when (action.status) {
                    "unsafe" -> return collector.snapshot(VideoSearchPaginationStop.UNSAFE)
                    "end" -> return collector.snapshot(if (collector.uniqueCount > 0)
                        VideoSearchPaginationStop.NO_MORE_RESULTS
                        else VideoSearchPaginationStop.STALLED)
                    "pending", "unavailable" -> Unit
                    "dispatched" -> {
                        if (!collector.recordAction())
                            return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)
                        dispatched = true
                        break
                    }
                    else -> return collector.snapshot(VideoSearchPaginationStop.ERROR)
                }
            }
            if (!dispatched) return collector.snapshot(VideoSearchPaginationStop.STALLED)

            if (config.pagination == VideoSearchPagination.NEXT_LINK) {
                val next = withTimeoutOrNull(7_000L) {
                    var destination: String? = null
                    while (destination == null) {
                        delay(160)
                        val live = matchingTab()
                        if (live?.first !== session) continue
                        val current = currentTab(session)?.pageUrl ?: continue
                        if (current != beforeUrl &&
                            live.second.documentId != beforeDoc &&
                            VideoSearchBridgeProtocol.sameOrigin(current, site.siteUrl))
                            destination = current
                    }
                    destination
                } ?: return collector.snapshot(VideoSearchPaginationStop.STALLED)
                if (!collector.beginDocument(next))
                    return collector.snapshot(VideoSearchPaginationStop.STALLED)
                if (newInDocument == 0) noNewPages++ else noNewPages = 0
                if (noNewPages >= 2)
                    return collector.snapshot(VideoSearchPaginationStop.STALLED)
            } else {
                // Same document; allow *only appended* positions. Do not
                // re-read the first cards or click repeatedly on no progress.
                if (!stillAuthorized())
                    return collector.snapshot(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
                collector.armSameDocumentContinuation()
                var grew = false
                for (attempt in 0..2) {
                    if (SystemClock.elapsedRealtime() >= deadline)
                        return collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT)
                    delay(if (attempt == 0) 400L else 650L)
                    if (!stillAuthorized())
                        return collector.snapshot(VideoSearchPaginationStop.CONFIGURATION_CHANGED)
                    val offset = collector.nextCardIndex
                    val reply = request(site, "crawl", workerTabId = workerTabId) { doc, id ->
                        VideoSearchBridgeProtocol.extractCrawl(doc, id, config, offset)
                    } as? VideoSearchBridgeMessage.Results
                        ?: return collector.snapshot(VideoSearchPaginationStop.ERROR)
                    if (reply.page.status == "stale")
                        return collector.snapshot(VideoSearchPaginationStop.ERROR)
                    if (reply.page.inspectedCards == 0) continue
                    try {
                        val added = collector.appendWindow(offset, reply.page)
                        if (added > 0 && stillAuthorized())
                            onProgress(collector.snapshot(VideoSearchPaginationStop.RESOURCE_LIMIT))
                    } catch (_: IllegalArgumentException) {
                        return collector.snapshot(VideoSearchPaginationStop.ERROR)
                    }
                    grew = true
                    break
                }
                if (!grew)
                    return collector.snapshot(VideoSearchPaginationStop.STALLED)
            }
        }
    }

    private suspend fun pickSite(site: VideoSearchSite, kind: String): VideoSearchBridgeMessage? =
        request(site, "pick:$kind", timeoutMs = 101_000L) { document, req ->
            VideoSearchBridgeProtocol.pick(document, req, kind)
        }

    /** The picker must target the page currently visible behind My Sites. */
    fun canStartManual(site: VideoSearchSite): Boolean {
        if (manualBusy || crawlBusy || isMultiSiteActive()) return false
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
                // A persisted partial configuration is a durable checkpoint:
                // the results page may have NO search input at all. Never
                // repick or overwrite an already confirmed input/submit.
                val partial = VideoSearchManualStages.pendingResults(site)
                val searchControls = if (partial != null) partial else {
                    val input = pickSite(site, "input") as? VideoSearchBridgeMessage.Picked
                        ?: return@launch
                    if (input.pickKind != "input") return@launch
                    if (manualSiteTab(site)?.let {
                            it.first === original.first &&
                                it.second.documentId == originalDocument
                        } != true) return@launch

                    val submit = pickSite(site, "submit")
                    if (submit is VideoSearchBridgeMessage.PickCancelled || submit == null)
                        return@launch
                    val submitSelector = (submit as? VideoSearchBridgeMessage.Picked)?.selector
                    // Skipping submit is legal only for a verified safe form.
                    if (submitSelector == null && !input.formSafe) return@launch
                    val submission = if (submitSelector != null)
                        VideoSearchSubmission.CLICK_SUBMIT else VideoSearchSubmission.FORM_SUBMIT
                    VideoSiteConfiguration(
                        searchInputSelector = input.selector, resultsCardSelector = null,
                        submission = submission, submitSelector = submitSelector,
                    )
                }
                if (manualSiteTab(site)?.let {
                        it.first === original.first &&
                            it.second.documentId == originalDocument
                    } != true) return@launch

                val cards = pickSite(site, "cards")
                if (cards is VideoSearchBridgeMessage.PickCancelled || cards == null)
                    return@launch
                val cardSelector = (cards as? VideoSearchBridgeMessage.Picked)?.selector
                // If there are no cards on the homepage, save the FORM
                // phase now. On a later results page, "Wskaż" completes it.
                if (cardSelector == null && partial != null) {
                    Toast.makeText(applicationContext,
                        "Wybierz kartę filmu na stronie wyników, aby dokończyć.",
                        Toast.LENGTH_LONG).show()
                    feedbackSent = true
                    return@launch
                }

                if (cardSelector != null) {
                    // A clicked DOM container is not necessarily a film card.
                    // Require at least one verified on-site film URL in the
                    // SAME bound results document before enabling this site.
                    val candidate = VideoSearchManualStages.withResults(
                        searchControls, cardSelector, null
                    ) ?: return@launch
                    val extracted = request(site, "crawl") { document, id ->
                        VideoSearchBridgeProtocol.extractCrawl(document, id, candidate, 0)
                    } as? VideoSearchBridgeMessage.Results
                    if (extracted?.page?.status != "ok" ||
                        extracted.page.films.isEmpty()) {
                        Toast.makeText(applicationContext,
                            "Wskazany element nie zawiera poprawnej karty filmu. " +
                                "Dotychczasowa konfiguracja została zachowana.",
                            Toast.LENGTH_LONG).show()
                        feedbackSent = true
                        return@launch
                    }
                }
                val next = if (cardSelector == null) null else pickSite(site, "next")
                if (cardSelector != null && (next is VideoSearchBridgeMessage.PickCancelled ||
                        next == null)) return@launch
                if (manualSiteTab(site)?.let {
                        it.first === original.first &&
                            it.second.documentId == originalDocument
                    } != true) return@launch

                val nextPicked = next as? VideoSearchBridgeMessage.Picked
                val configuration = VideoSearchManualStages.withResults(
                    searchControls, cardSelector, nextPicked
                ) ?: return@launch
                // Optimistic CAS: a deleted/renamed/reconfigured site cannot
                // be resurrected or overwritten by an old picker session.
                val saved = withContext(Dispatchers.IO) {
                    repository.applyManualConfiguration(site, configuration)
                }
                val message = when {
                    !saved -> "Nie zapisano: witryna zmieniła się. Ponów konfigurację."
                    cardSelector != null -> "Zapisano karty wyników. Konfiguracja witryny jest gotowa."
                    else -> "Zapisano formularz. Otwórz wyniki na tej witrynie, " +
                        "wróć do „Moje witryny” i wybierz „Wskaż” ponownie."
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

    /** Site-specific per-tab search: verifies trusted context, exact saved
     * URL, engine identity and document port. Never selects the worker tab. */
    private suspend fun submitWorker(
        site: VideoSearchSite, tabId: String, query: String,
        repository: VideoSearchRepository,
    ): VideoSearchQueueStatus {
        val cfg = site.configuration ?: return VideoSearchQueueStatus.NEEDS_ATTENTION
        val scope = site.scope as? VideoSearchScope.Normal
            ?: return VideoSearchQueueStatus.STOPPED
        fun ownerIsActive(): Boolean {
            val manager = ProfileManager.getInstance(applicationContext)
            return !manager.isPrivateMode() &&
                manager.getActiveProfile().id == scope.profileId
        }
        if (!ownerIsActive()) return VideoSearchQueueStatus.STOPPED
        val live = withTimeoutOrNull(10_000L) {
            while (workerSiteTab(site, tabId) == null) {
                val lease = VideoSearchNormalWorkerSelectionHost.registry.forTab(tabId)
                if (lease?.owner != scope || !lease.canUse(store.state.selectedTabId))
                    return@withTimeoutOrNull false
                if (!ownerIsActive()) return@withTimeoutOrNull false
                // Background tabs may be lazily paused: no spoofed success.
                if (store.state.tabs.none { it.id == tabId }) return@withTimeoutOrNull false
                delay(180)
            }
            true
        } ?: false
        if (!live) return if (ownerIsActive())
            VideoSearchQueueStatus.NEEDS_ATTENTION else VideoSearchQueueStatus.STOPPED
        if (!ownerIsActive()) return VideoSearchQueueStatus.STOPPED
        // The site can be edited while Gecko creates a background session.
        // Never send a query using stale or disabled selectors.
        val latest = withContext(Dispatchers.IO) {
            repository.site(scope, site.id)
        }
        if (latest != site || !latest.enabled)
            return VideoSearchQueueStatus.NEEDS_ATTENTION
        val response = request(site, "search", workerTabId = tabId) { document, request ->
            VideoSearchBridgeProtocol.search(document, request, query,
                cfg.searchInputSelector, cfg.submission, cfg.submitSelector)
        }
        if (!ownerIsActive()) return VideoSearchQueueStatus.STOPPED
        return when (response) {
            is VideoSearchBridgeMessage.Submitted -> VideoSearchQueueStatus.SUBMITTED
            is VideoSearchBridgeMessage.SearchFailed ->
                VideoSearchQueueStatus.NEEDS_ATTENTION
            else -> VideoSearchQueueStatus.FAILED
        }
    }

    /**
     * An ACK is not enough. Confirm that the original document has been
     * replaced AND the worker's actual results document contains a film card.
     * SPA-only same-document responses are conservatively NEEDS_ATTENTION,
     * never misreported as a completed search.
     */
    private suspend fun awaitWorkerResults(
        site: VideoSearchSite, tabId: String, beforeDocument: String,
        beforePage: VideoSearchExtractedPage?,
    ): Boolean {
        val scope = site.scope as? VideoSearchScope.Normal ?: return false
        val start = SystemClock.elapsedRealtime()
        val cfg = site.configuration ?: return false
        while (SystemClock.elapsedRealtime() - start < 13_000L) {
            val lease = VideoSearchNormalWorkerSelectionHost.registry.forTab(tabId)
            if (lease?.owner != scope || !lease.canUse(store.state.selectedTabId)) return false
            val manager = ProfileManager.getInstance(applicationContext)
            if (manager.isPrivateMode() ||
                manager.getActiveProfile().id != scope.profileId) return false
            val live = workerOwnedTab(site, tabId)
            if (live != null) {
                val answer = request(site, "crawl", workerTabId = tabId) { doc, req ->
                    VideoSearchBridgeProtocol.extractCrawl(doc, req, cfg, 0)
                } as? VideoSearchBridgeMessage.Results
                if (VideoSearchWorkerVerification.confirmed(
                        beforeDocument, live.second.documentId, answer?.page, beforePage
                    )) return true
                if (answer?.page?.status == "stale") return false
            }
            delay(380)
        }
        return false
    }

    fun lastMultiRunId(scope: VideoSearchScope.Normal): String? =
        lastMultiRunIds[scope.profileId]

    fun multiSiteStatus(scope: VideoSearchScope.Normal): VideoSearchQueueSnapshot? =
        normalRunGate.liveQueue(scope)?.snapshot() ?: lastMulti[scope.profileId]

    // Cancellation and NonCancellable journal cleanup still occupy the slot.
    fun isMultiSiteActive(): Boolean = !normalRunGate.canStart()

    fun stopMultiSite(scope: VideoSearchScope.Normal): Boolean =
        normalRunGate.stop(scope)

    /**
     * User-authorized normal-profile multi-site run. One transactional Room
     * journal stores the run, site tasks and validated film-page metadata.
     * Worker tabs are new, background and never adopt the selected user tab.
     * Never declare success from the search form ACK alone.
     */
    fun startMultiSite(
        plan: VideoSearchDispatchPlan, repository: VideoSearchRepository,
    ): Boolean {
        if (isMultiSiteActive() || manualBusy || crawlBusy || !extensionReady) return false
        val manager = ProfileManager.getInstance(applicationContext)
        if (manager.isPrivateMode() ||
            manager.getActiveProfile().id != plan.scope.profileId ||
            plan.runnableSites.isEmpty()) return false
        val queue = plan.createQueue()
        val journal = VideoSearchMultiRunJournal(repository, plan)
        // LAZY guarantees that the job is registered before executing any body.
        val job = manualScope.launch(start = CoroutineStart.LAZY) {
            try {
                withContext(Dispatchers.IO) {
                    repository.recoverOrphanedRunsOnce(plan.scope)
                    journal.initialize()
                }
                lastMultiRunIds[plan.scope.profileId] = journal.runId
                if (lastMultiRunIds.size > 4)
                    lastMultiRunIds.remove(lastMultiRunIds.keys.first())
                withTimeoutOrNull(180_000L) {
                    VideoSearchMultiSiteExecutor.dispatch(
                        queue,
                        permittedWorkers = {
                            val am = applicationContext.getSystemService(
                                Context.ACTIVITY_SERVICE
                            ) as? ActivityManager
                            val mem = ActivityManager.MemoryInfo()
                            try { am?.getMemoryInfo(mem) }
                            catch (_: Exception) { mem.lowMemory = true }
                            if (am == null) 0 else VideoSearchWorkerBudget.permits(
                                mem.availMem, mem.lowMemory
                            )
                        },
                        authorized = {
                            val active = ProfileManager.getInstance(applicationContext)
                            journal.hasCapacity() && !active.isPrivateMode() &&
                                active.getActiveProfile().id == plan.scope.profileId
                        },
                        submit = { site ->
                            var outcome = VideoSearchQueueStatus.FAILED
                            var snapshot: VideoSearchPaginationSnapshot? = null
                            val current = withContext(Dispatchers.IO) {
                                repository.site(plan.scope, site.id)
                            }
                            val managerNow = ProfileManager.getInstance(applicationContext)
                            if (!journal.hasCapacity()) {
                                outcome = VideoSearchQueueStatus.STOPPED
                            } else if (managerNow.isPrivateMode() ||
                                managerNow.getActiveProfile().id != plan.scope.profileId) {
                                outcome = VideoSearchQueueStatus.STOPPED
                            } else if (current == null || !current.enabled ||
                                current.siteUrl != site.siteUrl ||
                                current.updatedAtMs != site.updatedAtMs ||
                                current.configuration != site.configuration) {
                                outcome = VideoSearchQueueStatus.NEEDS_ATTENTION
                            } else {
                                val tabId = applicationContext.components.tabsUseCases.addTab.invoke(
                                    site.siteUrl, selectTab = false, private = false,
                                    contextId = plan.scope.contextId,
                                )
                                val lease = VideoSearchNormalWorkerLease(tabId, plan.scope)
                                VideoSearchNormalWorkerSelectionHost.registry.register(lease, store.state.selectedTabId)
                                try {
                                    val before = withTimeoutOrNull(10_000L) {
                                        var doc: String? = null
                                        while (doc == null) {
                                            val manager = ProfileManager.getInstance(applicationContext)
                                            if (manager.isPrivateMode() ||
                                                manager.getActiveProfile().id != plan.scope.profileId)
                                                break
                                            doc = workerSiteTab(current, tabId)?.second?.documentId
                                            if (doc == null) delay(160)
                                        }
                                        doc
                                    }
                                    if (before == null) {
                                        outcome = VideoSearchQueueStatus.NEEDS_ATTENTION
                                    } else {
                                        // The pre-submit crawl is read-only and tied to the
                                        // original worker document/selector. If it fails,
                                        // same-document result acceptance fails closed.
                                        val beforePage = current.configuration?.let { config ->
                                            (request(current, "crawl", workerTabId = tabId) { doc, req ->
                                                VideoSearchBridgeProtocol.extractCrawl(doc, req, config, 0)
                                            } as? VideoSearchBridgeMessage.Results)?.page
                                        }?.takeIf { it.status != "stale" }
                                        outcome = submitWorker(current, tabId, plan.query, repository)
                                        if (outcome == VideoSearchQueueStatus.SUBMITTED) {
                                            val confirmed = awaitWorkerResults(
                                                current, tabId, before, beforePage
                                            )
                                            if (!journal.hasCapacity()) {
                                                outcome = VideoSearchQueueStatus.STOPPED
                                            } else if (!confirmed) {
                                                outcome = VideoSearchQueueStatus.NEEDS_ATTENTION
                                            } else {
                                                snapshot = executePagination(
                                                    current, repository, workerTabId = tabId,
                                                    hasAggregateCapacity = journal::hasCapacity,
                                                    onProgress = { partial ->
                                                        withContext(NonCancellable + Dispatchers.IO) {
                                                            val manager = ProfileManager.getInstance(applicationContext)
                                                            if (!manager.isPrivateMode() &&
                                                                manager.getActiveProfile().id == plan.scope.profileId &&
                                                                workerOwnedTab(current, tabId) != null &&
                                                                repository.site(plan.scope, site.id) == current)
                                                                journal.progress(site, partial)
                                                        }
                                                    },
                                                )
                                                outcome = VideoSearchWorkerVerification
                                                    .finalQueueStatus(snapshot)
                                            }
                                        }
                                    }
                                } finally {
                                    // Adopted once = user tab forever, even after focus moves away.
                                    val live = store.state.tabs.singleOrNull { it.id == tabId }
                                    val active = ProfileManager.getInstance(applicationContext)
                                    val profileActive = !active.isPrivateMode() &&
                                        active.getActiveProfile().id == plan.scope.profileId
                                    val safeOrigin = live?.content?.url?.let {
                                        VideoSearchBridgeProtocol.sameOrigin(it, site.siteUrl)
                                    } == true
                                    // Capture revocation BEFORE removeTab(), which
                                    // deliberately destroys the bound EngineSession.
                                    // Cleanup of an unadopted worker is not STOPPED.
                                    if (!lease.canUse(store.state.selectedTabId) ||
                                        (lease.hasBoundSession &&
                                            !lease.acceptsSession(live?.engineState?.engineSession))) {
                                        outcome = VideoSearchQueueStatus.STOPPED
                                        snapshot = null
                                    }
                                    try {
                                        if (profileActive && lease.mayReclaim(
                                            live?.let {
                                                VideoSearchNormalWorkerTab(
                                                    it.id, it.contextId, it.content.private
                                                )
                                            },
                                            store.state.selectedTabId,
                                            if (live != null && plan.scope.ownsTab(
                                                live.contextId, live.content.private
                                            )) plan.scope else null,
                                            live?.engineState?.engineSession,
                                            safeOrigin,
                                        )) {
                                            applicationContext.components.tabsUseCases.removeTab(tabId)
                                        }
                                    } catch (_: Exception) {
                                        // Ownership uncertain: keep the user's tab.
                                    } finally {
                                        VideoSearchNormalWorkerSelectionHost.registry.release(lease)
                                    }
                                }
                            }
                            withContext(Dispatchers.IO) {
                                journal.record(site, outcome, snapshot)
                            }
                            outcome
                        },
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                queue.stopPending()
            } finally {
                queue.stopPending()
                plan.selectedSites.forEach { site ->
                    if (queue.statusOf(site.id) == VideoSearchQueueStatus.LEASED)
                        queue.resolve(site.id, VideoSearchQueueStatus.STOPPED)
                }
                try {
                    withContext(NonCancellable + Dispatchers.IO) {
                        journal.finish(queue)
                    }
                } catch (_: Exception) {
                    // Do not claim a completed run if the transaction failed.
                }
                // An older generation must never reset the new run's state.
                if (normalRunGate.owns(coroutineContext[Job], queue)) {
                    lastMulti[plan.scope.profileId] = queue.snapshot()
                    if (lastMulti.size > 4) lastMulti.remove(lastMulti.keys.first())
                    Toast.makeText(applicationContext,
                        if (!journal.hasCapacity() && queue.snapshot().stopped > 0)
                            "Zavro: osiągnięto limit 2000 filmów. Pozostałe witryny zatrzymano."
                        else "Zavro: wyszukiwanie zakończone lub zatrzymane. Sprawdź historię wyników.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
        if (!normalRunGate.begin(job, queue)) {
            job.cancel()
            return false
        }
        job.start()
        return true
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

package com.prirai.android.nira.media

import java.util.IdentityHashMap

/** Trusted BrowserStore snapshot. No field here is supplied as a tab/profile ID by a web page. */
internal data class MediaTabState(
    val binding: MediaSessionBinding,
    val pageUrl: String,
    val loading: Boolean,
)

/** Returned only inside the native process. String representations never expose signed URLs. */
internal class MediaEntry(
    val url: String,
    val format: MediaFormat,
    val sources: Set<MediaSource>,
) {
    override fun toString(): String = "MediaEntry(format=$format, sources=$sources, url=<redacted>)"
}

/**
 * Process-only, synchronized repository scoped to the real EngineSession, tab,
 * profile, privacy mode, document and top-level navigation.
 *
 * GeckoStore's content.url and loading transitions invalidate prior navigation
 * results. The extension's per-document sequence is a *correlation guard*, not
 * a source of native tab identity. It must be verified against BrowserStore.
 * No URL is ever serialized or logged.
 */
internal class MediaRepository(
    private val perTabLimit: Int = 64,
    private val globalLimit: Int = 384,
    private val scopeLimit: Int = 128,
) {
    init {
        require(perTabLimit > 0 && globalLimit > 0 && scopeLimit > 0)
    }

    internal data class Change(
        val binding: MediaSessionBinding,
        val removed: Int,
        val navigation: Boolean,
    )
    internal data class DocumentResult(val accepted: Boolean, val removed: Int = 0)
    internal enum class InsertResult { ADDED, DUPLICATE, STALE }

    internal data class TabCounts(
        val tabId: String, val count: Int, val dom: Int, val network: Int,
        val hls: Int, val dash: Int, val last: MediaFormat?,
    )

    private class Record(val url: String, var format: MediaFormat, val serial: Long) {
        var dom = false
        var network = false
        fun copy() = MediaEntry(url, format, buildSet {
            if (dom) add(MediaSource.DOM)
            if (network) add(MediaSource.NETWORK)
        })
    }

    private class Scope(
        var binding: MediaSessionBinding, var pageUrl: String, var loading: Boolean,
        var documentKey: Long = 0, var access: Long = 0,
        val entries: LinkedHashMap<String, Record> = LinkedHashMap(),
    )
    private val scopes = IdentityHashMap<Any, Scope>()
    private var serial = 0L
    private fun tick(): Long = ++serial
    private fun same(a: MediaSessionBinding, b: MediaSessionBinding) =
        a.session === b.session && a.tabId == b.tabId &&
            a.contextId == b.contextId && a.isPrivate == b.isPrivate

    /**
     * Called for all Store changes, including removal/replacement of sessions.
     * Do NOT key this invalidation to selectedTabId: tab focus is not navigation.
     */
    @Synchronized fun observe(tabs: List<MediaTabState>): List<Change> {
        val live = IdentityHashMap<Any, MediaTabState>()
        tabs.forEach { live[it.binding.session] = it }
        val changes = ArrayList<Change>()
        val iterator = scopes.entries.iterator()
        while (iterator.hasNext()) {
            val (_, scope) = iterator.next()
            val actual = live[scope.binding.session]
            if (actual == null || !same(scope.binding, actual.binding)) {
                changes.add(Change(scope.binding, scope.entries.size, false))
                iterator.remove()
                continue
            }
            val navigated = actual.pageUrl != scope.pageUrl || (!scope.loading && actual.loading)
            scope.loading = actual.loading
            if (navigated) {
                changes.add(Change(scope.binding, scope.entries.size, true))
                scope.entries.clear()
                scope.pageUrl = actual.pageUrl
                // Keep documentKey as a high-water mark. A late event from the
                // previous document must not register again after a reload.
            }
        }
        return changes
    }

    private fun scope(tab: MediaTabState, create: Boolean): Scope? {
        var current = scopes[tab.binding.session]
        if (current != null && (!same(current.binding, tab.binding) ||
                    current.pageUrl != tab.pageUrl || current.loading != tab.loading)) return null
        if (current == null && create) {
            if (scopes.size >= scopeLimit) {
                val oldest = scopes.entries.minByOrNull { it.value.access }?.key
                if (oldest != null) scopes.remove(oldest)
            }
            current = Scope(tab.binding, tab.pageUrl, tab.loading, access = tick())
            scopes[tab.binding.session] = current
        }
        return current
    }

    /** Document keys are minted by the extension background, never by the website. */
    @Synchronized fun registerDocument(tab: MediaTabState, pageUrl: String, documentKey: Long): DocumentResult {
        if (documentKey !in 1..9007199254740991L || pageUrl != tab.pageUrl ||
            !pageUrl.startsWith("https://") && !pageUrl.startsWith("http://")) {
            return DocumentResult(false)
        }
        val current = scope(tab, create = true) ?: return DocumentResult(false)
        if (documentKey < current.documentKey) return DocumentResult(false)
        if (documentKey == current.documentKey) return DocumentResult(true)
        val removed = current.entries.size
        current.entries.clear()
        current.documentKey = documentKey
        current.access = tick()
        return DocumentResult(true, removed)
    }

    @Synchronized fun add(
        tab: MediaTabState, pageUrl: String, documentKey: Long,
        media: DetectedMedia, source: MediaSource,
    ): InsertResult {
        val current = scope(tab, create = false) ?: return InsertResult.STALE
        if (current.documentKey == 0L || current.documentKey != documentKey ||
            current.pageUrl != pageUrl || media.tabId != tab.binding.tabId ||
            media.contextId != tab.binding.contextId || media.isPrivate != tab.binding.isPrivate) {
            return InsertResult.STALE
        }
        current.access = tick()
        val found = current.entries[media.url]
        if (found != null) {
            if (source == MediaSource.NETWORK) found.format = media.format
            if (source == MediaSource.DOM) found.dom = true else found.network = true
            return InsertResult.DUPLICATE
        }
        val added = Record(media.url, media.format, tick())
        if (source == MediaSource.DOM) added.dom = true else added.network = true
        current.entries[media.url] = added
        while (current.entries.size > perTabLimit) current.entries.remove(current.entries.keys.first())
        // Deterministic global FIFO eviction, bounded even under unique URL floods.
        while (scopes.values.sumOf { it.entries.size } > globalLimit) {
            val victim = scopes.values.flatMap { s -> s.entries.values.map { e -> s to e } }
                .minByOrNull { it.second.serial } ?: break
            victim.first.entries.remove(victim.second.url)
        }
        return InsertResult.ADDED
    }

    @Synchronized fun forTab(binding: MediaSessionBinding): List<MediaEntry> =
        scopes[binding.session]?.takeIf { same(it.binding, binding) }
            ?.entries?.values?.map { it.copy() } ?: emptyList()

    /** Sanitized aggregates used by diagnostics; no URLs escape via this API. */
    @Synchronized fun counts(contextId: String?, privateMode: Boolean): List<TabCounts> =
        scopes.values.filter { it.binding.contextId == contextId && it.binding.isPrivate == privateMode }
            .map { s ->
                val entries = s.entries.values.toList()
                TabCounts(s.binding.tabId, entries.size,
                    entries.count { it.dom }, entries.count { it.network },
                    entries.count { it.format == MediaFormat.HLS },
                    entries.count { it.format == MediaFormat.DASH },
                    entries.lastOrNull()?.format)
            }

    @Synchronized fun currentSize(): Int = scopes.values.sumOf { it.entries.size }
    @Synchronized fun activeScopes(): Int = scopes.size
}

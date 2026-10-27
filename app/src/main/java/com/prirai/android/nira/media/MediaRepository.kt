package com.prirai.android.nira.media

import java.util.IdentityHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Trusted BrowserStore snapshot. No field here is supplied as a tab/profile ID by a web page. */
internal data class MediaTabState(
    val binding: MediaSessionBinding,
    val pageUrl: String,
    val loading: Boolean,
) {
    override fun toString(): String = "MediaTabState(pageUrl=<redacted>)"
}

/** Returned only inside the native process. String representations never expose signed URLs. */
internal class MediaEntry(
    val id: Long,
    val url: String,
    val format: MediaFormat,
    val sources: Set<MediaSource>,
    val groupId: String? = null,
    val title: String? = null,
    val titleKind: String? = null,
    val durationSeconds: Int? = null,
    val sizeBytes: Long? = null,
    val posterUrl: String? = null,
    val quality: String? = null,
    val bitrate: Long? = null,
    val codecs: String? = null,
    val revisionCount: Int = 1,
    val frameUrl: String? = null,
    val playbackStatus: MediaPlaybackStatus = MediaPlaybackStatus.NEEDS_VERIFICATION,
    val httpStatus: Int? = null,
    val frameAmbiguous: Boolean = false,
    val frameKey: Long? = null,
    val sizeEstimated: Boolean = false,
    val durationOrigin: String? = null,
    val sizeOrigin: String? = null,
    val isLive: Boolean = false,
    val titleAmbiguous: Boolean = false,
) {
    override fun toString(): String = "MediaEntry(format=$format, sources=$sources, url=<redacted>)"
}

internal enum class MediaPlaybackStatus {
    CONFIRMED, NEEDS_VERIFICATION, EXPIRED, AUTH_ERROR, UNPLAYABLE
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

    private class Record(val key: String, var url: String, var format: MediaFormat,
                         val serial: Long, var groupId: String? = null) {
        var activeProvenance = MediaProvenance.DOM_HINT
        val revisions = ArrayDeque<MediaUrlRevision>()
        fun observeUrl(exactUrl: String, provenance: MediaProvenance, sequence: Long) {
            if (revisions.none { it.exactUrl == exactUrl }) {
                revisions.addLast(MediaUrlRevision(exactUrl, sequence, provenance))
                while (revisions.size > 8) revisions.removeFirst()
            }
            // Parser fetches must never displace a real player network request.
            if (exactUrl == url) {
                if (provenance == MediaProvenance.NETWORK_OBSERVED ||
                    activeProvenance != MediaProvenance.NETWORK_OBSERVED) activeProvenance = provenance
            } else if (provenance == MediaProvenance.NETWORK_OBSERVED ||
                (activeProvenance != MediaProvenance.NETWORK_OBSERVED &&
                    provenance == MediaProvenance.MANIFEST_DERIVED)) {
                url = exactUrl
                activeProvenance = provenance
            }
        }
        var dom = false
        var page = false
        var network = false
        private val titleResolver = MediaTitleResolver.State()
        var title: String? = null
        var titleKind: String? = null
        var titleAmbiguous = false
        var durationSeconds: Int? = null
        var sizeBytes: Long? = null
        var sizeEstimated = false
        var durationOrigin: String? = null
        var sizeOrigin: String? = null
        var isLive = false
        var posterUrl: String? = null
        var quality: String? = null
        var bitrate: Long? = null
        var codecs: String? = null
        var frameUrl: String? = null
        var frameKey: Long? = null
        var playbackStatus = MediaPlaybackStatus.NEEDS_VERIFICATION
        var httpStatus: Int? = null
        var highestNetworkRequestSequence: Long = 0
        fun metadata(candidate: MediaDetectorMessage.Candidate?, format: MediaFormat) {
            if (candidate == null) return
            titleResolver.offer(candidate.title, candidate.titleKind)
            val titleDecision = titleResolver.snapshot()
            title = titleDecision.text
            titleKind = titleDecision.kind
            titleAmbiguous = titleDecision.ambiguous
            if (candidate.posterUrl != null && candidate.source != MediaSource.NETWORK)
                posterUrl = candidate.posterUrl
            if (candidate.isLive == true) {
                isLive = true
                durationSeconds = null
                durationOrigin = "LIVE"
                // A live playlist cannot inherit total VOD bytes from an older update.
                sizeBytes = null; sizeEstimated = false; sizeOrigin = null
            } else if (candidate.isLive == false) {
                isLive = false
            }
            if (!isLive && candidate.durationSeconds != null) {
                durationSeconds = candidate.durationSeconds
                durationOrigin = if (candidate.source == MediaSource.NETWORK &&
                    candidate.verification == "manifest_verified") {
                    if (format == MediaFormat.HLS) "HLS_EXTINF" else "DASH_MPD"
                } else "HTML5"
            }
            if (candidate.source == MediaSource.NETWORK &&
                (format !in setOf(MediaFormat.HLS, MediaFormat.DASH) ||
                  format == MediaFormat.HLS && candidate.verification == "manifest_verified") &&
                candidate.sizeBytes != null) {
                sizeBytes = candidate.sizeBytes
                sizeEstimated = false
                sizeOrigin = if (format == MediaFormat.HLS) "HLS_BYTERANGE" else "HTTP_LENGTH"
            }
            if (candidate.quality != null) quality = candidate.quality
            if (candidate.bitrate != null) bitrate = candidate.bitrate
            if (!isLive && (sizeBytes == null || sizeEstimated) &&
                format in setOf(MediaFormat.HLS, MediaFormat.DASH) &&
                durationSeconds != null && bitrate != null) {
                val estimate = bitrate!! * durationSeconds!!.toLong() / 8L
                if (estimate in 1L..1_000_000_000_000L) {
                    sizeBytes = estimate
                    sizeEstimated = true
                    sizeOrigin = "MANIFEST_BITRATE"
                }
            }
            if (candidate.codecs != null) codecs = candidate.codecs
            if (candidate.frameUrl != null) frameUrl = candidate.frameUrl
            if (candidate.httpStatus != null) httpStatus = candidate.httpStatus
            playbackStatus = when (candidate.verification) {
                "stream_verified" -> MediaPlaybackStatus.CONFIRMED
                "manifest_observed", "manifest_verified" -> MediaPlaybackStatus.NEEDS_VERIFICATION
                "expired" -> MediaPlaybackStatus.EXPIRED
                "auth_error" -> MediaPlaybackStatus.AUTH_ERROR
                "unplayable" -> MediaPlaybackStatus.UNPLAYABLE
                else -> playbackStatus
            }
        }
        fun mark(source: MediaSource) {
            when (source) {
                MediaSource.DOM -> dom = true
                MediaSource.PAGE -> page = true
                MediaSource.NETWORK -> network = true
            }
        }
        fun copy(ambiguous: Boolean = false) = MediaEntry(serial, url, format, buildSet {
            if (dom) add(MediaSource.DOM)
            if (page) add(MediaSource.PAGE)
            if (network) add(MediaSource.NETWORK)
        }, groupId, title, titleKind, durationSeconds, sizeBytes, posterUrl,
            quality, bitrate, codecs, revisions.size.coerceAtLeast(1), frameUrl,
            playbackStatus, httpStatus, ambiguous, frameKey,
            sizeEstimated, durationOrigin, sizeOrigin, isLive, titleAmbiguous)
    }

    private class FrameBinding(val id: Long, val key: Long, val url: String, var active: Boolean)
    private class Scope(
        var binding: MediaSessionBinding, var pageUrl: String, var loading: Boolean,
        var documentKey: Long = 0, var registered: Boolean = false, var access: Long = 0,
        val entries: LinkedHashMap<String, Record> = LinkedHashMap(),
    ) {
        var frameTracking = false
        var frameFloor = 0L
        val frames = linkedMapOf<Long, FrameBinding>()
    }
    private val scopes = IdentityHashMap<Any, Scope>()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    private fun changed() { mutableRevision.value = mutableRevision.value + 1 }
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
            if (actual == null) {
                changes.add(Change(scope.binding, scope.entries.size, false))
                iterator.remove()
                continue
            }
            if (!same(scope.binding, actual.binding)) {
                // Profile/partition changes on the same EngineSession must not
                // reset the document high-water mark. An old extension message
                // can never re-register itself into the new partition.
                changes.add(Change(scope.binding, scope.entries.size, false))
                scope.entries.clear(); scope.frames.clear()
                scope.binding = actual.binding
                scope.pageUrl = actual.pageUrl
                scope.loading = actual.loading
                scope.registered = false
                continue
            }
            // A fragment is not a new document/material. Keep the document high-water
            // mark and media, but require subsequent messages to use the current URL.
            val fragmentOnly = actual.pageUrl != scope.pageUrl &&
                actual.pageUrl.substringBefore('#') == scope.pageUrl.substringBefore('#') &&
                !(!scope.loading && actual.loading)
            if (fragmentOnly) {
                scope.entries.values.filter { it.frameKey == null && it.frameUrl == scope.pageUrl }
                    .forEach { it.frameUrl = actual.pageUrl }
                scope.pageUrl = actual.pageUrl
                scope.loading = actual.loading
                changed()
                continue
            }
            val navigated = actual.pageUrl != scope.pageUrl || (!scope.loading && actual.loading)
            scope.loading = actual.loading
            if (navigated) {
                changes.add(Change(scope.binding, scope.entries.size, true))
                scope.entries.clear(); scope.frames.clear()
                scope.registered = false
                scope.pageUrl = actual.pageUrl
                // Keep documentKey as a high-water mark. A late event from the
                // previous document must not register again after a reload.
            }
        }
        if (changes.isNotEmpty()) changed()
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
    @Synchronized fun registerDocument(tab: MediaTabState, pageUrl: String, documentKey: Long, frameTracking: Boolean = false): DocumentResult {
        if (documentKey !in 1..9007199254740991L || pageUrl != tab.pageUrl ||
            !pageUrl.startsWith("https://") && !pageUrl.startsWith("http://")) {
            return DocumentResult(false)
        }
        val current = scope(tab, create = true) ?: return DocumentResult(false)
        if (documentKey < current.documentKey) return DocumentResult(false)
        if (documentKey == current.documentKey) {
            current.frameTracking = current.frameTracking || frameTracking
            return DocumentResult(current.registered)
        }
        val removed = current.entries.size
        current.entries.clear()
        current.documentKey = documentKey
        current.frames.clear(); current.frameFloor = 0L; current.frameTracking = frameTracking
        current.registered = true
        current.access = tick()
        changed()
        return DocumentResult(true, removed)
    }

    @Synchronized fun registerFrame(tab: MediaTabState, frame: MediaDetectorMessage.Frame): Boolean {
        val current = scope(tab, create = false) ?: return false
        if (!current.registered || !current.frameTracking || current.documentKey != frame.documentKey ||
            current.pageUrl.substringBefore('#') != frame.pageUrl.substringBefore('#')) return false
        val previous = current.frames[frame.frameId]
        if (frame.frameKey <= current.frameFloor || previous != null && frame.frameKey < previous.key) return false
        if (previous != null && previous.key == frame.frameKey && previous.url != frame.frameUrl) return false
        if (!frame.active) {
            if (previous?.key != frame.frameKey || previous.url != frame.frameUrl) return false
            previous.active = false
            current.entries.entries.removeAll { it.value.frameKey == frame.frameKey }
        } else {
            if (previous != null && previous.key == frame.frameKey && !previous.active) return false
            if (previous != null && previous.key != frame.frameKey)
                current.entries.entries.removeAll { it.value.frameKey == previous.key }
            current.frames[frame.frameId] = FrameBinding(frame.frameId, frame.frameKey, frame.frameUrl, true)
            while (current.frames.size > 256) {
                // Native messages from different frames may arrive out of order.
                // Evict by minted key so no retained binding falls below the floor.
                val oldestId = current.frames.minBy { it.value.key }.key
                val evicted = current.frames.remove(oldestId)!!
                current.frameFloor = maxOf(current.frameFloor, evicted.key)
                current.entries.entries.removeAll { it.value.frameKey == evicted.key }
            }
        }
        changed(); return true
    }
    private fun frameCurrent(scope: Scope, key: Long?, url: String?): Boolean =
        key == null || !scope.frameTracking || scope.frames.values.any { it.key == key && it.url == url && it.active }

    private fun copies(scope: Scope): List<MediaEntry> = scope.entries.values.map { record ->
        val count = scope.frames.values.count { it.active && it.url == record.frameUrl } +
            if (record.frameUrl == scope.pageUrl) 1 else 0
        record.copy(ambiguous = count > 1)
    }

    @Synchronized fun isCurrentDocument(tab: MediaTabState, documentKey: Long): Boolean =
        scope(tab, create = false)?.let { it.registered && it.documentKey == documentKey } == true

    @Synchronized fun add(
        tab: MediaTabState, pageUrl: String, documentKey: Long,
        media: DetectedMedia, source: MediaSource, groupId: String? = null,
        metadata: MediaDetectorMessage.Candidate? = null,
    ): InsertResult {
        val current = scope(tab, create = false) ?: return InsertResult.STALE
        if (!current.registered || current.documentKey == 0L || current.documentKey != documentKey ||
            current.pageUrl != pageUrl || media.tabId != tab.binding.tabId ||
            media.contextId != tab.binding.contextId || media.isPrivate != tab.binding.isPrivate ||
            !frameCurrent(current, metadata?.frameKey, metadata?.frameUrl)) {
            return InsertResult.STALE
        }
        current.access = tick()
        val identity = MediaStreamIdentity.key(media.url, media.format, metadata?.frameKey)
        val provenance = when {
            source == MediaSource.NETWORK && metadata?.provenance == "derived" -> MediaProvenance.MANIFEST_DERIVED
            source == MediaSource.NETWORK -> MediaProvenance.NETWORK_OBSERVED
            else -> MediaProvenance.DOM_HINT
        }
        // Use exact manifest URLs by default. A known SigV4 refresh may
        // extend one existing record only with independently verified network
        // lineage: same frame, parser-minted group, observed requests, and a
        // monotonic request sequence. Group equality alone is NOT sufficient.
        val refreshSequence = metadata?.requestSequence
        val refreshed = if (source == MediaSource.NETWORK &&
            provenance == MediaProvenance.NETWORK_OBSERVED &&
            groupId?.startsWith("manifest-") == true &&
            refreshSequence != null) {
            current.entries.values.firstOrNull { record ->
                record.groupId == groupId && record.format == media.format &&
                    record.frameKey == metadata?.frameKey &&
                    record.activeProvenance == MediaProvenance.NETWORK_OBSERVED &&
                    record.highestNetworkRequestSequence > 0 &&
                    MediaStreamIdentity.sameSignedRefresh(
                        record.url, media.url, media.format, metadata?.frameKey)
            }
        } else null
        val found = current.entries[identity] ?: refreshed
        if (found != null) {
            val requestSequence = metadata?.requestSequence
            // Distinct signatures in the same request sequence are not proof
            // of a newer credential. Leave the last observed URL untouched.
            if (refreshed != null && requestSequence != null &&
                requestSequence <= found.highestNetworkRequestSequence)
                return InsertResult.DUPLICATE
            if (source == MediaSource.NETWORK && requestSequence != null &&
                requestSequence < found.highestNetworkRequestSequence) return InsertResult.DUPLICATE
            if (source == MediaSource.NETWORK && requestSequence != null)
                found.highestNetworkRequestSequence = requestSequence
            if (provenance == MediaProvenance.NETWORK_OBSERVED) found.format = media.format
            found.observeUrl(media.url, provenance, tick())
            found.mark(if (provenance == MediaProvenance.MANIFEST_DERIVED) MediaSource.PAGE else source)
            if (groupId != null && (found.groupId == null ||
                    // The network manifest group may reconcile an anonymous
                    // earlier page hint, but must not move a source that already
                    // owns an individual DOM/player/structured title.
                    source == MediaSource.NETWORK && media.format in
                        setOf(MediaFormat.HLS, MediaFormat.DASH) &&
                        (found.title == null || found.titleKind == "page"))) found.groupId = groupId
            val earlierTitle = found.title
            val earlierKind = found.titleKind
            found.metadata(metadata, found.format)
            if (com.prirai.android.nira.diagnostics.testlab.TestLab.mediaObservationActive()) {
                if (earlierTitle != null && metadata?.title != null &&
                    earlierTitle != metadata.title && found.title == earlierTitle)
                    com.prirai.android.nira.diagnostics.testlab.TestLab.mediaRejectedTitle(
                        tab.binding, earlierKind == metadata.titleKind)
                com.prirai.android.nira.diagnostics.testlab.TestLab.observedMedia(tab.binding, found.copy())
            }
            if (source == MediaSource.NETWORK && metadata?.frameKey == null) found.frameUrl = current.pageUrl
            changed()
            return InsertResult.DUPLICATE
        }
        val added = Record(identity, media.url, media.format, tick(), groupId)
        if (source == MediaSource.NETWORK && metadata?.requestSequence != null)
            added.highestNetworkRequestSequence = metadata.requestSequence
        added.observeUrl(media.url, provenance, tick())
        added.mark(if (provenance == MediaProvenance.MANIFEST_DERIVED) MediaSource.PAGE else source)
        added.metadata(metadata, media.format)
        added.frameKey = metadata?.frameKey
        if (source == MediaSource.NETWORK && metadata?.frameKey == null) added.frameUrl = current.pageUrl
        current.entries[identity] = added
        if (com.prirai.android.nira.diagnostics.testlab.TestLab.mediaObservationActive())
            com.prirai.android.nira.diagnostics.testlab.TestLab.observedMedia(tab.binding, added.copy())
        while (current.entries.size > perTabLimit) current.entries.remove(current.entries.keys.first())
        // Deterministic global FIFO eviction, bounded even under unique URL floods.
        while (scopes.values.sumOf { it.entries.size } > globalLimit) {
            var oldestScope: Scope? = null
            var oldestRecord: Record? = null
            for (candidateScope in scopes.values) {
                for (candidate in candidateScope.entries.values) {
                    if (oldestRecord == null || candidate.serial < oldestRecord.serial) {
                        oldestRecord = candidate
                        oldestScope = candidateScope
                    }
                }
            }
            if (oldestScope == null || oldestRecord == null) break
            oldestScope.entries.remove(oldestRecord.key)
        }
        changed()
        return InsertResult.ADDED
    }

    @Synchronized fun forTab(binding: MediaSessionBinding): List<MediaEntry> =
        scopes[binding.session]?.takeIf { same(it.binding, binding) }
            ?.let(::copies) ?: emptyList()

    /**
     * Strict UI read. BrowserStore's current page/loading state must match
     * the registered document; a focus switch never grants other tab access.
     */
    @Synchronized fun currentEntries(tab: MediaTabState): List<MediaEntry> =
        scopes[tab.binding.session]?.takeIf {
            same(it.binding, tab.binding) && it.pageUrl == tab.pageUrl &&
                // Store may publish loading=false before the detector observes it.
                // A new load (false -> true) must still hide stale results immediately.
                (!tab.loading || it.loading) && it.registered
        }?.let(::copies) ?: emptyList()

    /** Sanitized aggregates used by diagnostics; no URLs escape via this API. */
    @Synchronized fun counts(contextId: String?, privateMode: Boolean): List<TabCounts> =
        scopes.values.filter { it.binding.contextId == contextId && it.binding.isPrivate == privateMode }
            .map { s ->
                val entries = s.entries.values.toList()
                TabCounts(s.binding.tabId, entries.size,
                    entries.count { it.dom || it.page }, entries.count { it.network },
                    entries.count { it.format == MediaFormat.HLS },
                    entries.count { it.format == MediaFormat.DASH },
                    entries.lastOrNull()?.format)
            }

    @Synchronized fun currentSize(): Int = scopes.values.sumOf { it.entries.size }
    @Synchronized fun activeScopes(): Int = scopes.size
}

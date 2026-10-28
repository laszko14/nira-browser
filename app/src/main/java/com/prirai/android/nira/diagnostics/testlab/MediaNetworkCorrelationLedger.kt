package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaSource
import java.util.IdentityHashMap
import kotlinx.serialization.Serializable

/** Only bounded anonymous counters are exported; source URLs remain in ephemeral RAM. */
@Serializable data class MediaNetworkCorrelationSummary(
    val shadowOnly: Boolean = true,
    val changesMediaOrPlayback: Boolean = false,
    val performedNetworkRequests: Boolean = false,
    val observedRecords: Int,
    val networkAndDomOnOneRecord: Int,
    val trustedFrameCoObserved: Int,
    val coObservedWithIndividualMetadata: Int,
    val networkOnlyRecords: Int,
    val domOrPageOnlyRecords: Int,
    val networkWithoutTrustedFrame: Int,
    val exactUrlCrossFramePairs: Int,
    val exactUrlUnknownFramePairs: Int,
    val sameGroupDifferentUrlPairs: Int,
    val exactUrlDifferentFormatPairs: Int,
    val navigationEpochs: Int,
    val partitionResets: Int,
    val droppedRecords: Int,
)

/**
 * Stages 6+ audit only. The repo already co-observes network + DOM/Page hints
 * within an accepted native record. This collector measures that actual
 * co-observation and *unresolved* risks, but never transfers a title/poster,
 * merges streams or strips signed URL parameters.
 *
 * One source is keyed by the native EngineSession identity, profile partition,
 * navigation epoch and native entry id; no page supplied tab ID is trusted as
 * an independent identity. All comparisons stay inside the same scope/epoch.
 */
internal class MediaNetworkCorrelationLedger(private val capacity: Int = 256) {
    init { require(capacity in 1..1024) }
    private class Scope(
        val serial: Int, var tab: String, var context: String?,
        var privateMode: Boolean, var epoch: Int = 0,
    )
    private data class Source(
        val owner: Int, val epoch: Int, val entryId: Long,
        var url: String, var format: String, var frame: Long?,
        var group: String?, var domOrPage: Boolean, var network: Boolean,
        var titled: Boolean, var poster: Boolean,
    )
    private val scopes = IdentityHashMap<Any, Scope>()
    private val sources = LinkedHashMap<String, Source>()
    private var nextScope = 0
    private var navigationEpochs = 0
    private var partitionResets = 0
    private var dropped = 0

    private fun owner(binding: MediaSessionBinding): Scope? {
        val old = scopes[binding.session]
        if (old != null) {
            if (old.tab != binding.tabId || old.context != binding.contextId ||
                old.privateMode != binding.isPrivate) {
                old.epoch++
                old.tab = binding.tabId
                old.context = binding.contextId
                old.privateMode = binding.isPrivate
                partitionResets++
            }
            return old
        }
        if (scopes.size >= capacity) { dropped++; return null }
        return Scope(++nextScope, binding.tabId, binding.contextId, binding.isPrivate).also {
            scopes[binding.session] = it
        }
    }

    @Synchronized fun navigation(binding: MediaSessionBinding) {
        owner(binding)?.let { it.epoch++; navigationEpochs++ }
    }

    @Synchronized fun observe(binding: MediaSessionBinding, entry: MediaEntry) {
        val scope = owner(binding) ?: return
        val key = "${scope.serial}:${scope.epoch}:${entry.id}"
        val previous = sources[key]
        val dom = entry.sources.any { it == MediaSource.DOM || it == MediaSource.PAGE }
        val network = MediaSource.NETWORK in entry.sources
        val individual = !entry.title.isNullOrBlank() &&
            entry.titleKind != "page" && !entry.titleAmbiguous
        val poster = !entry.posterUrl.isNullOrBlank() && !entry.posterAmbiguous
        if (previous == null) {
            if (sources.size >= capacity) { dropped++; return }
            sources[key] = Source(scope.serial, scope.epoch, entry.id, entry.url,
                entry.format.name, entry.frameKey, entry.groupId,
                dom, network, individual, poster)
        } else {
            previous.url = entry.url
            previous.format = entry.format.name
            previous.frame = entry.frameKey ?: previous.frame
            previous.group = entry.groupId ?: previous.group
            previous.domOrPage = previous.domOrPage || dom
            previous.network = previous.network || network
            previous.titled = individual
            previous.poster = poster
        }
    }

    /** A pair is a diagnostic collision, not evidence of the same video. */
    @Synchronized fun snapshot(): MediaNetworkCorrelationSummary {
        val records = sources.values.toList()
        var crossFrame = 0
        var unknownFrame = 0
        var sameGroupOtherUrl = 0
        var differentFormat = 0
        val groups = records.groupBy { it.owner to it.epoch }
        for (items in groups.values) {
            for (i in items.indices) for (j in i + 1 until items.size) {
                val a = items[i]; val b = items[j]
                if (a.url == b.url) {
                    if (a.format != b.format) differentFormat++
                    else if (a.frame == null || b.frame == null) unknownFrame++
                    else if (a.frame != b.frame) crossFrame++
                } else if (a.group != null && a.group == b.group &&
                    a.format == b.format && a.frame == b.frame) {
                    sameGroupOtherUrl++
                }
            }
        }
        return MediaNetworkCorrelationSummary(
            observedRecords = records.size,
            networkAndDomOnOneRecord = records.count { it.domOrPage && it.network },
            trustedFrameCoObserved = records.count {
                it.domOrPage && it.network && it.frame != null
            },
            coObservedWithIndividualMetadata = records.count {
                it.domOrPage && it.network && (it.titled || it.poster)
            },
            networkOnlyRecords = records.count { it.network && !it.domOrPage },
            domOrPageOnlyRecords = records.count { it.domOrPage && !it.network },
            networkWithoutTrustedFrame = records.count { it.network && it.frame == null },
            exactUrlCrossFramePairs = crossFrame,
            exactUrlUnknownFramePairs = unknownFrame,
            sameGroupDifferentUrlPairs = sameGroupOtherUrl,
            exactUrlDifferentFormatPairs = differentFormat,
            navigationEpochs = navigationEpochs,
            partitionResets = partitionResets,
            droppedRecords = dropped,
        )
    }

    @Synchronized fun clear() { scopes.clear(); sources.clear() }
}

package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaPlaybackStatus
import com.prirai.android.nira.media.MediaSessionBinding
import java.util.IdentityHashMap
import kotlinx.serialization.Serializable

@Serializable enum class MediaFunnelStage {
    DISCOVERED, CANDIDATE, ASSOCIATED, VERIFIED, STORED, DISPLAYED
}
@Serializable enum class MediaUnknownReason {
    TITLE_UNKNOWN, TITLE_AMBIGUOUS, POSTER_UNKNOWN, DURATION_UNKNOWN,
    SIZE_UNKNOWN, QUALITY_UNKNOWN, OWNER_AMBIGUOUS
}
@Serializable internal enum class MediaFunnelConflict {
    TITLE, POSTER_ALTERNATIVE, VARIANT_GROUP_MISMATCH, VARIANT_GROUP_DOM_NETWORK, VARIANT_GROUP_OTHER
}

/**
 * Snapshot-only diagnostics. No URL, title, host, cookies or profile ID is retained.
 * Counters describe native accepted observations, NOT end-to-end correctness.
 * Network byte/time budgets are NULL until a separately approved probe instrument exists.
 */
@Serializable data class MediaFunnelSummary(
    val stages: Map<MediaFunnelStage, Long>,
    val unknownReasons: Map<MediaUnknownReason, Long>,
    val titleConflicts: Long,
    val posterAlternatives: Long,
    val variantGroupMismatches: Long,
    val droppedRecords: Long,
    val extendedNetworkProbesEnabled: Boolean = false,
    val probeRequests: Long? = null,
    val probeBytes: Long? = null,
    val probeElapsedMs: Double? = null,
    val variantGroupDomNetwork: Long = 0,
    val variantGroupOther: Long = 0,
)

internal class MediaFunnelLedger(private val capacity: Int = 256) {
    init { require(capacity in 1..1024) }
    private data class Scope(val serial: Int, var epoch: Int = 0)
    private data class Flags(
        var named: Boolean = false, var titleAmbiguous: Boolean = false,
        var poster: Boolean = false, var duration: Boolean = false,
        var size: Boolean = false, var quality: Boolean = false,
        var ownerAmbiguous: Boolean = false, var associated: Boolean = false,
        var verified: Boolean = false, var displayed: Boolean = false,
    )
    private val scopes = IdentityHashMap<Any, Scope>()
    private val records = LinkedHashMap<String, Flags>()
    private var serial = 0
    private var candidates = 0L
    private var dropped = 0L
    private var titleConflicts = 0L
    private var posterAlternatives = 0L
    private var variantMismatches = 0L
    private var variantDomNetwork = 0L
    private var variantOther = 0L
    private fun scope(binding: MediaSessionBinding): Scope? {
        scopes[binding.session]?.let { return it }
        if (scopes.size >= capacity) { dropped++; return null }
        return Scope(++serial).also { scopes[binding.session] = it }
    }
    private fun key(binding: MediaSessionBinding, id: Long): String? =
        scope(binding)?.let { "${it.serial}:${it.epoch}:$id" }

    @Synchronized fun candidate() { if (candidates < Long.MAX_VALUE) candidates++ }
    @Synchronized fun navigation(binding: MediaSessionBinding) { scope(binding)?.let { it.epoch++ } }
    @Synchronized fun conflict(kind: MediaFunnelConflict) {
        when (kind) {
            MediaFunnelConflict.TITLE -> titleConflicts++
            MediaFunnelConflict.POSTER_ALTERNATIVE -> posterAlternatives++
            MediaFunnelConflict.VARIANT_GROUP_MISMATCH -> variantMismatches++
            MediaFunnelConflict.VARIANT_GROUP_DOM_NETWORK -> variantDomNetwork++
            MediaFunnelConflict.VARIANT_GROUP_OTHER -> variantOther++
        }
    }
    @Synchronized fun observe(binding: MediaSessionBinding, entry: MediaEntry, verifiedEvidence: Boolean = false) {
        val key = key(binding, entry.id) ?: return
        if (!records.containsKey(key) && records.size >= capacity) { dropped++; return }
        val flags = records.getOrPut(key) { Flags() }
        flags.named = !entry.title.isNullOrBlank() && entry.titleKind != "page" && !entry.titleAmbiguous
        flags.titleAmbiguous = entry.titleAmbiguous
        flags.poster = !entry.posterUrl.isNullOrBlank()
        flags.duration = entry.durationSeconds != null
        flags.size = entry.sizeBytes != null
        flags.quality = entry.height != null || !entry.quality.isNullOrBlank()
        flags.ownerAmbiguous = entry.frameAmbiguous
        // "Associated" means a specific hint attached to a native record,
        // not that the movie's title/poster has been independently confirmed.
        flags.associated = flags.named || flags.poster
        flags.verified = flags.verified || verifiedEvidence || entry.playbackStatus == MediaPlaybackStatus.CONFIRMED
    }
    @Synchronized fun displayed(binding: MediaSessionBinding, entry: MediaEntry) {
        val key = key(binding, entry.id) ?: return
        records[key]?.displayed = true // panel snapshot, NOT a drawn pixel
    }
    @Synchronized fun snapshot(): MediaFunnelSummary {
        val flags = records.values.toList()
        val unknown = MediaUnknownReason.entries.associateWith { reason ->
            flags.count { item ->
                when (reason) {
                    MediaUnknownReason.TITLE_UNKNOWN -> !item.named
                    MediaUnknownReason.TITLE_AMBIGUOUS -> item.titleAmbiguous
                    MediaUnknownReason.POSTER_UNKNOWN -> !item.poster
                    MediaUnknownReason.DURATION_UNKNOWN -> !item.duration
                    MediaUnknownReason.SIZE_UNKNOWN -> !item.size
                    MediaUnknownReason.QUALITY_UNKNOWN -> !item.quality
                    MediaUnknownReason.OWNER_AMBIGUOUS -> item.ownerAmbiguous
                }
            }.toLong()
        }
        val stage = MediaFunnelStage.entries.associateWith { step ->
            when (step) {
                MediaFunnelStage.DISCOVERED, MediaFunnelStage.STORED -> flags.size.toLong()
                MediaFunnelStage.CANDIDATE -> candidates
                MediaFunnelStage.ASSOCIATED -> flags.count { it.associated }.toLong()
                MediaFunnelStage.VERIFIED -> flags.count { it.verified }.toLong()
                MediaFunnelStage.DISPLAYED -> flags.count { it.displayed }.toLong()
            }
        }
        return MediaFunnelSummary(stage, unknown, titleConflicts, posterAlternatives,
            variantMismatches, dropped, variantGroupDomNetwork = variantDomNetwork,
            variantGroupOther = variantOther)
    }
    @Synchronized fun clear() { scopes.clear(); records.clear() }
}

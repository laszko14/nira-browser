package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaFormat
import com.prirai.android.nira.media.MediaSessionBinding
import java.util.IdentityHashMap

/** Bounded, ephemeral, per-EngineSession metadata evidence. No titles or URLs retained. */
internal class VideoMetadataLedger(private val capacity: Int = 256) {
    init { require(capacity in 1..1024) }
    private data class Scope(val serial: Int, var epoch: Int = 0)
    private data class Track(
        val ordinal: Int, val scope: Int, val epoch: Int, val id: Long,
        var format: String, var durationStatus: String, var durationSource: String,
        var sizeStatus: String, var sizeSource: String, var durationSeconds: Int?,
        var sizeBytes: Long?, var visible: Boolean = false, var updates: Int = 0,
        var titleKnown: Boolean = false, var titleConflict: Boolean = false,
        var posterKnown: Boolean = false, var posterOrigin: String? = null,
        var posterConflict: Boolean = false, var resolutionKnown: Boolean = false,
        var qualityKnown: Boolean = false, var qualityDerived: Boolean = false,
        var fpsKnown: Boolean = false, var codecsKnown: Boolean = false,
        var bitrateKnown: Boolean = false, var variantsKnown: Boolean = false,
        var availableQualitiesKnown: Boolean = false,
    )
    private val scopes = IdentityHashMap<Any, Scope>()
    private val tracks = LinkedHashMap<String, Track>()
    private var nextScope = 0
    private var nextOrdinal = 0
    private var dropped = 0
    private var navigations = 0

    private fun key(binding: MediaSessionBinding, entry: MediaEntry): Pair<String, Scope>? {
        val owner = scopes[binding.session] ?: run {
            if (scopes.size >= capacity) { dropped++; return null }
            Scope(++nextScope).also { scopes[binding.session] = it }
        }
        return "${owner.serial}:${owner.epoch}:${entry.id}" to owner
    }

    @Synchronized fun navigation(binding: MediaSessionBinding) {
        val owner = scopes[binding.session] ?: run {
            if (scopes.size >= capacity) { dropped++; return }
            Scope(++nextScope).also { scopes[binding.session] = it }
        }
        owner.epoch++
        navigations++
    }

    @Synchronized fun observe(binding: MediaSessionBinding, entry: MediaEntry) {
        val (identity, scope) = key(binding, entry) ?: return
        val durationStatus = when {
            entry.isLive -> "LIVE"
            entry.durationSeconds != null -> "EXACT"
            else -> "UNKNOWN"
        }
        val sizeStatus = when {
            entry.isLive -> "LIVE"
            entry.sizeBytes == null -> "UNKNOWN"
            entry.sizeEstimated -> "ESTIMATED"
            else -> "EXACT"
        }
        val durationSource = when {
            entry.isLive -> "LIVE_MANIFEST"
            entry.durationSeconds == null -> "NOT_EXPOSED"
            entry.durationOrigin == "HLS_EXTINF" -> "HLS_EXTINF"
            entry.durationOrigin == "DASH_MPD" -> "DASH_MPD"
            else -> "HTML5"
        }
        val sizeSource = when {
            entry.isLive -> "LIVE"
            entry.sizeBytes == null && entry.durationSeconds == null -> "NO_DURATION_OR_LENGTH"
            entry.sizeBytes == null && entry.format in setOf(MediaFormat.HLS, MediaFormat.DASH) -> "NO_VARIANT_BITRATE"
            entry.sizeBytes == null -> "NO_HTTP_LENGTH"
            entry.sizeEstimated -> "MANIFEST_BITRATE"
            entry.sizeOrigin == "HLS_BYTERANGE" -> "HLS_BYTERANGE"
            else -> "HTTP_LENGTH"
        }
        val prior = tracks[identity]
        if (prior == null) {
            if (tracks.size >= capacity) { dropped++; return }
            tracks[identity] = Track(++nextOrdinal, scope.serial, scope.epoch, entry.id,
                entry.format.name, durationStatus, durationSource, sizeStatus, sizeSource,
                entry.durationSeconds, entry.sizeBytes)
        } else {
            if (prior.durationStatus != durationStatus || prior.sizeStatus != sizeStatus ||
                prior.durationSeconds != entry.durationSeconds || prior.sizeBytes != entry.sizeBytes)
                prior.updates++
            prior.format = entry.format.name
            prior.durationStatus = durationStatus
            prior.durationSource = durationSource
            prior.sizeStatus = sizeStatus
            prior.sizeSource = sizeSource
            prior.durationSeconds = entry.durationSeconds
            prior.sizeBytes = entry.sizeBytes
        }
        // Count only anonymous evidence; never retain poster, title or stream URLs.
        // Poster presence is not proof that the remote image was rendered.
        tracks[identity]?.apply {
            titleKnown = !entry.title.isNullOrBlank() && entry.titleKind != "page" &&
                !entry.titleAmbiguous
            titleConflict = entry.titleAmbiguous
            posterKnown = !entry.posterUrl.isNullOrBlank()
            posterOrigin = entry.posterOrigin
            posterConflict = entry.posterAmbiguous
            resolutionKnown = entry.width != null && entry.height != null
            qualityKnown = !entry.quality.isNullOrBlank() || entry.height != null
            qualityDerived = entry.quality.isNullOrBlank() && entry.height != null
            fpsKnown = entry.frameRate != null
            codecsKnown = !entry.codecs.isNullOrBlank()
            bitrateKnown = entry.bitrate != null
            variantsKnown = (entry.variantCount ?: 0) > 1
            availableQualitiesKnown = !entry.availableQualities.isNullOrBlank()
        }
    }

    /** Only a real open panel confirms presentation; it does not prove a pixel was drawn. */
    @Synchronized fun displayed(binding: MediaSessionBinding, entry: MediaEntry) {
        val (identity, _) = key(binding, entry) ?: return
        tracks[identity]?.visible = true
    }

    @Synchronized fun snapshot(): VideoMetadataSummary {
        val items = tracks.values.toList()
        return VideoMetadataSummary(
            observedSources = items.size,
            durationKnown = items.count { it.durationStatus == "EXACT" },
            sizeExact = items.count { it.sizeStatus == "EXACT" },
            sizeEstimated = items.count { it.sizeStatus == "ESTIMATED" },
            live = items.count { it.durationStatus == "LIVE" },
            durationUnknown = items.count { it.durationStatus == "UNKNOWN" },
            sizeUnknown = items.count { it.sizeStatus == "UNKNOWN" },
            uiDisplayed = items.count { it.visible },
            updates = items.sumOf { it.updates },
            navigations = navigations, droppedSources = dropped,
            titlesAssociated = items.count { it.titleKnown },
            titlesFallback = items.count { !it.titleKnown },
            titleConflicts = items.count { it.titleConflict },
            postersAssociated = items.count { it.posterKnown },
            posterOrigins = items.filter { it.posterKnown }
                .groupingBy { it.posterOrigin ?: "UNKNOWN" }.eachCount(),
            posterConflicts = items.count { it.posterConflict },
            resolutionKnown = items.count { it.resolutionKnown },
            qualityKnown = items.count { it.qualityKnown },
            qualityDerived = items.count { it.qualityDerived },
            fpsKnown = items.count { it.fpsKnown },
            codecsKnown = items.count { it.codecsKnown },
            bitrateKnown = items.count { it.bitrateKnown },
            variantsKnown = items.count { it.variantsKnown },
            availableQualitiesKnown = items.count { it.availableQualitiesKnown },
            examples = items.take(32).map {
                VideoMetadataFilm(it.ordinal, it.format, it.durationStatus, it.durationSource,
                    it.sizeStatus, it.sizeSource, it.durationSeconds, it.sizeBytes, it.visible, it.updates)
            },
        )
    }

    @Synchronized fun clear() { scopes.clear(); tracks.clear() }
}

package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaSessionBinding
import java.util.IdentityHashMap

/**
 * Session-local, bounded correlation. Titles and group IDs stay only in RAM and
 * never enter the serialized report. Native EngineSession identity isolates
 * profiles; document epochs isolate navigations.
 */
internal class VideoDiscoveryLedger(private val capacity: Int = 256) {
    init { require(capacity in 1..1024) }

    private data class Scope(val serial: Int, var epoch: Int = 0)
    private data class Track(
        val number: Int, val scope: Int, val epoch: Int,
        val id: Long, val frame: Long?, var group: String?,
        var title: String?, var kind: String?,
        var format: String, val sources: MutableSet<String>,
    )
    private val scopes = IdentityHashMap<Any, Scope>()
    private val tracks = LinkedHashMap<String, Track>()
    private var nextScope = 0
    private var nextNumber = 0
    private var dropped = 0
    private var firstTitles = 0
    private var improved = 0
    private var conflicts = 0
    private var weakerRejected = 0
    private var navigations = 0

    private fun scope(binding: MediaSessionBinding) =
        scopes[binding.session] ?: Scope(++nextScope).also { scopes[binding.session] = it }

    @Synchronized fun navigation(binding: MediaSessionBinding) {
        scope(binding).epoch++
        navigations++
    }

    private fun priority(kind: String?): Int = when (kind) {
        "player" -> 5
        "structured" -> 4
        "video" -> 3
        "link" -> 2
        "page" -> 1
        else -> 0
    }

    @Synchronized fun observe(binding: MediaSessionBinding, entry: MediaEntry) {
        val owner = scope(binding)
        val key = "${owner.serial}:${owner.epoch}:${entry.id}"
        val previous = tracks[key]
        if (previous == null) {
            if (tracks.size >= capacity) { dropped++; return }
            val record = Track(++nextNumber, owner.serial, owner.epoch, entry.id,
                entry.frameKey, entry.groupId, entry.title, entry.titleKind,
                entry.format.name, entry.sources.mapTo(mutableSetOf()) { it.name })
            tracks[key] = record
            if (entry.title != null) firstTitles++
            return
        }
        previous.sources += entry.sources.map { it.name }
        previous.format = entry.format.name
        if (entry.groupId != null) previous.group = entry.groupId
        if (entry.title != previous.title && entry.title != null) {
            if (previous.title == null) firstTitles++
            else if (priority(entry.titleKind) > priority(previous.kind)) improved++
            else conflicts++
            previous.title = entry.title
            previous.kind = entry.titleKind
        }
    }

    /** Count rejected alternative metadata without retaining its text. */
    @Synchronized fun rejectedTitle(binding: MediaSessionBinding, different: Boolean) {
        scope(binding) // Explicitly keep the rejection associated with a native session.
        if (different) conflicts++ else weakerRejected++
    }

    @Synchronized fun snapshot(): VideoDiscoverySummary {
        val groups = tracks.values.groupBy {
            val origin = "${it.scope}/${it.epoch}/${it.frame ?: 0}"
            origin + "/" + (it.group?.let { group -> "group:$group" } ?: "source:${it.id}")
        }
        val samples = ArrayList<VideoDiscoveryFilm>()
        val kinds = linkedMapOf<String, Int>()
        val formats = linkedMapOf<String, Int>()
        var multivariant = 0
        var unknown = 0
        var ambiguous = 0
        var groupConflicts = 0
        var groupNumber = 0
        for (members in groups.values) {
            groupNumber++
            if (members.size > 1) multivariant++
            if (members.any { it.group == null }) ambiguous++
            val titled = members.filter { !it.title.isNullOrBlank() }
            val strongest = titled.maxOfOrNull { priority(it.kind) } ?: 0
            val winners = titled.filter { priority(it.kind) == strongest }
            val conflict = winners.map { it.title }.distinct().size > 1
            if (conflict) groupConflicts++
            if (strongest == 0) unknown++
            val kind = if (conflict) "CONFLICT" else
                winners.firstOrNull()?.kind?.uppercase() ?: "UNKNOWN"
            kinds[kind] = (kinds[kind] ?: 0) + 1
            members.forEach { formats[it.format] = (formats[it.format] ?: 0) + 1 }
            if (samples.size < 32) {
                samples += VideoDiscoveryFilm(groupNumber, members.size, kind,
                    if (conflict) "CONFLICT" else if (strongest == 0) "UNKNOWN" else "OBSERVED",
                    members.any { it.group == null })
            }
        }
        return VideoDiscoverySummary(
            sources = tracks.size, groups = groups.size,
            groupsWithVariants = multivariant, uncertainGroups = ambiguous,
            groupsWithoutTitle = unknown, groupTitleConflicts = groupConflicts,
            initialTitles = firstTitles, improvements = improved,
            rejectedOrConflictingTitles = conflicts, weakerRejected = weakerRejected,
            navigations = navigations, droppedSources = dropped,
            titleKinds = kinds, formats = formats, examples = samples,
        )
    }

    @Synchronized fun clear() {
        scopes.clear()
        tracks.clear()
    }
}

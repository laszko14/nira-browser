package com.prirai.android.nira.diagnostics.testlab

import com.prirai.android.nira.media.MediaEntry
import com.prirai.android.nira.media.MediaSessionBinding
import com.prirai.android.nira.media.MediaTitleResolver
import java.util.IdentityHashMap
import kotlinx.serialization.Serializable

/**
 * Stage 2 diagnostic only. No mutations of MediaRepository, VideoTileItem or playback.
 * Native session + document epoch + frame + parser group are provisional evidence,
 * NOT a definitive media ID. In particular, group IDs never cross frames or profiles.
 * Raw titles/group IDs remain in this ephemeral collector only; neither is exported.
 */
@Serializable data class MediaIdentityShadowSummary(
    val enabled: Boolean = true,
    val changesPlaybackOrUi: Boolean = false,
    val sourceNodes: Int,
    val filmNodes: Int,
    val groupedFilms: Int,
    val provisionalVariantEdges: Int,
    val groupsWithConflictingTitles: Int,
    val groupsWithAmbiguousOwners: Int,
    val groupsWithoutIndividualTitles: Int,
    val manifestVariantHintsWithoutLinkedSources: Int,
    val crossFrameGroupIdCollisions: Int,
    val sourceGroupReassignments: Int,
    val sourceFrameReassignments: Int,
    val partitionResets: Int,
    val navigationEpochs: Int,
    val panelSnapshotsCompared: Int,
    val panelGroupCountDisagreements: Int,
    val panelShadowUnsafeGroups: Int,
    val audioTracksLinked: Int = 0,
    val subtitleTracksLinked: Int = 0,
    val trackLinkageAvailable: Boolean = false,
    val droppedSources: Int = 0,
)

internal class MediaIdentityShadowGraph(private val capacity: Int = 256) {
    init { require(capacity in 1..1024) }
    private class Scope(
        val serial: Int, var tab: String, var context: String?, var privateMode: Boolean,
        var epoch: Int = 0,
    )
    private data class Source(
        val scope: Int, val epoch: Int, val id: Long,
        var frame: Long?, var group: String?,
        var title: String?, var titleKind: String?, var ambiguousTitle: Boolean,
        var ambiguousOwner: Boolean, var format: String, var declaredVariants: Int,
        var hasPoster: Boolean, var displayed: Boolean = false,
    )
    private data class FilmKey(
        val scope: Int, val epoch: Int, val frame: Long?, val group: String?,
        val standalone: Long?,
    )
    private val sessions = IdentityHashMap<Any, Scope>()
    private val sources = LinkedHashMap<String, Source>()
    private var nextScope = 0
    private var navigationEpochs = 0
    private var partitionResets = 0
    private var groupReassignments = 0
    private var frameReassignments = 0
    private var panelSnapshots = 0
    private var panelDisagreements = 0
    private var panelUnsafe = 0
    private var dropped = 0

    private fun scope(binding: MediaSessionBinding): Scope? {
        val existing = sessions[binding.session]
        if (existing != null) {
            if (existing.tab != binding.tabId || existing.context != binding.contextId ||
                existing.privateMode != binding.isPrivate) {
                existing.epoch++
                existing.tab = binding.tabId
                existing.context = binding.contextId
                existing.privateMode = binding.isPrivate
                partitionResets++
            }
            return existing
        }
        if (sessions.size >= capacity) { dropped++; return null }
        return Scope(++nextScope, binding.tabId, binding.contextId, binding.isPrivate).also {
            sessions[binding.session] = it
        }
    }

    private fun nodeKey(owner: Scope, id: Long) = "${owner.serial}:${owner.epoch}:$id"
    private fun filmKey(node: Source) = FilmKey(
        node.scope, node.epoch, node.frame, node.group,
        if (node.group == null) node.id else null,
    )

    @Synchronized fun navigation(binding: MediaSessionBinding) {
        scope(binding)?.let { it.epoch++; navigationEpochs++ }
    }

    @Synchronized fun observe(binding: MediaSessionBinding, entry: MediaEntry) {
        val owner = scope(binding) ?: return
        val key = nodeKey(owner, entry.id)
        val old = sources[key]
        if (old == null) {
            if (sources.size >= capacity) { dropped++; return }
            sources[key] = Source(owner.serial, owner.epoch, entry.id, entry.frameKey,
                entry.groupId, entry.title, entry.titleKind, entry.titleAmbiguous,
                entry.frameAmbiguous, entry.format.name,
                entry.variantCount ?: 0, !entry.posterUrl.isNullOrBlank())
            return
        }
        // An anonymous stream may acquire a group after a manifest arrives.
        // A conflicting second group must not silently rewrite its shadow owner.
        if (old.group == null) old.group = entry.groupId
        else if (entry.groupId != null && old.group != entry.groupId) groupReassignments++
        // A first trusted frame key can strengthen an anonymous node.
        // Missing frame evidence on a later update does not invalidate it.
        if (old.frame == null && entry.frameKey != null) old.frame = entry.frameKey
        else if (entry.frameKey != null && old.frame != entry.frameKey) frameReassignments++
        old.title = entry.title
        old.titleKind = entry.titleKind
        old.ambiguousTitle = entry.titleAmbiguous
        old.ambiguousOwner = entry.frameAmbiguous
        old.declaredVariants = entry.variantCount ?: old.declaredVariants
        old.hasPoster = !entry.posterUrl.isNullOrBlank()
        old.format = entry.format.name
    }

    /** Panel observation is not proof of a drawn frame. */
    @Synchronized fun displayed(binding: MediaSessionBinding, entry: MediaEntry) {
        val owner = scope(binding) ?: return
        sources[nodeKey(owner, entry.id)]?.displayed = true
    }

    /** Compare the same snapshot to the current VideoTileItem group-key policy. */
    @Synchronized fun comparePanel(binding: MediaSessionBinding, entries: List<MediaEntry>) {
        val owner = scope(binding) ?: return
        if (entries.isEmpty()) return
        panelSnapshots++
        val legacyCount = entries.groupBy { it.frameKey to (it.groupId ?: "url:${it.url}") }.size
        val groups = entries.groupBy { entry ->
            FilmKey(owner.serial, owner.epoch, entry.frameKey, entry.groupId,
                if (entry.groupId == null) entry.id else null)
        }
        if (legacyCount != groups.size) panelDisagreements++
        panelUnsafe += groups.values.count { group ->
            val highest = group.maxOfOrNull { MediaTitleResolver.rank(it.titleKind) } ?: 0
            val conflict = highest > 1 && (
                group.any { it.titleAmbiguous && MediaTitleResolver.rank(it.titleKind) >= highest } ||
                group.filter { MediaTitleResolver.rank(it.titleKind) == highest }
                    .mapNotNull { it.title }.distinct().size > 1
            )
            group.size > 1 && (group.any { it.frameAmbiguous } || conflict)
        }
    }

    private fun hasTitleConflict(group: List<Source>): Boolean {
        val rank = group.maxOfOrNull { MediaTitleResolver.rank(it.titleKind) } ?: 0
        if (rank <= 1) return false
        return group.any { it.ambiguousTitle &&
            MediaTitleResolver.rank(it.titleKind) >= rank } ||
            group.filter { MediaTitleResolver.rank(it.titleKind) == rank }
                .mapNotNull { it.title }.distinct().size > 1
    }

    @Synchronized fun snapshot(): MediaIdentityShadowSummary {
        val nodes = sources.values.toList()
        val groups = nodes.groupBy(::filmKey)
        val crossFrame = nodes.filter { it.group != null }
            .groupBy { Triple(it.scope, it.epoch, it.group) }
            .values.count { members -> members.map { it.frame }.distinct().size > 1 }
        val multi = groups.values.count { it.size > 1 }
        return MediaIdentityShadowSummary(
            sourceNodes = nodes.size,
            filmNodes = groups.size,
            groupedFilms = multi,
            provisionalVariantEdges = groups.values.sumOf { (it.size - 1).coerceAtLeast(0) },
            groupsWithConflictingTitles = groups.values.count(::hasTitleConflict),
            groupsWithAmbiguousOwners = groups.values.count {
                it.any(Source::ambiguousOwner) || it.size > 1 && it.first().frame == null
            },
            groupsWithoutIndividualTitles = groups.values.count { group ->
                group.none { it.title != null && it.titleKind != "page" && !it.ambiguousTitle } ||
                    hasTitleConflict(group)
            },
            manifestVariantHintsWithoutLinkedSources = groups.values.count { group ->
                (group.maxOfOrNull { it.declaredVariants } ?: 0) > group.size
            },
            crossFrameGroupIdCollisions = crossFrame,
            sourceGroupReassignments = groupReassignments,
            sourceFrameReassignments = frameReassignments,
            partitionResets = partitionResets,
            navigationEpochs = navigationEpochs,
            panelSnapshotsCompared = panelSnapshots,
            panelGroupCountDisagreements = panelDisagreements,
            panelShadowUnsafeGroups = panelUnsafe,
            droppedSources = dropped,
        )
    }

    @Synchronized fun clear() { sessions.clear(); sources.clear() }
}

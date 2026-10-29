package com.prirai.android.nira.videosearch

import java.util.Locale

/**
 * Pure presentation of one existing normal-profile Room search run.
 * A film from another run/profile/site must never leak into this view.
 * The same URL at two selected websites remains TWO attributed cards.
 */
/** All rows in this snapshot come from one Room read transaction. */
internal data class VideoSearchSavedResults(
    val run: VideoSearchRun,
    val tasks: List<VideoSearchSiteTask>,
    val films: List<VideoSearchFilm>,
    val siteNames: Map<String, String>,
)

internal data class VideoSearchResultsSource(
    val siteId: String,
    val name: String,
    val state: VideoSearchSiteState,
    val count: Int,
    val limit: Int,
    val pages: Int,
    val reason: VideoSearchCompletionReason?,
    val issue: VideoSearchSiteIssue? = null,
)

internal data class VideoSearchResultsDisplay(
    val sources: List<VideoSearchResultsSource>,
    val visibleFilms: List<VideoSearchFilm>,
    val totalFilms: Int,
    val completedSites: Int,
    val partialSites: Int,
) {
    override fun toString() = "VideoSearchResultsDisplay(redacted)"
}

/**
 * A per-site group, in the original site order (not alphabetical order).
 * count is the actual number of saved unique cards, not a promised limit.
 */
internal data class VideoSearchResultsGroup(
    val source: VideoSearchResultsSource,
    val films: List<VideoSearchFilm>,
) {
    val savedCount: Int get() = films.size
    override fun toString() = "VideoSearchResultsGroup(redacted)"
}

/** Pure selection logic; null means every group expanded by default. */
internal object VideoSearchGroupExpansion {
    fun isExpanded(expandedIds: Set<String>?, siteId: String): Boolean =
        expandedIds == null || siteId in expandedIds

    fun toggle(expandedIds: Set<String>?, sources: List<VideoSearchResultsSource>,
               siteId: String): Set<String>? {
        val known = sources.map { it.siteId }.toSet()
        if (siteId !in known) return expandedIds
        val expanded = (expandedIds ?: known).intersect(known)
        val next = if (siteId in expanded) expanded - siteId else expanded + siteId
        return if (next.size == known.size) null else next
    }

    fun toggleAll(expandedIds: Set<String>?, sources: List<VideoSearchResultsSource>): Set<String>? {
        val known = sources.map { it.siteId }.toSet()
        if (known.isEmpty()) return expandedIds
        return if (expandedIds == null || known.all { it in expandedIds }) emptySet()
        else null
    }
}

internal object VideoSearchResultsPresenter {
    fun project(
        run: VideoSearchRun,
        tasks: List<VideoSearchSiteTask>,
        films: List<VideoSearchFilm>,
        siteNames: Map<String, String> = emptyMap(),
        selectedSiteId: String? = null,
        sort: VideoSearchSort = VideoSearchSort.ORIGINAL,
    ): VideoSearchResultsDisplay {
        require(run.scope is VideoSearchScope.Normal)
        require(tasks.all { it.runId == run.id && it.scope == run.scope })
        require(tasks.map { it.siteId }.distinct().size == tasks.size)
        val tasksBySite = tasks.associateBy { it.siteId }
        require(tasksBySite.keys.all { it in run.siteIds })
        require(films.all {
            it.scope == run.scope && it.runId == run.id &&
                it.siteId in tasksBySite
        })
        val rank = run.siteIds.withIndex().associate { it.value to it.index }
        val original = compareBy<VideoSearchFilm>(
            { rank[it.siteId] ?: Int.MAX_VALUE }, { it.originalIndex }, { it.id }
        )
        // Keep per-site duplicates collapsed by exact film-page URL. Do NOT
        // normalize query parameters, compare titles or dedupe across sites.
        val unique = films.sortedWith(original).distinctBy {
            it.siteId to it.filmPageUrl
        }
        val sources = run.siteIds.mapNotNull { id ->
            val task = tasksBySite[id] ?: return@mapNotNull null
            val name = unique.firstOrNull { it.siteId == id }?.siteName
                ?: siteNames[id] ?: "Witryna usunięta"
            VideoSearchResultsSource(
                id, name, task.status, task.uniqueResults,
                task.resultLimit, task.visitedPages, task.completionReason,
                task.issue.takeIf { it == VideoSearchSiteIssue.RESULT_BUDGET_REACHED }
            )
        }
        val visible = unique.filter {
            selectedSiteId == null || it.siteId == selectedSiteId
        }
        val sorting = when (sort) {
            VideoSearchSort.ORIGINAL -> original
            VideoSearchSort.TITLE_A_Z ->
                compareBy<VideoSearchFilm> { it.originalTitle == null }
                    .thenBy { it.originalTitle?.lowercase(Locale.ROOT) ?: "" }
                    .then(original)
            VideoSearchSort.TITLE_Z_A ->
                compareBy<VideoSearchFilm> { it.originalTitle == null }
                    .thenByDescending { it.originalTitle?.lowercase(Locale.ROOT) ?: "" }
                    .then(original)
            VideoSearchSort.DURATION_SHORTEST ->
                compareBy<VideoSearchFilm> { it.durationSeconds == null }
                    .thenBy { it.durationSeconds ?: Long.MAX_VALUE }.then(original)
            VideoSearchSort.DURATION_LONGEST ->
                compareBy<VideoSearchFilm> { it.durationSeconds == null }
                    .thenByDescending { it.durationSeconds ?: -1L }.then(original)
        }
        return VideoSearchResultsDisplay(
            sources = sources,
            visibleFilms = visible.sortedWith(sorting),
            totalFilms = unique.size,
            completedSites = tasks.count { it.status == VideoSearchSiteState.COMPLETED },
            partialSites = tasks.count {
                it.status != VideoSearchSiteState.COMPLETED &&
                    (it.uniqueResults > 0 || it.status in setOf(
                        VideoSearchSiteState.STOPPED, VideoSearchSiteState.FAILED,
                        VideoSearchSiteState.NEEDS_ATTENTION))
            },
        )
    }

    /**
     * Group the already-validated and globally sorted display in O(n + m).
     * Every selected source receives a header, including empty/failed sites.
     * Within each site, cards retain the selected user sort.
     */
    fun groups(
        display: VideoSearchResultsDisplay,
        selectedSiteId: String? = null,
    ): List<VideoSearchResultsGroup> {
        val bySite = display.visibleFilms.groupBy { it.siteId }
        return display.sources.asSequence()
            .filter { selectedSiteId == null || it.siteId == selectedSiteId }
            .map { source -> VideoSearchResultsGroup(source, bySite[source.siteId].orEmpty()) }
            .toList()
    }

    fun durationLabel(seconds: Long?): String? {
        if (seconds == null || seconds < 0) return null
        val hours = seconds / 3600
        val minutes = (seconds / 60) % 60
        val secs = seconds % 60
        return if (hours > 0)
            "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, secs)
        else "%d:%02d".format(Locale.ROOT, minutes, secs)
    }

    fun statusLabel(status: VideoSearchSiteState): String = when (status) {
        VideoSearchSiteState.QUEUED -> "Oczekuje"
        VideoSearchSiteState.ACTIVE -> "W trakcie"
        VideoSearchSiteState.PAUSED -> "Wstrzymano"
        VideoSearchSiteState.COMPLETED -> "Zakończono"
        VideoSearchSiteState.STOPPED -> "Częściowe / zatrzymane"
        VideoSearchSiteState.FAILED -> "Błąd"
        VideoSearchSiteState.NEEDS_ATTENTION -> "Wymaga uwagi"
    }

    fun budgetLabel(issue: VideoSearchSiteIssue?): String? =
        if (issue == VideoSearchSiteIssue.RESULT_BUDGET_REACHED)
            "Osiągnięto łączny limit 2000 filmów" else null

    fun completionLabel(reason: VideoSearchCompletionReason?): String? = when (reason) {
        VideoSearchCompletionReason.LIMIT_REACHED -> "Osiągnięto limit witryny"
        VideoSearchCompletionReason.NO_MORE_RESULTS -> "Brak kolejnych wyników"
        null -> null
    }
}

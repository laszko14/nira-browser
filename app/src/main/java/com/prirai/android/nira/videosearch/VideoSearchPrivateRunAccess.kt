package com.prirai.android.nira.videosearch

import java.util.UUID

internal data class VideoSearchPrivateRunSnapshot(
    val run: VideoSearchRun,
    val films: List<VideoSearchFilm>,
    val tasks: List<VideoSearchSiteTask> = emptyList(),
)

/** All private results and queries stay in the session-owned RAM store. */
internal class VideoSearchPrivateRunAccess(
    private val registry: VideoSearchPrivateSessionRegistry = VideoSearchPrivateSessionHost.registry,
) {
    fun selectedSite(tab: VideoSearchPrivateTab, profileId: String, siteId: String): VideoSearchSite? =
        registry.withAuthorizedTab(tab, profileId) { store, scope ->
            store.sites(scope).singleOrNull {
                it.id == siteId && it.enabled && it.id in store.settings(scope).selectedSiteIds
            }
        }

    /**
     * Preparation is atomic with respect to session closure. The returned
     * snapshot never confers future permission: each queued site must be
     * reauthorized immediately before visiting its already-open private tab.
     */
    fun prepareMulti(
        anchor: VideoSearchPrivateTab,
        profileId: String,
        query: String,
    ): VideoSearchPrivateMultiPlan? =
        registry.withAuthorizedTab(anchor, profileId) { store, scope ->
            try {
                VideoSearchPrivateMultiPlan.create(
                    scope, query, store.settings(scope), store.sites(scope)
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    fun record(
        tab: VideoSearchPrivateTab, profileId: String, siteId: String,
        query: String, cards: List<VideoSearchExtractedFilm>,
        now: Long = System.currentTimeMillis(),
        reason: VideoSearchPaginationStop? = null,
        visitedPages: Int = 1,
    ): Boolean = registry.withAuthorizedTab(tab, profileId) { store, scope ->
        if (!VideoSearchBridgeProtocol.validQuery(query) ||
            cards.size > 200 || visitedPages !in 1..12)
            return@withAuthorizedTab false
        val site = store.sites(scope).singleOrNull {
            it.id == siteId && it.enabled && it.id in store.settings(scope).selectedSiteIds
        } ?: return@withAuthorizedTab false
        if (cards.any { !VideoSearchBridgeProtocol.sameOrigin(it.filmPageUrl, site.siteUrl) })
            return@withAuthorizedTab false
        val id = UUID.randomUUID().toString()
        val films = cards.distinctBy { it.filmPageUrl }.mapIndexed { index, card ->
            VideoSearchFilm(
                id = UUID.randomUUID().toString(), runId = id, siteId = site.id,
                scope = scope, siteName = site.displayName,
                filmPageUrl = card.filmPageUrl, originalTitle = card.originalTitle,
                thumbnailUrl = card.thumbnailUrl, durationSeconds = card.durationSeconds,
                titleSource = card.titleSource, imageSource = card.imageSource,
                originalIndex = index, foundAtMs = now,
            )
        }
        // Only evidence from the bounded collector permits a complete run.
        val complete = (reason == VideoSearchPaginationStop.LIMIT_REACHED &&
            films.size == DEFAULT_VIDEO_SEARCH_SITE_LIMIT) ||
            reason == VideoSearchPaginationStop.NO_MORE_RESULTS
        val completion = if (complete) when (reason) {
            VideoSearchPaginationStop.LIMIT_REACHED ->
                VideoSearchCompletionReason.LIMIT_REACHED
            VideoSearchPaginationStop.NO_MORE_RESULTS ->
                VideoSearchCompletionReason.NO_MORE_RESULTS
            else -> null
        } else null
        store.putRun(VideoSearchRun(
            id = id, scope = scope, query = query, siteIds = listOf(site.id),
            parallelism = VideoSearchParallelism.ONE,
            status = if (complete) VideoSearchRunState.COMPLETED else VideoSearchRunState.STOPPED,
            startedAtMs = now, updatedAtMs = now, completedAtMs = now,
        ))
        store.putTask(VideoSearchSiteTask(
            runId = id, siteId = site.id, scope = scope,
            status = if (complete) VideoSearchSiteState.COMPLETED
                else VideoSearchSiteState.STOPPED,
            uniqueResults = films.size,
            resultLimit = DEFAULT_VIDEO_SEARCH_SITE_LIMIT,
            visitedPages = visitedPages,
            issue = if (complete) null else VideoSearchSiteIssue.OTHER,
            completionReason = completion,
        ))
        films.forEach { store.putFilm(it) }
        store.trimOldRuns(scope)
        true
    } ?: false

    /** Up to twelve latest site runs: no query or result crosses a lifetime. */
    fun history(tab: VideoSearchPrivateTab, profileId: String):
        List<VideoSearchPrivateRunSnapshot>? =
        registry.withAuthorizedTab(tab, profileId) { store, scope ->
            store.runs(scope).asReversed().take(12).map { run ->
                VideoSearchPrivateRunSnapshot(run, run.siteIds.flatMap { siteId ->
                    store.films(scope, run.id, siteId).sortedBy { it.originalIndex }
                }.take(VideoSearchPrivateGroupAccess.MAX_AGGREGATE_FILMS),
                    store.tasks(scope, run.id))
            }
        }

    fun latest(tab: VideoSearchPrivateTab, profileId: String): VideoSearchPrivateRunSnapshot? =
        registry.withAuthorizedTab(tab, profileId) { store, scope ->
            val run = store.runs(scope).maxWithOrNull(
                compareBy<VideoSearchRun> { it.startedAtMs }.thenBy { it.id }
            ) ?: return@withAuthorizedTab null
            VideoSearchPrivateRunSnapshot(run, run.siteIds.flatMap { siteId ->
                store.films(scope, run.id, siteId).sortedBy { it.originalIndex }
            }.take(VideoSearchPrivateGroupAccess.MAX_AGGREGATE_FILMS),
                store.tasks(scope, run.id))
        }
}

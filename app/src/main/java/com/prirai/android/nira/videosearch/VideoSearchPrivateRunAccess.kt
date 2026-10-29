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

    /** A private, single-site run is created in RAM before its Gecko work. */
    fun beginSingle(
        tab: VideoSearchPrivateTab, profileId: String, siteId: String, query: String,
    ): String? = registry.withAuthorizedTab(tab, profileId) { store, scope ->
        if (!VideoSearchBridgeProtocol.validQuery(query)) return@withAuthorizedTab null
        val site = store.sites(scope).singleOrNull {
            it.id == siteId && it.enabled && it.id in store.settings(scope).selectedSiteIds
        } ?: return@withAuthorizedTab null
        val id = UUID.randomUUID().toString()
        val stamp = System.currentTimeMillis()
        store.putRun(VideoSearchRun(
            id = id, scope = scope, query = query, siteIds = listOf(site.id),
            parallelism = VideoSearchParallelism.ONE,
            status = VideoSearchRunState.RUNNING, startedAtMs = stamp,
        ))
        store.putTask(VideoSearchSiteTask(
            runId = id, siteId = site.id, scope = scope, resultLimit = 200
        ))
        store.trimOldRuns(scope)
        id
    }

    /** Accept verified windows only for the same current private epoch. */
    fun checkpointSingle(
        tab: VideoSearchPrivateTab, profileId: String, runId: String,
        siteId: String, snapshot: VideoSearchPaginationSnapshot,
        terminal: Boolean = false,
    ): Boolean = registry.withAuthorizedTab(tab, profileId) { store, scope ->
        val run = store.runs(scope).singleOrNull {
            it.id == runId && it.status == VideoSearchRunState.RUNNING &&
                it.siteIds == listOf(siteId)
        } ?: return@withAuthorizedTab false
        val site = store.sites(scope).singleOrNull {
            it.id == siteId && it.enabled && it.id in store.settings(scope).selectedSiteIds
        } ?: return@withAuthorizedTab false
        val old = store.tasks(scope, runId).singleOrNull {
            it.siteId == siteId &&
                it.status in setOf(VideoSearchSiteState.QUEUED, VideoSearchSiteState.ACTIVE)
        } ?: return@withAuthorizedTab false
        if (snapshot.films.any {
                !VideoSearchBridgeProtocol.sameOrigin(site.siteUrl, it.filmPageUrl)
            }) return@withAuthorizedTab false
        val existing = store.films(scope, runId, siteId)
        val knownUrls = existing.mapTo(HashSet()) { it.filmPageUrl }
        val fresh = snapshot.films.filterNot { it.filmPageUrl in knownUrls }
        if (existing.size + fresh.size > 200) return@withAuthorizedTab false
        val stamp = maxOf(System.currentTimeMillis(), run.updatedAtMs + 1)
        fresh.forEachIndexed { index, film ->
            store.putFilm(VideoSearchFilm(
                id = UUID.nameUUIDFromBytes(
                    (runId + "|" + siteId + "|" + film.filmPageUrl).toByteArray(Charsets.UTF_8)
                ).toString(),
                runId = runId, siteId = siteId, scope = scope,
                siteName = site.displayName, filmPageUrl = film.filmPageUrl,
                originalTitle = film.originalTitle, thumbnailUrl = film.thumbnailUrl,
                durationSeconds = film.durationSeconds, titleSource = film.titleSource,
                imageSource = film.imageSource, originalIndex = existing.size + index,
                foundAtMs = stamp,
            ))
        }
        val count = existing.size + fresh.size
        val complete = terminal && when (snapshot.reason) {
            VideoSearchPaginationStop.NO_MORE_RESULTS -> true
            VideoSearchPaginationStop.LIMIT_REACHED -> count == 200
            else -> false
        }
        val completion = if (!complete) null else when (snapshot.reason) {
            VideoSearchPaginationStop.NO_MORE_RESULTS -> VideoSearchCompletionReason.NO_MORE_RESULTS
            VideoSearchPaginationStop.LIMIT_REACHED -> VideoSearchCompletionReason.LIMIT_REACHED
            else -> null
        }
        val status = if (!terminal) VideoSearchSiteState.ACTIVE else
            if (complete) VideoSearchSiteState.COMPLETED else VideoSearchSiteState.STOPPED
        store.putTask(old.copy(
            status = status, uniqueResults = count,
            visitedPages = maxOf(old.visitedPages, snapshot.visitedPages),
            issue = if (status == VideoSearchSiteState.STOPPED) VideoSearchSiteIssue.OTHER else null,
            completionReason = completion,
        ))
        store.putRun(run.copy(
            status = if (!terminal) VideoSearchRunState.RUNNING else
                if (complete) VideoSearchRunState.COMPLETED else VideoSearchRunState.STOPPED,
            updatedAtMs = stamp, completedAtMs = if (terminal) stamp else null,
        ))
        true
    } ?: false

    fun finishSingle(
        tab: VideoSearchPrivateTab, profileId: String, runId: String,
    ): Boolean = registry.withAuthorizedTab(tab, profileId) { store, scope ->
        val run = store.runs(scope).singleOrNull { it.id == runId }
            ?: return@withAuthorizedTab false
        if (run.status != VideoSearchRunState.RUNNING) return@withAuthorizedTab false
        store.tasks(scope, runId).filter {
            it.status in setOf(VideoSearchSiteState.QUEUED, VideoSearchSiteState.ACTIVE)
        }.forEach { task ->
            store.putTask(task.copy(status = VideoSearchSiteState.STOPPED,
                issue = VideoSearchSiteIssue.OTHER))
        }
        val stamp = maxOf(System.currentTimeMillis(), run.updatedAtMs + 1)
        store.putRun(run.copy(status = VideoSearchRunState.STOPPED,
            updatedAtMs = stamp, completedAtMs = stamp))
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

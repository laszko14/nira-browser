package com.prirai.android.nira.videosearch

import java.util.UUID

/**
 * One aggregated private multi-site search, stored only in the current RAM
 * lifetime. All writes are guarded by the tab registry's same session lock.
 * This is intentionally separate from the normal Room-backed run journal.
 */
internal class VideoSearchPrivateGroupAccess(
    private val registry: VideoSearchPrivateSessionRegistry =
        VideoSearchPrivateSessionHost.registry,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        /** Memory ceiling across ALL sites of one private grouped search. */
        const val MAX_AGGREGATE_FILMS = 2_000
    }

    fun start(
        anchor: VideoSearchPrivateTab, profileId: String,
        plan: VideoSearchPrivateMultiPlan,
    ): String? = registry.withAuthorizedTab(anchor, profileId) { store, scope ->
        if (scope != plan.owner ||
            store.settings(scope).selectedSiteIds.let { selected ->
                plan.sites.any { it.id !in selected }
            } ||
            plan.sites.any { planned ->
                store.sites(scope).none { it == planned && it.enabled }
            }) return@withAuthorizedTab null
        val id = UUID.randomUUID().toString()
        val stamp = now()
        val run = VideoSearchRun(
            id = id, scope = scope, query = plan.query,
            siteIds = plan.sites.map { it.id }, parallelism = VideoSearchParallelism.ONE,
            status = VideoSearchRunState.RUNNING, startedAtMs = stamp,
        )
        store.putRun(run)
        plan.sites.forEach { site ->
            store.putTask(VideoSearchSiteTask(
                runId = id, siteId = site.id, scope = scope, resultLimit = 200,
            ))
        }
        store.trimOldRuns(scope)
        id
    }

    /**
     * Records one site's terminal checkpoint exactly once, including absent
     * workers. A user may deselect or delete a site while it is running: the
     * old result is then rejected rather than silently assigned elsewhere.
     */
    fun checkpoint(
        anchor: VideoSearchPrivateTab, profileId: String,
        runId: String, site: VideoSearchSite,
        snapshot: VideoSearchPaginationSnapshot?,
        issue: VideoSearchSiteIssue = VideoSearchSiteIssue.OTHER,
    ): Boolean = registry.withAuthorizedTab(anchor, profileId) { store, scope ->
        val run = store.runs(scope).singleOrNull {
            it.id == runId && it.status == VideoSearchRunState.RUNNING
        } ?: return@withAuthorizedTab false
        if (run.scope != scope || site.scope != scope ||
            site.id !in run.siteIds) return@withAuthorizedTab false
        val old = store.tasks(scope, runId).singleOrNull {
            it.siteId == site.id && it.status == VideoSearchSiteState.QUEUED
        } ?: return@withAuthorizedTab false

        // Invalidated selections cannot write their stale search payload.
        val stillSelected = store.settings(scope).selectedSiteIds.contains(site.id) &&
            store.sites(scope).any { it == site && it.enabled }
        if (snapshot != null && !stillSelected) return@withAuthorizedTab false
        if (snapshot != null && (snapshot.films.size > 200 ||
                snapshot.visitedPages !in 1..12 ||
                snapshot.films.any {
                    !VideoSearchBridgeProtocol.sameOrigin(site.siteUrl, it.filmPageUrl)
                })) return@withAuthorizedTab false

        val consumed = run.siteIds.sumOf { id ->
            store.films(scope, runId, id).size
        }
        val space = (MAX_AGGREGATE_FILMS - consumed).coerceAtLeast(0)
        val unique = snapshot?.films?.distinctBy { it.filmPageUrl }.orEmpty()
        val truncated = unique.size > space
        val retained = unique.take(space)
        val complete = !truncated && when (snapshot?.reason) {
            VideoSearchPaginationStop.NO_MORE_RESULTS -> true
            VideoSearchPaginationStop.LIMIT_REACHED -> retained.size == 200
            else -> false
        }
        val reason = when (snapshot?.reason) {
            VideoSearchPaginationStop.NO_MORE_RESULTS ->
                if (complete) VideoSearchCompletionReason.NO_MORE_RESULTS else null
            VideoSearchPaginationStop.LIMIT_REACHED ->
                if (complete) VideoSearchCompletionReason.LIMIT_REACHED else null
            else -> null
        }
        val status = when {
            complete -> VideoSearchSiteState.COMPLETED
            snapshot == null -> VideoSearchSiteState.NEEDS_ATTENTION
            else -> VideoSearchSiteState.STOPPED
        }
        val stamp = maxOf(now(), run.updatedAtMs + 1)
        retained.forEachIndexed { index, film ->
            store.putFilm(VideoSearchFilm(
                id = UUID.randomUUID().toString(),
                runId = runId, siteId = site.id, scope = scope,
                siteName = site.displayName, filmPageUrl = film.filmPageUrl,
                originalTitle = film.originalTitle, thumbnailUrl = film.thumbnailUrl,
                durationSeconds = film.durationSeconds, titleSource = film.titleSource,
                imageSource = film.imageSource, originalIndex = index,
                foundAtMs = stamp,
            ))
        }
        store.putTask(old.copy(
            status = status, uniqueResults = retained.size,
            visitedPages = snapshot?.visitedPages ?: 0,
            issue = if (complete) null else issue,
            completionReason = reason,
        ))
        store.putRun(run.copy(updatedAtMs = stamp))
        true
    } ?: false

    /** Finalize all still-queued sites as stopped without inventing results. */
    fun finish(
        anchor: VideoSearchPrivateTab, profileId: String, runId: String,
    ): Boolean = registry.withAuthorizedTab(anchor, profileId) { store, scope ->
        val run = store.runs(scope).singleOrNull { it.id == runId }
            ?: return@withAuthorizedTab false
        if (run.status != VideoSearchRunState.RUNNING) return@withAuthorizedTab false
        store.tasks(scope, runId).filter {
            it.status == VideoSearchSiteState.QUEUED ||
                it.status == VideoSearchSiteState.ACTIVE
        }.forEach { queued ->
            store.putTask(queued.copy(
                status = VideoSearchSiteState.STOPPED,
                issue = VideoSearchSiteIssue.OTHER,
            ))
        }
        val terminal = store.tasks(scope, runId)
        val completed = terminal.size == run.siteIds.size &&
            terminal.all { it.status == VideoSearchSiteState.COMPLETED }
        val stamp = maxOf(now(), run.updatedAtMs + 1)
        store.putRun(run.copy(
            status = if (completed) VideoSearchRunState.COMPLETED
                else VideoSearchRunState.STOPPED,
            updatedAtMs = stamp, completedAtMs = stamp,
        ))
        true
    } ?: false
}
